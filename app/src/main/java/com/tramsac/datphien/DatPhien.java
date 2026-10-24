package com.tramsac.datphien;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Đặt chỗ sạc: khung giờ [GioBatDau, GioKetThuc) trên một cổng đã được phân bổ. Thời gian lưu theo UTC. */
@Entity
@Table(name = "DatPhien")
public class DatPhien {

    private static final int MAX_LY_DO = 255;

    @Id
    @Column(name = "MaDatPhien")
    private String maDatPhien;

    @Column(name = "MaND")
    private String maND;

    @Column(name = "MaXe")
    private String maXe;

    @Column(name = "MaCong")
    private String maCong;

    @Column(name = "NgayDat")
    private LocalDateTime ngayDat;

    @Column(name = "GioBatDau")
    private LocalDateTime gioBatDau;

    @Column(name = "GioKetThuc")
    private LocalDateTime gioKetThuc;

    @Column(name = "LoaiCong")
    private String loaiCong;

    @Column(name = "TrangThai")
    private String trangThai;

    @Column(name = "SoLanDoiLich")
    private int soLanDoiLich;

    @Column(name = "LyDoHuy")
    private String lyDoHuy;

    protected DatPhien() {
    }

    public DatPhien(String maDatPhien, String maND, String maXe, String maCong, LocalDateTime ngayDat,
                    LocalDateTime gioBatDau, LocalDateTime gioKetThuc, String loaiCong) {
        this.maDatPhien = maDatPhien;
        this.maND = maND;
        this.maXe = maXe;
        this.maCong = maCong;
        this.ngayDat = ngayDat;
        this.gioBatDau = gioBatDau;
        this.gioKetThuc = gioKetThuc;
        this.loaiCong = loaiCong;
        this.trangThai = TrangThaiDatPhien.DA_DAT.giaTri();
        this.soLanDoiLich = 0;
    }

    public String getMaDatPhien() { return maDatPhien; }
    public String getMaND() { return maND; }
    public String getMaXe() { return maXe; }
    public String getMaCong() { return maCong; }
    public LocalDateTime getNgayDat() { return ngayDat; }
    public LocalDateTime getGioBatDau() { return gioBatDau; }
    public LocalDateTime getGioKetThuc() { return gioKetThuc; }
    public String getLoaiCong() { return loaiCong; }
    public String getTrangThai() { return trangThai; }
    public TrangThaiDatPhien layTrangThai() { return TrangThaiDatPhien.tuGiaTri(trangThai); }
    public int getSoLanDoiLich() { return soLanDoiLich; }
    public String getLyDoHuy() { return lyDoHuy; }

    /** Sơ đồ trạng thái: huy() [chưa bắt đầu sạc]. */
    public void huy(String lyDo) {
        yeuCauDaDat("hủy");
        this.trangThai = TrangThaiDatPhien.HUY.giaTri();
        this.lyDoHuy = cat(lyDo);
    }

    /** Sơ đồ trạng thái: doiLich() [đổi chưa quá 2 lần, không xung đột]. Việc kiểm tra xung đột do service làm. */
    public void doiLich(LocalDateTime gioBatDauMoi, LocalDateTime gioKetThucMoi, String maCongMoi, String lyDo) {
        yeuCauDaDat("đổi lịch");
        if (soLanDoiLich >= QuyDinhDatPhien.TOI_DA_LAN_DOI_LICH) {
            throw new IllegalStateException("Mỗi đặt chỗ chỉ được đổi lịch tối đa "
                    + QuyDinhDatPhien.TOI_DA_LAN_DOI_LICH + " lần.");
        }
        this.gioBatDau = gioBatDauMoi;
        this.gioKetThuc = gioKetThucMoi;
        this.maCong = maCongMoi;
        this.soLanDoiLich++;
        this.lyDoHuy = cat(lyDo);
    }

    /** Sơ đồ trạng thái: quaHan() [quá 15 phút chưa sạc, thiết bị xác nhận không có phiên sạc]. */
    public void danhDauQuaHan() {
        yeuCauDaDat("đánh dấu quá hạn");
        this.trangThai = TrangThaiDatPhien.QUA_HAN.giaTri();
    }

    public void batDauSac() {
        yeuCauDaDat("bắt đầu sạc");
        this.trangThai = TrangThaiDatPhien.DANG_SAC.giaTri();
    }

    public void hoanTatSac() {
        if (layTrangThai() != TrangThaiDatPhien.DANG_SAC) return;
        this.trangThai = TrangThaiDatPhien.HOAN_TAT.giaTri();
    }

    public void huyDoHeThong(String lyDo) {
        if (layTrangThai() != TrangThaiDatPhien.DA_DAT && layTrangThai() != TrangThaiDatPhien.DANG_SAC) return;
        this.trangThai = TrangThaiDatPhien.HUY.giaTri();
        this.lyDoHuy = cat(lyDo);
    }

    public void ganLaiCong(String maCongMoi) {
        yeuCauDaDat("phân bổ lại cổng");
        this.maCong = maCongMoi;
    }

    private void yeuCauDaDat(String thaoTac) {
        if (layTrangThai() != TrangThaiDatPhien.DA_DAT) {
            throw new IllegalStateException("Không thể " + thaoTac + " đặt chỗ đang ở trạng thái " + trangThai + ".");
        }
    }

    private static String cat(String s) {
        return (s != null && s.length() > MAX_LY_DO) ? s.substring(0, MAX_LY_DO) : s;
    }
}
