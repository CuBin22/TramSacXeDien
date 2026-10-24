package com.tramsac.baocao;

import com.tramsac.thanhtoan.ThanhToanBaseIT;
import com.tramsac.nguoidung.VaiTro;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BaoCaoIT extends ThanhToanBaseIT {
    @Test void reportUsesRealSchemaAndCountsEachIncidentOnce() throws Exception {
        var c = phienChoThanhToan("AC-1");
        var don = taoDonThanhCong(c, "THANH_CONG");
        webhook(don, "THANH_CONG").andExpect(status().isOk());
        String admin = tokenVaiTro(VaiTro.QUAN_TRI);
        String id = json(gui(HttpMethod.POST, "/api/su-co", c.token(), Map.of("maCong", "AC-1", "loaiSuCo", "Dây sạc", "moTa", "Lỏng giắc")).andReturn()).get("maSuCo").asText();
        var ktv = taoNguoiDung(VaiTro.KY_THUAT_VIEN);
        gui(HttpMethod.PUT, "/api/su-co/"+id+"/giao-viec", admin, Map.of("maKTV", ktv.getMaND())).andExpect(status().isOk());
        gui(HttpMethod.GET, "/api/bao-cao", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.phien.tong_phien").value(1))
                .andExpect(jsonPath("$.phien.phien_hoan_tat").value(1))
                .andExpect(jsonPath("$.thanhToan.doanh_thu").value(10000))
                .andExpect(jsonPath("$.suCo[0].so_luong").value(1));
        String future = LocalDate.now(clock).plusDays(1).toString();
        gui(HttpMethod.GET, "/api/bao-cao?tuNgay="+future+"&denNgay="+future, admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.phien.tong_phien").value(0))
                .andExpect(jsonPath("$.suCo.length()").value(0));
    }

    @Test void reportRejectsNonAdminAndInvalidRange() throws Exception {
        gui(HttpMethod.GET, "/api/bao-cao", null, null).andExpect(status().isUnauthorized());
        gui(HttpMethod.GET, "/api/bao-cao", tokenVaiTro(VaiTro.NGUOI_DUNG), null).andExpect(status().isForbidden());
        gui(HttpMethod.GET, "/api/bao-cao?tuNgay=2026-10-19&denNgay=2026-10-18", tokenVaiTro(VaiTro.QUAN_TRI), null).andExpect(status().isBadRequest());
    }
}
