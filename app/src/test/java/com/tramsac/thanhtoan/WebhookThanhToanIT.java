package com.tramsac.thanhtoan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** UC24 (kịch bản 7): webhook ký HMAC, callback trùng/sai thứ tự/sai chữ ký, thanh toán lại, đơn hết hạn. */
class WebhookThanhToanIT extends ThanhToanBaseIT {

    @Autowired
    DonThanhToanRepository donRepository;
    @Autowired
    PaymentOrderExpiryTxService expiryTx;

    // ---------- thành công ----------

    @Test
    void thanhCong_phienHoanTatTrongCungGiaoDichVaGhiCallbackMoi() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");

        webhook(don, "THANH_CONG").andExpect(status().isOk()).andExpect(jsonPath("$.ketQua").value("DA_XU_LY"));

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("THANH_CONG");
        assertThat(ketQuaGiaoDich(don.get("maGiaoDich").asText())).isEqualTo("THANH_CONG");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
        assertThat(soCallback(don.get("maGiaoDich").asText(), "Mới")).isEqualTo(1);
        assertThat(soLanHoanTat(c.maPhien())).isEqualTo(1);
        // Người dùng xem lại đơn: đã thành công.
        gui(HttpMethod.GET, "/api/thanh-toan/don/" + don.get("maDon").asText(), c.token(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.trangThai").value("THANH_CONG"));
    }

    // ---------- callback trùng ----------

    @Test
    void callbackTrung_chiGhiNhanKhongXuLyLai() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        String gd = don.get("maGiaoDich").asText();

        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DA_XU_LY");
        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("TRUNG");
        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("TRUNG");

        assertThat(soCallback(gd, "Mới")).isEqualTo(1);
        assertThat(soCallback(gd, "Trùng")).isEqualTo(2);
        assertThat(soLanHoanTat(c.maPhien())).isEqualTo(1);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
    }

