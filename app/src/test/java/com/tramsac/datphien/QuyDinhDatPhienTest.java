package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tramsac.common.ApiException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** QĐ5: 30 phút–3 giờ, không đặt giờ đã qua, trước tối đa 24 giờ, loại cổng phù hợp xe. */
class QuyDinhDatPhienTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 8, 0, 0);
    private final QuyDinhDatPhien quyDinh = new QuyDinhDatPhien();

    private void kiemTra(LocalDateTime bd, LocalDateTime kt) {
        quyDinh.kiemTra(bd, kt, NOW, "AC", "AC");
    }

    @Test
    void khungGioHopLe_khongNemLoi() {
        LocalDateTime bd = NOW.plusHours(2);
        assertThatCode(() -> kiemTra(bd, bd.plusHours(1))).doesNotThrowAnyException();
    }

    @Test
    void thoiLuong_dungBienBaMuoiPhutVaBaGio_duocChapNhan() {
        LocalDateTime bd = NOW.plusHours(2);
        assertThatCode(() -> kiemTra(bd, bd.plusMinutes(30))).doesNotThrowAnyException();
        assertThatCode(() -> kiemTra(bd, bd.plusHours(3))).doesNotThrowAnyException();
    }

    @Test
    void thoiLuong_ngoaiBien_biTuChoiTheoTruongGioKetThuc() {
        LocalDateTime bd = NOW.plusHours(2);
        assertThatThrownBy(() -> kiemTra(bd, bd.plusMinutes(29).plusSeconds(59)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.ma()).isEqualTo("DU_LIEU_KHONG_HOP_LE");
                    assertThat(e.truong()).containsKey("gioKetThuc");
                });
        assertThatThrownBy(() -> kiemTra(bd, bd.plusHours(3).plusSeconds(1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.truong()).containsKey("gioKetThuc"));
    }

    @Test
    void gioKetThucKhongSauGioBatDau_biTuChoi() {
        LocalDateTime bd = NOW.plusHours(2);
        assertThatThrownBy(() -> kiemTra(bd, bd)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> kiemTra(bd, bd.minusMinutes(10))).isInstanceOf(ApiException.class);
    }

    @Test
    void khungGioDaQua_biTuChoi_dungLucHienTaiThiDuocChapNhan() {
        assertThatCode(() -> kiemTra(NOW, NOW.plusHours(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> kiemTra(NOW.minusSeconds(1), NOW.plusHours(1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.truong()).containsKey("gioBatDau"));
    }

    @Test
    void datTruocToiDaHaiMuoiBonGio() {
        LocalDateTime bienTren = NOW.plusHours(24);
        assertThatCode(() -> kiemTra(bienTren, bienTren.plusHours(1))).doesNotThrowAnyException();
        LocalDateTime quaBien = bienTren.plusSeconds(1);
        assertThatThrownBy(() -> kiemTra(quaBien, quaBien.plusHours(1)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.truong()).containsKey("gioBatDau"));
    }

    @Test
    void loaiCongKhongPhuHopXe_biTuChoiTheoTruongLoaiCong() {
        LocalDateTime bd = NOW.plusHours(2);
        assertThatThrownBy(() -> quyDinh.kiemTra(bd, bd.plusHours(1), NOW, "DC", "AC"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.truong()).containsKey("loaiCong"));
    }

    @Test
    void nhieuLoiCungLuc_duocGomLaiMotLan() {
        LocalDateTime bd = NOW.minusHours(1);
        assertThatThrownBy(() -> quyDinh.kiemTra(bd, bd.plusMinutes(10), NOW, "DC", "AC"))
                .isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.truong()).containsKeys("gioKetThuc", "gioBatDau", "loaiCong"));
    }
}
