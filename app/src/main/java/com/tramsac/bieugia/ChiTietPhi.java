package com.tramsac.bieugia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity @Table(name = "ChiTietPhi")
public class ChiTietPhi {
    @Id @Column(name = "MaCTPhi") private String maCTPhi;
    @Column(name = "MaPhi") private String maPhi;
    @Column(name = "KhungGio") private String khungGio;
    @Column(name = "DienNangKWh") private BigDecimal dienNangKWh;
    @Column(name = "DonGia") private BigDecimal donGia;
    @Column(name = "ThanhTien") private BigDecimal thanhTien;
    protected ChiTietPhi() { }
    public ChiTietPhi(String id, String maPhi, String khungGio, BigDecimal kwh, BigDecimal donGia,
                      BigDecimal thanhTien) {
        this.maCTPhi = id; this.maPhi = maPhi; this.khungGio = khungGio;
        this.dienNangKWh = kwh; this.donGia = donGia; this.thanhTien = thanhTien;
    }
    public String getKhungGio() { return khungGio; }
    public BigDecimal getDienNangKWh() { return dienNangKWh; }
    public BigDecimal getDonGia() { return donGia; }
    public BigDecimal getThanhTien() { return thanhTien; }
}
