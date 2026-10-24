package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.CongSacIT;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/** UC05 Đặt phiên sạc, UC06 Kiểm tra xung đột, UC08 Phân bổ cổng. */
class DatPhienIT extends CongSacIT {

    private NguoiDung nd;
    private Xe xe;
    private String token;

    @BeforeEach
    void dungNguoiDung() throws Exception {
        nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        xe = themXe(nd, bienSoMoi(), "AC");
        token = dangNhapLayToken(nd);
    }

    // ================= đường chính =================

    @Test
    void datPhien_valid_returns201_createsBookingSessionAndHistory() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        taoCong("DC-1", "DC");
        LocalDateTime bd = bayGio().plusHours(2);
        LocalDateTime kt = bd.plusHours(1);

        MvcResult r = datPhien(token, xe.getBienSo(), "AC", bd, kt)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maDatPhien").value(startsWith("DP-")))
                .andExpect(jsonPath("$.maPhien").value(startsWith("PS-")))
                .andExpect(jsonPath("$.maCong").value(startsWith("AC-")))
                .andExpect(jsonPath("$.trangThai").value("Đã đặt"))
                .andExpect(jsonPath("$.loaiCong").value("AC"))
                .andExpect(jsonPath("$.bienSo").value(xe.getBienSo()))
                .andExpect(jsonPath("$.soLanDoiLich").value(0))
                .andExpect(jsonPath("$.gioBatDau").value(iso(bd)))
                .andExpect(jsonPath("$.gioKetThuc").value(iso(kt)))
                .andReturn();
        JsonNode j = json(r);
        String maDatPhien = j.get("maDatPhien").asText();
        String maPhien = j.get("maPhien").asText();
        String maCong = j.get("maCong").asText();

        Map<String, Object> dp = jdbc.queryForMap("SELECT * FROM DatPhien WHERE MaDatPhien = ?", maDatPhien);
        assertThat(dp.get("mand")).isEqualTo(nd.getMaND());
        assertThat(dp.get("macong")).isEqualTo(maCong);
        assertThat(dp.get("trangthai")).isEqualTo("Đã đặt");
        assertThat(dp.get("solandoilich")).isEqualTo(0);
        assertThat(gioBatDauTrongDb(maDatPhien)).isEqualTo(bd);

        Map<String, Object> ps = jdbc.queryForMap("SELECT * FROM PhienSac WHERE MaPhien = ?", maPhien);
        assertThat(ps.get("madatphien")).isEqualTo(maDatPhien);
        assertThat(ps.get("macong")).isEqualTo(maCong);
        assertThat(ps.get("trangthai")).isEqualTo("Đã đặt");
        assertThat(ps.get("khoaphien")).isEqualTo(false);

