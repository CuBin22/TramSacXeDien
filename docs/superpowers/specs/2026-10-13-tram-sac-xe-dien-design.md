# Bản thiết kế: Hệ thống quản lý trạm sạc xe điện quy mô khuôn viên

Ngày: 13/10/2026 · Tác giả: Trương Kiến Phi (MSSV 1250080141) · Trạng thái: chờ duyệt

## 1. Mục tiêu và phạm vi

Xây dựng hệ thống chạy được cho toàn bộ 30 use case (UC01–UC30) đã đặc tả ở chương I–IV, chạy đúng nghiệp vụ theo các sơ đồ đã vẽ (trạng thái, tuần tự, activity, lớp, ERD). Ba điểm nhấn: **idempotency lệnh**, **an toàn thiết bị**, **thanh toán trực tuyến giả lập**. Kèm báo cáo đúng 120 trang (từ bìa đến tài liệu tham khảo).

**Ngoài phạm vi:** OCPP và nhiều hãng thiết bị, thanh toán thật, ứng dụng di động riêng, microservice.

**Ràng buộc:** làm một mình, dưới 1 tháng. Ưu tiên khi thiếu thời gian: backend và test đầy đủ cho cả 30 UC; giao diện React làm gọn.

## 2. Quyết định đã chốt

| Mục | Quyết định |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Kiến trúc | Modular monolith phân lớp (Controller → Service → Repository → Entity) |
| CSDL | PostgreSQL 16, schema theo ERD 22 bảng, tạo bằng Flyway |
| Truyền thông thiết bị | MQTT (Mosquitto), QoS 1 kết hợp khóa idempotency |
| Giao diện | React |
| Triển khai | Docker Compose |
| Quan sát | OpenTelemetry + Jaeger |
| Thanh toán | `payment-sim` làm chính; ZaloPay Sandbox là phương thức thêm ở cuối giai đoạn 4 nếu còn thời gian |
| Báo cáo | Times New Roman 13, lề trái 3 cm, các lề khác 2 cm, giãn dòng 1.5, đánh số trang, 7 chương theo mẫu |

## 3. Kiến trúc tổng thể

### 3.1. Container

| Container | Công nghệ | Vai trò |
|---|---|---|
| `web` | React + Nginx | Giao diện 4 vai trò |
| `app` | Spring Boot | Toàn bộ nghiệp vụ, REST API, MQTT client, scheduler |
| `postgres` | PostgreSQL 16 | CSDL |
| `mosquitto` | Eclipse Mosquitto | MQTT broker |
| `device-sim` | Java (Paho) | Actor phụ "Thiết bị cổng sạc": nhận lệnh, ack, trạng thái, heartbeat 30 giây, giả lập lỗi |
| `payment-sim` | Spring Boot nhỏ | Actor phụ "Cổng thanh toán giả lập" |
| `otel-collector`, `jaeger` | OpenTelemetry | Thu và xem trace |

`device-sim` và `payment-sim` là module riêng trong cùng repo Maven; `app` chỉ giao tiếp với chúng qua MQTT và HTTP.

### 3.2. Cấu trúc package của `app`

```
com.tramsac
├── common      idempotency, audit (NhatKyHeThong), security (JWT, kiemTraQuyen), exception, tracing
├── nguoidung   NguoiDung, Xe, LichSuPhanQuyen                         UC01–04
├── datphien    DatPhien, kiemTraXungDot, phanBoCong                   UC05–09
├── congsac     CongSac, CauHinhCongSuat, LichSuKetNoi, canBangTai,
│               heartbeat watcher                                      UC11,16,17,20,21
├── phiensac    PhienSac, bảng chuyển trạng thái, LenhDieuKhien,
│               CanhBaoChiemCong                                       UC10,12,18,19
├── bieugia     BieuGia, ChiTietBieuGia, PhiSac, ChiTietPhi            UC14,15
├── thanhtoan   DonThanhToan, GiaoDichThanhToan, CallbackLog,
│               HoanTien, CongThanhToan (interface)                    UC22–25
├── suco        SuCo, LichSuSuCo                                       UC26–29
├── baocao      thống kê                                               UC30
└── mqtt        adapter MQTT
```

### 3.3. Quy tắc phụ thuộc