    @Test
    void haiCallbackDongThoi_chiMotLanXuLy() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        byte[] body = bodyWebhook(don.get("maGiaoDich").asText(), don.get("maDon").asText(), "THANH_CONG",
                don.get("soTien").decimalValue());
        String ts = Long.toString(clock.instant().getEpochSecond());
        String chuKy = ky(WEBHOOK_SECRET, ts, body);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch xuatPhat = new CountDownLatch(1);
        List<Future<String>> kq = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                kq.add(pool.submit(() -> {
                    xuatPhat.await();
                    var r = guiWebhook(body, ts, chuKy).andReturn().getResponse();
                    return r.getStatus() + ":" + r.getContentAsString(StandardCharsets.UTF_8);
                }));
            }
            xuatPhat.countDown();
            List<String> ketQua = new ArrayList<>();
            for (Future<String> f : kq) ketQua.add(f.get());
            assertThat(ketQua).allMatch(s -> s.startsWith("200:"));
            assertThat(ketQua.stream().filter(s -> s.contains("DA_XU_LY")).count()).isEqualTo(1);
            assertThat(ketQua.stream().filter(s -> s.contains("TRUNG")).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(soLanHoanTat(c.maPhien())).isEqualTo(1);
        assertThat(soCallback(don.get("maGiaoDich").asText(), "Mới")).isEqualTo(1);
    }

    // ---------- sai thứ tự ----------

    @Test
    void ketQuaCuoiDenTruoc_trangThaiXuLyDenSau_biBoQuaVaKhongLuiTrangThai() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "CHAM_PHAN_HOI");
        String gd = don.get("maGiaoDich").asText();

        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DA_XU_LY");
        assertThat(ketQuaWebhook(webhook(don, "DANG_XU_LY"))).isEqualTo("SAI_THU_TU");

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("THANH_CONG");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
        assertThat(soCallback(gd, "Sai thứ tự")).isEqualTo(1);
        assertThat(soLanHoanTat(c.maPhien())).isEqualTo(1);
    }

    @Test
    void chamPhanHoi_dangXuLyRoiThanhCong_donHienDangXuLyTrongLucCho() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "CHAM_PHAN_HOI");

        assertThat(ketQuaWebhook(webhook(don, "DANG_XU_LY"))).isEqualTo("DA_XU_LY");
        gui(HttpMethod.GET, "/api/thanh-toan/don/" + don.get("maDon").asText(), c.token(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.trangThai").value("DANG_XU_LY"));
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        // Đang xử lý thì vẫn không cho tạo đơn thứ hai.
        taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG")).andExpect(status().isConflict());

        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DA_XU_LY");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
    }

    // ---------- chữ ký và timestamp ----------

    @Test
    void saiChuKy_bi401_khongDoiTrangThaiVaGhiNhatKyTuChoi() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        byte[] body = bodyWebhook(don.get("maGiaoDich").asText(), don.get("maDon").asText(), "THANH_CONG",
                don.get("soTien").decimalValue());
        String ts = Long.toString(clock.instant().getEpochSecond());
        int truoc = dem("SELECT count(*) FROM CallbackLog WHERE KetQuaXacThuc = 'KHONG_HOP_LE'");

        guiWebhook(body, ts, ky("khoa-sai-khoa-sai-khoa-sai-khoa-sai", ts, body)).andExpect(status().isUnauthorized());

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("CHO");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(dem("SELECT count(*) FROM CallbackLog WHERE KetQuaXacThuc = 'KHONG_HOP_LE'")).isEqualTo(truoc + 1);
    }

    @Test
    void thieuHeaderHoacTimestampQuaHan_bi401() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        byte[] body = bodyWebhook(don.get("maGiaoDich").asText(), don.get("maDon").asText(), "THANH_CONG",
                don.get("soTien").decimalValue());
        String tsNow = Long.toString(clock.instant().getEpochSecond());
        guiWebhook(body, null, null).andExpect(status().isUnauthorized());
        guiWebhook(body, tsNow, null).andExpect(status().isUnauthorized());
        // Chữ ký đúng nhưng timestamp lệch quá 5 phút (chống phát lại).
        String tsCu = Long.toString(clock.instant().minus(Duration.ofMinutes(6)).getEpochSecond());
        guiWebhook(body, tsCu, ky(WEBHOOK_SECRET, tsCu, body)).andExpect(status().isUnauthorized());
        // Body bị sửa sau khi ký.
        byte[] suaBody = bodyWebhook(don.get("maGiaoDich").asText(), don.get("maDon").asText(), "THANH_CONG",
                new BigDecimal("1"));
        guiWebhook(suaBody, tsNow, ky(WEBHOOK_SECRET, tsNow, body)).andExpect(status().isUnauthorized());

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("CHO");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
    }

    // ---------- thất bại rồi thanh toán lại (kịch bản 6) ----------

    @Test
    void thatBai_phienGiuChoThanhToan_thanhToanLaiBangDonMoiThanhCong() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don1 = taoDonThanhCong(c, "KHONG_DU_SO_DU");

        assertThat(ketQuaWebhook(webhook(don1, "THAT_BAI"))).isEqualTo("DA_XU_LY");

        assertThat(trangThaiDon(don1.get("maDon").asText())).isEqualTo("THAT_BAI");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(soLanHoanTat(c.maPhien())).isZero();

        JsonNode don2 = taoDonThanhCong(c, "THANH_CONG");
        assertThat(don2.get("maDon").asText()).isNotEqualTo(don1.get("maDon").asText());
        assertThat(ketQuaWebhook(webhook(don2, "THANH_CONG"))).isEqualTo("DA_XU_LY");

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isEqualTo(2);
        // Callback muộn của giao dịch cũ không động đến phiên đã hoàn tất.
        assertThat(ketQuaWebhook(webhook(don1, "THANH_CONG"))).isEqualTo("DA_KET_THUC");
        assertThat(soLanHoanTat(c.maPhien())).isEqualTo(1);
    }

    // ---------- hết hạn ----------

    @Test
    void sukienHetHan_donHetHanPhienGiuChoThanhToan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "HET_HAN");

        assertThat(ketQuaWebhook(webhook(don, "HET_HAN"))).isEqualTo("DA_XU_LY");

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("HET_HAN");
        assertThat(ketQuaGiaoDich(don.get("maGiaoDich").asText())).isEqualTo("HET_HAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
    }

    @Test
    void donQuaHan15Phut_jobDongDonVaCallbackThanhCongMuonKhongHoanTatPhien() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        PaymentOrderExpiryJob job = new PaymentOrderExpiryJob(donRepository, expiryTx, clock);

        clock.advance(Duration.ofMinutes(10));
        job.quetDonHetHan();
        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("CHO");

        clock.advance(Duration.ofMinutes(6));
        job.quetDonHetHan();
        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("HET_HAN");
        assertThat(ketQuaGiaoDich(don.get("maGiaoDich").asText())).isEqualTo("HET_HAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");

        // Callback thành công đến sau khi đơn đã hết hạn: không hoàn tất phiên.
        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DA_KET_THUC");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(soLanHoanTat(c.maPhien())).isZero();
    }

    @Test
    void callbackThanhCongDenSauHanKhiDonChuaDuocQuet_donChuyenHetHan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        clock.advance(Duration.ofMinutes(16));

        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DON_HET_HAN");

        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("HET_HAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(soCallback(don.get("maGiaoDich").asText(), "Bỏ qua")).isEqualTo(1);
    }

    // ---------- dữ liệu không hợp lệ (vẫn trả 200 để cổng không gửi lại) ----------

    @Test
    void soTienSai_maGiaoDichLa_suKienLa_deuBoQuaVaGhiNhat() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        String gd = don.get("maGiaoDich").asText();
        String maDon = don.get("maDon").asText();

        assertThat(ketQuaWebhook(webhook(gd, maDon, "THANH_CONG", new BigDecimal("1")))).isEqualTo("DU_LIEU_SAI");
        assertThat(ketQuaWebhook(webhook("GDT-KHONG-CO", maDon, "THANH_CONG", new BigDecimal("10000"))))
                .isEqualTo("KHONG_TIM_THAY");
        assertThat(ketQuaWebhook(webhook(gd, "DTT-KHAC", "THANH_CONG", new BigDecimal("10000"))))
                .isEqualTo("DU_LIEU_SAI");
        assertThat(ketQuaWebhook(webhook(gd, maDon, "SU_KIEN_LA", new BigDecimal("10000"))))
                .isEqualTo("KHONG_HOP_LE");

        assertThat(trangThaiDon(maDon)).isEqualTo("CHO");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(soCallback(gd, "Bỏ qua")).isEqualTo(3);
        assertThat(dem("SELECT count(*) FROM CallbackLog WHERE MaGiaoDich IS NULL AND KetQuaXuLy = 'Bỏ qua'"))
                .isEqualTo(1);
    }
}
