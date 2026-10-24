package com.tramsac.nguoidung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Mỗi lần đổi vai trò một tài khoản; chỉ thêm, không sửa hoặc xóa. */
@Entity
@Table(name = "LichSuPhanQuyen")
public class LichSuPhanQuyen {

    @Id
    @Column(name = "MaLSPQ")
    private String maLSPQ;

    @Column(name = "MaND")
    private String maND;

    @Column(name = "ThoiDiem")
    private LocalDateTime thoiDiem;

    @Column(name = "VaiTroCu")
    private String vaiTroCu;

    @Column(name = "VaiTroMoi")
    private String vaiTroMoi;

    @Column(name = "NguoiThucHien")
    private String nguoiThucHien;

    protected LichSuPhanQuyen() {
    }

    public LichSuPhanQuyen(String maLSPQ, String maND, LocalDateTime thoiDiem, String vaiTroCu, String vaiTroMoi,
                           String nguoiThucHien) {
        this.maLSPQ = maLSPQ;
        this.maND = maND;
        this.thoiDiem = thoiDiem;
        this.vaiTroCu = vaiTroCu;
        this.vaiTroMoi = vaiTroMoi;
        this.nguoiThucHien = nguoiThucHien;
    }

    public String getMaLSPQ() { return maLSPQ; }
    public String getMaND() { return maND; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getVaiTroCu() { return vaiTroCu; }
    public String getVaiTroMoi() { return vaiTroMoi; }
    public String getNguoiThucHien() { return nguoiThucHien; }
}
