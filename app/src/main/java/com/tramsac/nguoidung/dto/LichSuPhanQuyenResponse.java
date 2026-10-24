package com.tramsac.nguoidung.dto;

import com.tramsac.nguoidung.LichSuPhanQuyen;
import java.time.LocalDateTime;

public record LichSuPhanQuyenResponse(LocalDateTime thoiDiem, String vaiTroCu, String vaiTroMoi,
                                      String nguoiThucHien) {

    public static LichSuPhanQuyenResponse tu(LichSuPhanQuyen ls) {
        return new LichSuPhanQuyenResponse(ls.getThoiDiem(), ls.getVaiTroCu(), ls.getVaiTroMoi(),
                ls.getNguoiThucHien());
    }
}
