# Tiến độ đồ án: Hệ thống quản lý trạm sạc xe điện quy mô khuôn viên

Cập nhật: 19/10/2026 (kết quả Java, React và E2E ngày 18/10 đạt; chi tiết trong `GIAI_DOAN_6.md`) · Sinh viên: Trương Kiến Phi (MSSV 1250080141) · Làm một mình, hạn nộp dưới 1 tháng tính từ 13/10/2026

Tài liệu liên quan: `docs/superpowers/specs/2026-10-13-tram-sac-xe-dien-design.md` (thiết kế), `docs/superpowers/plans/2026-10-13-giai-doan-1-nen-tang.md` (kế hoạch giai đoạn 1), `docs/superpowers/plans/2026-10-17-giai-doan-3-bo-sung.md` (bổ sung giai đoạn 3), `docs/superpowers/plans/2026-10-17-giai-doan-4-hoan-thien.md` (hoàn thiện giai đoạn 4), `README.md` (cách chạy).

## 1. Chú giải trạng thái

| Ký hiệu | Ý nghĩa |
|---|---|
| ✅ Xong | Đã viết xong và **đã được chạy thật trên máy bạn, kết quả đạt** |
| 🟡 Chờ xác nhận | Đã viết xong và commit, **chưa có kết quả chạy `mvn test` trên máy bạn** |
| ⬜ Chưa làm | Chưa bắt đầu |

## 2. Tổng quan theo giai đoạn

| Giai đoạn | Nội dung | Use case | Dự kiến | Trạng thái |
|---|---|---|---|---|
| 1 | Nền tảng, schema 22 bảng, xác thực, phân quyền, Docker Compose | UC01–UC04 | 3 ngày | ✅ Xong (Task 1–8, 89 test) |
| 2 | Đặt phiên, phân bổ cổng, kiểm tra xung đột, hủy/đổi lịch, giải phóng cổng quá hạn | UC05–UC09 | 3 ngày | ✅ Pass theo xác nhận người dùng |
| 3 | Lõi sạc: MQTT, device-sim, vòng đời phiên, lệnh/ack idempotent, an toàn thiết bị, heartbeat, cân bằng tải, tính phí | UC10–UC21 | 6 ngày | ✅ Pass theo xác nhận người dùng, gồm phần bổ sung 17/10 (phí tạm tính, sửa lỗi, 61 test mới) |
| 4 | Thanh toán giả lập: payment-sim, webhook, callback trùng/sai thứ tự, hoàn tiền (ZaloPay Sandbox nếu còn thời gian) | UC22–UC25 | 4 ngày | ✅ Suite Java và E2E thanh toán/hoàn tiền đạt |
| 5 | Sự cố, báo cáo thống kê, giao diện React (~14 màn hình) | UC26–UC30 | 4 ngày | Đã có backend và 4 màn hình chính; còn thiếu các màn hình theo thiết kế |
| 6 | Kiểm thử xuyên suốt, OpenTelemetry, sửa lỗi theo sơ đồ, bảng truy vết | tất cả | 3 ngày | ✅ Đã triển khai; Java, React, E2E và trace Jaeger đạt; xem `GIAI_DOAN_6.md` |
| Báo cáo | Chương V, VI, VII, chỉnh định dạng, đúng 120 trang | | 4–5 ngày | ⬜ (chương I–IV đã có nội dung, cần chỉnh) |

## 3. Giai đoạn 1: chi tiết từng task

Tổng số test: **89, tất cả đã pass** trên máy bạn (`mvn -pl app test`), và script thử nhanh `scripts\smoke-test.ps1` đã chạy đạt.

