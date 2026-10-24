-- Schema 22 bảng theo tài liệu ERD (Chương 4). Tên bảng/cột giữ đúng ERD, không dùng dấu nháy kép
-- (PostgreSQL tự đổi thành chữ thường). Kiểu timestamp giữ nguyên như ERD.
-- Thứ tự tạo bảng theo phụ thuộc khóa ngoại.

-- ===== Nhóm người dùng và phân quyền =====
CREATE TABLE NguoiDung (
    MaND             varchar(20)  PRIMARY KEY,
    HoTen            varchar(120) NOT NULL,
    Email            varchar(120),
    SoDienThoai      varchar(20),
    MatKhauHash      varchar(255) NOT NULL,
    DoiTuong         varchar(30)  NOT NULL,
    MaDinhDanh       varchar(30),
    VaiTro           varchar(30)  NOT NULL DEFAULT 'Người dùng',
    SoLanSaiLienTiep integer      NOT NULL DEFAULT 0,
    KhoaDenLuc       timestamp,
    CONSTRAINT uq_nguoidung_email UNIQUE (Email),
    CONSTRAINT uq_nguoidung_sodienthoai UNIQUE (SoDienThoai),
    CONSTRAINT ck_nguoidung_lien_he CHECK (Email IS NOT NULL OR SoDienThoai IS NOT NULL),
    CONSTRAINT ck_nguoidung_vaitro CHECK (VaiTro IN ('Quản trị', 'Kỹ thuật viên', 'Nhân viên trực', 'Người dùng')),
    CONSTRAINT ck_nguoidung_doituong CHECK (DoiTuong IN ('Sinh viên', 'Cán bộ', 'Khách vãng lai', 'Xe công vụ')),
    CONSTRAINT ck_nguoidung_solansai CHECK (SoLanSaiLienTiep >= 0)
);

CREATE TABLE Xe (
    MaXe           varchar(20) PRIMARY KEY,
    MaND           varchar(20) NOT NULL REFERENCES NguoiDung (MaND),
    BienSo         varchar(20) NOT NULL,
    LoaiXe         varchar(50),
    LoaiCongSuDung varchar(10) NOT NULL,
    CONSTRAINT uq_xe_bienso UNIQUE (BienSo),
    CONSTRAINT ck_xe_loaicong CHECK (LoaiCongSuDung IN ('AC', 'DC'))
);
CREATE INDEX ix_xe_mand ON Xe (MaND);

CREATE TABLE LichSuPhanQuyen (
    MaLSPQ        varchar(30) PRIMARY KEY,
    MaND          varchar(20) NOT NULL REFERENCES NguoiDung (MaND),
    ThoiDiem      timestamp   NOT NULL,
    VaiTroCu      varchar(30),
    VaiTroMoi     varchar(30) NOT NULL,
    NguoiThucHien varchar(120)
);
CREATE INDEX ix_lichsuphanquyen_mand ON LichSuPhanQuyen (MaND);

CREATE TABLE NhatKyHeThong (
    MaNhatKy varchar(30)  PRIMARY KEY,
    MaND     varchar(20)  REFERENCES NguoiDung (MaND),   -- rỗng với thao tác tự động
    ThoiDiem timestamp    NOT NULL,
    HanhDong varchar(60)  NOT NULL,
    NoiDung  varchar(500)
);
CREATE INDEX ix_nhatkyhethong_thoidiem ON NhatKyHeThong (ThoiDiem);

-- ===== Nhóm cổng sạc và giám sát thiết bị =====
CREATE TABLE CauHinhCongSuat (
    MaCauHinh      varchar(20)   PRIMARY KEY,
    CongSuatDauNoi decimal(10,2) NOT NULL,
    NguongGiamTai  decimal(5,2)  NOT NULL,
    NgayApDung     date          NOT NULL,
    NguoiCauHinh   varchar(120),
    CONSTRAINT ck_cauhinh_dau_noi CHECK (CongSuatDauNoi > 0),
    CONSTRAINT ck_cauhinh_nguong CHECK (NguongGiamTai BETWEEN 0 AND 100)
);

