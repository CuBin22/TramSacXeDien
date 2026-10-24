package com.tramsac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.common.DevDataSeeder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@ActiveProfiles("dev")
class DevDataSeederIT extends AbstractIntegrationTest {

    private static final List<String> EMAILS = List.of(
            "admin@tramsac.local", "ktv@tramsac.local", "truc@tramsac.local", "user@tramsac.local");

    @Autowired
    DevDataSeeder seeder;
    @Autowired
    ObjectMapper om;

    @BeforeEach
    @AfterEach
    void xoaTaiKhoanMau() {
        // Chạy cả trước và sau: runner đã nạp dữ liệu lúc khởi động context nên cần dọn để mỗi test tự nạp lại
        List<String> ids = jdbc.queryForList("SELECT MaND FROM NguoiDung WHERE Email LIKE '%@tramsac.local'",
                String.class);
        for (String id : ids) {
            jdbc.update("DELETE FROM NhatKyHeThong WHERE MaND = ?", id);
            jdbc.update("DELETE FROM LichSuTrangThaiPhien WHERE MaPhien IN (SELECT p.MaPhien FROM PhienSac p "
                    + "JOIN DatPhien d ON d.MaDatPhien = p.MaDatPhien WHERE d.MaND = ?)", id);
            jdbc.update("DELETE FROM PhienSac WHERE MaDatPhien IN (SELECT MaDatPhien FROM DatPhien WHERE MaND = ?)", id);
            jdbc.update("DELETE FROM DatPhien WHERE MaND = ?", id);
            jdbc.update("DELETE FROM Xe WHERE MaND = ?", id);
            jdbc.update("DELETE FROM LichSuPhanQuyen WHERE MaND = ?", id);
            jdbc.update("DELETE FROM NguoiDung WHERE MaND = ?", id);
        }
        jdbc.update("DELETE FROM CongSac WHERE MaCong LIKE 'C-%'");
        jdbc.update("DELETE FROM CauHinhCongSuat WHERE MaCauHinh = 'CH-MAC-DINH'");
    }

    @Test
    void seed_createsFourAccountsWithFourDistinctRoles() {
        seeder.run(null);

        Integer soTaiKhoan = jdbc.queryForObject(
                "SELECT count(*) FROM NguoiDung WHERE Email LIKE '%@tramsac.local'", Integer.class);
        Integer soVaiTro = jdbc.queryForObject(
                "SELECT count(DISTINCT VaiTro) FROM NguoiDung WHERE Email LIKE '%@tramsac.local'", Integer.class);
        assertThat(soTaiKhoan).isEqualTo(4);
        assertThat(soVaiTro).isEqualTo(4);
    }

    @Test
    void seed_createsFourPortsInOneConfig_andIsIdempotent() {
        seeder.run(null);
        seeder.run(null);

        Integer soCong = jdbc.queryForObject("SELECT count(*) FROM CongSac WHERE MaCauHinh = 'CH-MAC-DINH'",
                Integer.class);
        Integer soAc = jdbc.queryForObject("SELECT count(*) FROM CongSac WHERE LoaiCong = 'AC' AND MaCong LIKE 'C-%'",
                Integer.class);
        Integer soRanh = jdbc.queryForObject("SELECT count(*) FROM CongSac WHERE TrangThai = 'Rảnh' AND MaCong LIKE 'C-%'",
                Integer.class);
        assertThat(soCong).isEqualTo(4);
        assertThat(soAc).isEqualTo(2);
        assertThat(soRanh).isEqualTo(4);
    }

    @Test
    void seededUser_canBookASeededPort() throws Exception {
        seeder.run(null);
        String dangNhap = mvc.perform(MockMvcRequestBuilders.post("/api/auth/dang-nhap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("dinhDanh", "user@tramsac.local", "matKhau", "Demo1234"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String tk = om.readTree(dangNhap).get("token").asText();

        java.time.LocalDateTime bd = java.time.LocalDateTime.now(clock).plusHours(2)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        mvc.perform(MockMvcRequestBuilders.post("/api/dat-phien")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
                        .content(om.writeValueAsString(Map.of("bienSo", "59A-123.45", "loaiCong", "AC",
                                "gioBatDau", f.format(bd.atOffset(java.time.ZoneOffset.UTC)),
                                "gioKetThuc", f.format(bd.plusHours(1).atOffset(java.time.ZoneOffset.UTC))))))
                .andExpect(status().isCreated());
    }

    @Test
    void seed_isIdempotent() {
        seeder.run(null);
        seeder.run(null);

        Integer soTaiKhoan = jdbc.queryForObject(
                "SELECT count(*) FROM NguoiDung WHERE Email LIKE '%@tramsac.local'", Integer.class);
        Integer soXe = jdbc.queryForObject(
                "SELECT count(*) FROM Xe x JOIN NguoiDung n ON n.MaND = x.MaND WHERE n.Email = 'user@tramsac.local'",
                Integer.class);
        assertThat(soTaiKhoan).isEqualTo(4);
        assertThat(soXe).isEqualTo(1);
    }

    @Test
    void seededUser_hasOneAcVehicle() {
        seeder.run(null);

        Map<String, Object> xe = jdbc.queryForMap(
                "SELECT x.BienSo, x.LoaiCongSuDung FROM Xe x JOIN NguoiDung n ON n.MaND = x.MaND "
                        + "WHERE n.Email = 'user@tramsac.local'");
        assertThat(xe.get("bienso")).isEqualTo("59A-123.45");
        assertThat(xe.get("loaicongsudung")).isEqualTo("AC");
    }

    @Test
    void seededAccounts_canLoginWithDemoPassword_andAdminHasAdminRights() throws Exception {
        seeder.run(null);

        String tokenAdmin = null;
        for (String email : EMAILS) {
            MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/auth/dang-nhap")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("dinhDanh", email, "matKhau", "Demo1234"))))
                    .andExpect(status().isOk())
                    .andReturn();
            if (email.startsWith("admin@")) {
                tokenAdmin = om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .get("token").asText();
            }
        }
        mvc.perform(MockMvcRequestBuilders.get("/api/test/chi-quan-tri")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }
}
