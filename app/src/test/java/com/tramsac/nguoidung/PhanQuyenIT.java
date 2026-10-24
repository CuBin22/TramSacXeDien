package com.tramsac.nguoidung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.AuthenticatedIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;

class PhanQuyenIT extends AuthenticatedIT {

    // ---------- đường chính ----------

    @Test
    void adminChangesUserRole_updatesAndWritesHistoryAndAudit() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        NguoiDung user = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = dangNhapLayToken(admin);

        doiVaiTro(token, user, "Kỹ thuật viên")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaiTroCu").value("Người dùng"))
                .andExpect(jsonPath("$.vaiTroMoi").value("Kỹ thuật viên"))
                .andExpect(jsonPath("$.thayDoi").value(true));

        assertThat(vaiTroTrongDb(user)).isEqualTo("Kỹ thuật viên");
        Map<String, Object> ls = jdbc.queryForMap("SELECT * FROM LichSuPhanQuyen WHERE MaND = ?", user.getMaND());
        assertThat(ls.get("vaitrocu")).isEqualTo("Người dùng");
        assertThat(ls.get("vaitromoi")).isEqualTo("Kỹ thuật viên");
        assertThat(ls.get("nguoithuchien")).isEqualTo(admin.getMaND());
        assertThat(ls.get("thoidiem")).isNotNull();
        Integer nhatKy = jdbc.queryForObject(
                "SELECT count(*) FROM NhatKyHeThong WHERE MaND = ? AND HanhDong = 'PHAN_QUYEN'",
                Integer.class, admin.getMaND());
        assertThat(nhatKy).isEqualTo(1);

        gui(HttpMethod.GET, "/api/nguoi-dung/" + user.getMaND() + "/lich-su-phan-quyen", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vaiTroMoi").value("Kỹ thuật viên"));
    }

    @Test
    void promotedUser_getsAdminRightsWithExistingToken() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        NguoiDung user = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String tokenUser = dangNhapLayToken(user);