- Module chỉ gọi module khác qua Service, không truy cập Repository của module khác.
- Mọi thay đổi trạng thái phiên đi qua một hàm duy nhất `PhienSac.chuyenTrangThai()`: kiểm tra theo bảng hợp lệ, ghi `LichSuTrangThaiPhien`, cùng một giao dịch DB.
- Tác vụ nền (đặt chỗ quá hạn, heartbeat, nhắc chiếm cổng, hết hạn đơn thanh toán, quét lệnh chờ ack, chuyển cổng sang Đã đặt) là scheduler gọi vào cùng Service với luồng thủ công; mỗi chức năng chỉ có một luồng nghiệp vụ.
- Tên lớp, thuộc tính, phương thức giữ nguyên chương III để đối chiếu với sơ đồ.

## 4. Quy tắc nghiệp vụ đã làm rõ (bổ sung cho BM/QĐ)

Các quy tắc này lấp chỗ sơ đồ và đặc tả mâu thuẫn hoặc để ngỏ; code và test theo đúng các quy tắc này.

| # | Quy tắc |
|---|---|
| 1 | Hoàn tiền áp dụng cho giao dịch Thành công của phiên **Hoàn tất**; phiên giữ Hoàn tất. Quản trị hoàn với lý do Thiết bị lỗi (có sự cố liên quan) hoặc Hủy phiên (khiếu nại). Bỏ bước hoàn tự động trong UC19. |
| 2 | Giữ chỗ theo khoảng thời gian trong `DatPhien`. Cổng chuyển sang Đã đặt khi đến cửa sổ giờ của đặt chỗ (mặc định 15 phút trước giờ bắt đầu, có thể cấu hình), và về Rảnh khi hủy, quá hạn hoặc bắt đầu sạc. |
| 3 | Cổng mất kết nối từ Rảnh, Đã đặt hoặc Đang sạc (nhớ trạng thái trước). Khôi phục: hỏi trạng thái thực; đang sạc thì về Đang sạc; Rảnh thì kết thúc phiên theo điện năng thiết bị báo; Lỗi thì sang Lỗi. Cổng Bảo trì giữ nguyên. |
| 4 | `PhienSac` tạo cùng giao dịch với `DatPhien`. `DatPhien.huy()` và `danhDauQuaHan()` gọi `PhienSac.chuyenTrangThai(Hủy)` trong cùng giao dịch. |
| 5 | Từ Đang sạc, người dùng chỉ dừng sạc; chỉ hệ thống mới chuyển sang Hủy (thiết bị báo lỗi). Từ Chờ, người dùng được hủy. |
| 6 | Hủy do thiết bị lỗi thì không giải phóng cổng; cổng giữ Lỗi đến khi Kỹ thuật viên xác nhận (`xacNhanXuLy()`). Phiên Hủy do lỗi không tính phí. |
| 7 | Điện năng chia cho các khung giờ theo tỷ lệ thời gian sạc trong mỗi khung; tổng bằng điện năng thiết bị báo. |
| 8 | Từ Chờ cho phép thử lại bắt đầu sạc với khóa idempotency mới hoặc hủy. Phiên Chờ quá 15 phút tự Hủy. |
| 9 | Đặt chỗ chỉ bị đánh dấu Quá hạn không đến khi `PhienSac` còn ở Đã đặt. |
| 10 | Khi cổng sang Lỗi, Bảo trì hoặc Mất kết nối, các đặt chỗ tương lai gán cổng đó được phân bổ lại (UC08) sang cổng cùng loại còn trống; không có thì hủy và thông báo. |
| 11 | Không có phí hủy sát giờ. Người dùng không đến hoặc hủy sát giờ (dưới 15 phút) 3 lần trong 30 ngày bị hạn chế đặt chỗ 24 giờ. |
| 12 | Công suất tối thiểu của cổng = 20% định mức. Khi tổng công suất đang cấp vượt `Pđấu nối × ngưỡng%`, nhân mỗi cổng đang sạc với `hệ số = (Pđấu nối × ngưỡng%) / Σ định mức` (hệ số tối đa 1) để tổng đúng bằng ngưỡng. |
| 13 | Người dùng còn phiên Chờ thanh toán thì không được đặt chỗ mới. |
| 14 | Nhân viên trực và Quản trị được dừng phiên qua cùng API `dungSac` với khóa idempotency; ghi nguồn thao tác. |
| 15 | Không cho đổi vai trò của Quản trị viên cuối cùng. |

