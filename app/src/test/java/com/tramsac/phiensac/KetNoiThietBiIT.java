package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.PhanBoLaiDatPhienService;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** UC17 (kịch bản 5): heartbeat 30 giây, mất kết nối sau 90 giây, sự cố sau 5 phút, khôi phục và đối chiếu. */
class KetNoiThietBiIT extends PhienSacBaseIT {

    @Autowired
    CongSacRepository congRepository;
    @Autowired
    DeviceRecoveryTxService recoveryTx;
    @Autowired
    DeviceStateService stateService;
    @Autowired
    PhanBoLaiDatPhienService reassignment;

    private HeartbeatWatcherJob job() {
        return new HeartbeatWatcherJob(congRepository, recoveryTx, stateService, reassignment, clock);
    }

    private void datHeartbeatCuoi(String maCong, int giayTruoc) {
        jdbc.update("UPDATE CongSac SET HeartbeatCuoi = ? WHERE MaCong = ?",
                LocalDateTime.now(clock).minusSeconds(giayTruoc), maCong);
    }

    private List<Map<String, Object>> lichSuKetNoi(String maCong) {
        return jdbc.queryForList("SELECT sukien, thoigiangiandoan FROM LichSuKetNoi WHERE MaCong = ? "
                + "ORDER BY ThoiDiem, MaLSKN", maCong);
    }

    // ---------- phát hiện mất kết nối ----------

    @Test
    void khongCoHeartbeatQua90Giay_congMatKetNoi() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        datHeartbeatCuoi("AC-1", 100);

        job().quet();

        assertThat(trangThaiCong("AC-1")).isEqualTo("Mất kết nối");
        assertThat(jdbc.queryForObject("SELECT TrangThaiTruocMatKetNoi FROM CongSac WHERE MaCong = 'AC-1'",
                String.class)).isEqualTo("Rảnh");
        List<Map<String, Object>> ls = lichSuKetNoi("AC-1");
        assertThat(ls).hasSize(1);
        assertThat(ls.get(0).get("sukien")).isEqualTo("Mất kết nối");
    }

    @Test
    void heartbeatDuoi90Giay_vanKetNoi() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        datHeartbeatCuoi("AC-1", 60);

        job().quet();

        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        assertThat(lichSuKetNoi("AC-1")).isEmpty();
    }

    @Test
    void congBaoTri_khongBiDanhDauMatKetNoi() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Bảo trì", CAU_HINH, 0);
        datHeartbeatCuoi("AC-1", 600);

        job().quet();

        assertThat(trangThaiCong("AC-1")).isEqualTo("Bảo trì");
    }

    @Test
    void matKetNoiQua5Phut_tuDongTaoSuCoMotLan() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        datHeartbeatCuoi("AC-1", 100);
        job().quet();
        assertThat(dem("SELECT count(*) FROM SuCo WHERE MaCong = 'AC-1'")).isZero();

        clock.advance(Duration.ofMinutes(6));
        job().quet();
        job().quet();

        List<Map<String, Object>> suCo = jdbc.queryForList(
                "SELECT loaisuco, mucuutien FROM SuCo WHERE MaCong = 'AC-1'");
        assertThat(suCo).hasSize(1);
        assertThat(suCo.get(0).get("loaisuco")).isEqualTo("Mất kết nối");
    }

    // ---------- khôi phục ----------

    @Test
    void khoiPhuc_heartbeatChiHoiTrangThaiThat_chuaTuVeRanh() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        datHeartbeatCuoi("AC-1", 100);
        job().quet();
        clock.advance(Duration.ofMinutes(6));

        thietBiHeartbeat("AC-1", false);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Mất kết nối");
        assertThat(mqtt.soThongDiepLoai("HOI_TRANG_THAI")).isEqualTo(1);

        thietBiBaoTrangThai("AC-1", "Rảnh", null);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        List<Map<String, Object>> ls = lichSuKetNoi("AC-1");
        assertThat(ls.get(ls.size() - 1).get("sukien")).isEqualTo("Khôi phục");
        assertThat(((Number) ls.get(ls.size() - 1).get("thoigiangiandoan")).intValue()).isGreaterThanOrEqualTo(360);
    }

    @Test
    void matKetNoiKhiDangSac_thietBiVanSac_phienGiuDangSac() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        datHeartbeatCuoi("AC-1", 100);

        job().quet();
        assertThat(trangThaiCong("AC-1")).isEqualTo("Mất kết nối");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");

        thietBiBaoTrangThai("AC-1", "Đang sạc", 1.0);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Đang sạc");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");
    }

    @Test
    void matKetNoiKhiDangSac_thietBiDaDung_doiChieuDienNangVaTinhPhi() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        datHeartbeatCuoi("AC-1", 100);
        job().quet();
        clock.advance(Duration.ofMinutes(5));

        thietBiBaoTrangThai("AC-1", "Rảnh", 3.5);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        assertThat(trangThaiDatPhien(c.maDatPhien())).isEqualTo("Hoàn tất");
        assertThat(jdbc.queryForObject("SELECT TongPhi FROM PhiSac WHERE MaPhien = ?", BigDecimal.class, c.maPhien()))
                .isEqualByComparingTo("3500");
    }

    @Test
    void khoiPhucVoiTrangThaiLoi_congLoiVaCoSuCoKhanCap() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        datHeartbeatCuoi("AC-1", 100);
        job().quet();

        thietBiBaoTrangThai("AC-1", "Lỗi", null);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
        List<Map<String, Object>> suCo = jdbc.queryForList("SELECT mucuutien FROM SuCo WHERE MaCong = 'AC-1'");
        assertThat(suCo).hasSize(1);
        assertThat(suCo.get(0).get("mucuutien")).isEqualTo("Khẩn cấp");
    }

    // ---------- đặt chỗ tương lai bị ảnh hưởng (quy tắc 10) ----------

    @Test
    void congMatKetNoi_datChoTuongLaiDuocPhanBoLaiSangCongKhac() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Rảnh", CAU_HINH, 0);
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, bienSoMoi(), "AC");
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", bayGio().plusHours(2), bayGio().plusHours(3),
                "Đã đặt", "Đã đặt");
        datHeartbeatCuoi("AC-1", 100);

        job().quet();

        assertThat(trangThaiCong("AC-1")).isEqualTo("Mất kết nối");
        assertThat(maCongCuaDatPhien(ma)).isEqualTo("AC-2");
        assertThat(jdbc.queryForObject("SELECT MaCong FROM PhienSac WHERE MaDatPhien = ?", String.class, ma))
                .isEqualTo("AC-2");
        assertThat(soNhatKy(nd, "PHAN_BO_LAI_CONG")).isEqualTo(1);
    }

    @Test
    void congMatKetNoi_khongCoCongThayThe_huyDatCho() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, bienSoMoi(), "AC");
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", bayGio().plusHours(2), bayGio().plusHours(3),
                "Đã đặt", "Đã đặt");
        datHeartbeatCuoi("AC-1", 100);

        job().quet();

        assertThat(trangThaiDatPhien(ma)).isEqualTo("Hủy");
        assertThat(trangThaiPhien(ma)).isEqualTo("Hủy");
        assertThat(soNhatKy(nd, "HUY_DAT_PHIEN_KHONG_CO_CONG")).isEqualTo(1);
    }
}
