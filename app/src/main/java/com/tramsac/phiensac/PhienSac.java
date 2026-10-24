package com.tramsac.phiensac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Phiên sạc, tạo cùng giao dịch với đặt chỗ (quy tắc 4). Vòng đời xem TrangThaiPhien. */
@Entity
@Table(name = "PhienSac")
public class PhienSac {

    @Id
    @Column(name = "MaPhien")
    private String maPhien;

    @Column(name = "MaDatPhien")
    private String maDatPhien;

    @Column(name = "MaCong")
    private String maCong;

    @Column(name = "TrangThai")
    private String trangThai;

    @Column(name = "BatDauLuc")
    private LocalDateTime batDauLuc;

    @Column(name = "ChoLuc")
    private LocalDateTime choLuc;

    @Column(name = "KetThucLuc")
    private LocalDateTime ketThucLuc;

    @Column(name = "SacDayLuc")
    private LocalDateTime sacDayLuc;

    @Column(name = "DienNangKWh")
    private BigDecimal dienNangKWh;

    @Column(name = "KhoaPhien")
    private boolean khoaPhien;

    protected PhienSac() {
    }

    public PhienSac(String maPhien, String maDatPhien, String maCong) {
        this.maPhien = maPhien;
        this.maDatPhien = maDatPhien;
        this.maCong = maCong;
        this.trangThai = TrangThaiPhien.DA_DAT.giaTri();
        this.khoaPhien = false;
    }

    public String getMaPhien() { return maPhien; }
    public String getMaDatPhien() { return maDatPhien; }
    public String getMaCong() { return maCong; }
    public String getTrangThai() { return trangThai; }
    public TrangThaiPhien layTrangThai() { return TrangThaiPhien.tuGiaTri(trangThai); }
    public LocalDateTime getBatDauLuc() { return batDauLuc; }
    public LocalDateTime getChoLuc() { return choLuc; }
    public LocalDateTime getKetThucLuc() { return ketThucLuc; }
    public LocalDateTime getSacDayLuc() { return sacDayLuc; }
    public BigDecimal getDienNangKWh() { return dienNangKWh; }
    public boolean isKhoaPhien() { return khoaPhien; }

    public void danhDauBatDau(LocalDateTime luc) { batDauLuc = luc; }
    public void danhDauCho(LocalDateTime luc) { choLuc = luc; }

    public void danhDauKetThuc(LocalDateTime luc, BigDecimal dienNang) {
        if (dienNang == null || dienNang.signum() < 0) {
            throw new IllegalArgumentException("Điện năng phải lớn hơn hoặc bằng 0.");
        }
        ketThucLuc = luc;
        dienNangKWh = dienNang;
    }

    public void khoa() { khoaPhien = true; }

    /** Chuyển trạng thái theo bảng hợp lệ; ném IllegalStateException nếu bỏ bước hoặc đi từ trạng thái cuối. */
    public void chuyenTrangThai(TrangThaiPhien moi) {
        TrangThaiPhien cu = layTrangThai();
        if (!cu.choPhepChuyenSang(moi)) {
            throw new IllegalStateException("Không thể chuyển phiên sạc từ " + cu.giaTri() + " sang "
                    + moi.giaTri() + ".");
        }
        this.trangThai = moi.giaTri();
    }

    /** Đặt chỗ được đổi lịch sang cổng khác. */
    public void doiCong(String maCongMoi) {
        this.maCong = maCongMoi;
    }
}
