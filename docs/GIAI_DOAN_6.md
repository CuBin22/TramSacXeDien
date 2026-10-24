# Giai đoạn 6 — Kiểm thử và quan sát

## Cách chạy

Yêu cầu: JDK 21, Maven 3.9, Node.js 24, Python 3.11+ và Docker Desktop đang chạy.

Windows: chạy toàn bộ quy trình bằng một lệnh (script tự chuyển về thư mục repo và dừng ngay khi một bước lỗi):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-phase6.ps1
```

Thêm `-SkipE2E` nếu chỉ muốn chạy Java, React và build; khi đó kết quả không xác nhận E2E. Script dùng `clean verify` để tránh tái sử dụng file `.class` lỗi do IDE tạo khi dependency thay đổi.

Hoặc chạy từng bước:

```powershell
mvn -B -ntp clean verify
cd web
npm ci
npm test
npm run build
cd ..
python scripts/verify-e2e.py
```

`mvn verify` chạy unit test và test tích hợp với PostgreSQL thật qua Testcontainers. Các test nghiệp vụ sạc/thanh toán điều khiển thời gian và lỗi bằng fake gateway để xác nhận timeout, thứ tự sự kiện và cạnh tranh dữ liệu. Module `payment-sim` có thêm test gọi HTTP thật và nhận webhook có chữ ký.

`verify-e2e.py` tạo project Compose tên ngẫu nhiên, cổng host ngẫu nhiên và thông tin xác thực riêng. Nó dựng PostgreSQL, Mosquitto, app, device-sim, payment-sim, web, Collector và Jaeger. Kiểm tra đăng nhập → đặt chỗ → bắt đầu → ack → dừng → tính phí → webhook → hoàn tất → hoàn tiền, sự cố, báo cáo, proxy Nginx và trace liên dịch vụ. Khi kết thúc, script chỉ xóa container/volume của project kiểm thử đó. Không dùng database demo đang mở. `--keep` giữ stack để xem thủ công; tên project và cổng web/Jaeger in ở đầu log. Dùng `docker compose -p <tên-project-đã-in> down -v` từ repo để dọn stack đã giữ.

Workflow `.github/workflows/verify.yml` chạy ba nhóm: Java, React và E2E với simulator thật. Kết quả JUnit và log E2E được lưu thành artifact; workflow cần được chạy trên GitHub sau khi push.

## OpenTelemetry

- Module `telemetry` dùng OpenTelemetry API/W3C; Java agent trong ba container Java cung cấp SDK, tự tạo span cho HTTP/JDBC và chèn `trace_id`, `span_id` vào MDC.
- Lỗi xử lý/gửi MQTT được ghi exception và trạng thái ERROR trước khi đóng span để tìm được trong Jaeger.
- Collector nhận OTLP và gửi đến Jaeger. Mở `http://localhost:16686`, chọn `tramsac-app`, `tramsac-device-sim` hoặc `tramsac-payment-sim`.
- Các span nghiệp vụ: `phien.batDau`, `an_toan.kiemTra`, `lenh.guiMqtt`, `lenh.nhanAck`, `lenh.guiLai`, `phien.dungSac`, `phi.tinhPhi`, `thanh_toan.taoDon`, `thanh_toan.xuLyCallback`, `thanh_toan.hoanTien`.
- MQTT truyền W3C `traceparent` trong payload, thiết bị nối context khi nhận lệnh và gắn context vào ack/trạng thái trả về. Migration V5 lưu `LenhDieuKhien.TraceParent` để scheduler gửi lại sau khi request đã kết thúc vẫn nối vào trace gốc.
- Payment simulator giữ context của đơn qua timer và gửi callback trong context đó. HMAC tiếp tục ký chính xác timestamp/body; tracing không sửa nội dung đã ký.
- Mỗi request người dùng (bắt đầu, dừng, thanh toán) có trace riêng. Các nhánh bất đồng bộ trong từng request nối vào trace đó; không gộp toàn bộ vòng đời phiên thành một HTTP trace.
- Chạy `mvn` không có Java agent sẽ không xuất trace; Compose tự gắn agent. Có thể đặt `OTEL_SDK_DISABLED=true` nếu cần tắt quan sát khi chạy thủ công.
- Jaeger dùng bộ nhớ trong môi trường demo: dữ liệu trace mất khi restart. Exporter metric/log được tắt; log app vẫn có ID để đối chiếu trace. Profile demo chưa bổ sung TLS MQTT trong giai đoạn này.

