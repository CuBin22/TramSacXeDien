-- Giai đoạn 2: lớp phòng vệ cuối ở tầng CSDL (NFR04, UC08).
-- Dù code có lỗi, CSDL không bao giờ cho hai đặt chỗ còn hiệu lực chồng khung giờ trên cùng một cổng,
-- hoặc hai đặt chỗ còn hiệu lực chồng khung giờ của cùng một người dùng. Khung giờ là [GioBatDau, GioKetThuc):
-- hai đặt chỗ nối đuôi nhau (kết thúc đúng lúc kia bắt đầu) không bị coi là chồng.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE DatPhien ADD CONSTRAINT ex_datphien_cong_khunggio
    EXCLUDE USING gist (MaCong WITH =, tsrange(GioBatDau, GioKetThuc) WITH &&)
    WHERE (TrangThai IN ('Đã đặt', 'Đang sạc'));

ALTER TABLE DatPhien ADD CONSTRAINT ex_datphien_nguoidung_khunggio
    EXCLUDE USING gist (MaND WITH =, tsrange(GioBatDau, GioKetThuc) WITH &&)
    WHERE (TrangThai IN ('Đã đặt', 'Đang sạc'));
