package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DatPhienDomainTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 15, 8, 0);

    private DatPhien moi() {
        return new DatPhien("DP-1", "ND-1", "XE-1", "C-1", T.minusHours(1), T, T.plusHours(1), "AC");
    }

    @Test
    void datMoi_laDaDat_chuaDoiLichLanNao() {
        DatPhien d = moi();
        assertThat(d.layTrangThai()).isEqualTo(TrangThaiDatPhien.DA_DAT);
        assertThat(d.getSoLanDoiLich()).isZero();
    }

    @Test
    void huy_chuyenSangHuyVaGhiLyDo_khongHuyLanHai() {
        DatPhien d = moi();
        d.huy("Bận việc");
        assertThat(d.layTrangThai()).isEqualTo(TrangThaiDatPhien.HUY);
        assertThat(d.getLyDoHuy()).isEqualTo("Bận việc");
        assertThatThrownBy(() -> d.huy("lần hai")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doiLich_tangSoLan_toiDaHaiLan() {
        DatPhien d = moi();
        d.doiLich(T.plusHours(2), T.plusHours(3), "C-2", "lý do 1");
        d.doiLich(T.plusHours(4), T.plusHours(5), "C-1", "lý do 2");
        assertThat(d.getSoLanDoiLich()).isEqualTo(2);
        assertThat(d.getMaCong()).isEqualTo("C-1");
        assertThat(d.getGioBatDau()).isEqualTo(T.plusHours(4));
        assertThatThrownBy(() -> d.doiLich(T.plusHours(6), T.plusHours(7), "C-1", "lý do 3"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(d.getSoLanDoiLich()).as("lần thứ 3 bị chặn, số lần không đổi").isEqualTo(2);
    }

    @Test
    void danhDauQuaHan_chiTuDaDat() {
        DatPhien d = moi();
        d.danhDauQuaHan();
        assertThat(d.layTrangThai()).isEqualTo(TrangThaiDatPhien.QUA_HAN);
        assertThatThrownBy(d::danhDauQuaHan).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void daHuyHoacQuaHan_khongDoiLichDuoc() {
        DatPhien huy = moi();
        huy.huy("x");
        assertThatThrownBy(() -> huy.doiLich(T, T.plusHours(1), "C-1", "x")).isInstanceOf(IllegalStateException.class);

        DatPhien quaHan = moi();
        quaHan.danhDauQuaHan();
        assertThatThrownBy(() -> quaHan.huy("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lyDoQuaDai_biCatTheoDoRongCot() {
        DatPhien d = moi();
        d.huy("x".repeat(400));
        assertThat(d.getLyDoHuy()).hasSize(255);
    }
}
