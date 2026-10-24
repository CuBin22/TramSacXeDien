package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.mqtt.MqttTopics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** UC10–UC12: bắt đầu/dừng sạc, lệnh idempotent, ack, gửi lại 3 lần, từ chối khi thiết bị không an toàn. */
class LenhDieuKhienIT extends PhienSacBaseIT {

    @Autowired
    LenhDieuKhienRepository lenhRepository;
    @Autowired
    LenhDieuKhienService lenhService;
    @Autowired
    PhienSacCommandService commandService;

    private LenhRetryJob retryJob() {
        return new LenhRetryJob(lenhRepository, lenhService, commandService, clock);
    }

    // ---------- bắt đầu sạc hợp lệ ----------

    @Test
    void batDau_guiLenhQuaMqttVaChoAck() throws Exception {
        Canh c = dungPhien("AC-1");

        var r = batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.trangThai").value("Chờ ack"))
                .andExpect(jsonPath("$.loaiLenh").value("BAT_DAU"));
        String ma = maLenh(r);

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");
        assertThat(mqtt.soLanGuiLenh(ma)).isEqualTo(1);
        assertThat(mqtt.danhSachGui().get(0).topic()).isEqualTo(MqttTopics.lenh("AC-1"));
        assertThat(mqtt.danhSachGui().get(0).payload().get("loai")).isEqualTo("BAT_DAU");
    }

    @Test
    void ackOk_chuyenSangDangSacVaGhiLichSu() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = batDauThanhCong(c);