        gui(HttpMethod.GET, "/api/test/chi-quan-tri", tokenUser, null).andExpect(status().isForbidden());
        doiVaiTro(dangNhapLayToken(admin), user, "Quản trị").andExpect(status().isOk());
        gui(HttpMethod.GET, "/api/test/chi-quan-tri", tokenUser, null).andExpect(status().isOk());
    }

    @Test
    void adminDemotesAnotherAdmin_ok() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.QUAN_TRI);
        NguoiDung b = taoNguoiDung(VaiTro.QUAN_TRI);
        doiVaiTro(dangNhapLayToken(a), b, "Người dùng").andExpect(status().isOk());
        assertThat(vaiTroTrongDb(b)).isEqualTo("Người dùng");
    }

    // ---------- từ chối ----------

    @ParameterizedTest
    @EnumSource(value = VaiTro.class, names = "QUAN_TRI", mode = EnumSource.Mode.EXCLUDE)
    void nonAdmin_returns403_andNothingChanges(VaiTro vaiTro) throws Exception {
        NguoiDung actor = taoNguoiDung(vaiTro);
        NguoiDung target = taoNguoiDung(VaiTro.NGUOI_DUNG);

        doiVaiTro(dangNhapLayToken(actor), target, "Quản trị").andExpect(status().isForbidden());

        assertThat(vaiTroTrongDb(target)).isEqualTo("Người dùng");
        Integer ls = jdbc.queryForObject("SELECT count(*) FROM LichSuPhanQuyen WHERE MaND = ?", Integer.class,
                target.getMaND());
        assertThat(ls).isZero();
    }

    @Test
    void withoutToken_returns401() throws Exception {
        NguoiDung target = taoNguoiDung(VaiTro.NGUOI_DUNG);
        doiVaiTro(null, target, "Quản trị").andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdmin_cannotReadHistory() throws Exception {
        NguoiDung user = taoNguoiDung(VaiTro.NGUOI_DUNG);
        gui(HttpMethod.GET, "/api/nguoi-dung/" + user.getMaND() + "/lich-su-phan-quyen",
                dangNhapLayToken(user), null).andExpect(status().isForbidden());
    }

    @Test
    void selfChange_returns403_evenToSameOrLowerRole() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        String token = dangNhapLayToken(admin);

        doiVaiTro(token, admin, "Người dùng")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ma").value("KHONG_TU_NANG_QUYEN"));
        doiVaiTro(token, admin, "Quản trị").andExpect(status().isForbidden());
        assertThat(vaiTroTrongDb(admin)).isEqualTo("Quản trị");
    }

    @Test
    void sameRole_returnsNoChangeAndNoHistory() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        NguoiDung user = taoNguoiDung(VaiTro.NGUOI_DUNG);

        doiVaiTro(dangNhapLayToken(admin), user, "Người dùng")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thayDoi").value(false));
        Integer ls = jdbc.queryForObject("SELECT count(*) FROM LichSuPhanQuyen WHERE MaND = ?", Integer.class,
                user.getMaND());
        assertThat(ls).isZero();
    }

    @Test
    void invalidOrBlankRole_returns400() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        NguoiDung user = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = dangNhapLayToken(admin);

        doiVaiTro(token, user, "Siêu quản trị")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"));
        doiVaiTro(token, user, "").andExpect(status().isBadRequest());
        assertThat(vaiTroTrongDb(user)).isEqualTo("Người dùng");
    }

    @Test
    void unknownAccount_returns404() throws Exception {
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        gui(HttpMethod.PUT, "/api/nguoi-dung/ND-khongco/vai-tro", dangNhapLayToken(admin),
                Map.of("vaiTroMoi", "Quản trị")).andExpect(status().isNotFound());
        gui(HttpMethod.GET, "/api/nguoi-dung/ND-khongco/lich-su-phan-quyen", dangNhapLayToken(admin), null)
                .andExpect(status().isNotFound());
    }

    // ---------- đồng thời ----------

    @Test
    void concurrentDemoteEachOther_neverLeavesZeroAdmins() throws Exception {
        for (int lan = 0; lan < 5; lan++) {
            NguoiDung a = taoNguoiDung(VaiTro.QUAN_TRI);
            NguoiDung b = taoNguoiDung(VaiTro.QUAN_TRI);
            String tokenA = dangNhapLayToken(a);
            String tokenB = dangNhapLayToken(b);

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> {
                start.await();
                return doiVaiTro(tokenA, b, "Người dùng").andReturn().getResponse().getStatus();
            }));
            futures.add(pool.submit(() -> {
                start.await();
                return doiVaiTro(tokenB, a, "Người dùng").andReturn().getResponse().getStatus();
            }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            pool.shutdown();

            // Một người hạ được người kia; người còn lại đã mất quyền khi tới lượt nên bị từ chối (403)
            assertThat(statuses).as("lan %d", lan).containsExactlyInAnyOrder(200, 403);
            Integer soQuanTriCuaCap = jdbc.queryForObject(
                    "SELECT count(*) FROM NguoiDung WHERE VaiTro = 'Quản trị' AND MaND IN (?, ?)",
                    Integer.class, a.getMaND(), b.getMaND());
            assertThat(soQuanTriCuaCap).as("lan %d", lan).isEqualTo(1);
        }
    }

    // ---------- tiện ích ----------

    private org.springframework.test.web.servlet.ResultActions doiVaiTro(String token, NguoiDung dich,
                                                                          String vaiTroMoi) throws Exception {
        return gui(HttpMethod.PUT, "/api/nguoi-dung/" + dich.getMaND() + "/vai-tro", token,
                Map.of("vaiTroMoi", vaiTroMoi));
    }

    private String vaiTroTrongDb(NguoiDung nd) {
        return jdbc.queryForObject("SELECT VaiTro FROM NguoiDung WHERE MaND = ?", String.class, nd.getMaND());
    }
}
