package com.tramsac.thanhtoan;

import java.math.BigDecimal;

public record WebhookThanhToanEvent(String maGiaoDich, String maDon, String suKien, BigDecimal soTien) { }
