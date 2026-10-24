package com.tramsac.phiensac;

import java.time.LocalDateTime;

public record CanhBaoChiemCongResponse(String maCanhBao, String maPhien, int lanCanhBao,
                                      LocalDateTime thoiDiem, String nguoiNhan, String phanHoi) {
    static CanhBaoChiemCongResponse tu(CanhBaoChiemCong c) {
        return new CanhBaoChiemCongResponse(c.getMaCanhBao(), c.getMaPhien(), c.getLanCanhBao(),
                c.getThoiDiem(), c.getNguoiNhan(), c.getPhanHoi());
    }
}
