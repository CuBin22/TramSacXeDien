-- UC22-25: payment simulator options and one active order per charging session.
ALTER TABLE GiaoDichThanhToan
    ADD COLUMN CheDoCallback varchar(20) NOT NULL DEFAULT 'BINH_THUONG',
    ADD COLUMN KetQuaCuoi varchar(20) NOT NULL DEFAULT 'THANH_CONG';

CREATE UNIQUE INDEX uq_giaodich_madon ON GiaoDichThanhToan (MaDon);
CREATE UNIQUE INDEX uq_donthanhtoan_cho_phien ON DonThanhToan (MaPhien)
    WHERE TrangThai IN ('CHO', 'DANG_XU_LY');
CREATE INDEX ix_donthanhtoan_het_han ON DonThanhToan (HanThanhToan)
    WHERE TrangThai IN ('CHO', 'DANG_XU_LY');
