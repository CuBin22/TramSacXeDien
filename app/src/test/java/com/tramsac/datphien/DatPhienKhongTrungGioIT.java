package com.tramsac.datphien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tramsac.CongSacIT;
import com.tramsac.nguoidung.NguoiDung;
import com.tramsac.nguoidung.VaiTro;
import com.tramsac.nguoidung.Xe;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Ràng buộc loại trừ ở CSDL (V2): lớp phòng vệ cuối, kiểm tra bằng cách chèn thẳng, bỏ qua mọi logic ứng dụng. */
class DatPhienKhongTrungGioIT extends CongSacIT {

    private NguoiDung a;
    private NguoiDung b;
    private Xe xa;
    private Xe xb;
    private LocalDateTime t;

    @BeforeEach
    void dung() {
        taoCong("C-1", "AC");
        taoCong("C-2", "AC");
        a = taoNguoiDung(VaiTro.NGUOI_DUNG);
        b = taoNguoiDung(VaiTro.NGUOI_DUNG);
        xa = themXe(a, bienSoMoi(), "AC");
        xb = themXe(b, bienSoMoi(), "AC");
        t = bayGio().plusHours(2);
    }

    @Test
    void sameCong_overlappingActiveBookings_areRejected() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");
        assertThatThrownBy(() -> taoDatChoTrucTiep(b, xb, "C-1", t.plusMinutes(30), t.plusMinutes(90),
                "Đã đặt", "Đã đặt")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCong_backToBackBookings_areAllowed() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");
        assertThatCode(() -> taoDatChoTrucTiep(b, xb, "C-1", t.plusHours(1), t.plusHours(2), "Đã đặt", "Đã đặt"))
                .doesNotThrowAnyException();
    }

    @Test
    void differentCong_sameWindow_isAllowed() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");
        assertThatCode(() -> taoDatChoTrucTiep(b, xb, "C-2", t, t.plusHours(1), "Đã đặt", "Đã đặt"))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelledOrExpiredBooking_doesNotBlockTheWindow() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Hủy", "Hủy");
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Quá hạn không đến", "Hủy");
        assertThatCode(() -> taoDatChoTrucTiep(b, xb, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt"))
                .doesNotThrowAnyException();
    }

    @Test
    void chargingBooking_alsoBlocksTheWindow() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đang sạc", "Đang sạc");
        assertThatThrownBy(() -> taoDatChoTrucTiep(b, xb, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUser_overlappingOnDifferentCong_isRejected() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");
        assertThatThrownBy(() -> taoDatChoTrucTiep(a, xa, "C-2", t.plusMinutes(30), t.plusMinutes(90),
                "Đã đặt", "Đã đặt")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUser_backToBack_isAllowed() {
        taoDatChoTrucTiep(a, xa, "C-1", t, t.plusHours(1), "Đã đặt", "Đã đặt");
        assertThatCode(() -> taoDatChoTrucTiep(a, xa, "C-2", t.plusHours(1), t.plusHours(2), "Đã đặt", "Đã đặt"))
                .doesNotThrowAnyException();
        Integer n = jdbc.queryForObject("SELECT count(*) FROM DatPhien WHERE MaND = ?", Integer.class, a.getMaND());
        assertThat(n).isEqualTo(2);
    }
}