## 5. Idempotency

- Áp dụng cho: bắt đầu/dừng sạc (`LenhDieuKhien`), tạo đơn thanh toán (`DonThanhToan`), hoàn tiền (`HoanTien`). Client gửi header `Idempotency-Key` (UUID); lưu vào cột `KhoaIdempotency` UNIQUE đã có trong ERD.
- Cách xử lý "ghi trước, kiểm tra sau": trong một giao dịch, `INSERT` bản ghi mang khóa; vi phạm UNIQUE thì đọc bản ghi cũ và trả kết quả cũ (header `Idempotent-Replay: true`), không chạy lại nghiệp vụ. Cùng khóa nhưng khác nội dung thì trả 409.
- Tính phí một lần: `PhiSac` UNIQUE(MaPhien) và cờ `DaTinh`.
- Callback trùng: UNIQUE(MaGiaoDich, SuKien) trong `CallbackLog`.
- Phía thiết bị: mỗi lệnh MQTT mang `maLenh`; `device-sim` nhớ các `maLenh` đã xử lý, nhận lại thì gửi lại ack cũ, không thực thi lần hai.

## 6. Lệnh điều khiển và ack (UC10, UC12)

Hai giao dịch, không giữ giao dịch DB mở khi chờ ack:

1. **Giao dịch 1:** kiểm tra khóa → Đã đặt → Chờ → `kiemTraAnToan` → tạo `LenhDieuKhien` (chờ ack) → publish MQTT QoS 1. API trả ngay "đang chờ thiết bị".
2. **Chờ ack:** scheduler quét lệnh chờ ack quá 10 giây, `guiLai()` tối đa 3 lần, rồi đánh dấu Thất bại; phiên giữ Chờ (xem quy tắc 8).
3. **Giao dịch 2:** có ack → lệnh Thành công → Chờ → Đang sạc → cổng Đang sạc; ghi lịch sử.
4. Ack muộn sau khi lệnh Thất bại: hỏi trạng thái thực rồi mới cập nhật.

Giao diện nhận thay đổi trạng thái qua SSE (hoặc polling vài giây) để đạt NFR06.

## 7. An toàn thiết bị

Nguyên tắc **đóng khi nghi ngờ** (NFR02):

- Một cổng chặn duy nhất `CongSac.kiemTraAnToan()` mà mọi đường cấp điện phải đi qua. Kiểm tra theo thứ tự: (1) cổng không Lỗi, Bảo trì, Mất kết nối và `KhoaPhien = false`; (2) thiết bị xác nhận an toàn qua MQTT, không trả lời hoặc quá thời gian thì Từ chối; (3) còn đủ công suất tối thiểu. Mọi lần từ chối ghi lý do.
- Khi thiết bị báo lỗi (rò điện, quá dòng, dừng khẩn cấp), `thietBiBaoLoi()` chạy ưu tiên cao: gửi lệnh dừng trước; rồi khóa phiên, cổng sang Lỗi; tạo sự cố Khẩn cấp (gộp vào sự cố cùng loại đang mở); thông báo người dùng và nhân viên. Cổng Lỗi chỉ thoát qua `xacNhanXuLy()` của Kỹ thuật viên khi thiết bị xác nhận an toàn; hệ thống không tự mở lại.
- Mất kết nối: heartbeat 30 giây; scheduler mỗi 30 giây chuyển cổng quá 90 giây không heartbeat sang Mất kết nối; quá 5 phút tự tạo sự cố; khôi phục theo quy tắc 3.
- Chống xung đột đồng thời: `SELECT ... FOR UPDATE` trên `CongSac`/`PhienSac` khi chuyển trạng thái và gán cổng; việc gán cổng nằm trong một giao dịch.

## 8. Thanh toán giả lập

### 8.1. Luồng (UC22 → UC23 → UC24)

1. Người dùng gửi `POST /api/thanh-toan/don` kèm `Idempotency-Key`, phương thức và kịch bản.
2. `app` kiểm tra khóa, kiểm tra phiên đang Chờ thanh toán, tạo `DonThanhToan` (số tiền = `PhiSac.TongPhi`, hạn 15 phút, `CheDoGiaLap = true`), gọi cổng qua interface `CongThanhToan`.
3. `payment-sim` gửi webhook về `POST /api/webhook/thanh-toan`, ký HMAC-SHA256 (`X-Timestamp`, `X-Signature`; ký trên `timestamp + "." + body`).
4. `app` xử lý: xác thực chữ ký và timestamp (lệch quá 5 phút thì từ chối) → kiểm tra trùng → kiểm tra thứ tự → cập nhật giao dịch (Thành công thì phiên Hoàn tất trong cùng giao dịch DB) → ghi `CallbackLog` → trả 200.

