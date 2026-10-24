package com.tramsac.suco;

import com.tramsac.phiensac.PhienSacBaseIT;
import com.tramsac.nguoidung.VaiTro;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SuCoIT extends PhienSacBaseIT {
    private Map<String, Object> report() { return Map.of("maCong", "AC-1", "loaiSuCo", "Không nhận xe", "moTa", "Đã cắm dây nhưng không sạc"); }

    @Test void staffReportTakesPortOutOfServiceBeforeAnotherUserCanBook() throws Exception {
        taoCong("AC-1", "AC");
        var nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        var xe = themXe(nd, bienSoMoi(), "AC");
        gui(HttpMethod.POST, "/api/su-co", tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), report())
                .andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
        assertThat(dem("SELECT count(*) FROM SuCo WHERE MaCong='AC-1'")).isEqualTo(1);
        var start = bayGio().plusHours(2);
        datPhien(dangNhapLayToken(nd), xe.getBienSo(), "AC", start, start.plusHours(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ma").value("HET_CONG"));
    }

    @Test void userReportDoesNotLetAnUnverifiedReporterDisableThePort() throws Exception {
        taoCong("AC-1", "AC");
        gui(HttpMethod.POST, "/api/su-co", tokenVaiTro(VaiTro.NGUOI_DUNG), report())
                .andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test void staffReportCancelsAnActiveSessionAndBlocksFurtherUse() throws Exception {
        Canh c = dungPhien("AC-1");
        batDauThanhCong(c);
        gui(HttpMethod.POST, "/api/su-co", tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), report())
                .andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Hủy");
    }

    @Test void staffReportMovesAnUpcomingBookingToAnAvailablePort() throws Exception {
        taoCong("AC-1", "AC");
        taoCong("AC-2", "AC");
        var nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        var xe = themXe(nd, bienSoMoi(), "AC");
        var start = bayGio().plusHours(2);
        String maDat = taoDatChoTrucTiep(nd, xe, "AC-1", start, start.plusHours(1), "Đã đặt", "Đã đặt");
        gui(HttpMethod.POST, "/api/su-co", tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), report())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT MaCong FROM DatPhien WHERE MaDatPhien=?", String.class, maDat))
                .isEqualTo("AC-2");
    }

    @Test void reportAssignmentResolutionHistoryAndOwnership() throws Exception {
        taoCong("AC-1", "AC");
        String user = tokenVaiTro(VaiTro.NGUOI_DUNG);
        String other = tokenVaiTro(VaiTro.NGUOI_DUNG);
        var ktv = taoNguoiDung(VaiTro.KY_THUAT_VIEN);
        String technician = dangNhapLayToken(ktv);
        String id = json(gui(HttpMethod.POST, "/api/su-co", user, report()).andExpect(status().isOk()).andReturn()).get("maSuCo").asText();
        gui(HttpMethod.GET, "/api/su-co", other, null).andExpect(jsonPath("$.length()").value(0));
        gui(HttpMethod.GET, "/api/su-co/"+id+"/lich-su", other, null).andExpect(status().isForbidden());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/giao-viec", user, Map.of("maKTV", ktv.getMaND())).andExpect(status().isForbidden());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/giao-viec", tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), Map.of("maKTV", ktv.getMaND())).andExpect(status().isOk());
        var resolved = Map.of("trangThai", "Đã xử lý", "nguyenNhan", "Giắc lỏng", "cachXuLy", "Gắn lại giắc", "ghiChu", "Đã kiểm tra");
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", tokenVaiTro(VaiTro.KY_THUAT_VIEN), resolved).andExpect(status().isForbidden());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", technician, resolved).andExpect(status().isOk());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", technician, Map.of("trangThai", "Đã đóng", "nguyenNhan", "Giắc lỏng", "cachXuLy", "Gắn lại giắc")).andExpect(status().isOk());
        gui(HttpMethod.GET, "/api/su-co/"+id+"/lich-su", user, null).andExpect(jsonPath("$.length()").value(4));
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", technician, resolved).andExpect(status().isConflict());
    }

    @Test void validationAndLifecycleRejectNullOversizeAndSkippedSteps() throws Exception {
        taoCong("AC-1", "AC");
        String admin = tokenVaiTro(VaiTro.QUAN_TRI);
        gui(HttpMethod.POST, "/api/su-co", admin, Map.of()).andExpect(status().isBadRequest());
        gui(HttpMethod.POST, "/api/su-co", admin, Map.of("maCong", "AC-1", "loaiSuCo", "X", "moTa", "x".repeat(501))).andExpect(status().isBadRequest());
        String id = json(gui(HttpMethod.POST, "/api/su-co", admin, report()).andReturn()).get("maSuCo").asText();
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/giao-viec", admin, Map.of()).andExpect(status().isBadRequest());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", admin, Map.of()).andExpect(status().isBadRequest());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", admin, Map.of("trangThai", "Đã đóng")).andExpect(status().isConflict());
        assertThat(dem("SELECT count(*) FROM LichSuSuCo WHERE MaSuCo=?", id)).isEqualTo(1);
    }

    @Test void faultCannotBeClosedWithoutDeviceSafetyConfirmation() throws Exception {
        taoCong("AC-1", "AC", "Lỗi", null, 0);
        String admin = tokenVaiTro(VaiTro.QUAN_TRI);
        String id = json(gui(HttpMethod.POST, "/api/su-co", admin, report()).andReturn()).get("maSuCo").asText();
        var ktv = taoNguoiDung(VaiTro.KY_THUAT_VIEN);
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/giao-viec", admin, Map.of("maKTV", ktv.getMaND())).andExpect(status().isOk());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", admin, Map.of("trangThai", "Đã xử lý", "nguyenNhan", "Dây lỗi", "cachXuLy", "Thay dây")).andExpect(status().isOk());
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/trang-thai", admin, Map.of("trangThai", "Đã đóng", "nguyenNhan", "Dây lỗi", "cachXuLy", "Thay dây")).andExpect(status().isConflict());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Lỗi");
    }
}
