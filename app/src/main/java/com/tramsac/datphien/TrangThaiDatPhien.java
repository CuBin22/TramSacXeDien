package com.tramsac.datphien;

/** Trạng thái đặt chỗ (sơ đồ trạng thái DatPhien). */
public enum TrangThaiDatPhien {
    DA_DAT("Đã đặt"),
    DANG_SAC("Đang sạc"),
    HOAN_TAT("Hoàn tất"),
    HUY("Hủy"),
    QUA_HAN("Quá hạn không đến");

    private final String giaTri;

    TrangThaiDatPhien(String giaTri) {
        this.giaTri = giaTri;
    }

    public String giaTri() {
        return giaTri;
    }

    public static TrangThaiDatPhien tuGiaTri(String giaTri) {
        for (TrangThaiDatPhien t : values()) {
            if (t.giaTri.equals(giaTri)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Trạng thái đặt chỗ không hợp lệ: " + giaTri);
    }
}