### 8.2. `payment-sim`

- API: `POST /sim/orders`, `GET /sim/orders/{id}`, `POST /sim/refunds` (idempotent theo khóa), `POST /sim/orders/{id}/gui-lai`. Dữ liệu trong bộ nhớ.
- 5 kịch bản: Thành công, Thất bại, Không đủ số dư (gửi ngay), Hết hạn (chờ rồi gửi), Chậm phản hồi (trả Đang xử lý, chờ `doTreGiay` ≤ 60 giây rồi gửi). Kịch bản do người kiểm thử chọn (mặc định Thành công); với thẻ thử nghiệm có thể gắn theo số thẻ.
- 4 chế độ callback: `BINH_THUONG`, `TRUNG` (gửi 3 lần), `SAI_THU_TU` (kết quả cuối gửi trước, trạng thái xử lý gửi sau), `SAI_CHU_KY`.
- Quy tắc thứ tự: mỗi sự kiện có hạng (Chờ < Chậm < kết thúc); hạng thấp hơn trạng thái hiện tại thì ghi "Sai thứ tự" và bỏ qua; giao dịch đã kết thúc thì mọi callback khác chỉ ghi nhận.
- Chỉ chấp nhận số thẻ thử nghiệm; không lưu số thẻ; giao diện luôn hiển thị nhãn "Chế độ giả lập".

### 8.3. Hoàn tiền

`POST /api/hoan-tien` kèm khóa idempotency. Điều kiện: giao dịch Thành công, chưa hoàn, số tiền hoàn không vượt số đã trả, phiên Hoàn tất (quy tắc 1). Mỗi giao dịch hoàn một lần.

### 8.4. ZaloPay Sandbox (tùy chọn)

Bản cài đặt `ZaloPaySandboxGateway` của `CongThanhToan`, hiển thị thêm phương thức "ZaloPay (Sandbox)". Callback của ZaloPay được quy đổi về cùng các sự kiện nội bộ nên dùng chung toàn bộ xử lý ở 8.1. Hạn chế đã biết: ZaloPay chỉ gửi callback khi thanh toán thành công, nên các nhánh lỗi và callback trùng/sai thứ tự vẫn kiểm thử bằng `payment-sim`. Làm cuối giai đoạn 4 nếu còn thời gian.

## 9. Giao thức MQTT

| Topic | Hướng | QoS | Nội dung |
|---|---|---|---|
| `tramsac/cong/{maCong}/lenh` | app → thiết bị | 1 | `BAT_DAU`, `DUNG`, `DAT_CONG_SUAT`, `HOI_AN_TOAN`, `HOI_TRANG_THAI` |
| `tramsac/cong/{maCong}/ack` | thiết bị → app | 1 | Ack (kèm `maLenh`) |
| `tramsac/cong/{maCong}/trangthai` | thiết bị → app | 1 | Trạng thái, công suất, điện năng tích lũy |
| `tramsac/cong/{maCong}/heartbeat` | thiết bị → app | 0 | Nhịp tim 30 giây |
| `tramsac/cong/{maCong}/loi` | thiết bị → app | 1 | `RO_DIEN`, `QUA_DONG`, `DUNG_KHAN_CAP` |

Payload JSON. Ví dụ lệnh: `{"maLenh":"LNH-0001","loai":"BAT_DAU","maPhien":"PS-0001","guiLuc":"...","traceparent":"..."}`; ack: `{"maLenh":"LNH-0001","ketQua":"OK","ackLuc":"...","dienNangKWh":0.0}`. Broker bật xác thực tài khoản; TLS ở profile `demo`.

## 10. API và phân quyền

