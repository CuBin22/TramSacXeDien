package com.tramsac.thanhtoan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "DonThanhToan")
public class DonThanhToan {
    @Id @Column(name = "MaDon") private String maDon;
    @Column(name = "MaPhien") private String maPhien;
    @Column(name = "SoTien") private BigDecimal soTien;
    @Column(name = "PhuongThuc") private String phuongThuc;
    @Column(name = "KhoaIdempotency") private String khoaIdempotency;
    @Column(name = "TrangThai") private String trangThai;
    @Column(name = "HanThanhToan") private LocalDateTime hanThanhToan;
    @Column(name = "CheDoGiaLap") private boolean cheDoGiaLap;

    protected DonThanhToan() { }

    public DonThanhToan(String maDon, String maPhien, BigDecimal soTien, PhuongThucThanhToan phuongThuc,
                        String khoaIdempotency, LocalDateTime hanThanhToan) {
        this.maDon = maDon;
        this.maPhien = maPhien;
        this.soTien = soTien;
        this.phuongThuc = phuongThuc.name();
        this.khoaIdempotency = khoaIdempotency;
        this.trangThai = TrangThaiThanhToan.CHO.name();
        this.hanThanhToan = hanThanhToan;
        this.cheDoGiaLap = true;
    }

    public String getMaDon() { return maDon; }
    public String getMaPhien() { return maPhien; }
    public BigDecimal getSoTien() { return soTien; }
    public String getPhuongThuc() { return phuongThuc; }
    public String getKhoaIdempotency() { return khoaIdempotency; }
    public String getTrangThai() { return trangThai; }
    public LocalDateTime getHanThanhToan() { return hanThanhToan; }
    public boolean isCheDoGiaLap() { return cheDoGiaLap; }

    public TrangThaiThanhToan layTrangThai() { return TrangThaiThanhToan.valueOf(trangThai); }
    public void capNhatTrangThai(TrangThaiThanhToan trangThai) { this.trangThai = trangThai.name(); }
}
