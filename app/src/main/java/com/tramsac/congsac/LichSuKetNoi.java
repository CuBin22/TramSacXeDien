package com.tramsac.congsac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity @Table(name = "LichSuKetNoi")
public class LichSuKetNoi {
    @Id @Column(name = "MaLSKN") private String maLSKN;
    @Column(name = "MaCong") private String maCong;
    @Column(name = "ThoiDiem") private LocalDateTime thoiDiem;
    @Column(name = "SuKien") private String suKien;
    @Column(name = "ThoiGianGianDoan") private Integer thoiGianGianDoan;
    protected LichSuKetNoi() { }
    public LichSuKetNoi(String id, String maCong, LocalDateTime luc, String suKien, Integer gianDoan) {
        this.maLSKN = id; this.maCong = maCong; this.thoiDiem = luc;
        this.suKien = suKien; this.thoiGianGianDoan = gianDoan;
    }
}
