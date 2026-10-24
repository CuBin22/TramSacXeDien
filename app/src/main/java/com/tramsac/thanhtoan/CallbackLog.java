package com.tramsac.thanhtoan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "CallbackLog")
public class CallbackLog {
    @Id @Column(name = "MaCallback") private String maCallback;
    @Column(name = "MaGiaoDich") private String maGiaoDich;
    @Column(name = "SuKien") private String suKien;
    @Column(name = "ChuKy") private String chuKy;
    @Column(name = "KetQuaXacThuc") private String ketQuaXacThuc;
    @Column(name = "KetQuaXuLy") private String ketQuaXuLy;
    @Column(name = "NhanLuc") private LocalDateTime nhanLuc;

    protected CallbackLog() { }

    public CallbackLog(String maCallback, String maGiaoDich, String suKien, String chuKy,
                       String ketQuaXacThuc, String ketQuaXuLy, LocalDateTime nhanLuc) {
        this.maCallback = maCallback;
        this.maGiaoDich = maGiaoDich;
        this.suKien = suKien;
        this.chuKy = chuKy;
        this.ketQuaXacThuc = ketQuaXacThuc;
        this.ketQuaXuLy = ketQuaXuLy;
        this.nhanLuc = nhanLuc;
    }
}
