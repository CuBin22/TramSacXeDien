# Giai đoạn 3: kế hoạch bổ sung (17/10/2026)

Mục tiêu: sau khi người dùng xác nhận giai đoạn 3 pass, rà soát mã so với đặc tả UC10–UC21 (BM10–BM21, QĐ10–QĐ21) và
bổ sung phần còn thiếu. Môi trường làm việc không có Maven nên mọi thay đổi chưa được biên dịch; cần chạy
`mvn -pl app test` trên máy.

## Kết quả rà soát

| # | Phát hiện | Xử lý |
|---|---|---|
| 1 | Chưa có phí tạm tính (FR19, UC13, BM21) | `TinhPhiService.tinhPhiTamTinh`, `PhiTamTinh`, trả trong `PhienSacResponse` |
| 2 | `TongPhi` làm tròn 2 chữ số, QĐ20 yêu cầu đến đồng | `setScale(0, HALF_UP)` |
| 3 | `GET /api/phien` chỉ cho Người dùng; QĐ12 cho Nhân viên trực và Quản trị xem mọi phiên; thiếu bộ lọc BM12 | Mở quyền, thêm `tuNgay`, `denNgay`, `trangThai` |
| 4 | Dừng phiên hỗ trợ không ghi nguồn thao tác (quy tắc 14) | Ghi `DUNG_SAC_HO_TRO` |
| 5 | Cảnh báo chiếm cổng: chu kỳ 5 phút, không leo thang, người nhận luôn là Nhân viên trực | 10 phút, 3 lần nhắc người dùng rồi leo thang |
| 6 | `LoaiSuCo` ghi mã lỗi thô | Ghi `An toàn điện`, mã lỗi vào `MoTa` |
| 7 | Sự kiện kết nối `Kết nối lại` thay vì `Khôi phục` (BM14) | Đổi tên |
| 8 | `PhanBoLaiDatPhienService`: gọi nội bộ làm mất `@Transactional`, khóa `FOR UPDATE` không có giao dịch | `TransactionTemplate` từng đặt chỗ |
| 9 | Thiếu test cho idempotency lệnh, gửi lại, rò điện, heartbeat, cân bằng tải, tính phí, bảo trì, cảnh báo | 8 lớp test, 61 lượt chạy |

## Kiểm chứng

Chưa chạy được. Trong môi trường làm việc chỉ kiểm tra được bằng cách biên dịch thử với `javac`: không có lỗi về ký hiệu
của chính dự án; các lỗi còn lại đều do thiếu thư viện bên ngoài (Spring, JUnit, AssertJ) nên **không phải là bằng chứng
biên dịch thành công**. Tiêu chí hoàn thành: `mvn -pl app test` xanh, số test = số cũ + 61.

## Giả định cần người dùng xác nhận

- Mốc đếm 10 phút của cảnh báo chiếm cổng là lúc kết thúc sạc (không phải `SacDayLuc`, cột này chưa được ghi ở đâu).
- Phí tạm tính dùng công suất được cấp hiện tại của cổng (sau cân bằng tải); nếu bằng 0 thì dùng công suất định mức.
- Nhân viên trực ghi phản hồi hộ người dùng qua `PUT /api/canh-bao-chiem-cong/{id}`; chưa có API cho người dùng tự phản hồi.
