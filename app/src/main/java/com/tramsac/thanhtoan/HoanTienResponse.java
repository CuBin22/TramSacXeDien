package com.tramsac.thanhtoan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HoanTienResponse(String maHoanTien, String maGiaoDich, BigDecimal soTienHoan,
                               String lyDo, LocalDateTime ngayHoan, boolean idempotentReplay) {
    static HoanTienResponse tu(HoanTien refund, boolean replay) {
        return new HoanTienResponse(refund.getMaHoanTien(), refund.getMaGiaoDich(), refund.getSoTienHoan(),
                refund.getLyDo(), refund.getNgayHoan(), replay);
    }
}
