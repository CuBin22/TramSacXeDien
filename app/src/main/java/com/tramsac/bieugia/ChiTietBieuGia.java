package com.tramsac.bieugia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity @Table(name = "ChiTietBieuGia")
public class ChiTietBieuGia {
    @Id @Column(name = "MaCTBG") private String maCTBG;
    @Column(name = "MaBieuGia") private String maBieuGia;
    @Column(name = "KhungGio") private String khungGio;
    @Column(name = "LoaiCong") private String loaiCong;
    @Column(name = "DoiTuong") private String doiTuong;
    @Column(name = "DonGia") private BigDecimal donGia;
    @Column(name = "GioBatDau") private LocalTime gioBatDau;
    @Column(name = "GioKetThuc") private LocalTime gioKetThuc;
    protected ChiTietBieuGia() { }
    public ChiTietBieuGia(String id, String maBieuGia, String khungGio, String loaiCong, String doiTuong,
                          BigDecimal donGia, LocalTime gioBatDau, LocalTime gioKetThuc) {
        this.maCTBG = id; this.maBieuGia = maBieuGia; this.khungGio = khungGio;
        this.loaiCong = loaiCong; this.doiTuong = doiTuong; this.donGia = donGia;
        this.gioBatDau = gioBatDau; this.gioKetThuc = gioKetThuc;
    }
    public String getMaCTBG() { return maCTBG; }
    public String getKhungGio() { return khungGio; }
    public String getLoaiCong() { return loaiCong; }
    public String getDoiTuong() { return doiTuong; }
    public BigDecimal getDonGia() { return donGia; }
    public LocalTime getGioBatDau() { return gioBatDau; }
    public LocalTime getGioKetThuc() { return gioKetThuc; }
    public boolean apDungLuc(LocalTime gio) {
        if (gioBatDau == null || gioKetThuc == null || gioBatDau.equals(gioKetThuc)) return true;
        if (gioBatDau.isBefore(gioKetThuc)) return !gio.isBefore(gioBatDau) && gio.isBefore(gioKetThuc);
        return !gio.isBefore(gioBatDau) || gio.isBefore(gioKetThuc);
    }
}