Tài liệu nền: [Java agent annotations](https://opentelemetry.io/docs/zero-code/java/agent/annotations/), [Java API và truyền context](https://opentelemetry.io/docs/languages/java/api/).

## Mười kịch bản demo và kiểm thử tương ứng

| # | Kịch bản trong thiết kế | Kiểm thử |
|---|---|---|
| 1 | Đặt → sạc → tính phí → thanh toán → Hoàn tất | `scripts/verify-e2e.py` (MQTT/HTTP thật), `WebhookThanhToanIT` |
| 2 | Cùng khóa không thực thi lệnh hai lần | `LenhDieuKhienIT`, `TraceParentPersistenceIT`, E2E |
| 3 | Không ack → gửi lại ba lần → Thất bại, phiên Chờ | `LenhDieuKhienIT` |
| 4 | Rò điện → Hủy, cổng Lỗi, sự cố Khẩn cấp, không phí | `AnToanThietBiIT` |
| 5 | Mất heartbeat và phục hồi theo trạng thái thật | `KetNoiThietBiIT` |
| 6 | Thanh toán thất bại, thử lại thành công | `WebhookThanhToanIT` |
| 7 | Callback trùng, sai thứ tự, sai chữ ký | `WebhookThanhToanIT`, `PaymentSimulatorTest`, E2E dùng chế độ TRUNG |
| 8 | Hai người đặt cổng cuối cùng | `DatPhienIT`, `DatPhienKhongTrungGioIT` |
| 9 | Vượt ngưỡng công suất, giảm theo tỷ lệ | `CanBangTaiIT` |
| 10 | Hoàn tiền một lần, chống gửi lại và cạnh tranh | `HoanTienIT`, `PaymentSimulatorTest`, E2E |

Các ca 3–9 dùng đồng hồ/gateway điều khiển trong suite tích hợp; không tuyên bố mọi nhánh lỗi đều đã chạy với phần cứng hoặc broker thật. Bảng chuyển trạng thái phiên được kiểm tra đủ 49 cặp trong `TrangThaiPhienTest`.

## Lỗi đã sửa khi triển khai giai đoạn 6

Bổ sung ngày 19/10: healthcheck HTTP cho app; web chờ app healthy và device-sim chờ broker healthy. Đã thêm script `scripts/verify-phase6.ps1`. Những bổ sung này chưa được chạy lại E2E; kết quả dưới đây thuộc lần chạy ngày 18/10.

Workspace VS Code tắt `java.autobuild.enabled` để tránh IDE ghi vào `target/classes` trong lúc Maven clean/build. Dùng Maven để biên dịch; tự động cập nhật cấu hình Maven vẫn bật. Phần Java bổ sung ngày 19/10 đã chạy `mvn -pl app -am clean compile -DskipTests` thành công; chưa chạy lại test trong lượt này.

Kết quả chạy ngày 18/10/2026: suite Java đạt 351 test (app 345, telemetry 2, payment-sim 4); test bổ sung `TraceParentPersistenceIT` chạy riêng đạt 1/1. React đạt 4/4 và build thành công. E2E với Docker, MQTT, payment simulator, Nginx và Jaeger báo `ALL E2E CHECKS PASSED`; container/volume riêng của lần E2E đã được dọn. Workflow GitHub Actions đã được thêm nhưng chưa chạy trên GitHub.

- Báo cáo truy vấn sai tên bảng lịch sử sự cố; đếm sự cố theo thời điểm tạo thay vì mỗi lần cập nhật. Phiên chưa bắt đầu được tính theo ngày đặt. Khoảng ngày mặc định dùng Clock chung.
- Sự cố thiếu validation và cập nhật cạnh tranh: kiểm tra độ dài, trường bắt buộc, hạn xử lý, khóa dòng và chuyển trạng thái hợp lệ; không cho đóng thủ công khi cổng còn Lỗi.
- Nhiều báo cáo cùng loại trên một cổng không còn làm truy vấn tìm sự cố mở trả nhiều kết quả vào `Optional`.
- Payment simulator không còn lùi trạng thái cuối khi callback xử lý đến muộn; từ chối cùng mã giao dịch khác nội dung và hoàn giao dịch không tồn tại; đồng bộ hóa kiểm tra/lưu hoàn tiền.
- Mosquitto: file mật khẩu tạo lúc khởi động được đổi chủ sở hữu đúng trước khi broker hạ quyền; healthcheck ngăn app khởi động với broker lỗi.
- React: ngày hiện tại thay cho ngày cố định, tải lại lịch sử sau cập nhật, lưu phản hồi server vào chi tiết, đăng xuất chỉ xóa khóa của ứng dụng. Thêm lockfile, cố định dependency và dùng `npm ci`.

Giao diện hiện có đăng nhập, tổng quan, sự cố và báo cáo. Các màn hình đặt chỗ/sạc/thanh toán/hồ sơ/cấu hình đầy đủ trong mục 11 của thiết kế chưa được triển khai ở giao diện giai đoạn 5; E2E của giai đoạn 6 kiểm tra các chức năng đó qua API. Không dùng việc test backend đạt để đánh dấu toàn bộ giao diện thiết kế đã hoàn tất.
