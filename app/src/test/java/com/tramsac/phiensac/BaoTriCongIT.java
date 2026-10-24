package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** UC21: khóa/mở cổng để bảo trì (QĐ18) và phân bổ lại đặt chỗ tương lai (quy tắc 10). */
class BaoTriCongIT extends PhienSacBaseIT {

    @Test
    void kyThuatVienKhoaCongRanh_chuyenBaoTriVaGhiNhatKy() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        String token = tokenVaiTro(VaiTro.KY_THUAT_VIEN);
        int truoc = dem("SELECT count(*) FROM NhatKyHeThong WHERE HanhDong = 'KHOA_CONG_BAO_TRI'");

        gui(HttpMethod.POST, "/api/cong/AC-1/khoa", token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("Bảo trì"));

        assertThat(trangThaiCong("AC-1")).isEqualTo("Bảo trì");
        assertThat(dem("SELECT count(*) FROM NhatKyHeThong WHERE HanhDong = 'KHOA_CONG_BAO_TRI'"))
                .isEqualTo(truoc + 1);
    }

    @Test
    void quanTriCungKhoaDuoc() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        gui(HttpMethod.POST, "/api/cong/AC-1/khoa", tokenVaiTro(VaiTro.QUAN_TRI), null).andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Bảo trì");
    }

    @Test
    void khongKhoaCongDangSac_phaiDungPhienTruoc() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Đang sạc", CAU_HINH, 0);
        String token = tokenVaiTro(VaiTro.KY_THUAT_VIEN);

        String body = gui(HttpMethod.POST, "/api/cong/AC-1/khoa", token, null).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("CONG_DANG_BAN");
        assertThat(trangThaiCong("AC-1")).isEqualTo("Đang sạc");
    }

    @Test
    void nguoiDungVaNhanVienTrucKhongDuocKhoaHoacMo() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        gui(HttpMethod.POST, "/api/cong/AC-1/khoa", tokenVaiTro(VaiTro.NGUOI_DUNG), null)
                .andExpect(status().isForbidden());
        gui(HttpMethod.POST, "/api/cong/AC-1/khoa", tokenVaiTro(VaiTro.NHAN_VIEN_TRUC), null)
                .andExpect(status().isForbidden());
        gui(HttpMethod.POST, "/api/cong/AC-1/mo", tokenVaiTro(VaiTro.NGUOI_DUNG), null)
                .andExpect(status().isForbidden());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void moCong_chiKhiThietBiXacNhanAnToan() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Bảo trì", CAU_HINH, 0);
        String token = tokenVaiTro(VaiTro.KY_THUAT_VIEN);

        mqtt.datAnToan(false);
        gui(HttpMethod.POST, "/api/cong/AC-1/mo", token, null).andExpect(status().isConflict());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Bảo trì");

        mqtt.datAnToan(true);
        gui(HttpMethod.POST, "/api/cong/AC-1/mo", token, null).andExpect(status().isOk());
        assertThat(trangThaiCong("AC-1")).isEqualTo("Rảnh");
    }

    @Test
    void khoaCong_datChoTuongLaiChuyenSangCongKhac() throws Exception {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Rảnh", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Rảnh", CAU_HINH, 0);
        NguoiDung nd = taoNguoiDung(VaiTro.NGUOI_DUNG);
        Xe xe = themXe(nd, bienSoMoi(), "AC");
        String ma = taoDatChoTrucTiep(nd, xe, "AC-1", bayGio().plusHours(2), bayGio().plusHours(3),
                "Đã đặt", "Đã đặt");

        gui(HttpMethod.POST, "/api/cong/AC-1/khoa", tokenVaiTro(VaiTro.KY_THUAT_VIEN), null)
                .andExpect(status().isOk());

        assertThat(maCongCuaDatPhien(ma)).isEqualTo("AC-2");
        assertThat(trangThaiDatPhien(ma)).isEqualTo("Đã đặt");
    }
}