| Task | Nội dung | Số test | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| 1 | Khung dự án Spring Boot, Docker Compose, test nền Testcontainers | 1 | ✅ Xong | `HealthIT` pass; `docker compose up` trả `{"status":"UP"}` |
| 2 | Schema 22 bảng bằng Flyway (`V1__schema.sql`), 25 khóa ngoại, UNIQUE/CHECK | 7 | ✅ Xong | `SchemaIT` 7/7 pass |
| 3 | Nền chung: `ApiException`, `GlobalExceptionHandler`, `MaGenerator`, `ClockConfig`, `AuditService` | 5 | ✅ Xong | Bạn báo pass sau khi thêm surefire `*IT` |
| 4 | UC01 Đăng ký tài khoản | 12 | ✅ Xong | Bạn báo pass (gồm test đăng ký đồng thời cùng email) |
| 5 | UC02 Đăng nhập, JWT 30 phút trượt, khóa 15 phút sau 5 lần sai, phân quyền phía server | 27 | ✅ Xong | 52/52 pass sau khi sửa lỗi đọc bản cũ khi khóa dòng |
| 6 | UC03 Hồ sơ người dùng và danh sách xe (tối đa 3 xe) | 20 | ✅ Xong | Bạn báo pass; gồm test 5 yêu cầu thêm xe đồng thời |
| 7 | UC04 Phân quyền theo vai trò, lịch sử cấp quyền | 13 | ✅ Xong | Bạn báo pass; gồm test hai Quản trị hạ nhau đồng thời |
| 8 | Dữ liệu mẫu (profile `dev`), README, script thử nhanh | 4 | ✅ Xong | Bạn báo pass; script thử nhanh đã chạy |

**Giai đoạn 1 đã hoàn thành toàn bộ (Task 1–8, 89 test pass, hoàn thành ngày 15/10/2026).**

## 4. Việc chưa làm

### Giai đoạn 2 (UC05–UC09): pass theo xác nhận

Chi tiết thiết kế và 9 task: `docs/superpowers/plans/2026-10-15-giai-doan-2-dat-phien.md`. Tổng số test mong đợi: **239**.

- [x] UC05 Đặt phiên (30 phút–3 giờ, trước tối đa 24 giờ, tối đa 2 đặt chỗ, loại cổng khớp xe)
- [x] UC06 Kiểm tra xung đột, UC08 phân bổ cổng tự động (khóa toàn bộ cổng cùng loại, ràng buộc loại trừ ở CSDL)
- [x] UC07 Hủy / đổi lịch (tối đa 2 lần đổi), hạn chế đặt chỗ 24 giờ sau 3 lần không đến hoặc hủy sát giờ
- [x] UC09 Giải phóng cổng quá hạn 15 phút, giữ cổng từ 15 phút trước giờ bắt đầu, bộ định thời mỗi phút
- [x] Chặn đặt chỗ mới khi còn phiên Chờ thanh toán (quy tắc 13)
- [x] Người dùng xác nhận giai đoạn 2 đã pass
- [x] Giai đoạn 3: Phân bổ lại đặt chỗ khi cổng Lỗi/Bảo trì/Mất kết nối (quy tắc 10), pass theo xác nhận người dùng

### Giai đoạn 3 (UC10–UC21): mã đã triển khai
- [x] Mosquitto xác thực; `device-sim` gửi ack, trạng thái, heartbeat 30 giây và có API mô phỏng lỗi/mất kết nối/xe rời cổng
- [x] Vòng đời phiên, khóa idempotency cho lệnh bắt đầu/dừng, gửi lại lệnh 3 lần
- [x] Xác nhận thiết bị an toàn, khóa phiên/cổng khi lỗi, ghi sự cố Khẩn cấp
- [x] Heartbeat 90 giây, truy vấn trạng thái thực khi khôi phục, phân bổ lại đặt chỗ tương lai
- [x] Cân bằng tải ngưỡng 20%, khóa/mở bảo trì, biểu giá theo dải giờ, tính phí theo kWh và thời lượng
- [x] Giai đoạn 3 pass theo xác nhận người dùng

### Giai đoạn 3: phần bổ sung ngày 17/10/2026 (✅ người dùng xác nhận `mvn -pl app test` pass; chưa ghi số `Tests run` thực tế)

Rà soát phát hiện giai đoạn 3 còn thiếu một chức năng, vài chỗ lệch đặc tả và gần như chưa có test cho hành vi cốt lõi
(chỉ có `PhienSacTest`, `TrangThaiPhienTest`). Đã bổ sung; **các thay đổi dưới đây chưa được biên dịch và chạy trên máy**
(môi trường làm việc không có Maven), nên cần chạy `mvn -pl app test` và báo lại kết quả.

