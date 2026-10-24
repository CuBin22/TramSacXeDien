package com.tramsac.thanhtoan;

import java.math.BigDecimal;

/** Port nghiệp vụ thanh toán; các gateway giả lập/nhà cung cấp cài đặt interface này. */
public interface CongThanhToan {
    void taoDon(DonThanhToan don, GiaoDichThanhToan giaoDich);
    void hoanTien(String khoaIdempotency, String maGiaoDich, BigDecimal soTien);
    void guiLaiCallback(String maGiaoDich);
}
