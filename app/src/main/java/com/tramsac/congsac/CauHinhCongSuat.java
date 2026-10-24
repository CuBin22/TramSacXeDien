package com.tramsac.congsac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Cấu hình công suất đấu nối dùng chung cho một nhóm cổng (giai đoạn 3 sẽ dùng để cân bằng tải). */
@Entity
@Table(name = "CauHinhCongSuat")
public class CauHinhCongSuat {

    @Id
    @Column(name = "MaCauHinh")
    private String maCauHinh;

    @Column(name = "CongSuatDauNoi")
    private BigDecimal congSuatDauNoi;

    @Column(name = "NguongGiamTai")
    private BigDecimal nguongGiamTai;

    @Column(name = "NgayApDung")
    private LocalDate ngayApDung;

    @Column(name = "NguoiCauHinh")
    private String nguoiCauHinh;

    protected CauHinhCongSuat() {
    }

    public CauHinhCongSuat(String maCauHinh, BigDecimal congSuatDauNoi, BigDecimal nguongGiamTai,
                           LocalDate ngayApDung, String nguoiCauHinh) {
        this.maCauHinh = maCauHinh;
        this.congSuatDauNoi = congSuatDauNoi;
        this.nguongGiamTai = nguongGiamTai;
        this.ngayApDung = ngayApDung;
        this.nguoiCauHinh = nguoiCauHinh;
    }

    public String getMaCauHinh() { return maCauHinh; }
    public BigDecimal getCongSuatDauNoi() { return congSuatDauNoi; }
    public BigDecimal getNguongGiamTai() { return nguongGiamTai; }
    public LocalDate getNgayApDung() { return ngayApDung; }
    public String getNguoiCauHinh() { return nguoiCauHinh; }
    public void capNhat(BigDecimal congSuatDauNoi, BigDecimal nguongGiamTai, LocalDate ngayApDung, String nguoiCauHinh) {
        if (congSuatDauNoi == null || congSuatDauNoi.signum() <= 0 || nguongGiamTai == null
                || nguongGiamTai.signum() < 0 || nguongGiamTai.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Công suất phải dương và ngưỡng phải nằm từ 0 đến 100%.");
        }
        this.congSuatDauNoi = congSuatDauNoi;
        this.nguongGiamTai = nguongGiamTai;
        this.ngayApDung = ngayApDung;
        this.nguoiCauHinh = nguoiCauHinh;
    }
}
