package com.tramsac.bieugia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "PhiSac")
public class PhiSac {
    @Id @Column(name = "MaPhi") private String maPhi;
    @Column(name = "MaPhien") private String maPhien;
    @Column(name = "MaBieuGia") private String maBieuGia;
    @Column(name = "TongPhi") private BigDecimal tongPhi;
    @Column(name = "TinhLuc") private LocalDateTime tinhLuc;
    protected PhiSac() { }
    public PhiSac(String maPhi, String maPhien, String maBieuGia, BigDecimal tongPhi, LocalDateTime tinhLuc) {
        this.maPhi = maPhi; this.maPhien = maPhien; this.maBieuGia = maBieuGia;
        this.tongPhi = tongPhi; this.tinhLuc = tinhLuc;
    }
    public String getMaPhi() { return maPhi; }
    public BigDecimal getTongPhi() { return tongPhi; }
    public void capNhatTongPhi(BigDecimal tongPhi) { this.tongPhi = tongPhi; }
}