        List<Map<String, Object>> ls = jdbc.queryForList("SELECT * FROM LichSuTrangThaiPhien WHERE MaPhien = ?",
                maPhien);
        assertThat(ls).hasSize(1);
        assertThat(ls.get(0).get("trangthaicu")).isNull();
        assertThat(ls.get(0).get("trangthaimoi")).isEqualTo("Đã đặt");
        assertThat(ls.get(0).get("nguonthaotac")).isEqualTo("Người dùng");
        assertThat(soNhatKy(nd, "DAT_PHIEN")).isEqualTo(1);
        assertThat(trangThaiCong(maCong)).as("giờ bắt đầu còn xa nên chưa giữ cổng").isEqualTo("Rảnh");
    }

    @Test
    void datPhien_withoutToken_returns401() throws Exception {
        datPhien(null, xe.getBienSo(), "AC", bayGio().plusHours(2), bayGio().plusHours(3))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void datPhien_plateIsNormalized() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, " " + xe.getBienSo().toLowerCase() + " ", "AC", bd, bd.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bienSo").value(xe.getBienSo()));
    }

    // ================= quy định QĐ5 =================

    @Test
    void datPhien_durationTooShort_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusMinutes(29))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truong.gioKetThuc").exists());
    }

    @Test
    void datPhien_durationTooLong_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusMinutes(181))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.gioKetThuc").exists());
    }

    @Test
    void datPhien_endNotAfterStart_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd).andExpect(status().isBadRequest());
    }

    @Test
    void datPhien_pastStart_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().minusMinutes(5);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.gioBatDau").exists());
    }

    @Test
    void datPhien_moreThan24HoursAhead_returns400() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(24).plusMinutes(10);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.gioBatDau").exists());
    }

    @Test
    void datPhien_connectorTypeNotMatchingVehicle_returns400() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("DC-1", "DC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "DC", bd, bd.plusHours(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.loaiCong").exists());
    }

    @Test
    void datPhien_missingFields_returns400() throws Exception {
        gui(HttpMethod.POST, "/api/dat-phien", token, Map.of("bienSo", xe.getBienSo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"));
    }

    @Test
    void datPhien_boundaryValues_areAccepted() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime gan = bayGio().plusHours(23).plusMinutes(50);
        datPhien(token, xe.getBienSo(), "AC", gan, gan.plusMinutes(30)).andExpect(status().isCreated());
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(3)).andExpect(status().isCreated());
    }

    // ================= xe =================

    @Test
    void datPhien_vehicleOfAnotherUser_returns404() throws Exception {
        taoCong("AC-1", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xeKhac.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ma").value("KHONG_TIM_THAY_XE"));
        datPhien(token, "KHONG-CO-XE-NAY", "AC", bd, bd.plusHours(1)).andExpect(status().isNotFound());
    }

    // ================= giới hạn đặt chỗ và trùng lịch =================

    @Test
    void datPhien_thirdActiveBooking_returns409() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime t = bayGio().plusHours(2);
        datPhienThanhCong(token, xe, t, t.plusHours(1));
        datPhienThanhCong(token, xe, t.plusHours(3), t.plusHours(4));

        datPhien(token, xe.getBienSo(), "AC", t.plusHours(6), t.plusHours(7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("VUOT_SO_DAT_CHO"));
    }

    @Test
    void datPhien_overlappingOwnBooking_returns409_butBackToBackIsFine() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime t = bayGio().plusHours(2);
        datPhienThanhCong(token, xe, t, t.plusHours(1));

        datPhien(token, xe.getBienSo(), "AC", t.plusMinutes(30), t.plusMinutes(90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_LICH"));
        datPhien(token, xe.getBienSo(), "AC", t.plusHours(1), t.plusHours(2)).andExpect(status().isCreated());
    }

    // ================= hết cổng =================

    @Test
    void datPhien_noPortOfRequestedType_returns409HetCong() throws Exception {
        taoCong("AC-1", "AC");
        Xe xeDc = themXe(nd, bienSoMoi(), "DC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xeDc.getBienSo(), "DC", bd, bd.plusHours(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("HET_CONG"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lỗi", "Bảo trì", "Mất kết nối"})
    void datPhien_portsInUnavailableState_areSkipped(String trangThai) throws Exception {
        taoCong("AC-1", "AC", trangThai, null, 0);
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("HET_CONG"));

        taoCong("AC-2", "AC");
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maCong").value("AC-2"));
    }

    @Test
    void datPhien_portBookedByOthersInThatWindow_returns409HetCong() throws Exception {
        taoCong("AC-1", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime t = bayGio().plusHours(2);
        taoDatChoTrucTiep(khac, xeKhac, "AC-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");

        datPhien(token, xe.getBienSo(), "AC", t.plusMinutes(30), t.plusMinutes(90))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("HET_CONG"));
        datPhien(token, xe.getBienSo(), "AC", t.plusHours(1), t.plusHours(2)).andExpect(status().isCreated());
    }

    // ================= ưu tiên phân bổ (UC08) =================

    @Test
    void phanBo_tie_choosesSmallestPortCode() throws Exception {
        taoCong("AC-2", "AC");
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maCong").value("AC-1"));
    }

    @Test
    void phanBo_prefersPortWithFewerBookingsThatDay() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        // một đặt chỗ khác trên AC-1 cùng ngày, không chồng giờ (luôn cùng ngày UTC với bd)
        LocalDateTime khacGio = bd.plusHours(5).toLocalDate().equals(bd.toLocalDate()) ? bd.plusHours(5)
                : bd.minusHours(5);
        taoDatChoTrucTiep(khac, xeKhac, "AC-1", khacGio, khacGio.plusHours(1), "Đã đặt", "Đã đặt");

        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maCong").value("AC-2"));
    }

    @Test
    void phanBo_prefersPortWithMoreSpareConnectionCapacity() throws Exception {
        taoCauHinh("CH-X", 100);
        taoCauHinh("CH-Y", 100);
        taoCong("AC-A", "AC", "Rảnh", "CH-Y", 90);   // nhóm Y đang cấp 90 kW: dư 10 kW
        taoCong("AC-B", "AC", "Rảnh", "CH-X", 0);    // nhóm X dư 100 kW
        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maCong").value("AC-B"));
    }

    // ================= đồng thời =================

    @Test
    void datPhien_fiveUsersRaceForTheLastPort_exactlyOneSucceeds() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        List<Integer> ketQua = chayDongThoi(5, 1, bd, bd.plusHours(1));
        assertThat(ketQua.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(ketQua.stream().filter(s -> s == 409).count()).isEqualTo(4);
        Integer soDat = jdbc.queryForObject("SELECT count(*) FROM DatPhien WHERE MaCong = 'AC-1'", Integer.class);
        assertThat(soDat).isEqualTo(1);
    }

    @Test
    void datPhien_fiveUsersTwoPorts_exactlyTwoSucceedOnDifferentPorts() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        List<Integer> ketQua = chayDongThoi(5, 2, bd, bd.plusHours(1));
        assertThat(ketQua.stream().filter(s -> s == 201).count()).isEqualTo(2);
        assertThat(ketQua.stream().filter(s -> s == 409).count()).isEqualTo(3);
        Integer soCong = jdbc.queryForObject("SELECT count(DISTINCT MaCong) FROM DatPhien", Integer.class);
        assertThat(soCong).isEqualTo(2);
    }

    @Test
    void datPhien_sameUserTwoIdenticalRequestsAtOnce_onlyOneSucceeds() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        LocalDateTime bd = bayGio().plusHours(2);
        List<Integer> ketQua = chayCungMotNguoi(List.of(bd, bd));
        assertThat(ketQua).containsExactlyInAnyOrder(201, 409);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM DatPhien WHERE MaND = ?", Integer.class, nd.getMaND());
        assertThat(n).isEqualTo(1);
    }

    @Test
    void datPhien_sameUserThreeNonOverlappingAtOnce_onlyTwoSucceed() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        taoCong("AC-3", "AC");
        LocalDateTime t = bayGio().plusHours(2);
        List<Integer> ketQua = chayCungMotNguoi(List.of(t, t.plusHours(3), t.plusHours(6)));
        assertThat(ketQua.stream().filter(s -> s == 201).count()).isEqualTo(2);
        assertThat(ketQua.stream().filter(s -> s == 409).count()).isEqualTo(1);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM DatPhien WHERE MaND = ?", Integer.class, nd.getMaND());
        assertThat(n).isEqualTo(2);
    }

    // ================= quy tắc 13 và quy tắc 11 =================

    @Test
    void datPhien_whileHavingSessionAwaitingPayment_returns409() throws Exception {
        taoCong("AC-1", "AC");
        LocalDateTime qua = bayGio().minusHours(3);
        taoDatChoTrucTiep(nd, xe, "AC-1", qua, qua.plusHours(1), "Đang sạc", "Chờ thanh toán");

        LocalDateTime bd = bayGio().plusHours(2);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("CON_PHIEN_CHO_THANH_TOAN"));
    }

    @Test
    void datPhien_afterThreeLateCancellations_isRestrictedFor24Hours() throws Exception {
        taoCong("AC-1", "AC");
        for (int i = 0; i < 3; i++) {
            LocalDateTime sat = bayGio().plusMinutes(5);
            String ma = datPhienThanhCong(token, xe, sat, sat.plusHours(1));
            huy(token, ma, "Đổi ý").andExpect(status().isOk());
        }

        LocalDateTime bd = bayGio().plusHours(3);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ma").value("BI_HAN_CHE_DAT_CHO"))
                .andExpect(jsonPath("$.truong.conLaiGiay").exists());

        clock.advance(java.time.Duration.ofHours(24).plusMinutes(1));
        String tokenMoi = dangNhapLayToken(nd);   // token cũ đã hết hạn sau khi tua đồng hồ
        LocalDateTime sau = bayGio().plusHours(3);
        datPhien(tokenMoi, xe.getBienSo(), "AC", sau, sau.plusHours(1)).andExpect(status().isCreated());
    }

    @Test
    void datPhien_cancellationsMoreThan15MinutesAhead_doNotCountTowardRestriction() throws Exception {
        taoCong("AC-1", "AC");
        for (int i = 0; i < 4; i++) {
            LocalDateTime xa = bayGio().plusHours(3);
            String ma = datPhienThanhCong(token, xe, xa, xa.plusHours(1));
            huy(token, ma, "Đổi ý").andExpect(status().isOk());
        }
        LocalDateTime bd = bayGio().plusHours(3);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1)).andExpect(status().isCreated());
    }

    @Test
    void datPhien_noShowAndLateCancellationsTogether_countTowardRestriction() throws Exception {
        taoCong("AC-1", "AC");
        // một lần không đến: đặt chỗ đã quá giờ 20 phút, chạy tác vụ quá hạn
        LocalDateTime quaGio = bayGio().minusMinutes(20);
        taoDatChoTrucTiep(nd, xe, "AC-1", quaGio, quaGio.plusHours(1), "Đã đặt", "Đã đặt");
        assertThat(jobs.quetQuaHan()).isEqualTo(1);
        // hai lần hủy sát giờ
        for (int i = 0; i < 2; i++) {
            LocalDateTime sat = bayGio().plusMinutes(5);
            huy(token, datPhienThanhCong(token, xe, sat, sat.plusHours(1)), "Đổi ý").andExpect(status().isOk());
        }
        LocalDateTime bd = bayGio().plusHours(3);
        datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ma").value("BI_HAN_CHE_DAT_CHO"));
    }

    // ================= xem =================

    @Test
    void danhSach_returnsOnlyOwnBookings_andDetailOfOthersIs404() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        NguoiDung khac = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xeKhac = themXe(khac, bienSoMoi(), "AC");
        String tokenKhac = dangNhapLayToken(khac);
        LocalDateTime t = bayGio().plusHours(2);
        String maCuaToi = datPhienThanhCong(token, xe, t, t.plusHours(1));
        String maCuaKhac = datPhienThanhCong(tokenKhac, xeKhac, t, t.plusHours(1));

        gui(HttpMethod.GET, "/api/dat-phien", token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].maDatPhien").value(maCuaToi));
        gui(HttpMethod.GET, "/api/dat-phien/" + maCuaToi, token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maDatPhien").value(maCuaToi));
        gui(HttpMethod.GET, "/api/dat-phien/" + maCuaKhac, token, null).andExpect(status().isNotFound());
        gui(HttpMethod.GET, "/api/dat-phien", null, null).andExpect(status().isUnauthorized());
    }

    // ================= tiện ích đồng thời =================

    /** soNguoi người dùng khác nhau cùng đặt một khung giờ AC cùng lúc; trả về mã trạng thái của từng yêu cầu. */
    private List<Integer> chayDongThoi(int soNguoi, int soCong, LocalDateTime bd, LocalDateTime kt) throws Exception {
        List<String> tokens = new ArrayList<>();
        List<String> bienSos = new ArrayList<>();
        for (int i = 0; i < soNguoi; i++) {
            NguoiDung u = taoNguoiDung(VaiTro.NGUOI_DUNG);
            Xe x = themXe(u, bienSoMoi(), "AC");
            tokens.add(dangNhapLayToken(u));
            bienSos.add(x.getBienSo());
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(soNguoi);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < soNguoi; i++) {
            String tk = tokens.get(i);
            String bs = bienSos.get(i);
            Callable<Integer> viec = () -> {
                start.await();
                return datPhien(tk, bs, "AC", bd, kt).andReturn().getResponse().getStatus();
            };
            futures.add(pool.submit(viec));
        }
        start.countDown();
        List<Integer> ketQua = new ArrayList<>();
        for (Future<Integer> f : futures) {
            ketQua.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return ketQua;
    }

    /** Một người dùng gửi đồng thời nhiều yêu cầu đặt chỗ (mỗi khung giờ dài 1 giờ). */
    private List<Integer> chayCungMotNguoi(List<LocalDateTime> cacGioBatDau) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(cacGioBatDau.size());
        List<Future<Integer>> futures = new ArrayList<>();
        for (LocalDateTime bd : cacGioBatDau) {
            Callable<Integer> viec = () -> {
                start.await();
                return datPhien(token, xe.getBienSo(), "AC", bd, bd.plusHours(1)).andReturn().getResponse()
                        .getStatus();
            };
            futures.add(pool.submit(viec));
        }
        start.countDown();
        List<Integer> ketQua = new ArrayList<>();
        for (Future<Integer> f : futures) {
            ketQua.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return ketQua;
    }
}