CREATE TABLE CongSac (
    MaCong          varchar(20)  PRIMARY KEY,
    MaCauHinh       varchar(20)  REFERENCES CauHinhCongSuat (MaCauHinh),
    LoaiCong        varchar(10)  NOT NULL,
    CongSuatDinhMuc decimal(8,2) NOT NULL,
    CongSuatDuocCap decimal(8,2) NOT NULL DEFAULT 0,
    TrangThai       varchar(20)  NOT NULL,
    HeartbeatCuoi   timestamp,
    CapNhatLuc      timestamp,
    CONSTRAINT ck_congsac_loaicong CHECK (LoaiCong IN ('AC', 'DC')),
    CONSTRAINT ck_congsac_dinhmuc CHECK (CongSuatDinhMuc > 0),
    CONSTRAINT ck_congsac_duoccap CHECK (CongSuatDuocCap >= 0)
);

CREATE TABLE LichSuKetNoi (
    MaLSKN           varchar(30) PRIMARY KEY,
    MaCong           varchar(20) NOT NULL REFERENCES CongSac (MaCong),
    ThoiDiem         timestamp   NOT NULL,
    SuKien           varchar(30) NOT NULL,
    ThoiGianGianDoan integer
);
CREATE INDEX ix_lichsuketnoi_macong ON LichSuKetNoi (MaCong);

-- ===== Nhóm đặt phiên sạc =====
CREATE TABLE DatPhien (
    MaDatPhien   varchar(30)  PRIMARY KEY,
    MaND         varchar(20)  NOT NULL REFERENCES NguoiDung (MaND),
    MaXe         varchar(20)  NOT NULL REFERENCES Xe (MaXe),
    MaCong       varchar(20)  NOT NULL REFERENCES CongSac (MaCong),
    NgayDat      timestamp    NOT NULL,
    GioBatDau    timestamp    NOT NULL,
    GioKetThuc   timestamp    NOT NULL,
    LoaiCong     varchar(10)  NOT NULL,
    TrangThai    varchar(30)  NOT NULL,
    SoLanDoiLich integer      NOT NULL DEFAULT 0,
    LyDoHuy      varchar(255),
    CONSTRAINT ck_datphien_loaicong CHECK (LoaiCong IN ('AC', 'DC')),
    CONSTRAINT ck_datphien_doilich CHECK (SoLanDoiLich BETWEEN 0 AND 2),
    CONSTRAINT ck_datphien_khunggio CHECK (GioKetThuc > GioBatDau)
);
CREATE INDEX ix_datphien_mand ON DatPhien (MaND);
CREATE INDEX ix_datphien_cong_gio ON DatPhien (MaCong, GioBatDau, GioKetThuc);

-- ===== Nhóm phiên sạc =====
CREATE TABLE PhienSac (
    MaPhien     varchar(30)   PRIMARY KEY,
    MaDatPhien  varchar(30)   NOT NULL REFERENCES DatPhien (MaDatPhien),
    MaCong      varchar(20)   NOT NULL REFERENCES CongSac (MaCong),
    TrangThai   varchar(30)   NOT NULL,
    BatDauLuc   timestamp,
    KetThucLuc  timestamp,
    SacDayLuc   timestamp,
    DienNangKWh decimal(10,3),
    KhoaPhien   boolean       NOT NULL DEFAULT false,
    CONSTRAINT uq_phiensac_madatphien UNIQUE (MaDatPhien),
    CONSTRAINT ck_phiensac_diennang CHECK (DienNangKWh IS NULL OR DienNangKWh >= 0)
);
CREATE INDEX ix_phiensac_macong ON PhienSac (MaCong);

CREATE TABLE LichSuTrangThaiPhien (
    MaLSTT       varchar(30)  PRIMARY KEY,
    MaPhien      varchar(30)  NOT NULL REFERENCES PhienSac (MaPhien),
    ThoiDiem     timestamp    NOT NULL,
    TrangThaiCu  varchar(30),
    TrangThaiMoi varchar(30)  NOT NULL,
    NguonThaoTac varchar(60),
    LyDo         varchar(255)
);
CREATE INDEX ix_lichsutrangthai_maphien ON LichSuTrangThaiPhien (MaPhien);

