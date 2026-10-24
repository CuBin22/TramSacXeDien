package com.tramsac.thanhtoan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** UC25 (kịch bản 10): hoàn tiền chỉ một lần, không vượt số đã trả, idempotent, điều kiện theo phiên và lý do. */
class HoanTienIT extends ThanhToanBaseIT {

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Phiên Hoàn tất với một giao dịch thanh toán thành công 10.000 đ. */
    private JsonNode daThanhToan(Canh c) throws Exception {
        JsonNode don = taoDonThanhCong(c, "THANH_CONG");
        assertThat(ketQuaWebhook(webhook(don, "THANH_CONG"))).isEqualTo("DA_XU_LY");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hoàn tất");
        return don;
    }

    private int soHoanTien(String maGiaoDich) {
        return dem("SELECT count(*) FROM HoanTien WHERE MaGiaoDich = ?", maGiaoDich);
    }

    // ---------- hoàn tiền hợp lệ ----------

    @Test
    void quanTriHoanTien_ghiNguoiThucHienVaDonChuyenDaHoan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = daThanhToan(c);
        String gd = don.get("maGiaoDich").asText();
        NguoiDung admin = taoNguoiDung(VaiTro.QUAN_TRI);
        String token = dangNhapLayToken(admin);
        String khoa = khoaMoi();

        hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.maGiaoDich").value(gd))
                .andExpect(jsonPath("$.lyDo").value("Hủy phiên"));

        assertThat(soHoanTien(gd)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT NguoiThucHien FROM HoanTien WHERE MaGiaoDich = ?", String.class, gd))
                .isEqualTo(admin.getMaND());
        assertThat(jdbc.queryForObject("SELECT SoTienHoan FROM HoanTien WHERE MaGiaoDich = ?", BigDecimal.class, gd))
                .isEqualByComparingTo("10000");
        assertThat(trangThaiDon(don.get("maDon").asText())).isEqualTo("DA_HOAN");
        assertThat(cong.soLanGoi("HOAN_TIEN")).isEqualTo(1);
        assertThat(cong.danhSach().stream().filter(g -> g.loai().equals("HOAN_TIEN")).findFirst().orElseThrow().khoa())
                .isEqualTo(khoa);
    }

    // ---------- idempotency và chỉ hoàn một lần ----------