Mã nguồn:
- [x] **Phí tạm tính** (FR19, UC13, BM21): `TinhPhiService.tinhPhiTamTinh`, record `PhiTamTinh`; trả trong `GET /api/phien` và `/api/phien/{id}` khi phiên Đang sạc, luôn có `nhan = "Tạm tính"`, không lưu
- [x] **Làm tròn phí đến đồng** (QĐ20): `PhiSac.TongPhi` trước đây làm tròn 2 chữ số thập phân
- [x] **Xem phiên theo vai trò** (QĐ12): Nhân viên trực và Quản trị xem phiên của mọi người dùng; thêm bộ lọc `tuNgay`, `denNgay`, `trangThai` (BM12); Kỹ thuật viên vẫn không được xem
- [x] **Quy tắc 14**: Nhân viên trực/Quản trị dừng phiên thay người dùng được ghi `DUNG_SAC_HO_TRO` vào nhật ký với mã tài khoản người thao tác
- [x] **Cảnh báo chiếm cổng** (QĐ8, UC18): chu kỳ 10 phút (trước là 5 phút), nhắc **người dùng** tối đa 3 lần rồi mới leo thang **Nhân viên trực** (trước đây mọi cảnh báo ghi người nhận là Nhân viên trực và không leo thang); có phản hồi thì dừng
- [x] **Loại sự cố** theo đúng danh mục: sự cố do thiết bị báo lỗi và do khôi phục ở trạng thái Lỗi ghi `LoaiSuCo = "An toàn điện"` (mã lỗi RO_DIEN/QUA_DONG/DUNG_KHAN_CAP nằm trong `MoTa`); trước đây ghi thẳng mã lỗi
- [x] **Sự kiện kết nối** ghi `"Khôi phục"` đúng BM14 (trước là `"Kết nối lại"`)
- [x] **Sửa lỗi thật**: `PhanBoLaiDatPhienService.phanBoLai` gọi `phanBoMotDatCho` trực tiếp trong cùng bean nên `@Transactional` không có tác dụng, trong khi hàm khóa dòng `FOR UPDATE` bắt buộc có giao dịch; việc phân bổ lại đặt chỗ khi cổng lỗi/bảo trì/mất kết nối (quy tắc 10) sẽ ném lỗi và bị nuốt trong log. Đã bọc từng đặt chỗ trong `TransactionTemplate`

Test mới (`app/src/test/java/com/tramsac/phiensac/`, dùng `FakeMqttGateway`, không cần Mosquitto): tổng **61 lượt chạy** (55 `@Test` + 2 test tham số hóa × 3 giá trị)

| Lớp test | Số test | Nội dung chính |
|---|---|---|
| `LenhDieuKhienIT` | 15 + 1 tham số (3) | Lệnh bắt đầu, ack, idempotency (gửi lại, khác loại lệnh → 409, khóa sai định dạng → 400, hai yêu cầu đồng thời), từ chối khi thiết bị không an toàn/MQTT mất/cổng Lỗi-Bảo trì-Mất kết nối/thiếu công suất tối thiểu, không ack → gửi lại 3 lần → Thất bại, ack muộn |
| `DungSacVaTinhPhiIT` | 7 | Dừng sạc và tính phí đúng một lần, nhân viên dừng thay (quy tắc 14), phân quyền, tách phí qua hai khung giờ (5 kWh × 1.000 + 5 kWh × 3.000 = 20.000) |
| `PhiTamTinhVaXemPhienIT` | 4 | Phí tạm tính (BM21), xem phiên theo vai trò, bộ lọc, lỗi 400 |
| `AnToanThietBiIT` | 3 + 1 tham số (3) | Rò điện/quá dòng/dừng khẩn cấp giữa phiên (kịch bản 4), phiên khóa không khởi động lại, sự cố trùng được gộp, xác nhận xử lý |
| `KetNoiThietBiIT` | 10 | Heartbeat 90 giây, sự cố sau 5 phút, khôi phục và đối chiếu (kịch bản 5), phân bổ lại/hủy đặt chỗ tương lai |
| `CanBangTaiIT` | 4 | Giảm tải theo tỷ lệ, tổng không vượt ngưỡng (kịch bản 9) |
| `BaoTriCongIT` | 6 | Khóa/mở cổng, phân quyền, từ chối khi cổng đang sạc, phân bổ lại đặt chỗ |
| `CanhBaoVaPhienChoIT` | 6 | Nhắc 10 phút × 3 rồi leo thang, phản hồi, phiên Chờ quá 15 phút tự hủy |

