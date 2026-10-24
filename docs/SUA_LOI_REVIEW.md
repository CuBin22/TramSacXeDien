# Sửa lỗi review ngày 19/10/2026

## Thay đổi

1. Khi giữ khóa nhóm công suất, kiểm tra cả các cổng có lệnh BAT_DAU chờ ack. Phép cân bằng cũng dành phần công suất cho các lệnh này. Lệnh hết hạn/thất bại hoặc phiên không còn Chờ không giữ chỗ công suất nữa.
2. Migration V6 thêm CongSuatOutbox (bảng kỹ thuật thứ 23) và sequence revision. Giá trị mong muốn được ghi cùng giao dịch cân bằng, gửi sau commit và gửi lại mỗi 5 giây cho đến ack đúng cổng/revision. Giá trị mới thay thế giá trị cũ. Simulator từ chối áp dụng revision cũ và heartbeat giúp gửi lại sau khi simulator restart.
3. Phản hồi HOI_TRANG_THAI mang mã yêu cầu. Phản hồi LATE được đối chiếu với lệnh thất bại và phiên thuộc cổng, kể cả khi cổng vẫn kết nối. Không mở lại cổng Lỗi/Bảo trì; nếu công suất không đủ khi đối chiếu, chuyển sang xử lý sự cố an toàn.
4. Replay thanh toán kiểm tra chủ phiên trước khi trả dữ liệu hoặc gọi payment gateway, gồm cả replay sau xung đột tạo đơn.
5. Simulator tích lũy kWh theo từng khoảng công suất; đổi mức cấp điện và dừng sạc chốt phần điện năng trước đó. Lệnh lặp không đặt lại đồng hồ khi đang sạc.
6. MQTT handler ghi exception và ERROR vào span hiện tại tại điểm bắt lỗi.
7. Hạn xử lý nhập ở giờ địa phương được chuyển sang UTC trước khi gửi. Lịch sử LocalDateTime UTC từ API được diễn giải đúng trước khi hiển thị giờ địa phương.

## Triển khai

Build lại cả app và device-sim vì giao thức công suất thêm revision và phản hồi trạng thái thêm maLenh. Flyway áp dụng V6 khi app khởi động; không sửa các migration đã chạy trước đó.

```powershell
docker compose up -d --build
```

## Kết quả thực hiện

- `mvn -B -ntp clean compile -DskipTests`: BUILD SUCCESS, toàn bộ module Java.
- `npm run build` trong web: thành công.
- Cập nhật kỳ vọng số bảng trong SchemaIT cho migration mới; không thêm hoặc chạy test trong lượt sửa này.
- Chưa chạy migration trên database hoặc E2E sau các thay đổi. Kết quả kiểm thử cũ không xác nhận những nhánh vừa sửa.
