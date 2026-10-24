package com.tramsac.nguoidung;

/** Bốn vai trò của hệ thống; giaTri() là chuỗi lưu trong cột NguoiDung.VaiTro. */
public enum VaiTro {
    QUAN_TRI("Quản trị"),
    KY_THUAT_VIEN("Kỹ thuật viên"),
    NHAN_VIEN_TRUC("Nhân viên trực"),
    NGUOI_DUNG("Người dùng");

    private final String giaTri;

    VaiTro(String giaTri) {
        this.giaTri = giaTri;
    }

    public String giaTri() {
        return giaTri;
    }

    public static VaiTro tuGiaTri(String giaTri) {
        for (VaiTro v : values()) {
            if (v.giaTri.equals(giaTri)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Vai trò không hợp lệ: " + giaTri);
    }
}