Hạ tầng test: `FakeMqttGateway`, `FakeMqttConfig`, `PhienSacBaseIT`; `CongSacIT` dọn thêm các bảng con (phí, lệnh, cảnh báo, sự cố, lịch sử kết nối, thanh toán) trước khi xóa phiên và cổng.

Lưu ý khi chạy: các test này tua `MutableClock` tối đa 16 phút trong khi vẫn dùng token (token hết hạn sau 30 phút, QĐ2). Nếu có test đỏ, gửi lại tên test và thông báo lỗi để sửa.

### Giai đoạn 4 (UC22–UC25): hoàn thiện ngày 17/10/2026, kiểm thử đạt ngày 18/10

Rà soát cho thấy sổ tiến độ cũ ghi sai: `payment-sim` chỉ có `pom.xml` và `Dockerfile`, **chưa có file Java nào** (Docker build
sẽ lỗi vì `COPY payment-sim/src`), `WebhookThanhToanService` không biên dịch được, thiếu phương thức mã QR của FR20, thiếu
kiểm tra số thẻ thử nghiệm của QĐ22 và giai đoạn 4 chưa có test nào. Đã xử lý như dưới đây.

Mã nguồn:
- [x] **`payment-sim` được viết lại từ đầu** (`payment-sim/src/main/java/com/tramsac/paymentsim/PaymentSimulator.java`, JDK `HttpServer` + Jackson, không thêm thư viện): `POST /sim/orders`, `GET /sim/orders/{maGiaoDich}`, `POST /sim/orders/{maGiaoDich}/gui-lai`, `POST /sim/refunds` (idempotent theo khóa), `GET /health`; xác thực bằng `X-Sim-Token`; 5 kịch bản (`THANH_CONG`, `THAT_BAI`, `KHONG_DU_SO_DU` gửi `THAT_BAI`, `HET_HAN`, `CHAM_PHAN_HOI`) và 4 chế độ callback (`BINH_THUONG`, `TRUNG` gửi 3 lần, `SAI_THU_TU` gửi kết quả cuối trước, `SAI_CHU_KY`); ký HMAC-SHA256 trên `timestamp + "." + body`; thử lại tối đa 3 lần khi app trả 5xx; webhook luôn gửi về địa chỉ cấu hình bằng biến môi trường, không theo địa chỉ trong yêu cầu
- [x] **Đã chạy thật và kiểm chứng** `payment-sim` (biên dịch `-Xlint:all` sạch; chạy cùng một máy chủ nhận webhook kiểm tra chữ ký giống `WebhookSignatureService`): 401 khi thiếu/sai token; 400 khi thiếu trường, kịch bản sai, JSON hỏng; tạo lại cùng đơn trả 200 và không phát lại callback; `TRUNG` gửi đúng 3 lần; `SAI_THU_TU` gửi `THAT_BAI` trước `DANG_XU_LY`; `SAI_CHU_KY` bị phát hiện chữ ký sai; `HET_HAN` và `CHAM_PHAN_HOI` trễ đúng `doTreGiay`; hoàn tiền: cùng khóa cùng dữ liệu trả lại kết quả cũ, cùng khóa khác số tiền → 409, hoàn lần hai bằng khóa khác → 409, giao dịch thất bại hoặc vượt số đã trả → 409
- [x] **Mã QR** (FR20, UC23 bước 6): thêm `MA_QR` vào `PhuongThucThanhToan`; phản hồi đơn có `maQr` (nội dung giả lập `TRAMSAC-GIALAP|maDon|soTien`)
- [x] **Số thẻ thử nghiệm** (UC22 nhánh 2a, QĐ22): trường tùy chọn `soTheThuNghiem`; chỉ nhận 3 số thẻ thử nghiệm chuẩn, số khác → 400 `SO_THE_KHONG_PHAI_THE_THU_NGHIEM`; nhập số thẻ cho phương thức khác → 400 `SO_THE_KHONG_AP_DUNG`; số thẻ chỉ được kiểm tra rồi bỏ đi: không lưu, không ghi log, không gửi sang cổng, không xuất hiện trong thông báo lỗi
- [x] **Hóa đơn chi tiết** (UC22 bước 1): `GET /api/thanh-toan/phien/{maPhien}/hoa-don` trả tổng phí và từng dòng khung giờ, điện năng, đơn giá, thành tiền; chỉ chủ phiên xem được
- [x] **Nhãn kết quả callback** theo đúng BM24 (`Mới` / `Trùng` / `Sai thứ tự` / `Bỏ qua`): trước đây còn các giá trị `Đã kết thúc`, `Đơn hết hạn`, `TU_CHOI`, `KHONG_TIM_THAY`...; nay callback đến sau khi giao dịch đã kết thúc: sự kiện sớm hơn là `Sai thứ tự`, sự kiện kết thúc khác là `Bỏ qua`
- [x] **Sửa lỗi biên dịch** `WebhookThanhToanService` (bắt `IOException` thay vì `JsonProcessingException`)
- [x] Đã có sẵn từ trước: tạo đơn idempotent, webhook (xác thực chữ ký và timestamp ≤ 5 phút, trùng, thứ tự), hoàn tất phiên cùng giao dịch CSDL, hoàn tiền một lần, tự hết hạn đơn sau 15 phút

