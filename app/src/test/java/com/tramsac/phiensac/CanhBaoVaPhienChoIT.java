package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.nguoidung.VaiTro;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/** UC18 (cảnh báo xe chiếm cổng, QĐ8) và phiên Chờ quá 15 phút không có xác nhận bắt đầu. */
class CanhBaoVaPhienChoIT extends PhienSacBaseIT {

    @Autowired
    CanhBaoChiemCongService canhBaoService;
    @Autowired
    PhienSacRepository phienRepository;
    @Autowired
    PhienSacChoQuaHanService expiryService;

    /** Phiên đã kết thúc sạc (Chờ thanh toán) cách đây phutTruoc phút, xe vẫn ở cổng. */
    private Canh phienDaKetThuc(int phutTruoc, boolean coXe) throws Exception {
        Canh c = dungPhien("AC-1");
        jdbc.update("UPDATE PhienSac SET TrangThai = 'Chờ thanh toán', KetThucLuc = ? WHERE MaPhien = ?",
                LocalDateTime.now(clock).minusMinutes(phutTruoc), c.maPhien());
        jdbc.update("UPDATE CongSac SET CoXe = ? WHERE MaCong = 'AC-1'", coXe);
        return c;
    }

    private List<Map<String, Object>> canhBao(String maPhien) {
        return jdbc.queryForList("SELECT lancanhbao, nguoinhan FROM CanhBaoChiemCong WHERE MaPhien = ? "
                + "ORDER BY LanCanhBao", maPhien);
    }

    // ---------- UC18 ----------

    @Test
    void nhacNguoiDungMoi10Phut_3Lan_roiLeoThangNhanVienTruc() throws Exception {
        Canh c = phienDaKetThuc(9, true);

        canhBaoService.quet();
        assertThat(canhBao(c.maPhien())).isEmpty();

        clock.advance(Duration.ofMinutes(1)); // đủ 10 phút
        canhBaoService.quet();
        canhBaoService.quet();
        assertThat(canhBao(c.maPhien())).hasSize(1);
        assertThat(canhBao(c.maPhien()).get(0).get("nguoinhan")).isEqualTo("Người dùng");

        clock.advance(Duration.ofMinutes(10));
        canhBaoService.quet();
        clock.advance(Duration.ofMinutes(10));
        canhBaoService.quet();
        List<Map<String, Object>> baLan = canhBao(c.maPhien());
        assertThat(baLan).hasSize(3);
        assertThat(baLan).allMatch(m -> "Người dùng".equals(m.get("nguoinhan")));

        clock.advance(Duration.ofMinutes(10));
        canhBaoService.quet();
        List<Map<String, Object>> bonLan = canhBao(c.maPhien());
        assertThat(bonLan).hasSize(4);
        assertThat(bonLan.get(3).get("nguoinhan")).isEqualTo("Nhân viên trực");

        clock.advance(Duration.ofMinutes(10));
        canhBaoService.quet();
        assertThat(canhBao(c.maPhien())).hasSize(4);
    }

    @Test
    void nguoiDungDaPhanHoi_dungNhacVaKhongLeoThang() throws Exception {
        Canh c = phienDaKetThuc(10, true);
        canhBaoService.quet();
        assertThat(canhBao(c.maPhien())).hasSize(1);
        jdbc.update("UPDATE CanhBaoChiemCong SET PhanHoi = 'Đang ra lấy xe' WHERE MaPhien = ?", c.maPhien());

        clock.advance(Duration.ofMinutes(40));
        canhBaoService.quet();

        assertThat(canhBao(c.maPhien())).hasSize(1);
    }

    @Test
    void xeDaRoiCong_khongCanhBao() throws Exception {
        Canh c = phienDaKetThuc(35, false);
        canhBaoService.quet();
        assertThat(canhBao(c.maPhien())).isEmpty();
    }

    @Test
    void nhanVienGhiPhanHoi_nguoiDungThuongKhongDuoc() throws Exception {
        Canh c = phienDaKetThuc(10, true);
        canhBaoService.quet();
        String ma = jdbc.queryForObject("SELECT MaCanhBao FROM CanhBaoChiemCong WHERE MaPhien = ?", String.class,
                c.maPhien());

        gui(HttpMethod.PUT, "/api/canh-bao-chiem-cong/" + ma, c.token(), Map.of("phanHoi", "Đã gọi chủ xe"))
                .andExpect(status().isForbidden());
        gui(HttpMethod.PUT, "/api/canh-bao-chiem-cong/" + ma, tokenVaiTro(VaiTro.NHAN_VIEN_TRUC),
                Map.of("phanHoi", "Đã gọi chủ xe")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT PhanHoi FROM CanhBaoChiemCong WHERE MaCanhBao = ?", String.class, ma))
                .isEqualTo("Đã gọi chủ xe");
    }

    // ---------- phiên Chờ quá 15 phút ----------

    @Test
    void phienChoQua15Phut_tuHuyVaGiaiPhongCong() throws Exception {
        Canh c = dungPhien("AC-1");
        batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted());
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");
        PhienSacMaintenanceJob job = new PhienSacMaintenanceJob(phienRepository, expiryService, clock);

        clock.advance(Duration.ofMinutes(10));
        job.quet();
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");

        clock.advance(Duration.ofMinutes(6));
        job.quet();

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
        assertThat(trangThaiDatPhien(c.maDatPhien())).isEqualTo("Hủy");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void phienDangSac_khongBiHuyDoQuaHan() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        clock.advance(Duration.ofMinutes(30));

        new PhienSacMaintenanceJob(phienRepository, expiryService, clock).quet();

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");
    }
}
