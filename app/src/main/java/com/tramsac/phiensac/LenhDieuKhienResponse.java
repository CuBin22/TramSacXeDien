package com.tramsac.phiensac;

import java.time.LocalDateTime;

public record LenhDieuKhienResponse(String maLenh, String maPhien, String loaiLenh, String trangThai,
                                    int soLanGui, LocalDateTime guiLuc, boolean idempotentReplay) {
    public static LenhDieuKhienResponse tu(LenhDieuKhien l, boolean replay) {
        return new LenhDieuKhienResponse(l.getMaLenh(), l.getMaPhien(), l.getLoaiLenh(), l.getTrangThai(),
                l.getSoLanGui(), l.getGuiLuc(), replay);
    }
}
