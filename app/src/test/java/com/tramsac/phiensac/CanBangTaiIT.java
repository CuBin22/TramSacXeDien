package com.tramsac.phiensac;

import static org.assertj.core.api.Assertions.assertThat;

import com.tramsac.congsac.CanBangTaiService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** UC20 (kịch bản 9): tổng công suất cấp không vượt ngưỡng; giảm theo tỷ lệ công suất định mức. */
class CanBangTaiIT extends PhienSacBaseIT {

    @Autowired
    CanBangTaiService canBangTaiService;

    private BigDecimal duocCap(String maCong) {
        return jdbc.queryForObject("SELECT CongSuatDuocCap FROM CongSac WHERE MaCong = ?", BigDecimal.class, maCong);
    }

    @Test
    void vuotNguong_giamTheoTyLeVaTongKhongVuotNguong() {
        // Đấu nối 10 kW, ngưỡng 80% → tối đa 8 kW; hai cổng 7,4 kW cùng sạc (tổng định mức 14,8 kW).
        taoCauHinh(CAU_HINH, 10);
        taoCong("AC-1", "AC", "Đang sạc", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Đang sạc", CAU_HINH, 0);

        canBangTaiService.canBangNgoaiGiaoDich(CAU_HINH);

        BigDecimal a = duocCap("AC-1");
        BigDecimal b = duocCap("AC-2");
        assertThat(a).isEqualByComparingTo(b);
        assertThat(a).isLessThan(new BigDecimal("7.40"));
        assertThat(a).isGreaterThanOrEqualTo(new BigDecimal("1.48"));
        assertThat(a.add(b)).isLessThanOrEqualTo(new BigDecimal("8.00"));
        assertThat(a.add(b)).isGreaterThan(new BigDecimal("7.90"));
    }

    @Test
    void khongVuotNguong_moiCongDuocCapDuDinhMuc() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Đang sạc", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Đang sạc", CAU_HINH, 0);

        canBangTaiService.canBangNgoaiGiaoDich(CAU_HINH);

        assertThat(duocCap("AC-1")).isEqualByComparingTo("7.40");
        assertThat(duocCap("AC-2")).isEqualByComparingTo("7.40");
    }

    @Test
    void congKhongSac_khongDuocCapDien() {
        taoCauHinh(CAU_HINH, 100);
        taoCong("AC-1", "AC", "Đang sạc", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Rảnh", CAU_HINH, 0);

        canBangTaiService.canBangNgoaiGiaoDich(CAU_HINH);

        assertThat(duocCap("AC-1")).isEqualByComparingTo("7.40");
        assertThat(duocCap("AC-2")).isEqualByComparingTo("0");
    }

    @Test
    void motCongDungSac_congConLaiDuocNoiLaiDayDu() {
        taoCauHinh(CAU_HINH, 10);
        taoCong("AC-1", "AC", "Đang sạc", CAU_HINH, 0);
        taoCong("AC-2", "AC", "Đang sạc", CAU_HINH, 0);
        canBangTaiService.canBangNgoaiGiaoDich(CAU_HINH);
        assertThat(duocCap("AC-1")).isLessThan(new BigDecimal("7.40"));

        datTrangThaiCong("AC-2", "Rảnh");
        canBangTaiService.canBangNgoaiGiaoDich(CAU_HINH);

        assertThat(duocCap("AC-1")).isEqualByComparingTo("7.40");
        assertThat(duocCap("AC-2")).isEqualByComparingTo("0");
    }
}
