# Truy vết UC → code → kiểm thử → trace

Bảng đối chiếu theo nhóm UC trong bản thiết kế ngày 13/10/2026 và tên lớp hiện có. Các sơ đồ cụ thể trong báo cáo Word cần gắn số hình khi hoàn thiện báo cáo; không gán số hình chưa có trong repo. `HTTP` nghĩa là span server tự động của Java agent; `JDBC` là span truy vấn tự động.

| UC | Chức năng / lớp chính | API hoặc tác vụ | Test | Span |
|---|---|---|---|---|
| 01 | Đăng ký / DangKyService | POST /api/auth/dang-ky | DangKyIT | HTTP, JDBC |
| 02 | Đăng nhập / DangNhapService | POST /api/auth/dang-nhap | DangNhapIT, JwtServiceTest | HTTP, JDBC |
| 03 | Hồ sơ và xe / HoSoService | /api/ho-so, /api/ho-so/xe | HoSoIT | HTTP, JDBC |
| 04 | Phân quyền / PhanQuyenService | PUT /api/nguoi-dung/{id}/vai-tro | PhanQuyenIT | HTTP, JDBC |
| 05 | Đặt phiên / DatPhienService | POST /api/dat-phien | DatPhienIT | HTTP, JDBC |
| 06 | Xung đột / KiemTraXungDotService | Trong thao tác đặt/đổi lịch | DatPhienKhongTrungGioIT | JDBC |
| 07 | Hủy/đổi lịch / DatPhienService | /api/dat-phien/{id}/huy, /doi-lich | HuyDoiLichIT | HTTP, JDBC |
| 08 | Phân bổ / PhanBoCongService, PhanBoLaiDatPhienService | Trong đặt và khi cổng không khả dụng | DatPhienIT, BaoTriCongIT, KetNoiThietBiIT | JDBC |
| 09 | Quá hạn / DatPhienJobs | /api/dat-phien/quet-qua-han hoặc scheduler | QuaHanVaGiuChoIT | HTTP, JDBC |
| 10 | Bắt đầu / PhienSacCommandService | POST /api/phien/{id}/bat-dau | LenhDieuKhienIT, E2E | phien.batDau, lenh.guiMqtt, lenh.nhanAck |
| 11 | An toàn / CongSac.kiemTraAnToan | Probe HOI_AN_TOAN trước cấp điện | AnToanThietBiIT, E2E | an_toan.kiemTra |
| 12 | Dừng / PhienSacCommandService | POST /api/phien/{id}/dung | DungSacVaTinhPhiIT, E2E | phien.dungSac, lenh.nhanAck |
| 13 | Xem phiên/phí tạm tính / PhienSacReadService | GET /api/phien, /api/phien/{id} | PhiTamTinhVaXemPhienIT | HTTP, JDBC |
| 14 | Biểu giá / BieuGiaService | POST /api/bieu-gia | DungSacVaTinhPhiIT | HTTP, JDBC |
| 15 | Tính phí / TinhPhiService | Khi phiên kết thúc | DungSacVaTinhPhiIT, E2E | phi.tinhPhi |
| 16 | Heartbeat / HeartbeatWatcherJob | MQTT heartbeat và scheduler | KetNoiThietBiIT | mqtt.nhan, JDBC |
| 17 | Quản lý trạng thái cổng / CongSacAdminService | /api/cong, /khoa, /mo, /xac-nhan-xu-ly | BaoTriCongIT, AnToanThietBiIT | HTTP, lenh.guiMqtt |
| 18 | Chiếm cổng / CanhBaoChiemCongService | /api/canh-bao-chiem-cong và scheduler | CanhBaoVaPhienChoIT | HTTP, JDBC |
| 19 | Lỗi thiết bị / DeviceFaultService | MQTT loi → dừng, khóa phiên/cổng | AnToanThietBiIT | mqtt.nhan, lenh.guiMqtt |
| 20 | Cân bằng tải / CanBangTaiService | Khi bắt đầu/dừng và cấu hình thay đổi | CanBangTaiIT | JDBC, lenh.guiMqtt |
| 21 | Công suất / CauHinhCongSuatService | PUT /api/cau-hinh-cong-suat/{id} | CanBangTaiIT | HTTP, JDBC |
| 22 | Đơn/hóa đơn / ThanhToanService | POST /api/thanh-toan/don, GET /api/thanh-toan/phien/{id}/hoa-don | ThanhToanIT | thanh_toan.taoDon |
| 23 | Cổng giả lập / PaymentSimGateway | HTTP /sim/orders | PaymentSimulatorTest, E2E | HTTP, payment.nhanDon |
| 24 | Callback / WebhookTxService | POST /api/webhook/thanh-toan | WebhookThanhToanIT, E2E | payment.guiCallback, thanh_toan.xuLyCallback |
| 25 | Hoàn tiền / HoanTienService | POST /api/hoan-tien | HoanTienIT, PaymentSimulatorTest, E2E | thanh_toan.hoanTien |
| 26 | Báo sự cố / SuCoController | POST /api/su-co | SuCoIT, E2E | HTTP, JDBC |
| 27 | Giao việc / SuCoController | PUT /api/su-co/{id}/giao-viec | SuCoIT, E2E | HTTP, JDBC |
| 28 | Cập nhật xử lý / SuCoController | PUT /api/su-co/{id}/trang-thai | SuCoIT, App.test.jsx, E2E | HTTP, JDBC |
| 29 | Tra cứu/lịch sử / SuCoController | GET /api/su-co, /api/su-co/{id}/lich-su | SuCoIT, App.test.jsx | HTTP, JDBC |
| 30 | Thống kê / BaoCaoController | GET /api/bao-cao | BaoCaoIT, E2E | HTTP, JDBC |

Kiểm thử quan sát riêng: `TraceContextTest` xác nhận quan hệ parent/child qua luồng MQTT giả lập và giải phóng scope; `TraceParentPersistenceIT` xác nhận lưu/khôi phục trace qua DB khi gửi lại; E2E đọc Jaeger API để xác nhận dữ liệu trace thực từ các container.
