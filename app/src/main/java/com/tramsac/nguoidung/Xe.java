package com.tramsac.nguoidung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Xe")
public class Xe {

    @Id
    @Column(name = "MaXe")
    private String maXe;

    @Column(name = "MaND")
    private String maND;

    @Column(name = "BienSo")
    private String bienSo;

    @Column(name = "LoaiXe")
    private String loaiXe;

    @Column(name = "LoaiCongSuDung")
    private String loaiCongSuDung;

    protected Xe() {
    }

    public Xe(String maXe, String maND, String bienSo, String loaiXe, String loaiCongSuDung) {
        this.maXe = maXe;
        this.maND = maND;
        this.bienSo = bienSo;
        this.loaiXe = loaiXe;
        this.loaiCongSuDung = loaiCongSuDung;
    }

    public void capNhat(String bienSo, String loaiXe, String loaiCongSuDung) {
        this.bienSo = bienSo;
        this.loaiXe = loaiXe;
        this.loaiCongSuDung = loaiCongSuDung;
    }

    public String getMaXe() { return maXe; }
    public String getMaND() { return maND; }
    public String getBienSo() { return bienSo; }
    public String getLoaiXe() { return loaiXe; }
    public String getLoaiCongSuDung() { return loaiCongSuDung; }
}
