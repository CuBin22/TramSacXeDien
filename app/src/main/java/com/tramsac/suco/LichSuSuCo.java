package com.tramsac.suco;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity @Table(name = "LichSuSuCo")
public class LichSuSuCo {
    @Id @Column(name = "MaLSSC") private String maLSSC;
    @Column(name = "MaSuCo") private String maSuCo;
    @Column(name = "ThoiDiem") private LocalDateTime thoiDiem;
    @Column(name = "TrangThai") private String trangThai;
    @Column(name = "NguoiCapNhat") private String nguoiCapNhat;
    @Column(name = "GhiChu") private String ghiChu;
    protected LichSuSuCo() { }
    public String getMaLSSC() { return maLSSC; }
    public String getMaSuCo() { return maSuCo; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getTrangThai() { return trangThai; }
    public String getNguoiCapNhat() { return nguoiCapNhat; }
    public String getGhiChu() { return ghiChu; }
    public LichSuSuCo(String id, String maSuCo, LocalDateTime luc, String status, String nguoiCapNhat, String ghiChu) {
        this.maLSSC = id; this.maSuCo = maSuCo; this.thoiDiem = luc; this.trangThai = status;
        this.nguoiCapNhat = nguoiCapNhat; this.ghiChu = ghiChu;
    }
}
