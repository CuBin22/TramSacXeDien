package com.tramsac.congsac;

/** Sáu trạng thái của cổng sạc (sơ đồ trạng thái CongSac). */
public enum TrangThaiCong {
    RANH("Rảnh"),
    DA_DAT("Đã đặt"),
    DANG_SAC("Đang sạc"),
    LOI("Lỗi"),
    BAO_TRI("Bảo trì"),
    MAT_KET_NOI("Mất kết nối");

    private final String giaTri;

    TrangThaiCong(String giaTri) {
        this.giaTri = giaTri;
    }

    public String giaTri() {
        return giaTri;
    }

    /** Cổng ở trạng thái này không được phân bổ cho đặt chỗ mới (UC08 bước 3). */
    public boolean khongKhaDung() {
        return this == LOI || this == BAO_TRI || this == MAT_KET_NOI;
    }

    public static TrangThaiCong tuGiaTri(String giaTri) {
        for (TrangThaiCong t : values()) {
            if (t.giaTri.equals(giaTri)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Trạng thái cổng không hợp lệ: " + giaTri);
    }
}
