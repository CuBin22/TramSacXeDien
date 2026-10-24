package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.CongSacIT;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;

/** UC09 Giải phóng cổng khi đặt phiên quá hạn và quy tắc 2 (giữ cổng theo cửa sổ giờ). */
class QuaHanVaGiuChoIT extends CongSacIT {

    @Autowired
    ApplicationContext context;

    private NguoiDung nd;
    private Xe xe;
    private String token;

    @BeforeEach
    void dungNguoiDung() throws Exception {
        nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        xe = themXe(nd, bienSoMoi(), "AC");
        token = dangNhapLayToken(nd);
    }

    // ================= UC09: quá hạn =================

    @Test
    void quetQuaHan_expiresBookingMoreThan15MinutesLate() {
        taoCong("AC-1", "AC", "Đã đặt", null, 0);
        LocalDateTime qua = bayGio().minusMinutes(16);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isEqualTo(1);

        assertThat(trangThaiDatPhien(ma)).isEqualTo("Quá hạn không đến");
        assertThat(trangThaiPhien(ma)).isEqualTo("Hủy");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        List<Map<String, Object>> ls = jdbc.queryForList(
                "SELECT l.* FROM LichSuTrangThaiPhien l JOIN PhienSac p ON p.MaPhien = l.MaPhien "
                        + "WHERE p.MaDatPhien = ?", ma);
        assertThat(ls).hasSize(1);
        assertThat(ls.get(0).get("trangthaicu")).isEqualTo("Đã đặt");
        assertThat(ls.get(0).get("trangthaimoi")).isEqualTo("Hủy");
        assertThat(ls.get(0).get("nguonthaotac")).isEqualTo("Hệ thống");
        assertThat(ls.get(0).get("lydo")).isEqualTo("Quá hạn không đến");
        assertThat(soNhatKy(nd, "GIAI_PHONG_QUA_HAN")).isEqualTo(1);
    }

    @Test
    void quetQuaHan_doesNotTouchBookingLessThan15MinutesLate() {
        taoCong("AC-1", "AC");
        LocalDateTime qua = bayGio().minusMinutes(14);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isZero();
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
    }

    @Test
    void quetQuaHan_skipsBookingWhoseSessionAlreadyLeftDaDat() {
        taoCong("AC-1", "AC");
        LocalDateTime qua = bayGio().minusMinutes(20);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Chờ");

        assertThat(jobs.quetQuaHan()).isZero();
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
        assertThat(trangThaiPhien(ma)).isEqualTo("Chờ");
    }

    @Test
    void quetQuaHan_postponesWhilePortIsDisconnected_thenExpiresAfterRecovery() {
        taoCong("AC-1", "AC", "Mất kết nối", null, 0);
        LocalDateTime qua = bayGio().minusMinutes(20);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isZero();
        assertThat(trangThaiDatPhien(ma)).as("chưa xác nhận được trạng thái thiết bị thì hoãn").isEqualTo("Đã đặt");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Mất kết nối");

        datTrangThaiCong("AC-1", "Rảnh");
        assertThat(jobs.quetQuaHan()).isEqualTo(1);
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Quá hạn không đến");
    }

    @Test
    void quetQuaHan_neverReleasesAPortThatIsCharging() {
        taoCong("AC-1", "AC", "Đang sạc", null, 0);
        LocalDateTime qua = bayGio().minusMinutes(20);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isZero();
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đang sạc");
    }

    @Test
    void quetQuaHan_isIdempotent() {
        taoCong("AC-1", "AC");
        LocalDateTime qua = bayGio().minusMinutes(20);
        taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isEqualTo(1);
        assertThat(jobs.quetQuaHan()).isZero();
        Integer soLichSu = jdbc.queryForObject("SELECT count(*) FROM LichSuTrangThaiPhien", Integer.class);
        assertThat(soLichSu).isEqualTo(1);
    }

