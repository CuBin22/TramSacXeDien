package com.tramsac.congsac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "CongSac")
public class CongSac {

    @Id
    @Column(name = "MaCong")
    private String maCong;

    @Column(name = "MaCauHinh")
    private String maCauHinh;

    @Column(name = "LoaiCong")
    private String loaiCong;

    @Column(name = "CongSuatDinhMuc")
    private BigDecimal congSuatDinhMuc;

    @Column(name = "CongSuatDuocCap")
    private BigDecimal congSuatDuocCap;

    @Column(name = "TrangThai")
    private String trangThai;

    @Column(name = "HeartbeatCuoi")
    private LocalDateTime heartbeatCuoi;

    @Column(name = "CapNhatLuc")
    private LocalDateTime capNhatLuc;

    @Column(name = "TrangThaiTruocMatKetNoi")
    private String trangThaiTruocMatKetNoi;

    @Column(name = "XacNhanAnToanLuc")
    private LocalDateTime xacNhanAnToanLuc;

    @Column(name = "CoXe", nullable = false)
    private boolean coXe;

    protected CongSac() {
    }

    public CongSac(String maCong, String maCauHinh, String loaiCong, BigDecimal congSuatDinhMuc) {
        this.maCong = maCong;
        this.maCauHinh = maCauHinh;
        this.loaiCong = loaiCong;
        this.congSuatDinhMuc = congSuatDinhMuc;
        this.congSuatDuocCap = BigDecimal.ZERO;
        this.trangThai = TrangThaiCong.RANH.giaTri();
    }

    public String getMaCong() { return maCong; }
    public String getMaCauHinh() { return maCauHinh; }
    public String getLoaiCong() { return loaiCong; }
    public BigDecimal getCongSuatDinhMuc() { return congSuatDinhMuc; }
    public BigDecimal getCongSuatDuocCap() { return congSuatDuocCap; }
    public String getTrangThai() { return trangThai; }
    public TrangThaiCong layTrangThai() { return TrangThaiCong.tuGiaTri(trangThai); }
    public LocalDateTime getHeartbeatCuoi() { return heartbeatCuoi; }
    public LocalDateTime getCapNhatLuc() { return capNhatLuc; }
    public String getTrangThaiTruocMatKetNoi() { return trangThaiTruocMatKetNoi; }
    public LocalDateTime getXacNhanAnToanLuc() { return xacNhanAnToanLuc; }
    public boolean isCoXe() { return coXe; }

    /** Rảnh → Đã đặt: giữ cổng cho đặt chỗ đã đến cửa sổ giờ (quy tắc 2). */
    public void ganCong(LocalDateTime luc) {
        chuyen(TrangThaiCong.RANH, TrangThaiCong.DA_DAT, luc);
    }

    /** Đã đặt → Rảnh: hủy, quá hạn hoặc không còn đặt chỗ nào đang giữ cổng. */
    public void giaiPhongCong(LocalDateTime luc) {
        chuyen(TrangThaiCong.DA_DAT, TrangThaiCong.RANH, luc);
    }

    public void batDauSac(LocalDateTime luc) {
        chuyen(TrangThaiCong.DA_DAT, TrangThaiCong.DANG_SAC, luc);
    }

    public void ketThucSac(LocalDateTime luc) {
        chuyen(TrangThaiCong.DANG_SAC, TrangThaiCong.RANH, luc);
        congSuatDuocCap = BigDecimal.ZERO;
    }

    public void capNhatHeartbeat(LocalDateTime luc) {
        heartbeatCuoi = luc;
        if (layTrangThai() == TrangThaiCong.MAT_KET_NOI) {
            // Chỉ ghi nhận heartbeat; trạng thái thực được hỏi riêng trước khi khôi phục.
            return;
        }
        capNhatLuc = luc;
    }

    public void xacNhanAnToan(LocalDateTime luc) { xacNhanAnToanLuc = luc; }
    public void capNhatXe(boolean present, LocalDateTime luc) {
        coXe = present;
        if (layTrangThai() != TrangThaiCong.MAT_KET_NOI) capNhatLuc = luc;
    }

