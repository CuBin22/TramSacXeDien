package com.tramsac.thanhtoan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DonThanhToanResponse(String maDon, String maGiaoDich, String maPhien, BigDecimal soTien,
                                   String phuongThuc, String trangThai, String ketQuaGiaoDich,
                                   LocalDateTime hanThanhToan, boolean cheDoGiaLap,
                                   boolean idempotentReplay, String maQr) {
    static DonThanhToanResponse tu(DonThanhToan don, GiaoDichThanhToan giaoDich, boolean replay) {
        return new DonThanhToanResponse(don.getMaDon(), giaoDich.getMaGiaoDich(), don.getMaPhien(),
                don.getSoTien(), don.getPhuongThuc(), don.getTrangThai(), giaoDich.getKetQua(),
                don.getHanThanhToan(), don.isCheDoGiaLap(), replay, maQr(don));
    }

    /** Nội dung mã QR giả lập (UC23 bước 6); chỉ có khi chọn phương thức MA_QR. Không phải mã thanh toán thật. */
    private static String maQr(DonThanhToan don) {
        if (!PhuongThucThanhToan.MA_QR.name().equals(don.getPhuongThuc())) return null;
        return "TRAMSAC-GIALAP|" + don.getMaDon() + "|" + don.getSoTien().toPlainString();
    }
}
