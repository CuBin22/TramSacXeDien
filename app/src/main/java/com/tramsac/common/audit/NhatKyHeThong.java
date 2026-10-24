package com.tramsac.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "NhatKyHeThong")
public class NhatKyHeThong {

    @Id
    @Column(name = "MaNhatKy")
    private String maNhatKy;

    @Column(name = "MaND")
    private String maND;

    @Column(name = "ThoiDiem")
    private LocalDateTime thoiDiem;

    @Column(name = "HanhDong")
    private String hanhDong;

    @Column(name = "NoiDung")
    private String noiDung;

    protected NhatKyHeThong() {
    }

    public NhatKyHeThong(String maNhatKy, String maND, LocalDateTime thoiDiem, String hanhDong, String noiDung) {
        this.maNhatKy = maNhatKy;
        this.maND = maND;
        this.thoiDiem = thoiDiem;
        this.hanhDong = hanhDong;
        this.noiDung = noiDung;
    }

    public String getMaNhatKy() { return maNhatKy; }
    public String getMaND() { return maND; }
    public LocalDateTime getThoiDiem() { return thoiDiem; }
    public String getHanhDong() { return hanhDong; }
    public String getNoiDung() { return noiDung; }
}
