package com.tramsac.thanhtoan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tramsac.FakeMqttConfig;
import com.tramsac.TestClockConfig;
import com.tramsac.phiensac.PhienSacBaseIT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Cơ sở cho test giai đoạn 4 (UC22–UC25). Cổng thanh toán là FakeCongThanhToan; test đóng vai cổng bằng cách gọi
 * webhook thật của app, ký HMAC-SHA256 trên "timestamp.body" giống payment-sim.
 */
@Import({TestClockConfig.class, FakeMqttConfig.class, FakePaymentConfig.class})
public abstract class ThanhToanBaseIT extends PhienSacBaseIT {

    protected static final String WEBHOOK_SECRET = "test-webhook-secret-test-webhook-secret-1234";
    protected static final String WEBHOOK_URL = "/api/webhook/thanh-toan";

    @DynamicPropertySource
    static void thanhToanProperties(DynamicPropertyRegistry registry) {
        registry.add("tramsac.payment.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("tramsac.payment.sim-api-token", () -> "test-sim-token-test-sim-token-1234");
    }

    @Autowired
    protected FakeCongThanhToan cong;

    @BeforeEach
    void datLaiCongThanhToanGia() {
        cong.datLai();
    }

    // ---------- dựng dữ liệu ----------

    /** Đưa một phiên tới Chờ thanh toán với phí 10 kWh × 1.000 đ = 10.000 đ (biểu giá mặc định, sinh viên, AC). */
    protected Canh phienChoThanhToan(String maCong) throws Exception {
        Canh c = dungPhien(maCong);
        return chuyenSangChoThanhToan(c);
    }

    protected Canh chuyenSangChoThanhToan(Canh c) throws Exception {
        batDauThanhCong(c);
        clock.advance(Duration.ofMinutes(10)); // < 30 phút để token chưa hết hạn
        String maDung = maLenh(dung(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        ack(c.maCong(), maDung, "OK", 10.0);
        assertThat(trangThaiPhienTheoMa(c.maPhien())).isEqualTo("Chờ thanh toán");
        return c;
    }

    // ---------- gọi API ----------

    protected static Map<String, Object> bodyDon(String maPhien, String phuongThuc, String kichBan) {
        Map<String, Object> m = new HashMap<>();
        m.put("maPhien", maPhien);
        m.put("phuongThuc", phuongThuc);
        m.put("kichBan", kichBan);
        m.put("cheDoCallback", "BINH_THUONG");
        return m;
    }

    protected ResultActions taoDon(String token, String khoa, Map<String, Object> body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/thanh-toan/don")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", khoa)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body)));
    }

    /** Tạo đơn thành công (202) và trả về JSON phản hồi. */
    protected JsonNode taoDonThanhCong(Canh c, String kichBan) throws Exception {
        return json(taoDon(c.token(), khoaMoi(), bodyDon(c.maPhien(), "THE_THU", kichBan))
                .andExpect(status().isAccepted()).andReturn());
    }

    protected ResultActions hoanTien(String token, String khoa, String maGiaoDich, Object soTien, String lyDo)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("maGiaoDich", maGiaoDich);
        body.put("soTienHoan", soTien);
        body.put("lyDo", lyDo);
        return mvc.perform(MockMvcRequestBuilders.post("/api/hoan-tien")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", khoa)
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body)));
    }

    // ---------- đóng vai cổng thanh toán: webhook ký HMAC ----------

    protected static String ky(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected byte[] bodyWebhook(String maGiaoDich, String maDon, String suKien, Object soTien) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("maGiaoDich", maGiaoDich);
        m.put("maDon", maDon);
        m.put("suKien", suKien);
        m.put("soTien", soTien);
        return om.writeValueAsBytes(m);
    }

    /** Webhook hợp lệ: ký bằng khóa đúng, timestamp theo đồng hồ test. */
    protected ResultActions webhook(JsonNode don, String suKien) throws Exception {
        return webhook(don.get("maGiaoDich").asText(), don.get("maDon").asText(), suKien,
                don.get("soTien").decimalValue());
    }

    protected ResultActions webhook(String maGiaoDich, String maDon, String suKien, Object soTien) throws Exception {
        byte[] body = bodyWebhook(maGiaoDich, maDon, suKien, soTien);
        String ts = Long.toString(clock.instant().getEpochSecond());
        return guiWebhook(body, ts, ky(WEBHOOK_SECRET, ts, body));
    }

    protected ResultActions guiWebhook(byte[] body, String timestamp, String chuKy) throws Exception {
        var rb = MockMvcRequestBuilders.post(WEBHOOK_URL).contentType(MediaType.APPLICATION_JSON).content(body);
        if (timestamp != null) rb.header("X-Timestamp", timestamp);
        if (chuKy != null) rb.header("X-Signature", chuKy);
        return mvc.perform(rb);
    }

    protected String ketQuaWebhook(ResultActions r) throws Exception {
        return json(r.andExpect(status().isOk()).andReturn()).get("ketQua").asText();
    }

    // ---------- truy vấn kiểm tra ----------

    protected String trangThaiDon(String maDon) {
        return jdbc.queryForObject("SELECT TrangThai FROM DonThanhToan WHERE MaDon = ?", String.class, maDon);
    }

    protected String ketQuaGiaoDich(String maGiaoDich) {
        return jdbc.queryForObject("SELECT KetQua FROM GiaoDichThanhToan WHERE MaGiaoDich = ?", String.class,
                maGiaoDich);
    }

    protected int soCallback(String maGiaoDich, String ketQuaXuLy) {
        return dem("SELECT count(*) FROM CallbackLog WHERE MaGiaoDich = ? AND KetQuaXuLy = ?", maGiaoDich,
                ketQuaXuLy);
    }

    protected int soLanHoanTat(String maPhien) {
        return dem("SELECT count(*) FROM LichSuTrangThaiPhien WHERE MaPhien = ? AND TrangThaiMoi = 'Hoàn tất'",
                maPhien);
    }
}
