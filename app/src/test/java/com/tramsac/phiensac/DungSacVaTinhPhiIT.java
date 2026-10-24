package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.bieugia.TinhPhiService;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** UC10 (dừng sạc), UC14 (tính phí chính thức), quy tắc 14 (nhân viên dừng thay người dùng). */
class DungSacVaTinhPhiIT extends PhienSacBaseIT {

    @Autowired
    PhienSacRepository phienRepository;
    @Autowired
    TinhPhiService tinhPhiService;
    @Autowired
    PlatformTransactionManager txManager;

    @AfterEach
    void xoaBieuGiaTest() {
        // Chạy trước bước dọn của lớp cha nên phải gỡ phí tham chiếu biểu giá trước.
        jdbc.update("DELETE FROM ChiTietPhi");
        jdbc.update("DELETE FROM PhiSac");
        jdbc.update("DELETE FROM ChiTietBieuGia WHERE MaBieuGia = 'BG-TEST'");
        jdbc.update("DELETE FROM BieuGia WHERE MaBieuGia = 'BG-TEST'");
    }

    // ---------- dừng sạc và tính phí chính thức ----------

    @Test
    void dungSac_tinhPhiMotLanTheoDienNangThietBiBao() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        clock.advance(Duration.ofMinutes(15)); // < 30 phút để token chưa hết hạn (QĐ2)

