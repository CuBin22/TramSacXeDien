package com.tramsac.thanhtoan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "GiaoDichThanhToan")
public class GiaoDichThanhToan {
    @Id @Column(name = "MaGiaoDich") private String maGiaoDich;
    @Column(name = "MaDon") private String maDon;
    @Column(name = "KichBan") private String kichBan;
    @Column(name = "KetQua") private String ketQua;
    @Column(name = "DoTreGiay") private Integer doTreGiay;
    @Column(name = "ThoiDiem") private LocalDateTime thoiDiem;
    @Column(name = "CheDoCallback") private String cheDoCallback;
    @Column(name = "KetQuaCuoi") private String ketQuaCuoi;

    protected GiaoDichThanhToan() { }

    public GiaoDichThanhToan(String maGiaoDich, String maDon, KichBanThanhToan kichBan,
                             CheDoCallback cheDoCallback, TrangThaiThanhToan ketQuaCuoi,
                             Integer doTreGiay, LocalDateTime thoiDiem) {
        this.maGiaoDich = maGiaoDich;
        this.maDon = maDon;
        this.kichBan = kichBan.name();
        this.ketQua = TrangThaiThanhToan.CHO.name();
        this.cheDoCallback = cheDoCallback.name();
        this.ketQuaCuoi = ketQuaCuoi.name();
        this.doTreGiay = doTreGiay;
        this.thoiDiem = thoiDiem;
    }

    public String getMaGiaoDich() { return maGiaoDich; }
    public String getMaDon() { return maDon; }
    public String getKichBan() { return kichBan; }
    public String getKetQua() { return ketQua; }
    public Integer getDoTreGiay() { return doTreGiay; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getCheDoCallback() { return cheDoCallback; }
    public String getKetQuaCuoi() { return ketQuaCuoi; }
    public TrangThaiThanhToan layKetQua() { return TrangThaiThanhToan.valueOf(ketQua); }

    public void capNhatKetQua(TrangThaiThanhToan ketQua) { this.ketQua = ketQua.name(); }
}
