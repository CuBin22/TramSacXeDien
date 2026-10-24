package com.tramsac.phiensac;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Vòng đời phiên sạc theo sơ đồ trạng thái PhienSac: Đã đặt → Chờ → Đang sạc → Kết thúc sạc →
 * Chờ thanh toán → Hoàn tất; Hủy từ Đã đặt, Chờ hoặc Đang sạc. Hoàn tất và Hủy là trạng thái cuối.
 * Đây là BẢNG DUY NHẤT quy định chuyển trạng thái hợp lệ (QĐ10): không bỏ qua trạng thái trung gian.
 */
public enum TrangThaiPhien {
    DA_DAT("Đã đặt"),
    CHO("Chờ"),
    DANG_SAC("Đang sạc"),
    KET_THUC_SAC("Kết thúc sạc"),
    CHO_THANH_TOAN("Chờ thanh toán"),
    HOAN_TAT("Hoàn tất"),
    HUY("Hủy");

    private static final Map<TrangThaiPhien, Set<TrangThaiPhien>> HOP_LE = new EnumMap<>(TrangThaiPhien.class);

    static {
        HOP_LE.put(DA_DAT, EnumSet.of(CHO, HUY));
        HOP_LE.put(CHO, EnumSet.of(DANG_SAC, HUY));
        HOP_LE.put(DANG_SAC, EnumSet.of(KET_THUC_SAC, HUY));
        HOP_LE.put(KET_THUC_SAC, EnumSet.of(CHO_THANH_TOAN));
        HOP_LE.put(CHO_THANH_TOAN, EnumSet.of(HOAN_TAT));
        HOP_LE.put(HOAN_TAT, EnumSet.noneOf(TrangThaiPhien.class));
        HOP_LE.put(HUY, EnumSet.noneOf(TrangThaiPhien.class));
    }

    private final String giaTri;

    TrangThaiPhien(String giaTri) {
        this.giaTri = giaTri;
    }

    public String giaTri() {
        return giaTri;
    }

    public boolean choPhepChuyenSang(TrangThaiPhien dich) {
        return HOP_LE.get(this).contains(dich);
    }

    public Set<TrangThaiPhien> cacTrangThaiTiepTheo() {
        return Collections.unmodifiableSet(HOP_LE.get(this));
    }

    public boolean laTrangThaiCuoi() {
        return HOP_LE.get(this).isEmpty();
    }

    public static TrangThaiPhien tuGiaTri(String giaTri) {
        for (TrangThaiPhien t : values()) {
            if (t.giaTri.equals(giaTri)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Trạng thái phiên không hợp lệ: " + giaTri);
    }
}