Test mới (`app/src/test/java/com/tramsac/thanhtoan/`, cổng thanh toán là `FakeCongThanhToan`, test tự gọi webhook có ký HMAC; không cần chạy `payment-sim`): tổng **40 test**

| Lớp test | Số test | Nội dung chính |
|---|---|---|
| `ThanhToanIT` | 15 | Tạo đơn (số tiền = tổng phí, hạn 15 phút, nhãn giả lập), mã QR, idempotency (gửi lại, khác dữ liệu → 409, khóa sai → 400), một đơn đang chờ mỗi phiên, phiên chưa Chờ thanh toán, phân quyền, cổng thanh toán lỗi rồi gửi lại cùng khóa, `doTreGiay`/`ketQuaCuoi`, số thẻ thử nghiệm, hóa đơn chi tiết |
| `WebhookThanhToanIT` | 12 | Thành công làm phiên Hoàn tất, callback trùng (và hai callback đồng thời), sai thứ tự, chậm phản hồi, sai chữ ký/thiếu header/timestamp quá hạn/body bị sửa, thất bại rồi thanh toán lại thành công (kịch bản 6), hết hạn (sự kiện, job quét, callback đến muộn), dữ liệu không hợp lệ |
| `HoanTienIT` | 13 | Hoàn tiền hợp lệ, idempotent, chỉ một lần (kể cả hai yêu cầu đồng thời khác khóa), số tiền (0, âm, vượt, hoàn một phần), điều kiện giao dịch/phiên/lý do, "Thiết bị lỗi" cần sự cố, phân quyền, cổng lỗi rồi thử lại (kịch bản 10) |

Hạ tầng test: `FakeCongThanhToan`, `FakePaymentConfig`, `ThanhToanBaseIT` (đặt `tramsac.payment.webhook-secret` bằng `@DynamicPropertySource`).

Chưa làm: ZaloPay Sandbox (tùy chọn); kiểm thử xuyên suốt với `payment-sim` thật qua Docker (làm ở giai đoạn 6); giao diện thanh toán (giai đoạn 5).
- [ ] Chạy xác nhận `mvn -pl app test` (các test mới và toàn bộ test cũ)
- [ ] (Tùy chọn, nếu còn thời gian) ZaloPay Sandbox qua interface `CongThanhToan`

### Giai đoạn 5 (UC26–UC30)
- [ ] Báo sự cố, ghi nhận, phân loại, giao việc, cập nhật xử lý, lịch sử sự cố
- [ ] Báo cáo thống kê
- [ ] Giao diện React ~14 màn hình + trang "Điều khiển mô phỏng" (client phải thay token mỗi khi nhận header `X-New-Token`, và backend cần mở CORS để đọc header đó)

### Giai đoạn 6
- [ ] 10 kịch bản demo cố định kèm test tự động, OpenTelemetry + Jaeger (trace nối liền qua MQTT)
- [ ] Bảng truy vết 30 dòng: use case → sơ đồ → lớp/phương thức → API → test → span
- [ ] Profile `demo`: TLS cho MQTT nếu kịp

## 5. Báo cáo 120 trang (chưa bắt đầu dựng)

Định dạng: Times New Roman 13, lề trái 3 cm, các lề khác 2 cm, giãn dòng 1.5, đánh số trang; đúng 120 trang từ bìa đến tài liệu tham khảo (dựng Word, chuyển PDF để đếm trang thật).