        assertThat(trangThaiCong("AC-1")).isEqualTo("Đang sạc");
        assertThat(trangThaiDatPhien(c.maDatPhien())).isEqualTo("Đang sạc");
        assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                .isEqualTo("Thành công");
        assertThat(jdbc.queryForObject("SELECT CongSuatDuocCap FROM CongSac WHERE MaCong = 'AC-1'",
                java.math.BigDecimal.class)).isEqualByComparingTo("7.40");
        List<Map<String, Object>> ls = jdbc.queryForList(
                "SELECT trangthaicu, trangthaimoi FROM LichSuTrangThaiPhien WHERE MaPhien = ? ORDER BY ThoiDiem, MaLSTT",
                c.maPhien());
        assertThat(ls.stream().map(m -> m.get("trangthaimoi")).toList()).contains("Chờ", "Đang sạc");
    }

    @Test
    void ackLapLai_khongXuLyHaiLan() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = batDauThanhCong(c);
        int truoc = dem("SELECT count(*) FROM LichSuTrangThaiPhien WHERE MaPhien = ?", c.maPhien());

        ack("AC-1", ma, "OK", null);
        ack("AC-1", ma, "OK", null);

        assertThat(dem("SELECT count(*) FROM LichSuTrangThaiPhien WHERE MaPhien = ?", c.maPhien())).isEqualTo(truoc);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");
    }

    // ---------- idempotency (kịch bản 2) ----------

    @Test
    void guiLaiCungKhoa_traKetQuaCuKhongTaoLenhMoi() throws Exception {
        Canh c = dungPhien("AC-1");
        String khoa = khoaMoi();
        String ma1 = maLenh(batDau(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted()));

        var lan2 = batDau(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "true"));
        String ma2 = maLenh(lan2);

        assertThat(ma2).isEqualTo(ma1);
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);

        ack("AC-1", ma1, "OK", null);
        var lan3 = batDau(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.trangThai").value("Thành công"));
        assertThat(maLenh(lan3)).isEqualTo(ma1);
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đang sạc");
    }

    @Test
    void cungKhoaChoLoaiLenhKhac_conflict() throws Exception {
        Canh c = dungPhien("AC-1");
        String khoa = khoaMoi();
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoa).andExpect(status().isAccepted()));
        ack("AC-1", ma, "OK", null);

        String body = dung(c.token(), c.maPhien(), khoa).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("IDEMPOTENCY_KEY_XUNG_DOT");
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    @Test
    void khoaKhongPhaiUuid_badRequest() throws Exception {
        Canh c = dungPhien("AC-1");
        String body = batDau(c.token(), c.maPhien(), "khong-phai-uuid").andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("IDEMPOTENCY_KEY_KHONG_HOP_LE");
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    @Test
    void haiYeuCauDongThoiCungKhoa_chiMotLenh() throws Exception {
        Canh c = dungPhien("AC-1");
        String khoa = khoaMoi();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch xuatPhat = new CountDownLatch(1);
        List<Future<Integer>> ketQua = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                ketQua.add(pool.submit(() -> {
                    xuatPhat.await();
                    return batDau(c.token(), c.maPhien(), khoa).andReturn().getResponse().getStatus();
                }));
            }
            xuatPhat.countDown();
            List<Integer> mas = new ArrayList<>();
            for (Future<Integer> f : ketQua) {
                mas.add(f.get());
            }
            assertThat(mas).contains(202);
            assertThat(mas).allMatch(m -> m == 202 || m == 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isEqualTo(1);
    }

    @Test
    void nguoiDungKhacChuPhien_khongTimThay() throws Exception {
        Canh c = dungPhien("AC-1");
        String tokenKhac = tokenVaiTro(com.tramsac.nguoidung.VaiTro.NGUOI_DUNG);
        batDau(tokenKhac, c.maPhien(), khoaMoi()).andExpect(status().isNotFound());
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    // ---------- an toàn: từ chối cấp điện (UC11) ----------

    @Test
    void thietBiKhongXacNhanAnToan_tuChoiVaGhiNhatKy() throws Exception {
        Canh c = dungPhien("AC-1");
        mqtt.datAnToan(false);
        int truoc = dem("SELECT count(*) FROM NhatKyHeThong WHERE HanhDong = 'TU_CHOI_CAP_DIEN'");

        String body = batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("TU_CHOI_AN_TOAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đã đặt");
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isZero();
        assertThat(dem("SELECT count(*) FROM NhatKyHeThong WHERE HanhDong = 'TU_CHOI_CAP_DIEN'"))
                .isEqualTo(truoc + 1);
        assertThat(mqtt.soThongDiepLoai("BAT_DAU")).isZero();
    }

    @Test
    void mqttMatKetNoi_khongMacDinhCapDien() throws Exception {
        Canh c = dungPhien("AC-1");
        mqtt.datKetNoi(false);

        batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict());

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đã đặt");
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lỗi", "Bảo trì", "Mất kết nối"})
    void congKhongAnToan_khongChoBatDau(String trangThai) throws Exception {
        Canh c = dungPhien("AC-1");
        datTrangThaiCong("AC-1", trangThai);

        String body = batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("TU_CHOI_AN_TOAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đã đặt");
        assertThat(trangThaiCong("AC-1")).isEqualTo(trangThai);
        assertThat(dem("SELECT count(*) FROM LenhDieuKhien WHERE MaPhien = ?", c.maPhien())).isZero();
    }

    @Test
    void khongDuCongSuatToiThieu_tuChoi() throws Exception {
        taoCauHinh("CH-NHO", 3);
        taoCong("AC-1", "AC", "Đang sạc", "CH-NHO", 0);
        taoCong("AC-2", "AC", "Rảnh", "CH-NHO", 0);
        Canh c = taoPhienChoCong("AC-2");

        String body = batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("TU_CHOI_AN_TOAN");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Đã đặt");
    }

    // ---------- ack lỗi, không ack, ack muộn (kịch bản 3) ----------

    @Test
    void ackThatBai_phienGiuNguyenCho() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));

        ack("AC-1", ma, "ERROR", null);

        assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                .isEqualTo("Thất bại");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đã đặt");
    }

    @Test
    void khongCoAck_guiLai3LanRoiThatBaiVaPhienKhongSang_DangSac() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        LenhRetryJob job = retryJob();

        for (int lan = 2; lan <= 4; lan++) {
            clock.advance(Duration.ofSeconds(11));
            job.quet();
            assertThat(jdbc.queryForObject("SELECT SoLanGui FROM LenhDieuKhien WHERE MaLenh = ?", Integer.class, ma))
                    .isEqualTo(lan);
            assertThat(mqtt.soLanGuiLenh(ma)).isEqualTo(lan);
            assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                    .isEqualTo("Chờ ack");
        }

        clock.advance(Duration.ofSeconds(11));
        job.quet();

        assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                .isEqualTo("Thất bại");
        assertThat(mqtt.soLanGuiLenh(ma)).isEqualTo(4);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");

        // Thử lại bằng khóa mới được phép vì lệnh cũ đã thất bại.
        String maMoi = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        assertThat(maMoi).isNotEqualTo(ma);
    }

    @Test
    void chuaQua10Giay_khongGuiLai() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));

        clock.advance(Duration.ofSeconds(5));
        retryJob().quet();

        assertThat(mqtt.soLanGuiLenh(ma)).isEqualTo(1);
    }

    @Test
    void ackDenMuonSauKhiThatBai_khongBatDauSacVaHoiTrangThaiThat() throws Exception {
        Canh c = dungPhien("AC-1");
        String ma = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        LenhRetryJob job = retryJob();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(11));
            job.quet();
        }
        assertThat(jdbc.queryForObject("SELECT TrangThai FROM LenhDieuKhien WHERE MaLenh = ?", String.class, ma))
                .isEqualTo("Thất bại");

        ack("AC-1", ma, "OK", null);

        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ");
        assertThat(mqtt.soThongDiepLoai("HOI_TRANG_THAI")).isGreaterThanOrEqualTo(1);
    }
}
