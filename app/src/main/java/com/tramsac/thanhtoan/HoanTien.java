package com.tramsac.thanhtoan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "HoanTien")
public class HoanTien {
    @Id @Column(name = "MaHoanTien") private String maHoanTien;
    @Column(name = "MaGiaoDich") private String maGiaoDich;
    @Column(name = "SoTienHoan") private BigDecimal soTienHoan;
    @Column(name = "LyDo") private String lyDo;
    @Column(name = "KhoaIdempotency") private String khoaIdempotency;
    @Column(name = "NguoiThucHien") private String nguoiThucHien;
    @Column(name = "NgayHoan") private LocalDateTime ngayHoan;

    protected HoanTien() { }

    public HoanTien(String maHoanTien, String maGiaoDich, BigDecimal soTienHoan, String lyDo,
                    String khoaIdempotency, String nguoiThucHien, LocalDateTime ngayHoan) {
        this.maHoanTien = maHoanTien;
        this.maGiaoDich = maGiaoDich;
        this.soTienHoan = soTienHoan;
        this.lyDo = lyDo;
        this.khoaIdempotency = khoaIdempotency;
        this.nguoiThucHien = nguoiThucHien;
        this.ngayHoan = ngayHoan;
    }

    public String getMaHoanTien() { return maHoanTien; }
    public String getMaGiaoDich() { return maGiaoDich; }
    public BigDecimal getSoTienHoan() { return soTienHoan; }
    public String getLyDo() { return lyDo; }
    public String getKhoaIdempotency() { return khoaIdempotency; }
    public String getNguoiThucHien() { return nguoiThucHien; }
    public LocalDateTime getNgayHoan() { return ngayHoan; }
}
