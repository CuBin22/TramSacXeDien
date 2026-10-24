"""Isolated real PostgreSQL/MQTT/device/payment/Jaeger acceptance run.
Run from repository root: python scripts/verify-e2e.py
Requires Docker Compose and Python 3.11+. No third-party Python packages.
Only the uniquely named test project's volumes are removed on completion.
"""
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]

def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

def wait_for(description, operation, timeout=60):
    until = time.monotonic() + timeout
    last = None
    while time.monotonic() < until:
        try:
            result = operation()
            if result:
                print("PASS", description, flush=True)
                return result
        except (urllib.error.URLError, AssertionError, TimeoutError, ConnectionError) as exc:
            last = type(exc).__name__
        time.sleep(0.5)
    raise AssertionError(f"Timeout: {description}; last error={last}")

class Client:
    def __init__(self, base):
        self.base, self.token = base, None

    def request(self, method, path, body=None, key=None, expected=(200, 201, 202)):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if key:
            headers["Idempotency-Key"] = key
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            payload = response.read()
            result = json.loads(payload) if payload else None
            assert response.status in expected, f"{method} {path}: HTTP {response.status}: {result}"
            fresh = response.headers.get("X-New-Token")
            if fresh:
                self.token = fresh
            return result

    def login(self, email):
        self.token = self.request("POST", "/api/auth/dang-nhap", {"dinhDanh": email, "matKhau": "Demo1234"})["token"]
        return self