- [ ] Chương I–IV: đã có nội dung, cần chỉnh định dạng (ước tính ~80–85 trang phần chữ sau khi sang giãn dòng 1.5)
- [ ] Sơ đồ: sơ đồ chính cả trang, còn lại 2–4 sơ đồ mỗi trang (phương án tạm chọn, có thể đổi khi ráp)
- [ ] Chương V Thiết kế kiến trúc, Chương VI Thiết kế giao diện, Chương VII Kết luận (môi trường, kết quả đạt được kèm kiểm thử, hướng phát triển, bảng phân chia công việc)
- [ ] Mục tài liệu tham khảo

## 6. Việc cần sửa trong tài liệu và sơ đồ cho khớp code

| Nơi sửa | Nội dung cần sửa | Lý do |
|---|---|---|
| Biểu mẫu BM1 (chương I) | Thêm trường **loại xe** và **loại cổng sử dụng (AC/DC)** | UC05 cần loại cổng của xe; form đăng ký đã có hai trường này |
| ERD, mục "Ràng buộc thiết kế" (chương IV) | `CallbackLog`: UNIQUE **từng phần** `(MaGiaoDich, SuKien) WHERE KetQuaXuLy = 'Mới'` thay vì UNIQUE toàn bảng | Phải ghi được cả callback trùng ("Trùng") |
| Sơ đồ lớp / ERD | `PhiSac.DaTinh` chỉ có ở sơ đồ lớp, không có cột trong ERD; tính một lần được đảm bảo bằng UNIQUE(MaPhien) | ERD là chuẩn cho CSDL |
| Sơ đồ trạng thái **CongSac** | Thêm mũi tên Mất kết nối từ Rảnh, Đã đặt, Đang sạc; nhãn "do thiết bị lỗi" | Quy tắc nghiệp vụ 3 và 6 |
| Sơ đồ trạng thái **DatPhien** | `entry/ Cổng = Đã đặt` chỉ xảy ra khi đến cửa sổ giờ của đặt chỗ (mặc định 15 phút trước giờ bắt đầu), không phải ngay lúc đặt | Quy tắc nghiệp vụ 2 (một cổng có thể có nhiều đặt chỗ ở các khung giờ khác nhau) |
| Sơ đồ tuần tự **Phân bổ cổng** | `layCongSuatKhaDung()` do lớp `TramSac` trả `congSuatDu`: trong code là `CauHinhCongSuat.congSuatDauNoi − tổng CongSuatDuocCap` của nhóm cổng (không có lớp `TramSac`); `phanBoCong(maDatPhien, ...)` được gọi trước khi `DatPhien` tồn tại nên mã đặt chỗ được sinh trước | Khớp với code |
| Sơ đồ tuần tự **Hủy / đổi lịch** | `giaiPhongCong(maCong)` thực hiện qua `GiuChoService.doiChieu` (cổng chỉ ở "Đã đặt" khi đến cửa sổ giờ, nên "giải phóng" chỉ có tác dụng nếu đặt chỗ đang giữ cổng) | Quy tắc 2 |
| Sơ đồ trạng thái **PhienSac** | Hủy từ Đang sạc chỉ do hệ thống (thiết bị lỗi); thêm vòng "thử lại" tại Chờ | Quy tắc 5 và 8 |
| Đặc tả **UC19, UC21, UC22, UC25** | Cập nhật phần Open Issues theo 15 quy tắc đã chốt (xem spec mục 4) | Cho khớp thiết kế |
| Đặc tả **UC13, UC14** | `tinhPhiTamTinh` đã có trong code: công suất cổng được cấp (hoặc định mức) × thời gian đã sạc × đơn giá theo khung giờ, làm tròn đến đồng, chỉ trả về khi phiên Đang sạc | Khớp BM21 |
| Sơ đồ lớp **PhiSac** | Thêm phương thức `tinhPhiTamTinh()` ở `TinhPhiService`; `TongPhi` làm tròn đến đồng | Khớp code |
| Đặc tả **UC18** và BM8 | Mốc tính là lúc kết thúc sạc; nhắc người dùng 3 lần (10, 20, 30 phút), lần thứ 4 (40 phút) báo Nhân viên trực nếu chưa có phản hồi | Khớp code (QĐ8) |
| Đặc tả **UC19** và BM27 | Sự cố do thiết bị báo có `LoaiSuCo = An toàn điện`, mã lỗi nằm trong mô tả; các lỗi cùng loại trên một cổng được gộp vào sự cố đang mở | Khớp code |
| Đặc tả **UC17** và BM14 | Sự kiện kết nối: `Mất kết nối` / `Khôi phục` | Khớp code |
| Đặc tả **UC10, UC13** | Dừng sạc hỗ trợ ghi `DUNG_SAC_HO_TRO` trong nhật ký; Nhân viên trực/Quản trị xem phiên mọi người dùng, Kỹ thuật viên thì không (xóa Open Issue 2 của UC10 và Open Issue 1 của UC13 sau khi vẽ lại sơ đồ use case) | Khớp code |
| Trạng thái sự cố (spec, ERD, UC28–UC29) | Code ghi trạng thái đóng là `Đã đóng`, tài liệu ghi `Đóng`; thống nhất khi làm giai đoạn 5 | Tránh lệch tên |
| Đặc tả **UC22, UC23**, BM22 | Phương thức: `THE_THU`, `VI_DIEN_TU`, `MA_QR`; số thẻ thử nghiệm là trường tùy chọn, chỉ nhận 3 số thẻ chuẩn, không lưu; trạng thái đơn: `CHO`, `DANG_XU_LY`, `THANH_CONG`, `THAT_BAI`, `HET_HAN`, `DA_HOAN` (đặc tả ghi "chờ, thành công, thất bại, hết hạn") | Khớp code |
| Sơ đồ lớp, ERD: **DonThanhToan – GiaoDichThanhToan** | Code (V4) có chỉ mục UNIQUE trên `GiaoDichThanhToan.MaDon`, nghĩa là một đơn có đúng một giao dịch; thanh toán lại sau khi thất bại tạo **đơn mới** (mỗi phiên chỉ có một đơn đang chờ hoặc đang xử lý). Sửa bản số và ý nghĩa "một đơn có nhiều giao dịch" | Khớp code |
| Đặc tả **UC24**, BM24 | `KetQuaXuLy` chỉ có `Mới`, `Trùng`, `Sai thứ tự`, `Bỏ qua`; `KetQuaXacThuc`: `HOP_LE`/`KHONG_HOP_LE`; callback chữ ký sai bị từ chối (401) và vẫn ghi nhật ký; các phản hồi `DA_XU_LY`, `TRUNG`, `SAI_THU_TU`, `DA_KET_THUC`, `DON_HET_HAN`, `DU_LIEU_SAI`, `KHONG_TIM_THAY`, `KHONG_HOP_LE` đều là 200 để cổng không gửi lại | Khớp code |
| Đặc tả **UC22** bước 1 | Có API hóa đơn chi tiết `GET /api/thanh-toan/phien/{maPhien}/hoa-don` | Khớp code |
| Đặc tả **UC25** | Hoàn tiền: giao dịch thành công, đơn chưa hoàn, phiên Hoàn tất, số tiền ≤ số đã trả, lý do `Hủy phiên` hoặc `Thiết bị lỗi` (cần có sự cố ghi nhận trên cổng); hoàn một phần được phép nhưng sau đó coi như đã hoàn, không hoàn thêm; hoàn tiền tự động khi thiết bị lỗi chưa có vì phiên lỗi giữa chừng bị Hủy trước khi thanh toán (xóa bước 6 của UC19 hoặc ghi chú rõ) | Khớp code |
| Sơ đồ tuần tự thanh toán | Tạo đơn lưu vào CSDL trước rồi mới gọi cổng (hai giao dịch tách rời); cổng lỗi tạm thời thì đơn giữ `CHO` và gửi lại cùng khóa idempotency được | Khớp code |
| Đặc tả **UC04** | `LichSuPhanQuyen.NguoiThucHien` lưu mã tài khoản Quản trị | Họ tên có thể đổi |