    /** Điểm chặn an toàn duy nhất trước mọi lần cấp điện. */
    @io.opentelemetry.instrumentation.annotations.WithSpan("an_toan.kiemTra")
    public void kiemTraAnToan(boolean thietBiAnToan, boolean khoaPhien, LocalDateTime now,
                               BigDecimal congSuatKhaDung) {
        TrangThaiCong state = layTrangThai();
        if ((state != TrangThaiCong.RANH && state != TrangThaiCong.DA_DAT) || khoaPhien) {
            throw new IllegalStateException("Cổng đang ở trạng thái không an toàn: " + state.giaTri() + ".");
        }
        if (!thietBiAnToan || xacNhanAnToanLuc == null
                || Duration.between(xacNhanAnToanLuc, now).abs().compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalStateException("Thiết bị chưa xác nhận an toàn hoặc xác nhận đã hết hạn.");
        }
        BigDecimal toiThieu = congSuatDinhMuc.multiply(new BigDecimal("0.20"));
        if (congSuatKhaDung == null || congSuatKhaDung.compareTo(toiThieu) < 0) {
            throw new IllegalStateException("Không còn đủ công suất tối thiểu cho cổng này.");
        }
    }

    public void capNhatCongSuat(BigDecimal congSuat, LocalDateTime luc) {
        if (congSuat == null || congSuat.signum() < 0
                || congSuat.compareTo(congSuatDinhMuc) > 0) {
            throw new IllegalArgumentException("Công suất cấp phải nằm trong khoảng 0 đến định mức.");
        }
        congSuatDuocCap = congSuat;
        capNhatLuc = luc;
    }

    public void baoLoi(LocalDateTime luc) {
        if (layTrangThai() == TrangThaiCong.BAO_TRI) return;
        trangThai = TrangThaiCong.LOI.giaTri();
        congSuatDuocCap = BigDecimal.ZERO;
        capNhatLuc = luc;
    }

    public void matKetNoi(LocalDateTime luc) {
        TrangThaiCong hienTai = layTrangThai();
        if (hienTai == TrangThaiCong.BAO_TRI || hienTai == TrangThaiCong.MAT_KET_NOI) return;
        trangThaiTruocMatKetNoi = hienTai.giaTri();
        trangThai = TrangThaiCong.MAT_KET_NOI.giaTri();
        capNhatLuc = luc;
    }

    public void phucHoi(String trangThaiThuc, LocalDateTime luc) {
        if (layTrangThai() != TrangThaiCong.MAT_KET_NOI) return;
        TrangThaiCong thuc = TrangThaiCong.tuGiaTri(trangThaiThuc);
        if (thuc == TrangThaiCong.BAO_TRI || thuc == TrangThaiCong.MAT_KET_NOI
                || thuc == TrangThaiCong.DA_DAT) {
            throw new IllegalArgumentException("Thiết bị trả trạng thái khôi phục không hợp lệ.");
        }
        trangThai = thuc.giaTri();
        if (thuc == TrangThaiCong.RANH || thuc == TrangThaiCong.LOI) {
            congSuatDuocCap = BigDecimal.ZERO;
        }
        trangThaiTruocMatKetNoi = null;
        heartbeatCuoi = luc;
        capNhatLuc = luc;
    }

    public void khoaBaoTri(LocalDateTime luc) {
        if (layTrangThai() != TrangThaiCong.RANH && layTrangThai() != TrangThaiCong.DA_DAT) {
            throw new IllegalStateException("Chỉ khóa bảo trì cổng đang rảnh hoặc đã đặt.");
        }
        trangThai = TrangThaiCong.BAO_TRI.giaTri();
        congSuatDuocCap = BigDecimal.ZERO;
        capNhatLuc = luc;
    }

    public void moBaoTri(LocalDateTime luc) {
        chuyen(TrangThaiCong.BAO_TRI, TrangThaiCong.RANH, luc);
    }

    public void xacNhanXuLy(LocalDateTime luc) {
        chuyen(TrangThaiCong.LOI, TrangThaiCong.RANH, luc);
        congSuatDuocCap = BigDecimal.ZERO;
    }

    private void chuyen(TrangThaiCong tu, TrangThaiCong den, LocalDateTime luc) {
        if (layTrangThai() != tu) {
            throw new IllegalStateException("Cổng " + maCong + " đang " + trangThai + ", không thể chuyển "
                    + tu.giaTri() + " → " + den.giaTri() + ".");
        }
        this.trangThai = den.giaTri();
        this.capNhatLuc = luc;
    }
}