    @Test
    void guiLaiCungKhoa_traKetQuaCuKhongHoanHaiLanVaKhongGoiLaiCong() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);
        String khoa = khoaMoi();
        String ma1 = json(hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isOk()).andReturn())
                .get("maHoanTien").asText();

        JsonNode lan2 = json(hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true")).andReturn());

        assertThat(lan2.get("maHoanTien").asText()).isEqualTo(ma1);
        assertThat(soHoanTien(gd)).isEqualTo(1);
        assertThat(cong.soLanGoi("HOAN_TIEN")).isEqualTo(1);
    }

    @Test
    void cungKhoaKhacDuLieu_conflict() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);
        String khoa = khoaMoi();
        hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isOk());

        assertThat(body(hoanTien(token, khoa, gd, 5000, "Hủy phiên").andExpect(status().isConflict())))
                .contains("IDEMPOTENCY_KEY_DUNG_LAI");
        assertThat(soHoanTien(gd)).isEqualTo(1);
    }

    @Test
    void khoaKhacChoGiaoDichDaHoan_conflictVaKhongGoiCong() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);
        hoanTien(token, khoaMoi(), gd, 4000, "Hủy phiên").andExpect(status().isOk());

        // Hoàn một phần xong thì giao dịch coi như đã hoàn: không hoàn thêm lần hai dù khóa khác.
        assertThat(body(hoanTien(token, khoaMoi(), gd, 6000, "Hủy phiên").andExpect(status().isConflict())))
                .contains("GIAO_DICH_DA_HOAN");
        assertThat(soHoanTien(gd)).isEqualTo(1);
        assertThat(cong.soLanGoi("HOAN_TIEN")).isEqualTo(1);
    }

    @Test
    void haiYeuCauHoanTienDongThoiKhoaKhacNhau_chiMotLanThanhCong() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch xuatPhat = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Integer>> kq = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                String khoa = khoaMoi();
                kq.add(pool.submit(() -> {
                    xuatPhat.await();
                    return hoanTien(token, khoa, gd, 10000, "Hủy phiên").andReturn().getResponse().getStatus();
                }));
            }
            xuatPhat.countDown();
            java.util.List<Integer> mas = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<Integer> f : kq) mas.add(f.get());
            assertThat(mas.stream().filter(m -> m == 200).count()).isEqualTo(1);
            assertThat(mas.stream().filter(m -> m == 409).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(soHoanTien(gd)).isEqualTo(1);
    }

    // ---------- số tiền ----------

    @Test
    void soTienHoanKhongVuotSoDaThanhToan_vaPhaiLonHon0() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);

        assertThat(body(hoanTien(token, khoaMoi(), gd, 10000.01, "Hủy phiên").andExpect(status().isBadRequest())))
                .contains("SO_TIEN_HOAN_KHONG_HOP_LE");
        hoanTien(token, khoaMoi(), gd, 0, "Hủy phiên").andExpect(status().isBadRequest());
        hoanTien(token, khoaMoi(), gd, -5, "Hủy phiên").andExpect(status().isBadRequest());

        assertThat(soHoanTien(gd)).isZero();
        assertThat(cong.soLanGoi("HOAN_TIEN")).isZero();
        hoanTien(token, khoaMoi(), gd, 3500.50, "Hủy phiên").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT SoTienHoan FROM HoanTien WHERE MaGiaoDich = ?", BigDecimal.class, gd))
                .isEqualByComparingTo("3500.50");
    }

    // ---------- điều kiện giao dịch, phiên và lý do ----------

    @Test
    void giaoDichChuaThanhCong_khongDuocHoan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        JsonNode don = taoDonThanhCong(c, "THAT_BAI");
        assertThat(ketQuaWebhook(webhook(don, "THAT_BAI"))).isEqualTo("DA_XU_LY");

        assertThat(body(hoanTien(tokenVaiTro(VaiTro.QUAN_TRI), khoaMoi(), don.get("maGiaoDich").asText(), 10000,
                "Hủy phiên").andExpect(status().isConflict()))).contains("GIAO_DICH_CHUA_THANH_CONG");
        assertThat(soHoanTien(don.get("maGiaoDich").asText())).isZero();
    }

    @Test
    void giaoDichKhongTonTai_notFound() throws Exception {
        hoanTien(tokenVaiTro(VaiTro.QUAN_TRI), khoaMoi(), "GDT-KHONG-CO", 1000, "Hủy phiên")
                .andExpect(status().isNotFound());
    }

    @Test
    void lyDoPhaiLaHuyPhienHoacThietBiLoi_thietBiLoiCanCoSuCoTrenCong() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);

        assertThat(body(hoanTien(token, khoaMoi(), gd, 10000, "Lý do tự chế").andExpect(status().isBadRequest())))
                .contains("LY_DO_HOAN_KHONG_HOP_LE");
        assertThat(body(hoanTien(token, khoaMoi(), gd, 10000, "Thiết bị lỗi").andExpect(status().isConflict())))
                .contains("CHUA_CO_SU_CO_LIEN_QUAN");
        assertThat(soHoanTien(gd)).isZero();

        // Thiết bị báo lỗi trên cổng đó (sự cố được ghi nhận tự động) thì hoàn tiền vì thiết bị lỗi được.
        thietBiBaoLoi("AC-1", "RO_DIEN");
        hoanTien(token, khoaMoi(), gd, 10000, "Thiết bị lỗi").andExpect(status().isOk())
                .andExpect(jsonPath("$.lyDo").value("Thiết bị lỗi"));
        assertThat(soHoanTien(gd)).isEqualTo(1);
    }

    @Test
    void phienChuaHoanTat_khongDuocHoan() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        jdbc.update("UPDATE PhienSac SET TrangThai = 'Hủy' WHERE MaPhien = ?", c.maPhien());

        assertThat(body(hoanTien(tokenVaiTro(VaiTro.QUAN_TRI), khoaMoi(), gd, 10000, "Hủy phiên")
                .andExpect(status().isConflict()))).contains("PHIEN_CHUA_HOAN_TAT");
        assertThat(soHoanTien(gd)).isZero();
    }

    // ---------- phân quyền và đầu vào ----------

    @Test
    void chiQuanTriMoiDuocHoanTien() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();

        hoanTien(c.token(), khoaMoi(), gd, 10000, "Hủy phiên").andExpect(status().isForbidden());
        hoanTien(tokenVaiTro(VaiTro.KY_THUAT_VIEN), khoaMoi(), gd, 10000, "Hủy phiên")
                .andExpect(status().isForbidden());
        hoanTien(tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), khoaMoi(), gd, 10000, "Hủy phiên")
                .andExpect(status().isForbidden());

        assertThat(soHoanTien(gd)).isZero();
    }

    @Test
    void khoaKhongPhaiUuid_badRequest() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        assertThat(body(hoanTien(tokenVaiTro(VaiTro.QUAN_TRI), "khong-phai-uuid", gd, 10000, "Hủy phiên")
                .andExpect(status().isBadRequest()))).contains("IDEMPOTENCY_KEY_KHONG_HOP_LE");
    }

    // ---------- cổng thanh toán lỗi ----------

    @Test
    void congLoiKhiHoan_khongGhiHoanTien_vaThuLaiCungKhoaDuoc() throws Exception {
        Canh c = phienChoThanhToan("AC-1");
        String gd = daThanhToan(c).get("maGiaoDich").asText();
        String token = tokenVaiTro(VaiTro.QUAN_TRI);
        String khoa = khoaMoi();
        cong.datLoi(true);

        hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isServiceUnavailable());
        assertThat(soHoanTien(gd)).isZero();

        cong.datLoi(false);
        hoanTien(token, khoa, gd, 10000, "Hủy phiên").andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"));
        assertThat(soHoanTien(gd)).isEqualTo(1);
    }
}