## 7. Quyết định thiết kế quan trọng

- Kiến trúc: modular monolith Spring Boot (Java 21), PostgreSQL 16, MQTT Mosquitto, React, Docker Compose; `device-sim` và `payment-sim` là hai container riêng (đúng với hai actor phụ trong sơ đồ use case).
- **15 quy tắc nghiệp vụ** đã chốt (spec mục 4), trong đó đáng nhớ nhất: hoàn tiền áp dụng cho phiên **Hoàn tất**; cổng "Đã đặt" theo cửa sổ giờ; mất kết nối từ nhiều trạng thái; công suất tối thiểu 20% định mức; người còn phiên Chờ thanh toán không được đặt mới.
- Idempotency kiểu INSERT-first với cột `KhoaIdempotency` UNIQUE; lệnh MQTT tách hai giao dịch (không giữ giao dịch DB khi chờ ack).
- Chọn lệch kế hoạch ở giai đoạn 1 (đều ghi trong sổ cái): `PUT /api/ho-so` chỉ chặn khi đối tượng/vai trò **khác** giá trị hiện tại; hai Quản trị hạ nhau cùng lúc cho kết quả {200, 403} thay vì {200, 409}.

## 8. Bài học kỹ thuật (nên nhắc lại khi làm giai đoạn 2–4)

