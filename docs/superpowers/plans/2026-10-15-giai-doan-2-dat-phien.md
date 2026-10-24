# Giai đoạn 2 – Đặt phiên và phân bổ cổng (UC05–UC09)

> Tài liệu ghi lại thiết kế và các task đã thực hiện của giai đoạn 2 (code viết trước, bạn chạy test sau).
> Thiết kế gốc: `docs/superpowers/specs/2026-10-13-tram-sac-xe-dien-design.md` (mục 4: quy tắc 2, 4, 5, 8, 9, 11, 13).
> Tên lớp, phương thức bám theo sơ đồ tuần tự: `DatPhienService.datPhien/huyHoacDoiLich`,
> `KiemTraXungDotService.kiemTraXungDot/demCongKhaDung`, `PhanBoCongService.phanBoCong`, `DatPhien.huy/doiLich/danhDauQuaHan`.

**Goal:** đặt, hủy, đổi lịch phiên sạc; tự phân bổ cổng không bao giờ trùng; tự giải phóng cổng khi người dùng không đến.

**Architecture:** 3 module mới (`congsac`, `phiensac`, `datphien`) trong modular monolith; ràng buộc loại trừ ở PostgreSQL làm lớp phòng
vệ cuối; thứ tự khóa dòng cố định **người dùng → đặt chỗ → phiên → cổng (theo MaCong)** để không deadlock.

## Quyết định quan trọng

| # | Quyết định | Lý do |
|---|---|---|
| 1 | API nhận/trả thời gian ISO-8601 có múi giờ, lưu UTC | Đồng hồ hệ thống là UTC; tránh sai giờ khi client ở UTC+7 |
| 2 | V2: hai ràng buộc `EXCLUDE USING gist` (theo cổng, theo người dùng) trên khung giờ `[bắt đầu, kết thúc)` khi trạng thái Đã đặt/Đang sạc | Dù code lỗi, CSDL không cho chồng giờ; nối đuôi nhau vẫn hợp lệ. Cần extension `btree_gist` |
| 3 | `phanBoCong` khóa **toàn bộ cổng cùng loại** (`FOR UPDATE`, thứ tự MaCong) rồi mới chọn | Các yêu cầu cùng loại cổng xếp hàng: không thể cùng chiếm một cổng |
| 4 | Mọi truy vấn trước khi khóa chỉ lấy **mã** (truy vấn chiếu), không nạp entity | Bài học "đọc bản cũ" ở đăng nhập: entity nạp trước khi khóa sẽ cũ |
| 5 | Khóa dòng người dùng đầu tiên trong mọi luồng đặt/hủy/đổi | Đếm "tối đa 2 đặt chỗ" và kiểm tra trùng lịch không bị qua mặt bởi yêu cầu đồng thời |
| 6 | Ưu tiên cổng: (1) dư công suất nhóm = đấu nối − tổng đang cấp, nhiều hơn trước; (2) ít đặt chỗ trong ngày UTC; (3) MaCong tăng dần | Đúng "ưu tiên dư công suất trước" (UC08 Open Issue); luôn có kết quả xác định |
| 7 | Cổng "Đã đặt" chỉ từ **15 phút trước** giờ bắt đầu, do `GiuChoService.doiChieu` (idempotent) điều khiển; cổng không còn đặt chỗ giữ thì về Rảnh | Quy tắc 2: một cổng có nhiều đặt chỗ ở các khung giờ khác nhau |
| 8 | "Không đến" và "hủy sát giờ" ghi vào `LichSuTrangThaiPhien.LyDo` (tiền tố `Quá hạn không đến` / `Hủy sát giờ`), đếm 30 ngày | Quy tắc 11 không cần đổi ERD |
| 9 | Đổi lịch sát giờ **không** bị tính vi phạm; chỉ hủy sát giờ và không đến mới tính | Đúng chữ của quy tắc 11 |
| 10 | Từ chối (lỗi 4xx) không ghi nhật ký; chỉ ghi khi thành công (`DAT_PHIEN`, `HUY_DAT_PHIEN`, `DOI_LICH`, `GIAI_PHONG_QUA_HAN`) | Giao dịch hoàn tác khi có lỗi; ghi nhật ký riêng sẽ chờ khóa dòng người dùng (deadlock) |
| 11 | Quá hạn bỏ qua khi cổng đang sạc hoặc mất kết nối (UC09 ngoại lệ 2a, 2b) | Thiết bị chưa xác nhận được; thử lại ở lần quét sau |
| 12 | Hai API quản trị `quet-qua-han`, `quet-giu-cho` chạy ngay các tác vụ định kỳ | Trình diễn và kiểm thử không phải chờ 15 phút |