        String maDung = maLenh(dung(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        ack("AC-1", maDung, "OK", 10.0);

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        assertThat(trangThaiDatPhien(c.maDatPhien())).isEqualTo("Hoàn tất");
        // Biểu giá mặc định: 1.000 đ/kWh cho sinh viên dùng cổng AC.
        assertThat(jdbc.queryForObject("SELECT TongPhi FROM PhiSac WHERE MaPhien = ?", BigDecimal.class, c.maPhien()))
                .isEqualByComparingTo("10000");
        assertThat(jdbc.queryForObject("SELECT sum(DienNangKWh) FROM ChiTietPhi ct JOIN PhiSac p ON p.MaPhi = ct.MaPhi "
                + "WHERE p.MaPhien = ?", BigDecimal.class, c.maPhien())).isEqualByComparingTo("10.000");
        List<Map<String, Object>> ls = jdbc.queryForList(
                "SELECT trangthaimoi FROM LichSuTrangThaiPhien WHERE MaPhien = ?", c.maPhien());
        assertThat(ls.stream().map(m -> m.get("trangthaimoi")).toList())
                .contains("Kết thúc sạc", "Chờ thanh toán");

        // Tin báo kết thúc đến lại: không tính phí lần hai.
        ack("AC-1", maDung, "OK", 10.0);
        assertThat(dem("SELECT count(*) FROM PhiSac WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    @Test
    void dungKhiChuaSac_conflict() throws Exception {
        Canh c = dungPhien("AC-1");
        String body = dung(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("PHIEN_KHONG_DANG_SAC");
    }

    @Test
    void dungLapLaiCungKhoa_khongTaoLenhDungMoi() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        String khoa = khoaMoi();
        String ma1 = maLenh(dung(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted()));
        String ma2 = maLenh(dung(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted()));
        assertThat(ma2).isEqualTo(ma1);
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ? AND LoaiLenh = 'DUNG'", c.maPhien()))
                .isEqualTo(1);
    }

    // ---------- quy tắc 14: nhân viên dừng thay người dùng ----------

    @Test
    void nhanVienTrucDungPhien_ghiNguonThaoTac() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        NguoiDung nv = taoNguoiDung(VaiTro.NHAN_VIEN_TRUC);
        String tokenNv = dangNhapLayToken(nv);

        dung(tokenNv, c.maPhien(), khoaMoi()).andExpect(status().isAccepted());

        assertThat(soNhatKy(nv, "DUNG_SAC_HO_TRO")).isEqualTo(1);
    }

    @Test
    void nguoiDungThuongDungPhienCuaMinh_khongGhiNhatKyHoTro() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        dung(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted());
        assertThat(soNhatKy(c.nd(), "DUNG_SAC_HO_TRO")).isZero();
    }

    @Test
    void kyThuatVienKhongDuocDungPhien_vaNguoiDungKhacKhongThay() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        dung(tokenVaiTro(VaiTro.KY_THUAT_VIEN), c.maPhien(), khoaMoi()).andExpect(status().isForbidden());
        dung(tokenVaiTro(VaiTro.NGUOI_DUNG), c.maPhien(), khoaMoi()).andExpect(status().isNotFound());
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ? AND LoaiLenh = 'DUNG'", c.maPhien()))
                .isZero();
    }

    // ---------- tách khung giờ khi tính phí ----------

    @Test
    void tinhPhi_phienQuaHaiKhungGio_tachDongVaTongKhop() throws Exception {
        LocalDate homNay = LocalDate.now(clock);
        jdbc.update("INSERT INTO BieuGia (MaBieuGia, TenBieuGia, NgayApDung, NguoiCauHinh) "
                + "VALUES ('BG-TEST', 'Biểu giá test', ?, 'test')", homNay);
        chenDongGia("CT-T1", "00:00-12:00", 1000, LocalTime.of(0, 0), LocalTime.of(12, 0));
        chenDongGia("CT-T2", "12:00-24:00", 3000, LocalTime.of(12, 0), LocalTime.of(0, 0));
        Canh c = dungPhien("AC-1");
        LocalDateTime batDau = homNay.atTime(11, 0);
        LocalDateTime ketThuc = homNay.atTime(13, 0);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.executeWithoutResult(st -> {
            PhienSac p = phienRepository.findById(c.maPhien()).orElseThrow();
            p.danhDauBatDau(batDau);
            p.danhDauKetThuc(ketThuc, new BigDecimal("10.000"));
            tinhPhiService.tinhPhi(p);
        });
        // Gọi lần hai: trả kết quả đã lưu, không tính lại.
        tx.executeWithoutResult(st -> tinhPhiService.tinhPhi(phienRepository.findById(c.maPhien()).orElseThrow()));

        assertThat(dem("SELECT count(*) FROM PhiSac WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
        // 5 kWh × 1.000 + 5 kWh × 3.000 = 20.000
        assertThat(jdbc.queryForObject("SELECT TongPhi FROM PhiSac WHERE MaPhien = ?", BigDecimal.class, c.maPhien()))
                .isEqualByComparingTo("20000");
        assertThat(jdbc.queryForObject("SELECT MaBieuGia FROM PhiSac WHERE MaPhien = ?", String.class, c.maPhien()))
                .isEqualTo("BG-TEST");
        List<Map<String, Object>> dong = jdbc.queryForList("SELECT ct.* FROM ChiTietPhi ct JOIN PhiSac p "
                + "ON p.MaPhi = ct.MaPhi WHERE p.MaPhien = ? ORDER BY ct.DonGia", c.maPhien());
        assertThat(dong).hasSize(2);
        BigDecimal tongKwh = BigDecimal.ZERO;
        BigDecimal tongTien = BigDecimal.ZERO;
        for (Map<String, Object> d : dong) {
            tongKwh = tongKwh.add((BigDecimal) d.get("diennangkwh"));
            tongTien = tongTien.add((BigDecimal) d.get("thanhtien"));
        }
        assertThat(tongKwh).isEqualByComparingTo("10.000");
        assertThat(tongTien).isEqualByComparingTo("20000");
    }

    private void chenDongGia(String ma, String khungGio, int donGia, LocalTime tu, LocalTime den) {
        jdbc.update("INSERT INTO ChiTietBieuGia (MaCTBG, MaBieuGia, KhungGio, LoaiCong, DoiTuong, DonGia, "
                + "GioBatDau, GioKetThuc) VALUES (?, 'BG-TEST', ?, 'AC', 'Sinh viên', ?, ?, ?)",
                ma, khungGio, donGia, tu, den);
    }
}
