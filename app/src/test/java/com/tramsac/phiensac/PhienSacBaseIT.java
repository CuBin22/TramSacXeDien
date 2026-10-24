package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.CongSacIT;
import com.tramsac.FakeMqttConfig;
import com.tramsac.FakeMqttGateway;
import com.tramsac.TestClockConfig;
import com.tramsac.mqtt.MqttMessageHandler;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cơ sở cho test giai đoạn 3 (UC10–UC21): thay MQTT bằng FakeMqttGateway; test đóng vai thiết bị bằng cách gọi
 * MqttMessageHandler.nhan(topic, payload). Các job quét (heartbeat, gửi lại lệnh, cảnh báo...) tắt bộ định thời nên
 * test tự tạo job và gọi quet() để kết quả xác định.
 */
@Import({TestClockConfig.class, FakeMqttConfig.class})
public abstract class PhienSacBaseIT extends CongSacIT {

    protected static final String CAU_HINH = "CH-TEST";

    @Autowired
    protected FakeMqttGateway mqtt;
    @Autowired
    protected MqttMessageHandler handler;

    protected record Canh(NguoiDung nd, Xe xe, String token, String maDatPhien, String maPhien, String maCong) { }

    @BeforeEach
    void datLaiMqttGia() {
        mqtt.datLai();
    }

    // ---------- dựng dữ liệu ----------

    /** Cấu hình 100 kW, một cổng AC rảnh và một đặt chỗ đang trong khung giờ (phiên ở Đã đặt). */
    protected Canh dungPhien(String maCong) throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong(maCong, "AC", "Rảnh", CAU_HINH, 0);
        return taoPhienChoCong(maCong);
    }

    /** Người dùng mới + xe AC + đặt chỗ đang trong khung giờ trên cổng đã có. */
    protected Canh taoPhienChoCong(String maCong) throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, bienSoMoi(), "AC");
        String token = dangNhapLayToken(nd);
        String maDat = taoDatChoTrucTiep(nd, xe, maCong, bayGio().minusMinutes(1), bayGio().plusHours(1),
                "Đã đặt", "Đã đặt");
        String maPhien = jdbc.queryForObject("SELECT MaPhien FROM PhienSac WHERE MaDatPhien = ?", String.class, maDat);
        return new Canh(nd, xe, token, maDat, maPhien, maCong);
    }

    protected String tokenVaiTro(VaiTro vaiTro) throws Exception {
        return dangNhapLayToken(taoNguoiDung(vaiTro));
    }

    // ---------- gọi API ----------

    protected static String khoaMoi() {
        return UUID.randomUUID().toString();
    }

    protected ResultActions batDau(String token, String maPhien, String khoa) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/phien/" + maPhien + "/bat-dau")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", khoa));
    }

    protected ResultActions dung(String token, String maPhien, String khoa) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/phien/" + maPhien + "/dung")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", khoa));
    }

    protected String maLenh(ResultActions r) throws Exception {
        return json(r.andReturn()).get("maLenh").asText();
    }

    /** Bắt đầu sạc qua API (202) rồi thiết bị ack OK; trả về mã lệnh bắt đầu. */
    protected String batDauThanhCong(Canh c) throws Exception {
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        ack(c.maCong(), ma, "OK", null);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");
        return ma;
    }

    // ---------- mô phỏng thiết bị ----------

    protected void ack(String maCong, String maLenh, String ketQua, Double dienNangKWh) {
        Map<String, Object> p = new HashMap<>();
        p.put("maLenh", maLenh);
        p.put("ketQua", ketQua);
        if (dienNangKWh != null) {
            p.put("dienNangKWh", dienNangKWh);
        }
        handler.nhan("tramsac/cong/" + maCong + "/ack", p);
    }

    protected void thietBiBaoLoi(String maCong, String loaiLoi) {
        Map<String, Object> p = new HashMap<>();
        p.put("loaiLoi", loaiLoi);
        handler.nhan("tramsac/cong/" + maCong + "/loi", p);
    }

    protected void thietBiHeartbeat(String maCong, boolean coXe) {
        Map<String, Object> p = new HashMap<>();
        p.put("coXe", coXe);
        handler.nhan("tramsac/cong/" + maCong + "/heartbeat", p);
    }

    protected void thietBiBaoTrangThai(String maCong, String trangThai, Double dienNangKWh) {
        Map<String, Object> p = new HashMap<>();
        p.put("trangThai", trangThai);
        if (dienNangKWh != null) {
            p.put("dienNangKWh", dienNangKWh);
        }
        handler.nhan("tramsac/cong/" + maCong + "/trangthai", p);
    }

    // ---------- truy vấn kiểm tra ----------

    protected String trangThaiPhienTheoMa(String maPhien) {
        return jdbc.queryForObject("SELECT TrangThai FROM PhienSac WHERE MaPhien = ?", String.class, maPhien);
    }

    protected int dem(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    protected static boolean laRong(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }
}
