package com.tramsac.phiensac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Mỗi lần phiên chuyển trạng thái, ghi trong cùng giao dịch với việc cập nhật PhienSac. */
@Entity
@Table(name = "LichSuTrangThaiPhien")
public class LichSuTrangThaiPhien {

    @Id
    @Column(name = "MaLSTT")
    private String maLSTT;

    @Column(name = "MaPhien")
    private String maPhien;

    @Column(name = "ThoiDiem")
    private LocalDateTime thoiDiem;

    @Column(name = "TrangThaiCu")
    private String trangThaiCu;

    @Column(name = "TrangThaiMoi")
    private String trangThaiMoi;

    @Column(name = "NguonThaoTac")
    private String nguonThaoTac;

    @Column(name = "LyDo")
    private String lyDo;

    protected LichSuTrangThaiPhien() {
    }

    public LichSuTrangThaiPhien(String maLSTT, String maPhien, LocalDateTime thoiDiem, String trangThaiCu,
                                String trangThaiMoi, String nguonThaoTac, String lyDo) {
        this.maLSTT = maLSTT;
        this.maPhien = maPhien;
        this.thoiDiem = thoiDiem;
        this.trangThaiCu = trangThaiCu;
        this.trangThaiMoi = trangThaiMoi;
        this.nguonThaoTac = nguonThaoTac;
        this.lyDo = lyDo;
    }

    public String getMaLSTT() { return maLSTT; }
    public String getMaPhien() { return maPhien; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getTrangThaiCu() { return trangThaiCu; }
    public String getTrangThaiMoi() { return trangThaiMoi; }
    public String getNguonThaoTac() { return nguonThaoTac; }
    public String getLyDo() { return lyDo; }
}
