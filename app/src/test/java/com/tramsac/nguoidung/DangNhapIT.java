package com.tramsac.nguoidung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.AbstractIntegrationTest;
import com.tramsac.common.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class DangNhapIT extends AbstractIntegrationTest {

    private static final String MAT_KHAU = "Abcd1234";

    @Autowired
    NguoiDungRepository nguoiDungRepository;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JwtService jwtService;
    @Autowired
    ObjectMapper om;

    @AfterEach
    void cleanUp() {
        List<String> ids = jdbc.queryForList(
                "SELECT MaND FROM NguoiDung WHERE Email LIKE '%@dn.test' OR SoDienThoai LIKE '0888%'",
                String.class);
        for (String id : ids) {
            jdbc.update("DELETE FROM NhatKyHeThong WHERE MaND = ?", id);
            jdbc.update("DELETE FROM NguoiDung WHERE MaND = ?", id);
        }
    }

    // ---------- đăng nhập thành công ----------

    @Test
    void dangNhap_correct_returnsTokenThatWorks() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);

        MvcResult r = dangNhap(nd.getEmail(), MAT_KHAU)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.maND").value(nd.getMaND()))
                .andExpect(jsonPath("$.vaiTro").value("Người dùng"))
                .andReturn();

        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", "Bearer " + token(r)))
                .andExpect(status().isOk())
                .andExpect(content().string(nd.getMaND()));
    }

    @Test
    void dangNhap_byPhoneWithSpaces_works() throws Exception {
        nguoiDungRepository.save(new NguoiDung("ND-" + uid(), "A", null, "0888123456",
                encoder.encode(MAT_KHAU), "Sinh viên", null, VaiTro.NGUOI_DUNG));
        dangNhap("0888 123 456", MAT_KHAU).andExpect(status().isOk());
    }

    @Test
    void dangNhap_emailIsCaseInsensitive() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        dangNhap("  " + nd.getEmail().toUpperCase() + " ", MAT_KHAU).andExpect(status().isOk());
    }

    // ---------- đăng nhập sai ----------

    @Test
    void dangNhap_unknownIdentifier_sameResponseAsWrongPassword() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String khongTonTai = dangNhap("khongco@dn.test", MAT_KHAU)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ma").value("SAI_THONG_TIN"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String saiMatKhau = dangNhap(nd.getEmail(), "Sai12345")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(khongTonTai).isEqualTo(saiMatKhau);
    }

    @Test
    void dangNhap_blankFields_returns400() throws Exception {
        dangNhap("", "").andExpect(status().isBadRequest());
    }

    @Test
    void dangNhap_wrongPasswordFiveTimes_locks15Minutes() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);

        for (int i = 1; i <= 5; i++) {
            dangNhap(nd.getEmail(), "Sai12345")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ma").value("SAI_THONG_TIN"));
        }
        assertThat(layKhoaDenLuc(nd)).isNotNull();
        assertThat(laySoLanSai(nd)).isEqualTo(5);

        dangNhap(nd.getEmail(), "Sai12345")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.ma").value("TAM_KHOA"))
                .andExpect(jsonPath("$.truong.conLaiGiay").exists());
        assertThat(laySoLanSai(nd)).as("khi đang khóa không tăng bộ đếm").isEqualTo(5);
    }

    @Test
    void dangNhap_lockedWithCorrectPassword_stillRejected_thenWorksAfter15Minutes() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        for (int i = 0; i < 5; i++) {
            dangNhap(nd.getEmail(), "Sai12345").andExpect(status().isUnauthorized());
        }

        dangNhap(nd.getEmail(), MAT_KHAU).andExpect(status().isLocked());

        clock.advance(Duration.ofMinutes(14));
        dangNhap(nd.getEmail(), MAT_KHAU).andExpect(status().isLocked());

        clock.advance(Duration.ofSeconds(61));
        dangNhap(nd.getEmail(), MAT_KHAU).andExpect(status().isOk());
        assertThat(laySoLanSai(nd)).isZero();
        assertThat(layKhoaDenLuc(nd)).isNull();
    }

    @Test
    void dangNhap_afterLockExpires_wrongPasswordStartsFreshCount() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        for (int i = 0; i < 5; i++) {
            dangNhap(nd.getEmail(), "Sai12345").andExpect(status().isUnauthorized());
        }
        clock.advance(Duration.ofMinutes(16));

        dangNhap(nd.getEmail(), "Sai12345").andExpect(status().isUnauthorized());
        assertThat(laySoLanSai(nd)).isEqualTo(1);
        assertThat(layKhoaDenLuc(nd)).isNull();
    }

    @Test
    void dangNhap_successResetsCounter() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        for (int i = 0; i < 3; i++) {
            dangNhap(nd.getEmail(), "Sai12345").andExpect(status().isUnauthorized());
        }
        dangNhap(nd.getEmail(), MAT_KHAU).andExpect(status().isOk());
        assertThat(laySoLanSai(nd)).isZero();

        for (int i = 0; i < 4; i++) {
            dangNhap(nd.getEmail(), "Sai12345").andExpect(status().isUnauthorized());
        }
        assertThat(layKhoaDenLuc(nd)).as("4 lần sai sau khi reset chưa bị khóa").isNull();
    }

    @Test
    void dangNhap_parallelWrongAttempts_stillLocksAfterExactlyFive() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        int soLuong = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(soLuong);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < soLuong; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return dangNhap(nd.getEmail(), "Sai12345").andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 401).count()).isEqualTo(5);
        assertThat(statuses.stream().filter(s -> s == 423).count()).isEqualTo(soLuong - 5);
        assertThat(laySoLanSai(nd)).isEqualTo(5);
        assertThat(layKhoaDenLuc(nd)).isNotNull();
    }

    // ---------- token và xác thực ----------

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ma").value("CHUA_XAC_THUC"));
    }

    @Test
    void protectedEndpoint_withTamperedOrMalformedToken_returns401() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());
        String[] parts = token.split("\\.");
        char c = parts[1].charAt(5);
        String gia = parts[0] + "." + parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A')
                + parts[1].substring(6) + "." + parts[2];

        for (String h : List.of("Bearer " + gia, "Bearer abc", "Bearer ", "Basic " + token, token)) {
            mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", h))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());

        clock.advance(Duration.ofMinutes(31));
        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void response_includesRefreshedToken_andSessionSlidesWithActivity() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token0 = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());

        clock.advance(Duration.ofMinutes(20));
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/test/toi")
                        .header("Authorization", "Bearer " + token0))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-New-Token"))
                .andReturn();
        String token1 = r.getResponse().getHeader("X-New-Token");
        assertThat(jwtService.docMaND(token1)).contains(nd.getMaND());

        clock.advance(Duration.ofMinutes(20));   // đã 40 phút kể từ lúc đăng nhập, 20 phút kể từ hoạt động cuối
        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", "Bearer " + token0))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());
    }

    // ---------- phân quyền ----------

    @ParameterizedTest
    @EnumSource(value = VaiTro.class, names = "QUAN_TRI", mode = EnumSource.Mode.EXCLUDE)
    void adminEndpoint_nonAdminRoles_return403(VaiTro vaiTro) throws Exception {
        NguoiDung nd = taoNguoiDung(vaiTro);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());
        mvc.perform(MockMvcRequestBuilders.get("/api/test/chi-quan-tri").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ma").value("KHONG_DU_QUYEN"));
    }

    @Test
    void adminEndpoint_adminRole_returns200() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.QUAN_TRI);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());
        mvc.perform(MockMvcRequestBuilders.get("/api/test/chi-quan-tri").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void roleChange_takesEffectWithoutRelogin() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());
        var req = MockMvcRequestBuilders.get("/api/test/chi-quan-tri").header("Authorization", "Bearer " + token);

        mvc.perform(req).andExpect(status().isForbidden());

        jdbc.update("UPDATE NguoiDung SET VaiTro = 'Quản trị' WHERE MaND = ?", nd.getMaND());
        mvc.perform(req).andExpect(status().isOk());

        jdbc.update("UPDATE NguoiDung SET VaiTro = 'Người dùng' WHERE MaND = ?", nd.getMaND());
        mvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void tokenOfDeletedAccount_returns401() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = token(dangNhap(nd.getEmail(), MAT_KHAU).andReturn());
        jdbc.update("DELETE FROM NhatKyHeThong WHERE MaND = ?", nd.getMaND());
        jdbc.update("DELETE FROM NguoiDung WHERE MaND = ?", nd.getMaND());

        mvc.perform(MockMvcRequestBuilders.get("/api/test/toi").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- tiện ích ----------

    private NguoiDung taoNguoiDung(VaiTro vaiTro) {
        String id = uid();
        return nguoiDungRepository.save(new NguoiDung("ND-" + id, "Người Test", "u" + id + "@dn.test", null,
                encoder.encode(MAT_KHAU), "Sinh viên", null, vaiTro));
    }

    private ResultActions dangNhap(String dinhDanh, String matKhau) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/dang-nhap")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(om.writeValueAsString(Map.of("dinhDanh", dinhDanh, "matKhau", matKhau))));
    }

    private String token(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(body).get("token").asText();
    }

    private int laySoLanSai(NguoiDung nd) {
        return jdbc.queryForObject("SELECT SoLanSaiLienTiep FROM NguoiDung WHERE MaND = ?", Integer.class,
                nd.getMaND());
    }

    private Object layKhoaDenLuc(NguoiDung nd) {
        return jdbc.queryForMap("SELECT KhoaDenLuc FROM NguoiDung WHERE MaND = ?", nd.getMaND())
                .get("khoadenluc");
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
