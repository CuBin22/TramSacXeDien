package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.CongSacIT;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** UC07 Hủy / đổi lịch đặt phiên (BM6, QĐ6). */
class HuyDoiLichIT extends CongSacIT {

    private NguoiDung nd;
    private Xe xe;
    private String token;

    @BeforeEach
    void dungNguoiDung() throws Exception {
        nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        xe = themXe(nd, bienSoMoi(), "AC");
        token = dangNhapLayToken(nd);
    }

    // ================= hủy =================

    @Test
    void huy_ahead_marksCancelledWritesHistoryAndAudit() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));

        huy(token, ma, "Bận việc đột xuất")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("Hủy"))
                .andExpect(jsonPath("$.lyDoHuy").value("Bận việc đột xuất"));

        assertThat(trangThaiDatPhien(ma)).isEqualTo("Hủy");
        assertThat(trangThaiPhien(ma)).isEqualTo("Hủy");
        List<Map<String, Object>> ls = jdbc.queryForList(
                "SELECT l.* FROM LichSuTrangThaiPhien l JOIN PhienSac p ON p.MaPhien = l.MaPhien "
                        + "WHERE p.MaDatPhien = ? ORDER BY l.ThoiDiem, l.MaLSTT", ma);
        assertThat(ls).hasSize(2);
        Map<String, Object> huy = ls.stream().filter(m -> "Hủy".equals(m.get("trangthaimoi"))).findFirst().get();
        assertThat(huy.get("trangthaicu")).isEqualTo("Đã đặt");
        assertThat(huy.get("nguonthaotac")).isEqualTo("Người dùng");
        assertThat((String) huy.get("lydo")).isEqualTo("Hủy: Bận việc đột xuất");
        assertThat(soNhatKy(nd, "HUY_DAT_PHIEN")).isEqualTo(1);
    }

    @Test
    void huy_within15MinutesOfStart_isRecordedAsLateCancellation() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime sat = bayGio().plusMinutes(5);
        String ma = datPhienThanhCong(token, xe, sat, sat.plusHours(1));

        huy(token, ma, "Đổi ý").andExpect(status().isOk());

        String lyDo = jdbc.queryForObject(
                "SELECT l.LyDo FROM LichSuTrangThaiPhien l JOIN PhienSac p ON p.MaPhien = l.MaPhien "
                        + "WHERE p.MaDatPhien = ? AND l.TrangThaiMoi = 'Hủy'", String.class, ma);
        assertThat(lyDo).startsWith("Hủy sát giờ");
    }

    @Test
    void huy_releasesThePortHeldForTheBooking() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime sat = bayGio().plusMinutes(5);   // cửa sổ giờ đã mở: cổng được giữ ngay khi đặt
        String ma = datPhienThanhCong(token, xe, sat, sat.plusHours(1));
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");

        huy(token, ma, "Đổi ý").andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void huy_farBooking_leavesPortAsIs() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime xa = bayGio().plusHours(5);
        String ma = datPhienThanhCong(token, xe, xa, xa.plusHours(1));
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        huy(token, ma, "Đổi ý").andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void huy_bookingOfAnotherUser_returns404_andLeavesItUntouched() throws Exception {
        taoCong("AC-1", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(dangNhapLayToken(khac), xeKhac, t, t.plusHours(1));

        huy(token, ma, "Phá").andExpect(status().isNotFound()).andExpect(jsonPath("$.ma").value("KHONG_TIM_THAY_DAT_CHO"));
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
    }

    @Test
    void huy_twice_secondIsRejected() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        huy(token, ma, "Lần một").andExpect(status().isOk());
        huy(token, ma, "Lần hai")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("DAT_CHO_KHONG_CON_HIEU_LUC"));
    }

    @Test
    void huy_afterChargingHasStarted_isRejected() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        jdbc.update("UPDATE PhienSac SET TrangThai = 'Chờ' WHERE MaDatPhien = ?", ma);

        huy(token, ma, "Muộn rồi")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("DA_BAT_DAU_SAC"));
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
        assertThat(trangThaiPhien(ma)).isEqualTo("Chờ");

        jdbc.update("UPDATE PhienSac SET TrangThai = 'Đang sạc' WHERE MaDatPhien = ?", ma);
        jdbc.update("UPDATE DatPhien SET TrangThai = 'Đang sạc' WHERE MaDatPhien = ?", ma);
        huy(token, ma, "Muộn rồi").andExpect(status().isConflict()).andExpect(jsonPath("$.ma").value("DA_BAT_DAU_SAC"));
    }

    @Test
    void huy_missingReason_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        gui(HttpMethod.POST, "/api/dat-phien/" + ma + "/huy", token, Map.of("lyDo", " "))
                .andExpect(status().isBadRequest());
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
    }

    @Test
    void huy_frees_oneOfTheTwoActiveSlots() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(2);
        String ma1 = datPhienThanhCong(token, xe, t, t.plusHours(1));
        datPhienThanhCong(token, xe, t.plusHours(3), t.plusHours(4));
        datPhien(token, xe.getBienSo(), "AC", t.plusHours(6), t.plusHours(7)).andExpect(status().isConflict());

        huy(token, ma1, "Đổi ý").andExpect(status().isOk());
        datPhien(token, xe.getBienSo(), "AC", t.plusHours(6), t.plusHours(7)).andExpect(status().isCreated());
    }

    // ================= đổi lịch =================

    @Test
    void doiLich_ok_updatesBookingSessionAndCountsTheChange() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        LocalDateTime moi = bayGio().plusHours(6);

        doiLich(token, ma, moi, moi.plusHours(2), "Đổi giờ làm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soLanDoiLich").value(1))
                .andExpect(jsonPath("$.gioBatDau").value(iso(moi)))
                .andExpect(jsonPath("$.gioKetThuc").value(iso(moi.plusHours(2))))
                .andExpect(jsonPath("$.trangThai").value("Đã đặt"));

        assertThat(gioBatDauTrongDb(ma)).isEqualTo(moi);
        assertThat(trangThaiPhien(ma)).isEqualTo("Đã đặt");
        String maCongPhien = jdbc.queryForObject("SELECT MaCong FROM PhienSac WHERE MaDatPhien = ?", String.class, ma);
        assertThat(maCongPhien).isEqualTo(maCongCuaDatPhien(ma));
        assertThat(soNhatKy(nd, "DOI_LICH")).isEqualTo(1);
    }

    @Test
    void doiLich_shiftOverlappingTheOldWindow_isAllowed_andKeepsThePort() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));

        doiLich(token, ma, t.plusMinutes(30), t.plusMinutes(90), "Lệch nửa tiếng")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maCong").value("AC-1"));
    }

    @Test
    void doiLich_thirdTime_returns409_andKeepsSecondSchedule() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        doiLich(token, ma, t.plusHours(1), t.plusHours(2), "Lần 1").andExpect(status().isOk());
        doiLich(token, ma, t.plusHours(2), t.plusHours(3), "Lần 2").andExpect(status().isOk());

        doiLich(token, ma, t.plusHours(4), t.plusHours(5), "Lần 3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("VUOT_SO_LAN_DOI_LICH"));
        assertThat(gioBatDauTrongDb(ma)).isEqualTo(t.plusHours(2));
        Integer so = jdbc.queryForObject("SELECT SoLanDoiLich FROM DatPhien WHERE MaDatPhien = ?", Integer.class, ma);
        assertThat(so).isEqualTo(2);
    }

    @Test
    void doiLich_ontoAnotherOwnBooking_returns409_andKeepsOldSchedule() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime t = bayGio().plusHours(2);
        datPhienThanhCong(token, xe, t, t.plusHours(1));
        String maB = datPhienThanhCong(token, xe, t.plusHours(4), t.plusHours(5));

        doiLich(token, maB, t.plusMinutes(30), t.plusMinutes(90), "Trùng nhau")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_LICH"));
        assertThat(gioBatDauTrongDb(maB)).isEqualTo(t.plusHours(4));
        Integer so = jdbc.queryForObject("SELECT SoLanDoiLich FROM DatPhien WHERE MaDatPhien = ?", Integer.class, maB);
        assertThat(so).isZero();
    }

    @Test
    void doiLich_noFreePortInNewWindow_returns409_andKeepsOldSchedule() throws Exception {
        taoCong("AC-1", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        LocalDateTime kia = bayGio().plusHours(8);
        taoDatChoTrucTiep(khac, xeKhac, "AC-1", kia, kia.plusHours(1), "Đã đặt", "Đã đặt");

        doiLich(token, ma, kia.plusMinutes(30), kia.plusMinutes(90), "Sang giờ kín chỗ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("HET_CONG"));
        assertThat(gioBatDauTrongDb(ma)).isEqualTo(t);
        assertThat(maCongCuaDatPhien(ma)).isEqualTo("AC-1");
    }

    @Test
    void doiLich_movesToAnotherPortWhenTheOldOneIsTakenInTheNewWindow() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        assertThat(maCongCuaDatPhien(ma)).isEqualTo("AC-1");
        LocalDateTime kia = bayGio().plusHours(8);
        taoDatChoTrucTiep(khac, xeKhac, "AC-1", kia, kia.plusHours(1), "Đã đặt", "Đã đặt");

        doiLich(token, ma, kia.plusMinutes(30), kia.plusMinutes(90), "Sang giờ khác")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maCong").value("AC-2"));
        String maCongPhien = jdbc.queryForObject("SELECT MaCong FROM PhienSac WHERE MaDatPhien = ?", String.class, ma);
        assertThat(maCongPhien).isEqualTo("AC-2");
    }

    @Test
    void doiLich_invalidNewWindow_returns400_andKeepsOldSchedule() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));

        doiLich(token, ma, t.plusHours(1), t.plusHours(5), "Quá dài")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.gioKetThuc").exists());
        doiLich(token, ma, bayGio().minusMinutes(10), bayGio().plusHours(1), "Giờ đã qua")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.gioBatDau").exists());
        gui(HttpMethod.PUT, "/api/dat-phien/" + ma + "/doi-lich", token, Map.of("lyDo", "thiếu giờ"))
                .andExpect(status().isBadRequest());
        assertThat(gioBatDauTrongDb(ma)).isEqualTo(t);
    }

    @Test
    void doiLich_afterChargingHasStarted_orOfAnotherUser_isRejected() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime t = bayGio().plusHours(3);
        String ma = datPhienThanhCong(token, xe, t, t.plusHours(1));
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        doiLich(dangNhapLayToken(khac), ma, t.plusHours(1), t.plusHours(2), "Phá").andExpect(status().isNotFound());

        jdbc.update("UPDATE PhienSac SET TrangThai = 'Chờ' WHERE MaDatPhien = ?", ma);
        doiLich(token, ma, t.plusHours(1), t.plusHours(2), "Muộn rồi")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("DA_BAT_DAU_SAC"));
    }

    @Test
    void doiLich_awayFromOpenWindow_releasesTheHeldPort() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime sat = bayGio().plusMinutes(5);
        String ma = datPhienThanhCong(token, xe, sat, sat.plusHours(1));
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");

        LocalDateTime xa = bayGio().plusHours(5);
        doiLich(token, ma, xa, xa.plusHours(1), "Dời sang chiều").andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    // ================= đồng thời: hủy và quá hạn =================

    @Test
    void cancelRacingWithExpiry_alwaysEndsInOneConsistentState() throws Exception {
        taoCong("AC-1", "AC");
        for (int lan = 0; lan < 5; lan++) {
            LocalDateTime qua = bayGio().minusMinutes(20);
            String ma = taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đã đặt", "Đã đặt");

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<Integer> huyF = pool.submit(() -> {
                start.await();
                return huy(token, ma, "Đổi ý").andReturn().getResponse().getStatus();
            });
            Future<Integer> quetF = pool.submit(() -> {
                start.await();
                return jobs.quetQuaHan();
            });
            start.countDown();
            int httpStatus = huyF.get(60, TimeUnit.SECONDS);
            int soQuet = quetF.get(60, TimeUnit.SECONDS);
            pool.shutdown();

            String trangThaiDat = trangThaiDatPhien(ma);
            assertThat(trangThaiPhien(ma)).as("lan %d", lan).isEqualTo("Hủy");
            List<Map<String, Object>> huyRows = jdbc.queryForList(
                    "SELECT l.LyDo FROM LichSuTrangThaiPhien l JOIN PhienSac p ON p.MaPhien = l.MaPhien "
                            + "WHERE p.MaDatPhien = ? AND l.TrangThaiMoi = 'Hủy'", ma);
            assertThat(huyRows).as("đúng một lần chuyển sang Hủy, lan %d", lan).hasSize(1);
            String lyDo = (String) huyRows.get(0).get("lydo");
            if ("Hủy".equals(trangThaiDat)) {
                assertThat(httpStatus).isEqualTo(200);
                assertThat(soQuet).isZero();
                assertThat(lyDo).startsWith("Hủy sát giờ");
            } else {
                assertThat(trangThaiDat).isEqualTo("Quá hạn không đến");
                assertThat(httpStatus).isEqualTo(409);
                assertThat(soQuet).isEqualTo(1);
                assertThat(lyDo).startsWith("Quá hạn không đến");
            }
        }
    }
}
