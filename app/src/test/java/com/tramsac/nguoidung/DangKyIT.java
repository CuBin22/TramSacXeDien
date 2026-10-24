package com.tramsac.nguoidung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class DangKyIT extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper om;

    @AfterEach
    void cleanUp() {
        List<String> ids = jdbc.queryForList(
                "SELECT MaND FROM NguoiDung WHERE Email LIKE '%@test.local' OR SoDienThoai LIKE '0999%'",
                String.class);
        for (String id : ids) {
            jdbc.update("DELETE FROM NhatKyHeThong WHERE MaND = ?", id);
            jdbc.update("DELETE FROM Xe WHERE MaND = ?", id);
            jdbc.update("DELETE FROM NguoiDung WHERE MaND = ?", id);
        }
    }

    // ---------- đường chính ----------

    @Test
    void dangKy_valid_returns201AndStoresHashedPassword() throws Exception {
        String email = email();
        post(hopLe(email, plate()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maND").value(startsWith("ND-")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Abcd1234"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2"))));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM NguoiDung WHERE Email = ?", email);
        assertThat((String) row.get("matkhauhash")).isNotEqualTo("Abcd1234").startsWith("$2");
        assertThat(row.get("vaitro")).isEqualTo("Người dùng");
        assertThat(row.get("solansailientiep")).isEqualTo(0);
        Integer xe = jdbc.queryForObject("SELECT count(*) FROM Xe WHERE MaND = ?", Integer.class, row.get("mand"));
        assertThat(xe).isEqualTo(1);
        Integer nhatKy = jdbc.queryForObject(
                "SELECT count(*) FROM NhatKyHeThong WHERE MaND = ? AND HanhDong = 'DANG_KY'",
                Integer.class, row.get("mand"));
        assertThat(nhatKy).isEqualTo(1);
    }

    @Test
    void dangKy_phoneOnly_returns201() throws Exception {
        Map<String, Object> body = hopLe(null, plate());
        body.put("soDienThoai", "0999 " + (100000 + (int) (Math.random() * 899999)));
        post(body).andExpect(status().isCreated());
    }

    @Test
    void dangKy_extraVaiTroField_isIgnored_roleStaysNguoiDung() throws Exception {
        String email = email();
        Map<String, Object> body = hopLe(email, plate());
        body.put("vaiTro", "Quản trị");
        post(body).andExpect(status().isCreated());

        String vaiTro = jdbc.queryForObject("SELECT VaiTro FROM NguoiDung WHERE Email = ?", String.class, email);
        assertThat(vaiTro).isEqualTo("Người dùng");
    }

    // ---------- dữ liệu không hợp lệ ----------

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "abc123", "12345678"})
    void dangKy_weakPassword_returns400(String matKhau) throws Exception {
        Map<String, Object> body = hopLe(email(), plate());
        body.put("matKhau", matKhau);
        body.put("xacNhanMatKhau", matKhau);
        post(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truong.matKhau").exists());
    }

    @Test
    void dangKy_passwordMismatch_returns400() throws Exception {
        Map<String, Object> body = hopLe(email(), plate());
        body.put("xacNhanMatKhau", "Khac1234");
        post(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truong.xacNhanMatKhau").exists());
    }

    @Test
    void dangKy_noEmailNoPhone_returns400() throws Exception {
        post(hopLe(null, plate()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"));
    }

    @Test
    void dangKy_invalidEmailFormat_returns400() throws Exception {
        post(hopLe("khong-phai-email", plate()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.email").exists());
    }

    // ---------- trùng dữ liệu ----------

    @Test
    void dangKy_duplicateEmailIgnoringCaseAndSpaces_returns409() throws Exception {
        String email = email();
        post(hopLe(email, plate())).andExpect(status().isCreated());

        post(hopLe(" " + email.toUpperCase() + " ", plate()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_DU_LIEU"));
    }

    @Test
    void dangKy_duplicatePlate_returns409() throws Exception {
        String bienSo = plate();
        post(hopLe(email(), bienSo)).andExpect(status().isCreated());

        post(hopLe(email(), " " + bienSo.toLowerCase() + " "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_BIEN_SO"));
    }

    @Test
    void dangKy_concurrentSameEmail_oneCreatedOtherConflict() throws Exception {
        for (int lan = 0; lan < 5; lan++) {
            String email = email();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                Map<String, Object> body = hopLe(email, plate());
                futures.add(pool.submit(() -> {
                    start.await();
                    return post(body).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertThat(statuses).as("lan %d", lan).containsExactlyInAnyOrder(201, 409);
        }
    }

    // ---------- tiện ích ----------

    private ResultActions post(Map<String, Object> body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/dang-ky")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(om.writeValueAsString(body)));
    }

    private static Map<String, Object> hopLe(String email, String bienSo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hoTen", "Nguyễn Văn A");
        if (email != null) {
            m.put("email", email);
        }
        m.put("doiTuong", "Sinh viên");
        m.put("maDinhDanh", "SV001");
        m.put("bienSo", bienSo);
        m.put("loaiXe", "Ô tô điện");
        m.put("loaiCongSuDung", "AC");
        m.put("matKhau", "Abcd1234");
        m.put("xacNhanMatKhau", "Abcd1234");
        return m;
    }

    private static String email() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    private static String plate() {
        return "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