## Các task

| Task | Nội dung | Test |
|---|---|---|
| 1 | Migration V2 (ràng buộc loại trừ) | `DatPhienKhongTrungGioIT` (7) |
| 2 | `congsac`: `CongSac`, `CauHinhCongSuat`, `TrangThaiCong`, repository (khóa dòng, truy vấn chiếu) | gián tiếp qua các IT |
| 3 | `phiensac`: `TrangThaiPhien` (bảng chuyển trạng thái duy nhất), `PhienSac.chuyenTrangThai`, `LichSuTrangThaiPhien`, `PhienSacService` | `TrangThaiPhienTest` (57, sinh từ sơ đồ: 49 cặp), `PhienSacTest` (6) |
| 4 | `DatPhien`, `TrangThaiDatPhien`, `QuyDinhDatPhien` (mọi con số quy định ở một nơi) | `DatPhienDomainTest` (6), `QuyDinhDatPhienTest` (8) |
| 5 | UC06 `KiemTraXungDotService`, UC08 `PhanBoCongService` | trong `DatPhienIT` |
| 6 | UC05 `DatPhienService.datPhien`, `DatPhienController` | `DatPhienIT` (31, gồm đồng thời) |
| 7 | UC07 `huyHoacDoiLich`, quy tắc 11 | `HuyDoiLichIT` (19, gồm đua giữa hủy và quá hạn) |
| 8 | UC09 `GiaiPhongQuaHanService`, `GiuChoService`, `DatPhienJobs`, bộ định thời | `QuaHanVaGiuChoIT` (14) |
| 9 | Cổng mẫu cho profile dev, README, script thử nhanh | `DevDataSeederIT` (+2) |

## Review Focus (những chỗ dễ sai nhất)

1. **Hai người cùng giành cổng cuối:** đúng 1 thành công (`DatPhienIT`: 5 người/1 cổng, 5 người/2 cổng).
2. **Cùng một người bấm đặt hai lần / ba yêu cầu cùng lúc:** chỉ 1 hoặc 2 thành công (`DatPhienIT`).
3. **Hủy đúng lúc bộ quét đánh dấu quá hạn:** chỉ một trạng thái cuối, không mất cập nhật (`HuyDoiLichIT`, 5 vòng).
4. **Đổi lịch thất bại không làm mất lịch cũ** (`HuyDoiLichIT`: trùng lịch, hết cổng, dữ liệu sai).
5. **Cổng Đã đặt không bị giải phóng nhầm** khi đặt chỗ kế tiếp đã đến cửa sổ giờ (`QuaHanVaGiuChoIT`).

## Không thuộc giai đoạn này

Phân bổ lại đặt chỗ khi cổng chuyển Lỗi/Bảo trì/Mất kết nối (quy tắc 10, cần sự kiện thiết bị của giai đoạn 3); kiểm tra cổng đang sạc bằng
xác nhận thật từ thiết bị (UC09 bước 2); bắt đầu/dừng sạc (giai đoạn 3); giao diện (giai đoạn 5).
Lưu ý: ngày "trong ngày" của tiêu chí phân bổ tính theo ngày UTC (lệch 7 giờ so với giờ Việt Nam); đổi sang múi giờ địa phương nếu cần ở giai đoạn 5.
