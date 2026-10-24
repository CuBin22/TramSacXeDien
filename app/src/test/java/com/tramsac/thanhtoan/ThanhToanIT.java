package com.tramsac.thanhtoan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.nguoidung.VaiTro;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** UC22 (thanh toán phiên sạc) và UC23 (tạo đơn thanh toán giả lập): kiểm tra đầu vào, idempotency, phương thức. */
class ThanhToanIT extends ThanhToanBaseIT {

    private String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------- tạo đơn hợp lệ ----------

    @Test
    void taoDon_hopLe_soTienBangTongPhiVaGanNhanGiaLap() throws Exception {
        Canh c = phienChoThanhToan("AC-1");

        JsonNode don = json(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.trangThai").value("CHO"))
                .andExpect(jsonPath("$.cheDoGiaLap").value(true))
                .andExpect(jsonPath("$.maPhien").value(c.maPhien())).andReturn());

        assertThat(don.get("soTien").decimalValue()).isEqualByComparingTo("10000");
        assertThat(jdbc.queryForObject("SELECT SoTien FROM DonThanhToan WHERE MaDon = ?", BigDecimal.class,
                don.get("maDon").asText())).isEqualByComparingTo("10000");
        assertThat(jdbc.queryForObject("SELECT CheDoGiaLap FROM DonThanhToan WHERE MaDon = ?", Boolean.class,
                don.get("maDon").asText())).isTrue();
        LocalDateTime han = jdbc.queryForObject("SELECT HanThanhToan FROM DonThanhToan WHERE MaDon = ?",
                LocalDateTime.class, don.get("maDon").asText());
        assertThat(Duration.between(LocalDateTime.now(clock).plusMinutes(15), han).abs()).isLessThan(Duration.ofSeconds(2));
        assertThat(cong.soLanGoi("TAO_DON")).isEqualTo(1);
        // Phiên vẫn Chờ thanh toán cho tới khi cổng báo thành công.
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(ketQuaGiaoDich(don.get("maGiaoDich").asText())).isEqualTo("CHO");
    }

