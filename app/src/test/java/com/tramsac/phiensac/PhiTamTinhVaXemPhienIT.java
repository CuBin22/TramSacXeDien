package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.nguoidung.VaiTro;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** UC13: phí tạm tính (FR19, BM21) và xem phiên theo vai trò, có lọc (BM12, QĐ12). */
class PhiTamTinhVaXemPhienIT extends PhienSacBaseIT {

    private JsonNode chiTiet(String token, String maPhien) throws Exception {
        return json(gui(HttpMethod.GET, "/api/phien/" + maPhien, token, null)
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> danhSachMa(String token, String... thamSo) throws Exception {
        var rb = MockMvcRequestBuilders.get("/api/phien").header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < thamSo.length; i += 2) {
            rb.param(thamSo[i], thamSo[i + 1]);
        }
        return json(mvc.perform(rb).andExpect(status().isOk()).andReturn()).findValuesAsText("maPhien");
    }

    // ---------- phí tạm tính ----------

    @Test
    void phiTamTinh_chiCoKhiDangSacVaGanNhanTamTinh() throws Exception {
        Canh c = dungPhien("AC-1");
        assertThat(laRong(chiTiet(c.token(), c.maPhien()).path("phiTamTinh"))).isTrue();

        String maBatDau = batDauThanhCong(c);
        clock.advance(Duration.ofMinutes(15)); // < 30 phút để token chưa hết hạn (QĐ2)

        JsonNode tam = chiTiet(c.token(), c.maPhien()).path("phiTamTinh");
        assertThat(laRong(tam)).isFalse();
        assertThat(tam.get("nhan").asText()).isEqualTo("Tạm tính");
        assertThat(tam.get("thoiGianDaSacGiay").asLong()).isEqualTo(900L);
        assertThat(tam.get("congSuatKW").decimalValue()).isEqualByComparingTo("7.40");
        assertThat(tam.get("dienNangUocLuongKWh").decimalValue()).isEqualByComparingTo("1.850");
        assertThat(tam.get("phiTamTinh").decimalValue()).isEqualByComparingTo("1850");
        assertThat(maBatDau).isNotBlank();
        // Phí tạm tính không được lưu.
        assertThat(dem("SELECT count(*) FROM PhiSac WHERE MaPhien = ?", c.maPhien())).isZero();

        String maDung = maLenh(dung(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        ack("AC-1", maDung, "OK", 1.85);

        JsonNode sau = chiTiet(c.token(), c.maPhien());
        assertThat(laRong(sau.path("phiTamTinh"))).isTrue();
        assertThat(sau.get("tongPhi").decimalValue()).isEqualByComparingTo("1850");
        assertThat(sau.get("trangThai").asText()).isEqualTo("Chờ thanh toán");
    }

    // ---------- xem phiên theo vai trò (QĐ12) ----------

    @Test
    void nguoiDungChiThayPhienCuaMinh_nhanVienVaQuanTriThayTatCa() throws Exception {
        Canh a = dungPhien("AC-1");
        taoCong("AC-2", "AC", "Rảnh", CAU_HINH, 0);
        Canh b = taoPhienChoCong("AC-2");
        String tokenNv = tokenVaiTro(VaiTro.NHAN_VIEN_TRUC);
        String tokenQt = tokenVaiTro(VaiTro.QUAN_TRI);

        assertThat(danhSachMa(a.token())).containsExactly(a.maPhien());
        assertThat(danhSachMa(b.token())).containsExactly(b.maPhien());
        assertThat(danhSachMa(tokenNv)).containsExactlyInAnyOrder(a.maPhien(), b.maPhien());
        assertThat(danhSachMa(tokenQt)).containsExactlyInAnyOrder(a.maPhien(), b.maPhien());

        chiTiet(tokenNv, a.maPhien());
        gui(HttpMethod.GET, "/api/phien/" + a.maPhien(), b.token(), null).andExpect(status().isNotFound());
        gui(HttpMethod.GET, "/api/phien", tokenVaiTro(VaiTro.KY_THUAT_VIEN), null).andExpect(status().isForbidden());
    }

    @Test
    void locTheoTrangThaiVaKhoangNgay() throws Exception {
        Canh a = dungPhien("AC-1");
        taoCong("AC-2", "AC", "Rảnh", CAU_HINH, 0);
        Canh b = taoPhienChoCong("AC-2");
        batDauThanhCong(b);
        String tokenNv = tokenVaiTro(VaiTro.NHAN_VIEN_TRUC);
        String homNay = bayGio().toLocalDate().toString();
        String homQua = bayGio().toLocalDate().minusDays(1).toString();

        assertThat(danhSachMa(tokenNv, "trangThai", "Đã đặt")).containsExactly(a.maPhien());
        assertThat(danhSachMa(tokenNv, "trangThai", "Đang sạc")).containsExactly(b.maPhien());
        assertThat(danhSachMa(tokenNv, "tuNgay", homNay, "denNgay", homNay))
                .containsExactlyInAnyOrder(a.maPhien(), b.maPhien());
        assertThat(danhSachMa(tokenNv, "tuNgay", homQua, "denNgay", homQua)).isEmpty();
    }

    @Test
    void boLocKhongHopLe_badRequest() throws Exception {
        Canh a = dungPhien("AC-1");
        mvc.perform(MockMvcRequestBuilders.get("/api/phien").header("Authorization", "Bearer " + a.token())
                .param("trangThai", "Không tồn tại")).andExpect(status().isBadRequest());
        String body = mvc.perform(MockMvcRequestBuilders.get("/api/phien")
                .header("Authorization", "Bearer " + a.token())
                .param("tuNgay", "2026-10-10").param("denNgay", "2026-10-01"))
                .andExpect(status().isBadRequest()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("KHOANG_THOI_GIAN_KHONG_HOP_LE");
    }
}
