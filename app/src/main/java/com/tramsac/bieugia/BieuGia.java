package com.tramsac.bieugia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity @Table(name = "BieuGia")
public class BieuGia {
    @Id @Column(name = "MaBieuGia") private String maBieuGia;
    @Column(name = "TenBieuGia") private String tenBieuGia;
    @Column(name = "NgayApDung") private LocalDate ngayApDung;
    @Column(name = "NguoiCauHinh") private String nguoiCauHinh;
    protected BieuGia() { }
    public BieuGia(String id, String ten, LocalDate ngayApDung, String nguoiCauHinh) {
        this.maBieuGia = id; this.tenBieuGia = ten; this.ngayApDung = ngayApDung; this.nguoiCauHinh = nguoiCauHinh;
    }
    public String getMaBieuGia() { return maBieuGia; }
    public String getTenBieuGia() { return tenBieuGia; }
    public LocalDate getNgayApDung() { return ngayApDung; }
}
