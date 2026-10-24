package com.tramsac.suco;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity @Table(name = "SuCo")
public class SuCo {
    @Id @Column(name = "MaSuCo") private String maSuCo;
    @Column(name = "MaCong") private String maCong;
    @Column(name = "MaNguoiBao") private String maNguoiBao;
    @Column(name = "MaKTV") private String maKTV;
    @Column(name = "NguonGhiNhan") private String nguonGhiNhan;
    @Column(name = "LoaiSuCo") private String loaiSuCo;
    @Column(name = "MucUuTien") private String mucUuTien;
    @Column(name = "TrangThai") private String trangThai;
    @Column(name = "MoTa") private String moTa;
    @Column(name = "NguyenNhan") private String nguyenNhan;
    @Column(name = "CachXuLy") private String cachXuLy;
    @Column(name = "HanXuLy") private LocalDateTime hanXuLy;
    protected SuCo() { }
    public SuCo(String id, String maCong, String source, String type, String priority, String status, String description) {
        this.maSuCo = id; this.maCong = maCong; this.nguonGhiNhan = source; this.loaiSuCo = type;
        this.mucUuTien = priority; this.trangThai = status; this.moTa = description;
    }
    public String getMaSuCo() { return maSuCo; }
    public String getMaCong() { return maCong; }
    public String getMaNguoiBao() { return maNguoiBao; }
    public String getMaKTV() { return maKTV; }
    public String getNguonGhiNhan() { return nguonGhiNhan; }
    public String getLoaiSuCo() { return loaiSuCo; }
    public String getMucUuTien() { return mucUuTien; }
    public String getTrangThai() { return trangThai; }
    public String getMoTa() { return moTa; }
    public String getNguyenNhan() { return nguyenNhan; }
    public String getCachXuLy() { return cachXuLy; }
    public LocalDateTime getHanXuLy() { return hanXuLy; }
    public void baoCao(String nguoiBao, String nguon) { maNguoiBao = nguoiBao; nguonGhiNhan = nguon; }
    public void giao(String kyThuatVien, LocalDateTime han) {
        if ("Đã đóng".equals(trangThai) || "Đã hủy".equals(trangThai)) throw xungDot("Sự cố đã kết thúc.");
        maKTV = kyThuatVien; hanXuLy = han; trangThai = "Đang xử lý";
    }
    public void capNhat(String status, String cause, String resolution) {
        var allowed = java.util.Map.of(
                "Đang xử lý", java.util.Set.of("Đang xử lý", "Chờ linh kiện", "Đã xử lý"),
                "Chờ linh kiện", java.util.Set.of("Chờ linh kiện", "Đang xử lý", "Đã xử lý"),
                "Đã xử lý", java.util.Set.of("Đang xử lý", "Đã xử lý", "Đã đóng"));
        if (!allowed.getOrDefault(trangThai, java.util.Set.of()).contains(status))
            throw xungDot("Không thể chuyển sự cố từ " + trangThai + " sang " + status + ".");
        if (("Đã xử lý".equals(status) || "Đã đóng".equals(status))
                && (cause == null || cause.isBlank() || resolution == null || resolution.isBlank()))
            throw xungDot("Cần ghi nguyên nhân và cách xử lý trước khi hoàn tất.");
        trangThai = status; nguyenNhan = cause; cachXuLy = resolution;
    }
    public void dong() { trangThai = "Đã đóng"; }
    private static com.tramsac.common.ApiException xungDot(String message) {
        return new com.tramsac.common.ApiException(org.springframework.http.HttpStatus.CONFLICT, "TRANG_THAI_SU_CO_KHONG_HOP_LE", message);
    }
}
