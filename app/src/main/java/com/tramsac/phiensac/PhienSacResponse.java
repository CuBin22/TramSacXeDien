package com.tramsac.phiensac;

import com.tramsac.bieugia.PhiSac;
import com.tramsac.bieugia.PhiTamTinh;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phiên sạc trả về cho giao diện. tongPhi là phí chính thức (chỉ có sau khi kết thúc sạc); phiTamTinh chỉ có khi
 * phiên đang sạc và luôn mang nhãn "Tạm tính" (FR19, BM21).
 */
public record PhienSacResponse(String maPhien, String maDatPhien, String maCong, String trangThai,
                              LocalDateTime choLuc, LocalDateTime batDauLuc, LocalDateTime ketThucLuc,
                              BigDecimal dienNangKWh, boolean khoaPhien, BigDecimal tongPhi,
                              PhiTamTinh phiTamTinh) {
    public static PhienSacResponse tu(PhienSac p, PhiSac phi) {
        return tu(p, phi, null);
    }

    public static PhienSacResponse tu(PhienSac p, PhiSac phi, PhiTamTinh tamTinh) {
        return new PhienSacResponse(p.getMaPhien(), p.getMaDatPhien(), p.getMaCong(), p.getTrangThai(),
                p.getChoLuc(), p.getBatDauLuc(), p.getKetThucLuc(), p.getDienNangKWh(), p.isKhoaPhien(),
                phi == null ? null : phi.getTongPhi(), tamTinh);
    }
}