| Nhóm | Endpoint (ví dụ) | Vai trò |
|---|---|---|
| Tài khoản | `POST /api/auth/dang-ky`, `/dang-nhap`, `PUT /api/ho-so`, `PUT /api/nguoi-dung/{id}/vai-tro` | Mọi vai trò; đổi vai trò chỉ Quản trị |
| Đặt phiên | `POST /api/dat-phien`, `PUT .../doi-lich`, `DELETE .../{id}` | Người dùng |
| Phiên sạc | `POST /api/phien/{id}/bat-dau`, `/dung`, `GET /api/phien` | Người dùng; Nhân viên trực và Quản trị (dừng) |
| Cổng sạc | `GET /api/cong`, SSE `/api/cong/stream`, `POST /api/cong/{id}/khoa`, `/mo` | Nhân viên trực (xem); Kỹ thuật viên và Quản trị (khóa/mở) |
| Cấu hình | `PUT /api/bieu-gia`, `PUT /api/cau-hinh-cong-suat` | Quản trị |
| Thanh toán | `POST /api/thanh-toan/don`, `POST /api/hoan-tien`, `POST /api/webhook/thanh-toan` | Người dùng; Quản trị (hoàn tiền); webhook xác thực bằng chữ ký |
| Sự cố | `POST /api/su-co`, `PUT .../giao-viec`, `PUT .../trang-thai` | Người dùng báo; Quản trị/Nhân viên trực giao; Kỹ thuật viên xử lý |
| Báo cáo | `GET /api/bao-cao` | Quản trị |

JWT hết hạn sau 30 phút không hoạt động (QĐ2); `kiemTraQuyen()` kiểm tra ở mọi API (QĐ4).

## 11. Giao diện React (gọn, khoảng 14 màn hình)

- Chung: Đăng nhập/Đăng ký, Hồ sơ và xe.
- Người dùng: Đặt phiên (kèm hủy/đổi lịch), Phiên sạc của tôi (bắt đầu/dừng, phí tạm tính, lịch sử), Thanh toán, Báo sự cố.
- Nhân viên trực: Bảng trạng thái cổng thời gian thực, Cảnh báo chiếm cổng, Danh sách sự cố.
- Kỹ thuật viên: Sự cố được giao (cập nhật xử lý, khóa/mở cổng).
- Quản trị: Phân quyền, Biểu giá và công suất, Giao dịch và hoàn tiền, Báo cáo.
- Trang "Điều khiển mô phỏng" (chỉ profile `dev`): bật lỗi thiết bị, cắt kết nối, chọn kịch bản thanh toán và chế độ callback.

## 12. Chiến lược kiểm thử

| Tầng | Công cụ | Phạm vi |
|---|---|---|
| Đơn vị | JUnit 5, Mockito | Bảng chuyển trạng thái, tính phí và chia khung giờ, cân bằng tải, thứ tự callback, kiểm tra khóa |
| Tích hợp | Spring Boot Test + Testcontainers (PostgreSQL, Mosquitto) | Giao dịch, UNIQUE, `FOR UPDATE`, lệnh/ack qua MQTT thật |
| Kịch bản nghiệp vụ | Testcontainers + `device-sim`, `payment-sim` thật | Luồng đặt → sạc → tính phí → thanh toán → hoàn tất |
| Giao diện | Thủ công theo kịch bản demo | Màn hình chính từng vai trò |

Bám sơ đồ: sơ đồ trạng thái sinh test (mỗi mũi tên một test dương; mọi cặp trạng thái không có mũi tên có test âm); sơ đồ tuần tự kiểm tra bằng thứ tự lời gọi (`InOrder`) và thứ tự span.

**10 kịch bản demo cố định** (mỗi cái có test tự động): (1) luồng chính tới Hoàn tất; (2) gửi lại lệnh cùng khóa không sạc hai lần; (3) không có ack → gửi lại 3 lần → Thất bại, phiên giữ Chờ; (4) rò điện giữa phiên → phiên Hủy, cổng Lỗi, sự cố Khẩn cấp, không tính phí; (5) mất heartbeat 90 giây → Mất kết nối → khôi phục hỏi trạng thái thực; (6) thanh toán thất bại rồi thanh toán lại thành công; (7) callback trùng, sai thứ tự, sai chữ ký; (8) hai người đặt đồng thời cổng cuối cùng; (9) vượt ngưỡng công suất → giảm theo tỷ lệ; (10) hoàn tiền chỉ một lần.

## 13. Bảng truy vết

