package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Sinh test từ sơ đồ trạng thái PhienSac: mỗi mũi tên một ca dương, mọi cặp còn lại một ca âm (7 × 7 = 49 cặp). */
class TrangThaiPhienTest {

    private static final Set<String> MUI_TEN_TREN_SO_DO = Set.of(
            "DA_DAT>CHO", "DA_DAT>HUY",
            "CHO>DANG_SAC", "CHO>HUY",
            "DANG_SAC>KET_THUC_SAC", "DANG_SAC>HUY",
            "KET_THUC_SAC>CHO_THANH_TOAN",
            "CHO_THANH_TOAN>HOAN_TAT");

    static Stream<Arguments> moiCapTrangThai() {
        return Arrays.stream(TrangThaiPhien.values())
                .flatMap(tu -> Arrays.stream(TrangThaiPhien.values()).map(den -> Arguments.of(tu, den)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("moiCapTrangThai")
    void choPhepChuyenSang_chiDungVoiCacMuiTenTrenSoDo(TrangThaiPhien tu, TrangThaiPhien den) {
        boolean mongDoi = MUI_TEN_TREN_SO_DO.contains(tu.name() + ">" + den.name());
        assertThat(tu.choPhepChuyenSang(den)).as("%s → %s", tu.giaTri(), den.giaTri()).isEqualTo(mongDoi);
    }

    @Test
    void hoanTatVaHuy_laTrangThaiCuoi_cacTrangThaiKhacThiKhong() {
        assertThat(TrangThaiPhien.HOAN_TAT.laTrangThaiCuoi()).isTrue();
        assertThat(TrangThaiPhien.HUY.laTrangThaiCuoi()).isTrue();
        for (TrangThaiPhien t : TrangThaiPhien.values()) {
            if (t != TrangThaiPhien.HOAN_TAT && t != TrangThaiPhien.HUY) {
                assertThat(t.laTrangThaiCuoi()).as(t.giaTri()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TrangThaiPhien.class)
    void giaTri_roundTrip(TrangThaiPhien t) {
        assertThat(TrangThaiPhien.tuGiaTri(t.giaTri())).isEqualTo(t);
    }
}
