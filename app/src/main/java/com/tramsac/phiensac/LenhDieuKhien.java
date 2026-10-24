package com.tramsac.phiensac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "LenhDieuKhien")
public class LenhDieuKhien {
    @Id @Column(name = "MaLenh") private String maLenh;
    @Column(name = "MaPhien") private String maPhien;
    @Column(name = "LoaiLenh") private String loaiLenh;
    @Column(name = "KhoaIdempotency") private String khoaIdempotency;
    @Column(name = "TrangThai") private String trangThai;
    @Column(name = "SoLanGui") private int soLanGui;
    @Column(name = "GuiLuc") private LocalDateTime guiLuc;
    @Column(name = "AckLuc") private LocalDateTime ackLuc;
    @Column(name = "TraceParent") private String traceParent;

    protected LenhDieuKhien() { }

    public LenhDieuKhien(String maLenh, String maPhien, String loaiLenh, String khoaIdempotency,
                         LocalDateTime guiLuc) {
        this.maLenh = maLenh;
        this.maPhien = maPhien;
        this.loaiLenh = loaiLenh;
        this.khoaIdempotency = khoaIdempotency;
        this.trangThai = "Chờ ack";
        this.soLanGui = 1;
        this.guiLuc = guiLuc;
        this.traceParent = com.tramsac.telemetry.TraceContext.parent();
    }

    public String getMaLenh() { return maLenh; }
    public String getTraceParent() { return traceParent; }
    public String getMaPhien() { return maPhien; }
    public String getLoaiLenh() { return loaiLenh; }
    public String getKhoaIdempotency() { return khoaIdempotency; }
    public String getTrangThai() { return trangThai; }
    public int getSoLanGui() { return soLanGui; }
    public LocalDateTime getGuiLuc() { return guiLuc; }
    public LocalDateTime getAckLuc() { return ackLuc; }

    public boolean dangChoAck() { return "Chờ ack".equals(trangThai); }
    public void daGui(LocalDateTime luc) { soLanGui++; guiLuc = luc; }
    public void thanhCong(LocalDateTime luc) { trangThai = "Thành công"; ackLuc = luc; }
    public void thatBai(LocalDateTime luc) { trangThai = "Thất bại"; ackLuc = luc; }
}
