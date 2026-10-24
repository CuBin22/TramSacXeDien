-- Bổ sung trạng thái thiết bị và khung giờ có thể cấu hình mà không sửa migration đã áp dụng.
ALTER TABLE CongSac ADD COLUMN TrangThaiTruocMatKetNoi varchar(20);
ALTER TABLE CongSac ADD COLUMN XacNhanAnToanLuc timestamp;
ALTER TABLE CongSac ADD COLUMN CoXe boolean NOT NULL DEFAULT false;
ALTER TABLE PhienSac ADD COLUMN ChoLuc timestamp;
ALTER TABLE ChiTietBieuGia ADD COLUMN GioBatDau time;
ALTER TABLE ChiTietBieuGia ADD COLUMN GioKetThuc time;

CREATE INDEX ix_lenhdieukhien_cho_ack ON LenhDieuKhien (TrangThai, GuiLuc);
CREATE INDEX ix_congsac_heartbeat ON CongSac (HeartbeatCuoi);

-- Biểu giá mẫu một mức cho profile dev; quản trị có thể thay bằng nhiều dải giờ.
INSERT INTO BieuGia (MaBieuGia, TenBieuGia, NgayApDung, NguoiCauHinh)
VALUES ('BG-MAC-DINH', 'Biểu giá mẫu', DATE '2000-01-01', 'Hệ thống');

INSERT INTO ChiTietBieuGia (MaCTBG, MaBieuGia, KhungGio, LoaiCong, DoiTuong, DonGia, GioBatDau, GioKetThuc)
SELECT 'CT-' || loai || '-' || doi_tuong, 'BG-MAC-DINH', 'Toàn thời gian', loai, doi_tuong, 1000.00,
       TIME '00:00', TIME '00:00'
FROM (VALUES ('AC'), ('DC')) AS l(loai)
CROSS JOIN (VALUES ('Sinh viên'), ('Cán bộ'), ('Khách vãng lai'), ('Xe công vụ')) AS d(doi_tuong);
