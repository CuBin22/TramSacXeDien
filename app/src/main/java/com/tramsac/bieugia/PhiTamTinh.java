package com.tramsac.bieugia;

import java.math.BigDecimal;

/**
 * Phí tạm tính của phiên đang sạc (BM21): chỉ ước lượng, không lưu. Nhãn luôn là "Tạm tính" để giao diện
 * không nhầm với phí chính thức tính theo điện năng thiết bị báo khi kết thúc phiên.
 */
public record PhiTamTinh(long thoiGianDaSacGiay, BigDecimal congSuatKW, BigDecimal dienNangUocLuongKWh,
                         BigDecimal phiTamTinh, String nhan) {

    public static final String NHAN_TAM_TINH = "Tạm tính";

    public PhiTamTinh(long thoiGianDaSacGiay, BigDecimal congSuatKW, BigDecimal dienNangUocLuongKWh,
                      BigDecimal phiTamTinh) {
        this(thoiGianDaSacGiay, congSuatKW, dienNangUocLuongKWh, phiTamTinh, NHAN_TAM_TINH);
    }
}
