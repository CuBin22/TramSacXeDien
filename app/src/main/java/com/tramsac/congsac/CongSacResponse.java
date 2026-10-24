package com.tramsac.congsac;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CongSacResponse(String maCong, String loaiCong, BigDecimal congSuatDinhMuc,
                              BigDecimal congSuatDuocCap, String trangThai, LocalDateTime heartbeatCuoi,
                              boolean coXe) {
    public static CongSacResponse tu(CongSac c) {
        return new CongSacResponse(c.getMaCong(), c.getLoaiCong(), c.getCongSuatDinhMuc(),
                c.getCongSuatDuocCap(), c.getTrangThai(), c.getHeartbeatCuoi(), c.isCoXe());
    }
}