    @Test
    void phuongThucMaQr_traNoiDungMaQr_viDienTuKhongCo() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode qr = json(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "MA_QR", "THANH_CONG"))
                .andExpect(status().isAccepted()).andReturn());
        assertThat(qr.get("phuongThuc").asText()).isEqualTo("MA_QR");
        assertThat(qr.get("maQr").asText()).startsWith("TRAMSAC-GIALAP|" + qr.get("maDon").asText() + "|");
        // Hủy đơn đang chờ bằng cách cho hết hạn để thử phương thức khác.
        jdbc.update("UPDATE DonThanhToan SET TrangThai = 'HET_HAN' WHERE MaDon = ?", qr.get("maDon").asText());
        JsonNode vi = json(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "VI_DIEN_TU", "THANH_CONG"))
                .andExpect(status().isAccepted()).andReturn());
        assertThat(laRong(vi.path("maQr"))).isTrue();
    }

    // ---------- idempotency (UC23 bước 2) ----------

    @Test
    void guiLaiCungKhoa_traDonCuKhongTaoDonMoi() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String khoa = khoaMoi();
        Map<String, Object> req = bodyDon(c.maPhien(), "THE_THU", "THANH_CONG");
        String ma1 = json(taoDon(c.token(), khoa, req).andExpect(status().isAccepted()).andReturn())
                .get("maDon").asText();

        JsonNode lan2 = json(taoDon(c.token(), khoa, req).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "true")).andReturn());

        assertThat(lan2.get("maDon").asText()).isEqualTo(ma1);
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
        assertThat(dem("SELECT count(*) FROM GiaoDichThanhToan g JOIN DonThanhToan d ON d.MaDon = g.MaDon "
                + "WHERE d.MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    @Test
    void cungKhoaKhacDuLieu_conflict() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String khoa = khoaMoi();
        taoDon(c.token(), khoa, bodyDon(c.maPhien(), "THE_THU", "THANH_CONG")).andExpect(status().isAccepted());

        String kq = body(taoDon(c.token(), khoa, bodyDon(c.maPhien(), "VI_DIEN_TU", "THANH_CONG"))
                .andExpect(status().isConflict()));

        assertThat(kq).contains("IDEMPOTENCY_KEY_DUNG_LAI");
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    @Test
    void khoaKhongPhaiUuid_badRequest() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String kq = body(taoDon(c.token(), "khong-phai-uuid", bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isBadRequest()));
        assertThat(kq).contains("IDEMPOTENCY_KEY_KHONG_HOP_LE");
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    @Test
    void phienDaCoDonDangCho_khongChoTaoDonThuHai() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG")).andExpect(status().isAccepted());

        String kq = body(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isConflict()));

        assertThat(kq).contains("DON_THANH_TOAN_DANG_CHO");
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    // ---------- điều kiện phiên (UC23 bước 3) ----------

    @Test
    void phienChuaChoThanhToan_tuChoi() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c); // đang sạc, chưa có phí

        String kq = body(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isConflict()));

        assertThat(kq).contains("PHIEN_CHUA_CHO_THANH_TOAN");
        assertThat(dem("SELECT count(*) FROM DonThanhToan")).isZero();
    }

    @Test
    void chiChuPhienMoiDuocTaoVaXemDon_vaoTroKhacBiChan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String tokenKhac = tokenVaiTro(VaiTro.NGUOI_DUNG);

        taoDon(tokenKhac, khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isNotFound());
        taoDon(tokenVaiTro(VaiTro.QUAN_TRI), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", "THANH_CONG"))
                .andExpect(status().isForbidden());

        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        gui(HttpMethod.GET, "/api/thanh-toan/don/" + don.get("maDon").asText(), c.token(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.maDon").value(don.get("maDon").asText()));
        gui(HttpMethod.GET, "/api/thanh-toan/don/" + don.get("maDon").asText(), tokenKhac, null)
                .andExpect(status().isNotFound());
    }

    // ---------- cổng thanh toán lỗi tạm thời (UC23 5a) ----------

    @Test
    void congThanhToanLoi_donGiuTrangThaiChoVaGuiLaiCungKhoaDuoc() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String khoa = khoaMoi();
        Map<String, Object> req = bodyDon(c.maPhien(), "THE_THU", "THANH_CONG");
        cong.datLoi(true);

        String kq = body(taoDon(c.token(), khoa, req).andExpect(status().isServiceUnavailable()));
        assertThat(kq).contains("PAYMENT_SIM_KHONG_KHA_DUNG");
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ? AND TrangThai = 'CHO'", c.maPhien()))
                .isEqualTo(1);

        cong.datLoi(false);
        taoDon(c.token(), khoa, req).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "true"));
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    // ---------- kiểm tra đầu vào: kịch bản, độ trễ, số thẻ thử nghiệm ----------

    @Test
    void doTreGiayChiApDungChoKichBanChamPhanHoiVaHetHan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        Map<String, Object> req = bodyDon(c.maPhien(), "THE_THU", "THANH_CONG");
        req.put("doTreGiay", 5);

        String kq = body(taoDon(c.token(), khoaMoi(), req).andExpect(status().isBadRequest()));

        assertThat(kq).contains("DO_TRE_KHONG_AP_DUNG");
        assertThat(dem("SELECT count(*) FROM DonThanhToan")).isZero();
    }

    @Test
    void kichBanChamPhanHoi_nhanDoTreVaKetQuaCuoi_nhungKhongNhanKetQuaCuoiKhongHopLe() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        Map<String, Object> hopLe = bodyDon(c.maPhien(), "THE_THU", "CHAM_PHAN_HOI");
        hopLe.put("doTreGiay", 30);
        hopLe.put("ketQuaCuoi", "THAT_BAI");
        JsonNode don = json(taoDon(c.token(), khoaMoi(), hopLe).andExpect(status().isAccepted()).andReturn());
        assertThat(jdbc.queryForObject("SELECT DoTreGiay FROM GiaoDichThanhToan WHERE MaGiaoDich = ?", Integer.class,
                don.get("maGiaoDich").asText())).isEqualTo(30);
        assertThat(jdbc.queryForObject("SELECT KetQuaCuoi FROM GiaoDichThanhToan WHERE MaGiaoDich = ?", String.class,
                don.get("maGiaoDich").asText())).isEqualTo("THAT_BAI");

        jdbc.update("UPDATE DonThanhToan SET TrangThai = 'HET_HAN' WHERE MaDon = ?", don.get("maDon").asText());
        Map<String, Object> sai = bodyDon(c.maPhien(), "THE_THU", "CHAM_PHAN_HOI");
        sai.put("ketQuaCuoi", "HET_HAN");
        assertThat(body(taoDon(c.token(), khoaMoi(), sai).andExpect(status().isBadRequest())))
                .contains("KET_QUA_CUOI_KHONG_HOP_LE");
        Map<String, Object> quaTre = bodyDon(c.maPhien(), "THE_THU", "CHAM_PHAN_HOI");
        quaTre.put("doTreGiay", 61);
        taoDon(c.token(), khoaMoi(), quaTre).andExpect(status().isBadRequest());
    }

    @Test
    void soTheThuNghiem_chiChapNhanTheThuNghiem_vaKhongBaoGioLuuHoacTraLai() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        Map<String, Object> theThat = bodyDon(c.maPhien(), "THE_THU", "THANH_CONG");
        theThat.put("soTheThuNghiem", "1234 5678 9012 3456");

        String kq = body(taoDon(c.token(), khoaMoi(), theThat).andExpect(status().isBadRequest()));

        assertThat(kq).contains("SO_THE_KHONG_PHAI_THE_THU_NGHIEM");
        assertThat(kq).doesNotContain("1234").doesNotContain("3456");
        assertThat(dem("SELECT count(*) FROM DonThanhToan")).isZero();

        Map<String, Object> theThu = bodyDon(c.maPhien(), "THE_THU", "THANH_CONG");
        theThu.put("soTheThuNghiem", "4111 1111 1111 1111");
        String ok = body(taoDon(c.token(), khoaMoi(), theThu).andExpect(status().isAccepted()));
        assertThat(ok).doesNotContain("4111111111111111").doesNotContain("4111 1111");
        // Không cột nào của đơn lưu số thẻ.
        assertThat(dem("SELECT count(*) FROM DonThanhToan WHERE KhoaIdempotency LIKE '%4111111111111111%' "
                + "OR PhuongThuc LIKE '%4111111111111111%' OR MaDon LIKE '%4111111111111111%'")).isZero();
    }

    @Test
    void soTheChiApDungChoPhuongThucTheThu() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        Map<String, Object> req = bodyDon(c.maPhien(), "VI_DIEN_TU", "THANH_CONG");
        req.put("soTheThuNghiem", "4242424242424242");

        String kq = body(taoDon(c.token(), khoaMoi(), req).andExpect(status().isBadRequest()));

        assertThat(kq).contains("SO_THE_KHONG_AP_DUNG");
        assertThat(kq).doesNotContain("4242");
    }

    // ---------- hóa đơn chi tiết (UC22 bước 1) ----------

    @Test
    void hoaDon_hienTongPhiVaChiTietCachTinh_chiChuPhienXem() throws Exception {
        Canh c = phienChoThanhToan("AC-1");

        JsonNode hd = json(gui(HttpMethod.GET, "/api/thanh-toan/phien/" + c.maPhien() + "/hoa-don", c.token(), null)
                .andExpect(status().isOk()).andReturn());

        assertThat(hd.get("trangThaiPhien").asText()).isEqualTo("Chờ thanh toán");
        assertThat(hd.get("tongPhi").decimalValue()).isEqualByComparingTo("10000");
        assertThat(hd.get("chiTiet")).hasSize(1);
        JsonNode dong = hd.get("chiTiet").get(0);
        assertThat(dong.get("dienNangKWh").decimalValue()).isEqualByComparingTo("10");
        assertThat(dong.get("donGia").decimalValue()).isEqualByComparingTo("1000");
        assertThat(dong.get("thanhTien").decimalValue()).isEqualByComparingTo("10000");

        gui(HttpMethod.GET, "/api/thanh-toan/phien/" + c.maPhien() + "/hoa-don", tokenVaiTro(VaiTro.NGUOI_DUNG), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void hoaDon_phienChuaCoPhi_notFound() throws Exception {
        Canh c = dungPhien("AC-1");
        String kq = body(gui(HttpMethod.GET, "/api/thanh-toan/phien/" + c.maPhien() + "/hoa-don", c.token(), null)
                .andExpect(status().isNotFound()));
        assertThat(kq).contains("CHUA_CO_PHI_SAC");
    }
}
