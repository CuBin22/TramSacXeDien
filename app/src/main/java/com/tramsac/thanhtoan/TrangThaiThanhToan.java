package com.tramsac.thanhtoan;

import java.util.Locale;

public enum TrangThaiThanhToan {
    CHO,
    DANG_XU_LY,
    THANH_CONG,
    THAT_BAI,
    HET_HAN,
    DA_HOAN;

    public boolean ketThuc() {
        return this == THANH_CONG || this == THAT_BAI || this == HET_HAN || this == DA_HOAN;
    }

    public static TrangThaiThanhToan tuSuKien(String suKien) {
        try {
            return valueOf(suKien.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Sự kiện thanh toán không hợp lệ.", e);
        }
    }
}