Bảng 30 dòng (đưa vào báo cáo): UC → sơ đồ (activity, sequence, trạng thái) → lớp/phương thức → API → test → span trace. Ví dụ UC10: Activity/Sequence/Trạng thái PhienSac → `PhienSac.batDau()`, `LenhDieuKhien.guiLenh()` → `POST /phien/{id}/bat-dau` → `PhienSacBatDauTest` → `phien.batDau` → `an_toan.kiemTra` → `lenh.guiMqtt` → `lenh.nhanAck`.

## 14. Triển khai và quan sát

- `docker compose up` dựng toàn bộ container ở mục 3.1. Hai profile: `dev` (tắt TLS, dữ liệu mẫu, trang Điều khiển mô phỏng) và `demo` (TLS cho MQTT nếu kịp).
- Cấu hình qua biến môi trường (khóa webhook, mật khẩu DB, tài khoản MQTT), không đưa vào mã nguồn.
- Flyway tạo schema 22 bảng và dữ liệu mẫu (cổng AC/DC, biểu giá, tài khoản 4 vai trò, cấu hình công suất). Healthcheck cho `postgres`, `mosquitto`, `app`.
- OpenTelemetry: tự động cho HTTP, JDBC, log gắn `traceId`; span thủ công cho mỗi bước chính của sơ đồ tuần tự (tên khớp phương thức); truyền `traceparent` qua payload MQTT để trace nối liền từ API tới lệnh MQTT, ack và thanh toán (NFR09). Metric đơn giản nếu kịp: số lần gửi lại lệnh, số callback trùng, số lần từ chối an toàn.

## 15. Kế hoạch và rủi ro

| Giai đoạn | Nội dung | Thời lượng |
|---|---|---|
| 1 | Nền tảng, schema, xác thực, Docker Compose (UC01–04) | 3 ngày |
| 2 | Đặt phiên, phân bổ cổng (UC05–09) | 3 ngày |
| 3 | Lõi sạc, MQTT, simulator, an toàn, tính phí (UC10–21) | 6 ngày |
| 4 | Thanh toán, `payment-sim`, hoàn tiền (UC22–25); ZaloPay nếu còn thời gian | 4 ngày |
| 5 | Sự cố, báo cáo, giao diện React (UC26–30) | 4 ngày |
| 6 | Kiểm thử, OpenTelemetry, sửa lỗi theo sơ đồ | 3 ngày |
| Báo cáo | Chương V, VI, VII, chỉnh định dạng, đủ 120 trang | 4–5 ngày, làm xen kẽ |

**Rủi ro chính:** tổng khối lượng so với một người dưới 1 tháng. Giảm thiểu bằng modular monolith, giao diện gọn, ZaloPay là phần tùy chọn, dùng simulator thay phần cứng. Nếu trễ, cắt theo thứ tự: ZaloPay → metric → TLS → màn hình phụ của giao diện → độ đẹp giao diện.

## 16. Báo cáo 120 trang

- Cấu trúc theo mẫu: Chương I (giới thiệu, khảo sát, yêu cầu, biểu mẫu); II (use case); III (phân tích: lớp, trạng thái, tuần tự, hoạt động); IV (thiết kế dữ liệu); V (kiến trúc); VI (giao diện); VII (kết luận: môi trường phát triển và hướng triển khai, kết quả đạt được kèm kiểm thử, hướng phát triển, bảng phân chia công việc); tài liệu tham khảo.
- Sơ đồ: sơ đồ chính vẽ cả trang; các sơ đồ còn lại thu nhỏ 2–4 sơ đồ mỗi trang (tạm chọn, có thể đổi khi ráp).
- Dựng bằng Word, chuyển sang PDF để đếm trang thật, chỉnh nội dung cho đúng 120 trang từ bìa đến tài liệu tham khảo.
- Phần chữ chương I–IV hiện khoảng 61 trang ở định dạng cũ; sau khi chuyển sang giãn dòng 1.5 ước tính khoảng 80–85 trang.
- Sơ đồ và đặc tả cần sửa nhỏ cho khớp các quy tắc ở mục 4: sơ đồ trạng thái CongSac (mất kết nối từ nhiều trạng thái, nhãn "do thiết bị lỗi"), sơ đồ trạng thái PhienSac (Hủy từ Đang sạc do hệ thống, thử lại tại Chờ), đặc tả UC19, UC21, UC22, UC25 (cập nhật phần Open Issues).