CREATE TABLE LenhDieuKhien (
    MaLenh          varchar(30) PRIMARY KEY,
    MaPhien         varchar(30) NOT NULL REFERENCES PhienSac (MaPhien),
    LoaiLenh        varchar(20) NOT NULL,
    KhoaIdempotency varchar(64) NOT NULL,
    TrangThai       varchar(20) NOT NULL,
    SoLanGui        integer     NOT NULL DEFAULT 0,
    GuiLuc          timestamp,
    AckLuc          timestamp,
    CONSTRAINT uq_lenhdieukhien_khoa UNIQUE (KhoaIdempotency),
    CONSTRAINT ck_lenhdieukhien_solangui CHECK (SoLanGui BETWEEN 0 AND 4)   -- 1 lần gửi đầu + tối đa 3 lần gửi lại
);
CREATE INDEX ix_lenhdieukhien_maphien ON LenhDieuKhien (MaPhien);

CREATE TABLE CanhBaoChiemCong (
    MaCanhBao  varchar(30)  PRIMARY KEY,
    MaPhien    varchar(30)  NOT NULL REFERENCES PhienSac (MaPhien),
    LanCanhBao integer      NOT NULL,
    ThoiDiem   timestamp    NOT NULL,
    NguoiNhan  varchar(120),
    PhanHoi    varchar(255)
);
CREATE INDEX ix_canhbaochiemcong_maphien ON CanhBaoChiemCong (MaPhien);

-- ===== Nhóm biểu giá và tính phí =====
CREATE TABLE BieuGia (
    MaBieuGia    varchar(20)  PRIMARY KEY,
    TenBieuGia   varchar(120) NOT NULL,
    NgayApDung   date         NOT NULL,
    NguoiCauHinh varchar(120)
);

CREATE TABLE ChiTietBieuGia (
    MaCTBG    varchar(30)   PRIMARY KEY,
    MaBieuGia varchar(20)   NOT NULL REFERENCES BieuGia (MaBieuGia),
    KhungGio  varchar(20)   NOT NULL,
    LoaiCong  varchar(10)   NOT NULL,
    DoiTuong  varchar(30)   NOT NULL,
    DonGia    decimal(18,2) NOT NULL,
    CONSTRAINT ck_ctbg_loaicong CHECK (LoaiCong IN ('AC', 'DC')),
    CONSTRAINT ck_ctbg_doituong CHECK (DoiTuong IN ('Sinh viên', 'Cán bộ', 'Khách vãng lai', 'Xe công vụ')),
    CONSTRAINT ck_ctbg_dongia CHECK (DonGia >= 0)
);
CREATE INDEX ix_ctbg_mabieugia ON ChiTietBieuGia (MaBieuGia);

CREATE TABLE PhiSac (
    MaPhi     varchar(30)   PRIMARY KEY,
    MaPhien   varchar(30)   NOT NULL REFERENCES PhienSac (MaPhien),
    MaBieuGia varchar(20)   NOT NULL REFERENCES BieuGia (MaBieuGia),
    TongPhi   decimal(18,2) NOT NULL,
    TinhLuc   timestamp     NOT NULL,
    CONSTRAINT uq_phisac_maphien UNIQUE (MaPhien),   -- mỗi phiên chỉ tính phí một lần
    CONSTRAINT ck_phisac_tongphi CHECK (TongPhi >= 0)
);

CREATE TABLE ChiTietPhi (
    MaCTPhi     varchar(30)   PRIMARY KEY,
    MaPhi       varchar(30)   NOT NULL REFERENCES PhiSac (MaPhi),
    KhungGio    varchar(20)   NOT NULL,
    DienNangKWh decimal(10,3) NOT NULL,
    DonGia      decimal(18,2) NOT NULL,
    ThanhTien   decimal(18,2) NOT NULL,
    CONSTRAINT ck_ctphi_diennang CHECK (DienNangKWh >= 0)
);
CREATE INDEX ix_ctphi_maphi ON ChiTietPhi (MaPhi);

-- ===== Nhóm thanh toán (giả lập) =====
CREATE TABLE DonThanhToan (
    MaDon           varchar(30)   PRIMARY KEY,
    MaPhien         varchar(30)   NOT NULL REFERENCES PhienSac (MaPhien),
    SoTien          decimal(18,2) NOT NULL,
    PhuongThuc      varchar(30)   NOT NULL,
    KhoaIdempotency varchar(64)   NOT NULL,
    TrangThai       varchar(20)   NOT NULL,
    HanThanhToan    timestamp,
    CheDoGiaLap     boolean       NOT NULL DEFAULT true,
    CONSTRAINT uq_donthanhtoan_khoa UNIQUE (KhoaIdempotency),
    CONSTRAINT ck_donthanhtoan_sotien CHECK (SoTien >= 0)
);
CREATE INDEX ix_donthanhtoan_maphien ON DonThanhToan (MaPhien);

