package com.tramsac.nguoidung;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "NguoiDung")
public class NguoiDung {

    @Id
    @Column(name = "MaND")
    private String maND;

    @Column(name = "HoTen")
    private String hoTen;

    @Column(name = "Email")
    private String email;

    @Column(name = "SoDienThoai")
    private String soDienThoai;

    @Column(name = "MatKhauHash")
    private String matKhauHash;

    @Column(name = "DoiTuong")
    private String doiTuong;

    @Column(name = "MaDinhDanh")
    private String maDinhDanh;

    @Column(name = "VaiTro")
    private String vaiTro;

    @Column(name = "SoLanSaiLienTiep")
    private int soLanSaiLienTiep;

    @Column(name = "KhoaDenLuc")
    private LocalDateTime khoaDenLuc;

    protected NguoiDung() {
    }

    public NguoiDung(String maND, String hoTen, String email, String soDienThoai, String matKhauHash,
                     String doiTuong, String maDinhDanh, VaiTro vaiTro) {
        this.maND = maND;
        this.hoTen = hoTen;
        this.email = email;
        this.soDienThoai = soDienThoai;
        this.matKhauHash = matKhauHash;
        this.doiTuong = doiTuong;
        this.maDinhDanh = maDinhDanh;
        this.vaiTro = vaiTro.giaTri();
        this.soLanSaiLienTiep = 0;
    }

    public String getMaND() { return maND; }
    public String getHoTen() { return hoTen; }
    public String getEmail() { return email; }
    public String getSoDienThoai() { return soDienThoai; }
    public String getMatKhauHash() { return matKhauHash; }
    public String getDoiTuong() { return doiTuong; }
    public String getMaDinhDanh() { return maDinhDanh; }
    public VaiTro layVaiTro() { return VaiTro.tuGiaTri(vaiTro); }
    public int getSoLanSaiLienTiep() { return soLanSaiLienTiep; }
    public LocalDateTime getKhoaDenLuc() { return khoaDenLuc; }

    public void capNhatHoSo(String hoTen, String email, String soDienThoai) {
        this.hoTen = hoTen;
        this.email = email;
        this.soDienThoai = soDienThoai;
    }

    public void doiVaiTro(VaiTro vaiTroMoi) {
        this.vaiTro = vaiTroMoi.giaTri();
    }

    /** Tăng số lần đăng nhập sai liên tiếp và trả về giá trị mới. */
    public int tangSoLanSai() {
        return ++soLanSaiLienTiep;
    }

    public void khoaDen(LocalDateTime thoiDiem) {
        this.khoaDenLuc = thoiDiem;
    }

    /** Đặt lại bộ đếm và gỡ khóa (đăng nhập thành công hoặc hết thời gian khóa). */
    public void datLaiBoDemDangNhap() {
        this.soLanSaiLienTiep = 0;
        this.khoaDenLuc = null;
    }
}