def exercise(app_port, device_port, jaeger_port, web_port):
    base = f"http://127.0.0.1:{app_port}"
    guest = Client(base)
    wait_for("app health", lambda: guest.request("GET", "/actuator/health")["status"] == "UP", 180)
    # HTTP health can become UP before the dev ApplicationRunner commits seeded accounts.
    admin = wait_for("dev seed committed", lambda: Client(base).login("admin@tramsac.local"), 30)
    user = Client(base).login("user@tramsac.local")
    tech = Client(base).login("ktv@tramsac.local")
    wait_for("real MQTT heartbeat", lambda: all(c["heartbeatCuoi"] for c in admin.request("GET", "/api/cong")))
    now = datetime.now(timezone.utc)
    booking = user.request("POST", "/api/dat-phien", {
        "bienSo": "59A-123.45", "loaiCong": "AC",
        "gioBatDau": (now + timedelta(minutes=1)).isoformat(),
        "gioKetThuc": (now + timedelta(minutes=61)).isoformat()})
    session, charger = booking["maPhien"], booking["maCong"]
    start_key = str(uuid.uuid4())
    start = user.request("POST", f"/api/phien/{session}/bat-dau", key=start_key)
    wait_for("device ACK starts charging", lambda: user.request("GET", f"/api/phien/{session}")["trangThai"] == "Đang sạc")
    replay = user.request("POST", f"/api/phien/{session}/bat-dau", key=start_key)
    assert replay["maLenh"] == start["maLenh"] and replay["idempotentReplay"]
    print("PASS start command idempotency", flush=True)
    time.sleep(3)  # generate measurable energy with the real device simulator
    user.request("POST", f"/api/phien/{session}/dung", key=str(uuid.uuid4()))
    wait_for("real meter and billing", lambda: user.request("GET", f"/api/phien/{session}")["trangThai"] == "Chờ thanh toán")
    bill = user.request("GET", f"/api/phien/{session}")
    assert bill["dienNangKWh"] > 0 and bill["tongPhi"] > 0
    order = user.request("POST", "/api/thanh-toan/don", {
        "maPhien": session, "phuongThuc": "THE_THU", "kichBan": "THANH_CONG", "cheDoCallback": "TRUNG"}, key=str(uuid.uuid4()))
    wait_for("signed payment callbacks finish session", lambda: user.request("GET", f"/api/phien/{session}")["trangThai"] == "Hoàn tất")
    refund_key = str(uuid.uuid4())
    refund_body = {"maGiaoDich": order["maGiaoDich"], "soTienHoan": order["soTien"], "lyDo": "Hủy phiên"}
    admin.request("POST", "/api/hoan-tien", refund_body, key=refund_key)
    assert admin.request("POST", "/api/hoan-tien", refund_body, key=refund_key)["idempotentReplay"]
    admin.request("POST", "/api/hoan-tien", refund_body, key=str(uuid.uuid4()), expected=(409,))
    print("PASS real refund and replay", flush=True)
    issue = user.request("POST", "/api/su-co", {"maCong": charger, "loaiSuCo": "Giắc sạc", "moTa": "Báo cáo kiểm thử E2E"})
    tech_id = tech.request("GET", "/api/ho-so")["maND"]
    issue_path = f'/api/su-co/{issue["maSuCo"]}'
    admin.request("PUT", issue_path+"/giao-viec", {"maKTV": tech_id})
    tech.request("PUT", issue_path+"/trang-thai", {"trangThai": "Đã xử lý", "nguyenNhan": "Giắc lỏng", "cachXuLy": "Gắn lại"})
    tech.request("PUT", issue_path+"/trang-thai", {"trangThai": "Đã đóng", "nguyenNhan": "Giắc lỏng", "cachXuLy": "Gắn lại"})
    assert len(user.request("GET", issue_path+"/lich-su")) == 4
    report = admin.request("GET", "/api/bao-cao")
    assert report["phien"]["phien_hoan_tat"] == 1 and report["thanhToan"]["doanh_thu"] > 0
    user.request("GET", "/api/bao-cao", expected=(403,))
    print("PASS incident lifecycle, report and authorization", flush=True)
    # The browser bundle is served by Nginx and the same-origin API proxy preserves JWT.
    with urllib.request.urlopen(f"http://127.0.0.1:{web_port}/", timeout=10) as response:
        assert 'id="root"' in response.read().decode()
    proxied = Client(f"http://127.0.0.1:{web_port}").login("admin@tramsac.local")
    assert proxied.request("GET", "/api/bao-cao")["phien"]["phien_hoan_tat"] == 1
    print("PASS web bundle and Nginx API proxy", flush=True)
    jaeger = Client(f"http://127.0.0.1:{jaeger_port}")
    def traces_linked():
        traces = jaeger.request("GET", "/api/traces?service=tramsac-app&limit=100")["data"]
        mqtt, payment, jdbc = False, False, False
        for trace in traces:
            services = {p["serviceName"] for p in trace["processes"].values()}
            names = {s["operationName"] for s in trace["spans"]}
            mqtt |= {"tramsac-app", "tramsac-device-sim"}.issubset(services) and "lenh.nhanAck" in names
            payment |= {"tramsac-app", "tramsac-payment-sim"}.issubset(services) and "thanh_toan.xuLyCallback" in names
            jdbc |= any(any(t["key"] in ("db.system", "db.system.name") for t in s.get("tags", [])) for s in trace["spans"])
        return mqtt and payment and jdbc
    wait_for("Jaeger linked MQTT + payment + JDBC spans", traces_linked, 60)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true", help="Keep the isolated containers for manual inspection")
    args = parser.parse_args()
    project = "tramsac-e2e-" + uuid.uuid4().hex[:8]
    ports = {k: port() for k in ("APP_HOST_PORT", "DB_HOST_PORT", "MQTT_HOST_PORT", "DEVICE_SIM_HOST_PORT", "PAYMENT_SIM_HOST_PORT", "WEB_HOST_PORT", "JAEGER_HOST_PORT")}
    config = {**ports, "SPRING_PROFILES_ACTIVE": "dev", "COMPOSE_PROFILES": "dev", "JWT_SECRET": secrets.token_hex(32),
              "DB_NAME": "tramsac", "DB_USER": "tramsac", "DB_PASSWORD": secrets.token_hex(16),
              "MQTT_USER": "tramsac", "MQTT_PASSWORD": secrets.token_hex(16),
              "PAYMENT_SIM_API_TOKEN": secrets.token_hex(16), "PAYMENT_WEBHOOK_SECRET": secrets.token_hex(32)}
    with tempfile.TemporaryDirectory(prefix="tramsac-e2e-") as directory:
        env = Path(directory)/"test.env"
        env.write_text("\n".join(f"{k}={v}" for k, v in config.items()), encoding="utf-8")
        compose = ["docker", "compose", "--project-name", project, "--env-file", str(env), "--file", str(ROOT/"docker-compose.yml")]
        print(f"Project: {project}; web={ports['WEB_HOST_PORT']}; Jaeger={ports['JAEGER_HOST_PORT']}", flush=True)
        try:
            subprocess.run(compose+["up", "-d", "--build"], cwd=ROOT, check=True)
            exercise(ports["APP_HOST_PORT"], ports["DEVICE_SIM_HOST_PORT"], ports["JAEGER_HOST_PORT"], ports["WEB_HOST_PORT"])
            print("ALL E2E CHECKS PASSED", flush=True)
        except Exception:
            subprocess.run(compose+["logs", "--tail=80", "app", "device-sim", "payment-sim", "otel-collector"], cwd=ROOT)
            raise
        finally:
            if not args.keep:
                subprocess.run(compose+["down", "--volumes", "--remove-orphans"], cwd=ROOT, check=True)

if __name__ == "__main__":
    main()