CREATE TABLE GiaoDichThanhToan (
    MaGiaoDich varchar(30) PRIMARY KEY,
    MaDon      varchar(30) NOT NULL REFERENCES DonThanhToan (MaDon),
    KichBan    varchar(30),
    KetQua     varchar(30),
    DoTreGiay  integer,
    ThoiDiem   timestamp   NOT NULL,
    CONSTRAINT ck_giaodich_dotre CHECK (DoTreGiay IS NULL OR DoTreGiay BETWEEN 0 AND 60)
);
CREATE INDEX ix_giaodich_madon ON GiaoDichThanhToan (MaDon);

CREATE TABLE CallbackLog (
    MaCallback    varchar(30)  PRIMARY KEY,
    MaGiaoDich    varchar(30)  REFERENCES GiaoDichThanhToan (MaGiaoDich),   -- rỗng khi mã không tồn tại hoặc chữ ký sai
    SuKien        varchar(30),
    ChuKy         varchar(255),
    KetQuaXacThuc varchar(20),
    KetQuaXuLy    varchar(20),
    NhanLuc       timestamp    NOT NULL
);
CREATE INDEX ix_callbacklog_magiaodich ON CallbackLog (MaGiaoDich);
-- Chỉ một callback "Mới" cho mỗi cặp (MaGiaoDich, SuKien); callback trùng vẫn được ghi với KetQuaXuLy = 'Trùng'.
CREATE UNIQUE INDEX uq_callbacklog_moi ON CallbackLog (MaGiaoDich, SuKien) WHERE KetQuaXuLy = 'Mới';

CREATE TABLE HoanTien (
    MaHoanTien      varchar(30)   PRIMARY KEY,
    MaGiaoDich      varchar(30)   NOT NULL REFERENCES GiaoDichThanhToan (MaGiaoDich),
    SoTienHoan      decimal(18,2) NOT NULL,
    LyDo            varchar(30)   NOT NULL,
    KhoaIdempotency varchar(64)   NOT NULL,
    NguoiThucHien   varchar(120),
    NgayHoan        timestamp     NOT NULL,
    CONSTRAINT uq_hoantien_magiaodich UNIQUE (MaGiaoDich),   -- mỗi giao dịch chỉ hoàn một lần
    CONSTRAINT uq_hoantien_khoa UNIQUE (KhoaIdempotency),
    CONSTRAINT ck_hoantien_sotien CHECK (SoTienHoan > 0),
    CONSTRAINT ck_hoantien_lydo CHECK (LyDo IN ('Hủy phiên', 'Thiết bị lỗi'))
);

-- ===== Nhóm quản lý sự cố =====
CREATE TABLE SuCo (
    MaSuCo       varchar(30)  PRIMARY KEY,
    MaCong       varchar(20)  NOT NULL REFERENCES CongSac (MaCong),
    MaNguoiBao   varchar(20)  REFERENCES NguoiDung (MaND),   -- rỗng với sự cố tự động
    MaKTV        varchar(20)  REFERENCES NguoiDung (MaND),   -- rỗng khi chưa giao
    NguonGhiNhan varchar(20)  NOT NULL,
    LoaiSuCo     varchar(30),
    MucUuTien    varchar(20),
    TrangThai    varchar(20)  NOT NULL,
    MoTa         varchar(500),
    NguyenNhan   varchar(500),
    CachXuLy     varchar(500),
    HanXuLy      timestamp
);
CREATE INDEX ix_suco_macong ON SuCo (MaCong);
CREATE INDEX ix_suco_maktv ON SuCo (MaKTV);

CREATE TABLE LichSuSuCo (
    MaLSSC       varchar(30)  PRIMARY KEY,
    MaSuCo       varchar(30)  NOT NULL REFERENCES SuCo (MaSuCo),
    ThoiDiem     timestamp    NOT NULL,
    TrangThai    varchar(20)  NOT NULL,
    NguoiCapNhat varchar(120),
    GhiChu       varchar(255)
);
CREATE INDEX ix_lichsususuco_masuco ON LichSuSuCo (MaSuCo);
