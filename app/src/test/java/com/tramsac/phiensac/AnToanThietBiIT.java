package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.nguoidung.VaiTro;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

/** UC19 (kịch bản 4): thiết bị báo lỗi giữa phiên → dừng, khóa phiên, khóa cổng, sự cố Khẩn cấp, không tính phí. */
class AnToanThietBiIT extends PhienSacBaseIT {

    private List<Map<String, Object>> suCoCuaCong(String maCong) {
        return jdbc.queryForList("SELECT mucuutien, loaisuco, trangthai FROM SuCo WHERE MaCong = ?",
                maCong);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RO_DIEN", "QUA_DONG", "DUNG_KHAN_CAP"})
    void thietBiBaoLoiGiuaPhien_khoaPhienVaCongVaTaoSuCoKhanCap(String loaiLoi) throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);

        thietBiBaoLoi("AC-1", loaiLoi);

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
        assertThat(jdbc.queryForObject("SELECT KhoaPhien FROM PhienSac WHERE MaPhien = ?", Boolean.class,
                c.maPhien())).isTrue();
        assertThat(trangThaiDatPhien(c.maDatPhien())).isEqualTo("Hủy");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
        assertThat(jdbc.queryForObject("SELECT CongSuatDuocCap FROM CongSac WHERE MaCong = 'AC-1'",
                BigDecimal.class)).isEqualByComparingTo("0");
        List<Map<String, Object>> suCo = suCoCuaCong("AC-1");
        assertThat(suCo).hasSize(1);
        assertThat(suCo.get(0).get("mucuutien")).isEqualTo("Khẩn cấp");
        assertThat(suCo.get(0).get("loaisuco")).isEqualTo("An toàn điện");
        assertThat(suCo.get(0).get("trangthai")).isEqualTo("Mới");
        assertThat(mqtt.soThongDiepLoai("DUNG")).isGreaterThanOrEqualTo(1);
        // Phiên lỗi giữa chừng không tính phí.
        assertThat(dem("SELECT count(*) FROM PhiSac WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    @Test
    void phienBiKhoaKhongKhoiDongLaiDuoc_vaLoiTrungGopVaoSuCoDangMo() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        thietBiBaoLoi("AC-1", "RO_DIEN");

        batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict());
        thietBiBaoLoi("AC-1", "QUA_DONG");
        thietBiHeartbeat("AC-1", false);

        assertThat(suCoCuaCong("AC-1")).hasSize(1);
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
    }

    @Test
    void loiKhiPhienDangCho_ackMuonKhongLamPhienSongLai() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));

        thietBiBaoLoi("AC-1", "RO_DIEN");
        ack("AC-1", ma, "OK", null);

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
        assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                .isEqualTo("Thất bại");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
    }

    @Test
    void xacNhanXuLy_chiKhiThietBiAnToan_vaDongSuCo() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        thietBiBaoLoi("AC-1", "RO_DIEN");
        String tokenKtv = tokenVaiTro(VaiTro.KY_THUAT_VIEN);

        mqtt.datAnToan(false);
        gui(HttpMethod.POST, "/api/cong/AC-1/xac-nhan-xu-ly", tokenKtv, null).andExpect(status().isConflict());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");

        mqtt.datAnToan(true);
        gui(HttpMethod.POST, "/api/cong/AC-1/xac-nhan-xu-ly", c.token(), null).andExpect(status().isForbidden());
        gui(HttpMethod.POST, "/api/cong/AC-1/xac-nhan-xu-ly", tokenKtv, null).andExpect(status().isOk());

        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        assertThat(suCoCuaCong("AC-1").get(0).get("trangthai")).isEqualTo("Đã đóng");
    }
}
