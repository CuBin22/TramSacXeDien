package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhienSacTest {

    private PhienSac phienMoi() {
        return new PhienSac("PS-1", "DP-1", "C-1");
    }

    @Test
    void phienMoi_batDauO_DaDat_khongKhoa() {
        PhienSac p = phienMoi();
        assertThat(p.layTrangThai()).isEqualTo(TrangThaiPhien.DA_DAT);
        assertThat(p.isKhoaPhien()).isFalse();
    }

    @Test
    void chuyenTrangThai_theoDungThuTuVongDoi() {
        PhienSac p = phienMoi();
        p.chuyenTrangThai(TrangThaiPhien.CHO);
        p.chuyenTrangThai(TrangThaiPhien.DANG_SAC);
        p.chuyenTrangThai(TrangThaiPhien.KET_THUC_SAC);
        p.chuyenTrangThai(TrangThaiPhien.CHO_THANH_TOAN);
        p.chuyenTrangThai(TrangThaiPhien.HOAN_TAT);
        assertThat(p.getTrangThai()).isEqualTo("Hoàn tất");
    }

    @Test
    void chuyenTrangThai_boQuaBuocTrungGian_biChan() {
        PhienSac p = phienMoi();
        assertThatThrownBy(() -> p.chuyenTrangThai(TrangThaiPhien.DANG_SAC))
                .isInstanceOf(IllegalStateException.class);
        assertThat(p.layTrangThai()).as("trạng thái không đổi khi bị chặn").isEqualTo(TrangThaiPhien.DA_DAT);
    }

    @Test
    void tuTrangThaiCuoi_khongChuyenTiepDuoc() {
        PhienSac huy = phienMoi();
        huy.chuyenTrangThai(TrangThaiPhien.HUY);
        assertThatThrownBy(() -> huy.chuyenTrangThai(TrangThaiPhien.CHO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> huy.chuyenTrangThai(TrangThaiPhien.DA_DAT)).isInstanceOf(IllegalStateException.class);

        PhienSac xong = phienMoi();
        for (TrangThaiPhien t : new TrangThaiPhien[]{TrangThaiPhien.CHO, TrangThaiPhien.DANG_SAC,
                TrangThaiPhien.KET_THUC_SAC, TrangThaiPhien.CHO_THANH_TOAN, TrangThaiPhien.HOAN_TAT}) {
            xong.chuyenTrangThai(t);
        }
        assertThatThrownBy(() -> xong.chuyenTrangThai(TrangThaiPhien.CHO_THANH_TOAN))
                .as("callback trùng không mở lại phiên đã Hoàn tất").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chuyenSangChinhNo_biChan() {
        PhienSac p = phienMoi();
        assertThatThrownBy(() -> p.chuyenTrangThai(TrangThaiPhien.DA_DAT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doiCong_capNhatMaCong() {
        PhienSac p = phienMoi();
        p.doiCong("C-2");
        assertThat(p.getMaCong()).isEqualTo("C-2");
    }
}
