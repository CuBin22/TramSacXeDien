package com.tramsac.thanhtoan;

import java.math.BigDecimal;
import java.util.List;

/** UC22 bước 1: tổng phí và chi tiết cách tính (khung giờ, điện năng, đơn giá, thành tiền) của một phiên. */
public record HoaDonPhienResponse(String maPhien, String trangThaiPhien, BigDecimal tongPhi, List<DongPhi> chiTiet) {

    public record DongPhi(String khungGio, BigDecimal dienNangKWh, BigDecimal donGia, BigDecimal thanhTien) { }
}
