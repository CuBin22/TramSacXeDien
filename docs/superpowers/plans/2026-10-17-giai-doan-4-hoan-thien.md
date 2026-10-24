# Giai đoạn 4: kế hoạch hoàn thiện (17/10/2026)

Mục tiêu: hoàn thiện thanh toán giả lập (UC22–UC25, FR20–FR24, BM22–BM26, QĐ22–QĐ26). Giai đoạn 4 được ghi là "đã triển
khai" nhưng rà soát cho thấy còn thiếu nhiều. Môi trường làm việc không có Maven nên phần `app` chưa được biên dịch và
chạy test; riêng `payment-sim` (chỉ phụ thuộc Jackson) đã biên dịch và chạy thật.

## Kết quả rà soát

| # | Phát hiện | Xử lý |
|---|---|---|
| 1 | `payment-sim` không có mã nguồn Java (chỉ `pom.xml`, `Dockerfile`); `docker compose build` lỗi | Viết `PaymentSimulator` đầy đủ theo thiết kế mục 8.2, chạy thử với máy chủ nhận webhook |
| 2 | `WebhookThanhToanService` không biên dịch được (`IOException` chưa được bắt) | `catch (IOException)` |
| 3 | FR20 yêu cầu mã QR nhưng chỉ có `THE_THU`, `VI_DIEN_TU` | Thêm `MA_QR` và trường `maQr` trong phản hồi |
| 4 | QĐ22 / UC22 2a: chỉ chấp nhận số thẻ thử nghiệm; code bỏ qua mọi số thẻ | Trường tùy chọn `soTheThuNghiem`, kiểm tra rồi bỏ đi, không lưu |
| 5 | UC22 bước 1: người dùng cần xem chi tiết cách tính phí, chưa có API | `GET /api/thanh-toan/phien/{maPhien}/hoa-don` |
| 6 | BM24 chỉ có 4 giá trị `KetQuaXuLy`, code ghi thêm nhiều giá trị khác | Chuẩn hóa về `Mới` / `Trùng` / `Sai thứ tự` / `Bỏ qua` |
| 7 | Chưa có test nào cho giai đoạn 4 | 3 lớp test, 40 test |

## Kiểm chứng

- `payment-sim`: đã chạy thật, kiểm tra từng kịch bản và từng chế độ callback (xem `docs/TIEN_DO.md`).
- `app`: chưa chạy được. Biên dịch thử bằng `javac` chỉ báo lỗi do thiếu thư viện bên ngoài (Spring, JUnit, AssertJ),
  không có lỗi nào về ký hiệu của dự án; đây **không phải** bằng chứng biên dịch thành công. Tiêu chí hoàn thành:
  `mvn -pl app test` xanh, số test = số cũ + 40.

## Giả định cần người dùng xác nhận

- Số thẻ thử nghiệm là tùy chọn (không bắt buộc khi chọn `THE_THU`); chỉ nhận `4111111111111111`, `4242424242424242`,
  `5555555555554444`.
- Hoàn một phần được phép, sau đó giao dịch coi như đã hoàn (đúng ràng buộc `UNIQUE (MaGiaoDich)` của bảng `HoanTien`).
- Khi payment-sim bị khởi động lại và mất dữ liệu trong bộ nhớ, yêu cầu hoàn tiền cho giao dịch không còn trong bộ nhớ
  vẫn được chấp nhận (app đã kiểm tra điều kiện trước khi gọi).