    @Test
    void quetQuaHan_oneBlockedBookingDoesNotStopTheOthers() throws Exception {
        taoCong("AC-1", "AC", "Mất kết nối", null, 0);
        taoCong("AC-2", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime qua = bayGio().minusMinutes(20);
        String maBiHoan = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");
        String maQuaHan = taoDatChoTrucTiep(khac, xeKhac, "AC-2", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetQuaHan()).isEqualTo(1);
        assertThat(trangThaiDatPhien(maBiHoan)).isEqualTo("Đã đặt");
        assertThat(trangThaiDatPhien(maQuaHan)).isEqualTo("Quá hạn không đến");
    }

    @Test
    void adminEndpoints_runTheJobs_andAreForbiddenForOthers() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime qua = bayGio().minusMinutes(20);
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        String tokenAdmin = dangNhapLayToken(admin);

        gui(HttpMethod.POST, "/api/dat-phien/quet-qua-han", token, null).andExpect(status().isForbidden());
        gui(HttpMethod.POST, "/api/dat-phien/quet-giu-cho", token, null).andExpect(status().isForbidden());
        gui(HttpMethod.POST, "/api/dat-phien/quet-qua-han", null, null).andExpect(status().isUnauthorized());
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");

        gui(HttpMethod.POST, "/api/dat-phien/quet-qua-han", tokenAdmin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soLuong").value(1));
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Quá hạn không đến");
        gui(HttpMethod.POST, "/api/dat-phien/quet-giu-cho", tokenAdmin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soLuong").value(0));
    }

    @Test
    void scheduler_isDisabledInTests() {
        assertThat(context.getBeanNamesForType(DatPhienScheduler.class)).isEmpty();
    }

    // ================= quy tắc 2: giữ cổng =================

    @Test
    void quetGiuCho_holdsPortOnlyOnceTheWindowHasOpened() {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime sap = bayGio().plusMinutes(10);
        LocalDateTime xa = bayGio().plusMinutes(60);
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        taoDatChoTrucTiep(nd, xe, "AC-1", sap, sap.plusHours(1), "Đã đặt", "Đã đặt");
        taoDatChoTrucTiep(khac, xeKhac, "AC-2", xa, xa.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetGiuCho()).isEqualTo(1);
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");
        assertThat(trangThaiCong("AC-2")).isEqualTo("Rảnh");
    }

    @Test
    void quetGiuCho_opensAsTimePasses() {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusMinutes(30);
        taoDatChoTrucTiep(nd, xe, "AC-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetGiuCho()).isZero();
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");

        clock.advance(Duration.ofMinutes(16));
        assertThat(jobs.quetGiuCho()).isEqualTo(1);
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");
        assertThat(jobs.quetGiuCho()).as("lần quét thứ hai không đổi gì").isZero();
    }

    @Test
    void quetGiuCho_releasesAPortNobodyIsHolding() {
        taoCong("AC-1", "AC", "Đã đặt", null, 0);
        assertThat(jobs.quetGiuCho()).isEqualTo(1);
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void quetGiuCho_leavesOtherPortStatesUntouched() throws Exception {
        String[] trangThai = {"Lỗi", "Bảo trì", "Mất kết nối", "Đang sạc"};
        LocalDateTime sap = bayGio().plusMinutes(5);
        for (int i = 0; i < trangThai.length; i++) {
            String maCong = "AC-" + (i + 1);
            taoCong(maCong, "AC", trangThai[i], null, 0);
            NguoiDung u = taoNguoiDung(VaiTro.NGUOI_DUNG);
            Xe x = themXe(u, bienSoMoi(), "AC");
            taoDatChoTrucTiep(u, x, maCong, sap, sap.plusHours(1), "Đã đặt", "Đã đặt");
        }

        assertThat(jobs.quetGiuCho()).isZero();
        for (int i = 0; i < trangThai.length; i++) {
            assertThat(trangThaiCong("AC-" + (i + 1))).isEqualTo(trangThai[i]);
        }
    }

    @Test
    void cancellingTheEarlierBooking_handsThePortToTheNextOne_withoutReleasingIt() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime a = bayGio().plusMinutes(5);
        LocalDateTime b = a.plusMinutes(30);   // nối đuôi A
        String maA = taoDatChoTrucTiep(nd, xe, "AC-1", a, b, "Đã đặt", "Đã đặt");
        String maB = taoDatChoTrucTiep(nd, xe, "AC-1", b, b.plusMinutes(30), "Đã đặt", "Đã đặt");

        assertThat(jobs.quetGiuCho()).isEqualTo(1);
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");

        clock.advance(Duration.ofMinutes(25));   // cửa sổ của B cũng đã mở, nhưng A vẫn đang giữ
        assertThat(jobs.quetGiuCho()).isZero();

        huy(token, maA, "Không đi nữa").andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).as("B đã đến cửa sổ giờ nên cổng vẫn được giữ").isEqualTo("Đã đặt");

        huy(token, maB, "Không đi nữa").andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }
}
