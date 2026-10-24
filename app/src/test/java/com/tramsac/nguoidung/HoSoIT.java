package com.tramsac.nguoidung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.AuthenticatedIT;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class HoSoIT extends AuthenticatedIT {

    @AfterEach
    void xoaDuLieuCong() {
        jdbc.update("DELETE FROM DatPhien WHERE MaCong = 'TEST-C-HS'");
        jdbc.update("DELETE FROM CongSac WHERE MaCong = 'TEST-C-HS'");
    }

    // ---------- xem hồ sơ ----------

    @Test
    void getHoSo_returnsOnlyOwnDataAndVehicles() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        NguoiDung b = taoNguoiDung(VaiTro.NGUOI_DUNG);
        themXe(a, "TA-1", "AC");
        themXe(a, "TA-2", "DC");
        themXe(b, "TB-1", "AC");

        gui(HttpMethod.GET, "/api/ho-so", dangNhapLayToken(a), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maND").value(a.getMaND()))
                .andExpect(jsonPath("$.email").value(a.getEmail()))
                .andExpect(jsonPath("$.vaiTro").value("Người dùng"))
                .andExpect(jsonPath("$.xe.length()").value(2))
                .andExpect(jsonPath("$.xe[?(@.bienSo=='TB-1')]").isEmpty());
    }

    @Test
    void getHoSo_withoutToken_returns401() throws Exception {
        gui(HttpMethod.GET, "/api/ho-so", null, null).andExpect(status().isUnauthorized());
    }

    // ---------- sửa hồ sơ ----------

    @Test
    void updateProfile_changesNameEmailAndPhone_normalized() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String sdt = soDienThoaiMoi();
        String sdtCoKhoangTrang = sdt.substring(0, 4) + " " + sdt.substring(4);
        String emailMoi = "Moi" + uid() + "@U.test";

        gui(HttpMethod.PUT, "/api/ho-so", dangNhapLayToken(nd), hoSo("Tên Mới", emailMoi, sdtCoKhoangTrang))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoTen").value("Tên Mới"))
                .andExpect(jsonPath("$.email").value(emailMoi.toLowerCase()))
                .andExpect(jsonPath("$.soDienThoai").value(sdt));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM NguoiDung WHERE MaND = ?", nd.getMaND());
        assertThat(row.get("hoten")).isEqualTo("Tên Mới");
        assertThat(row.get("email")).isEqualTo(emailMoi.toLowerCase());
        assertThat(row.get("sodienthoai")).isEqualTo(sdt);
        assertThat(soNhatKy(nd, "CAP_NHAT_HO_SO")).isEqualTo(1);
    }

    @Test
    void updateProfile_duplicateEmailOfOtherUser_returns409() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        NguoiDung b = taoNguoiDung(VaiTro.NGUOI_DUNG);

        gui(HttpMethod.PUT, "/api/ho-so", dangNhapLayToken(a), hoSo("A", b.getEmail().toUpperCase(), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_DU_LIEU"));
    }

    @Test
    void updateProfile_keepingOwnEmail_returns200() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        gui(HttpMethod.PUT, "/api/ho-so", dangNhapLayToken(a), hoSo("Tên khác", a.getEmail(), null))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_removingBothEmailAndPhone_returns400() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        gui(HttpMethod.PUT, "/api/ho-so", dangNhapLayToken(a), hoSo("A", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ma").value("DU_LIEU_KHONG_HOP_LE"));
    }

    @Test
    void updateProfile_tryChangeDoiTuongOrVaiTro_returns403_andNothingChanges() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = dangNhapLayToken(nd);

        Map<String, Object> doiDoiTuong = hoSo("A", nd.getEmail(), null);
        doiDoiTuong.put("doiTuong", "Cán bộ");
        gui(HttpMethod.PUT, "/api/ho-so", token, doiDoiTuong)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ma").value("CHI_QUAN_TRI_DOI_DOI_TUONG"));

        Map<String, Object> nangQuyen = hoSo("A", nd.getEmail(), null);
        nangQuyen.put("vaiTro", "Quản trị");
        gui(HttpMethod.PUT, "/api/ho-so", token, nangQuyen)
                .andExpect(status().isForbidden());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM NguoiDung WHERE MaND = ?", nd.getMaND());
        assertThat(row.get("doituong")).isEqualTo("Sinh viên");
        assertThat(row.get("vaitro")).isEqualTo("Người dùng");
        assertThat(row.get("hoten")).isEqualTo("Người Test");
    }

    @Test
    void updateProfile_echoingUnchangedDoiTuongAndVaiTro_isAccepted() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Map<String, Object> body = hoSo("Tên khác", nd.getEmail(), null);
        body.put("doiTuong", "Sinh viên");
        body.put("vaiTro", "Người dùng");
        gui(HttpMethod.PUT, "/api/ho-so", dangNhapLayToken(nd), body).andExpect(status().isOk());
    }

    // ---------- thêm xe ----------

    @Test
    void addVehicle_valid_returns201AndAudits() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        gui(HttpMethod.POST, "/api/ho-so/xe", dangNhapLayToken(nd), xe(" 59a-111.11 ", "DC"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bienSo").value("59A-111.11"))
                .andExpect(jsonPath("$.loaiCongSuDung").value("DC"))
                .andExpect(jsonPath("$.maXe").exists());
        assertThat(soNhatKy(nd, "THEM_XE")).isEqualTo(1);
    }

    @Test
    void addVehicle_fourth_returns409() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        themXe(nd, bienSoMoi(), "AC");
        themXe(nd, bienSoMoi(), "AC");
        themXe(nd, bienSoMoi(), "AC");

        gui(HttpMethod.POST, "/api/ho-so/xe", dangNhapLayToken(nd), xe(bienSoMoi(), "AC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("VUOT_SO_XE"));
    }

    @Test
    void addVehicle_plateOfOtherAccount_returns409() throws Exception {
        NguoiDung a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        NguoiDung b = taoNguoiDung(VaiTro.NGUOI_DUNG);
        themXe(a, "ABC-123.45", "AC");

        gui(HttpMethod.POST, "/api/ho-so/xe", dangNhapLayToken(b), xe("abc-123.45 ", "AC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_BIEN_SO"));
    }

    @Test
    void addVehicle_invalidLoaiCong_returns400() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        gui(HttpMethod.POST, "/api/ho-so/xe", dangNhapLayToken(nd), xe(bienSoMoi(), "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truong.loaiCongSuDung").exists());
    }

    @Test
    void addVehicle_fiveConcurrentRequests_onlyThreeSucceed() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = dangNhapLayToken(nd);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> body = xe(bienSoMoi(), "AC");
            futures.add(pool.submit(() -> {
                start.await();
                return gui(HttpMethod.POST, "/api/ho-so/xe", token, body).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(3);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(2);
        Integer soXe = jdbc.queryForObject("SELECT count(*) FROM Xe WHERE MaND = ?", Integer.class, nd.getMaND());
        assertThat(soXe).isEqualTo(3);
    }

    // ---------- sửa xe ----------

    @Test
    void updateVehicle_own_changesFields() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, "OLD-1", "AC");

        gui(HttpMethod.PUT, "/api/ho-so/xe/" + xe.getMaXe(), dangNhapLayToken(nd), xe("new-1", "DC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bienSo").value("NEW-1"))
                .andExpect(jsonPath("$.loaiCongSuDung").value("DC"));
        assertThat(soNhatKy(nd, "SUA_XE")).isEqualTo(1);
    }

    @Test
    void updateVehicle_keepingSamePlate_isAccepted() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, "SAME-1", "AC");
        gui(HttpMethod.PUT, "/api/ho-so/xe/" + xe.getMaXe(), dangNhapLayToken(nd), xe("same-1", "DC"))
                .andExpect(status().isOk());
    }

    @Test
    void updateVehicle_toPlateOfAnotherVehicle_returns409() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe1 = themXe(nd, "ONE-1", "AC");
        themXe(nd, "TWO-2", "AC");
        gui(HttpMethod.PUT, "/api/ho-so/xe/" + xe1.getMaXe(), dangNhapLayToken(nd), xe("two-2", "AC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("TRUNG_BIEN_SO"));
    }

    @Test
    void updateVehicle_ofAnotherUser_returns404_andLeavesItUnchanged() throws Exception {
        NguoiDung chu = taoNguoiDung(VaiTro.NGUOI_DUNG);
        NguoiDung ke = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(chu, "OWNER-1", "AC");

        gui(HttpMethod.PUT, "/api/ho-so/xe/" + xe.getMaXe(), dangNhapLayToken(ke), xe("HACK-1", "DC"))
                .andExpect(status().isNotFound());
        String bienSo = jdbc.queryForObject("SELECT BienSo FROM Xe WHERE MaXe = ?", String.class, xe.getMaXe());
        assertThat(bienSo).isEqualTo("OWNER-1");
    }

    // ---------- xóa xe ----------

    @Test
    void deleteVehicle_own_removesRowAndPlateCanBeReused() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        String token = dangNhapLayToken(nd);
        Xe xe = themXe(nd, "DEL-1", "AC");

        gui(HttpMethod.DELETE, "/api/ho-so/xe/" + xe.getMaXe(), token, null).andExpect(status().isNoContent());
        Integer n = jdbc.queryForObject("SELECT count(*) FROM Xe WHERE MaXe = ?", Integer.class, xe.getMaXe());
        assertThat(n).isZero();
        assertThat(soNhatKy(nd, "XOA_XE")).isEqualTo(1);

        gui(HttpMethod.POST, "/api/ho-so/xe", token, xe("del-1", "AC")).andExpect(status().isCreated());
    }

    @Test
    void deleteVehicle_ofAnotherUser_returns404_andRowRemains() throws Exception {
        NguoiDung chu = taoNguoiDung(VaiTro.NGUOI_DUNG);
        NguoiDung ke = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(chu, "KEEP-1", "AC");

        gui(HttpMethod.DELETE, "/api/ho-so/xe/" + xe.getMaXe(), dangNhapLayToken(ke), null)
                .andExpect(status().isNotFound());
        Integer n = jdbc.queryForObject("SELECT count(*) FROM Xe WHERE MaXe = ?", Integer.class, xe.getMaXe());
        assertThat(n).isEqualTo(1);
    }

    @Test
    void deleteVehicle_withBooking_returns409() throws Exception {
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, "BOOK-1", "AC");
        jdbc.update("INSERT INTO CongSac (MaCong, LoaiCong, CongSuatDinhMuc, TrangThai) "
                + "VALUES ('TEST-C-HS', 'AC', 7.40, 'Rảnh')");
        jdbc.update("INSERT INTO DatPhien (MaDatPhien, MaND, MaXe, MaCong, NgayDat, GioBatDau, GioKetThuc, "
                + "LoaiCong, TrangThai) VALUES (?, ?, ?, 'TEST-C-HS', now(), now() + interval '1 hour', "
                + "now() + interval '2 hour', 'AC', 'Đã đặt')", "DP-" + uid(), nd.getMaND(), xe.getMaXe());

        gui(HttpMethod.DELETE, "/api/ho-so/xe/" + xe.getMaXe(), dangNhapLayToken(nd), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("XE_DANG_DUOC_DUNG"));
        Integer n = jdbc.queryForObject("SELECT count(*) FROM Xe WHERE MaXe = ?", Integer.class, xe.getMaXe());
        assertThat(n).isEqualTo(1);
    }

    // ---------- tiện ích ----------

    private Map<String, Object> hoSo(String hoTen, String email, String soDienThoai) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hoTen", hoTen);
        m.put("email", email);
        m.put("soDienThoai", soDienThoai);
        return m;
    }

    private Map<String, Object> xe(String bienSo, String loaiCong) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bienSo", bienSo);
        m.put("loaiXe", "Ô tô điện");
        m.put("loaiCongSuDung", loaiCong);
        return m;
    }

    private int soNhatKy(NguoiDung nd, String hanhDong) {
        return jdbc.queryForObject("SELECT count(*) FROM NhatKyHeThong WHERE MaND = ? AND HanhDong = ?",
                Integer.class, nd.getMaND(), hanhDong);
    }

    private static String soDienThoaiMoi() {
        return String.format("0777%06d", (int) (Math.random() * 1_000_000));
    }
}