1. **Đọc bản cũ khi khóa dòng.** Nạp entity vào bộ nhớ rồi mới `SELECT ... FOR UPDATE` sẽ nhận bản cũ, khóa vô tác dụng. Phải đọc bằng truy vấn chiếu (chỉ lấy mã/vai trò) rồi mới khóa. Bài học này đã được kiểm chứng bằng test 20 luồng đoán mật khẩu song song.
2. **Giao dịch phải lưu lại lần đăng nhập sai.** Phải `noRollbackFor = ApiException.class`, nếu không bộ đếm bị hoàn tác và không bao giờ khóa được.
3. **Mọi thao tác đếm rồi mới thêm (đếm xe, đếm cổng trống) cần khóa dòng cha trước.** Nếu không, các yêu cầu đồng thời cùng thấy "còn chỗ".
4. **Test đồng thời phải đi kèm mỗi ràng buộc đếm/duy nhất** (có sẵn mẫu: `DangNhapIT`, `HoSoIT`, `PhanQuyenIT`).
5. `BUILD SUCCESS` với `Tests run: 0` **không phải bằng chứng**: Maven mặc định không chạy lớp có hậu tố `IT` (đã cấu hình surefire trong `app/pom.xml`).
6. **Gọi `@Transactional` trong cùng một bean là vô tác dụng** (không đi qua proxy). Nếu hàm đó khóa dòng `FOR UPDATE` thì phải bọc bằng `TransactionTemplate` hoặc gọi qua bean khác (lỗi thật ở `PhanBoLaiDatPhienService`, phát hiện ngày 17/10 khi đọc mã để viết test; trước đó không có test nào đi qua đường này nên không bị lộ).
7. **Tính năng cốt lõi nào cũng cần test riêng**: giai đoạn 3 từng được ghi "pass" khi chưa có test nào cho idempotency lệnh, gửi lại 3 lần, rò điện, heartbeat, cân bằng tải, tính phí.

## 9. Sự cố môi trường đã gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `client version 1.32 is too old` khi chạy test | Docker Desktop mới + Testcontainers cũ: tạo `%USERPROFILE%\.docker-java.properties` chứa `api.version=1.44` |
| `Tests run: 0` | Thêm khối `maven-surefire-plugin` (include `**/*Test.java`, `**/*IT.java`) vào `app/pom.xml` |
| Script PowerShell báo sai chữ tiếng Việt | PowerShell 5.1 đọc JSON không có charset bằng Latin-1; script mới tự giải mã UTF-8 |

## 10. Cách chạy nhanh

```powershell
copy .env.example .env                    # rồi sửa DB_PASSWORD, JWT_SECRET (>= 32 ký tự)
mvn test                                 # chạy test các module, gồm telemetry và payment-sim
docker compose up -d --build              # chạy hệ thống (profile dev có 4 tài khoản mẫu, mật khẩu Demo1234)
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
docker compose down
```

## 11. Bước tiếp theo

1. Hoàn thiện các màn hình giai đoạn 5 còn thiếu: hồ sơ, đặt chỗ, sạc, thanh toán và cấu hình theo thiết kế. Kết quả giai đoạn 6 cùng phạm vi kiểm thử được ghi trong `GIAI_DOAN_6.md`; bảng truy vết ở `TRUY_VET_KIEM_THU.md`.
2. Song song, mỗi khi xong một giai đoạn, ghi nhanh các điểm "cần sửa trong tài liệu" (mục 6) để không quên khi dựng báo cáo.
