package com.tramsac.phiensac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity @Table(name = "CanhBaoChiemCong")
public class CanhBaoChiemCong {
    @Id @Column(name = "MaCanhBao") private String maCanhBao;
    @Column(name = "MaPhien") private String maPhien;
    @Column(name = "LanCanhBao") private int lanCanhBao;
    @Column(name = "ThoiDiem") private LocalDateTime thoiDiem;
    @Column(name = "NguoiNhan") private String nguoiNhan;
    @Column(name = "PhanHoi") private String phanHoi;
    protected CanhBaoChiemCong() { }
    public CanhBaoChiemCong(String id, String maPhien, int lan, LocalDateTime luc, String nguoiNhan) {
        maCanhBao = id; this.maPhien = maPhien; lanCanhBao = lan; thoiDiem = luc; this.nguoiNhan = nguoiNhan;
    }
    public String getMaCanhBao() { return maCanhBao; }
    public String getMaPhien() { return maPhien; }
    public int getLanCanhBao() { return lanCanhBao; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getNguoiNhan() { return nguoiNhan; }
    public String getPhanHoi() { return phanHoi; }
    public void phanHoi(String content) { phanHoi = content; }
}
