package com.tramsac.thanhtoan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.common.ApiException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Adapter HTTP: app chỉ gọi payment-sim qua cổng này, không phụ thuộc vào module simulator. */
@Component
public class PaymentSimGateway implements CongThanhToan {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiToken;
    private final String webhookUrl;

    public PaymentSimGateway(ObjectMapper objectMapper,
                             @Value("${tramsac.payment.sim-url}") String baseUrl,
                             @Value("${tramsac.payment.sim-api-token}") String apiToken,
                             @Value("${tramsac.payment.webhook-url}") String webhookUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiToken = apiToken;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void taoDon(DonThanhToan don, GiaoDichThanhToan giaoDich) {
        gui("POST", "/sim/orders", Map.of(
                "maGiaoDich", giaoDich.getMaGiaoDich(),
                "maDon", don.getMaDon(),
                "soTien", don.getSoTien(),
                "phuongThuc", don.getPhuongThuc(),
                "kichBan", giaoDich.getKichBan(),
                "cheDoCallback", giaoDich.getCheDoCallback(),
                "ketQuaCuoi", giaoDich.getKetQuaCuoi(),
                "doTreGiay", giaoDich.getDoTreGiay(),
                "callbackUrl", webhookUrl));
    }

    @Override
    public void hoanTien(String khoa, String maGiaoDich, BigDecimal soTien) {
        gui("POST", "/sim/refunds", Map.of(
                "khoaIdempotency", khoa,
                "maGiaoDich", maGiaoDich,
                "soTien", soTien));
    }

    @Override
    public void guiLaiCallback(String maGiaoDich) {
        gui("POST", "/sim/orders/" + maGiaoDich + "/gui-lai", Map.of());
    }

    private void gui(String method, String path, Object body) {
        if (apiToken == null || apiToken.isBlank()) {
            throw unavailable("PAYMENT_SIM_API_TOKEN chưa được cấu hình.");
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Sim-Token", apiToken)
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 409) {
                    throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_SIM_XUNG_DOT",
                            "Cổng thanh toán báo giao dịch đã xử lý hoặc xung đột idempotency.");
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_SIM_TU_CHOI",
                        "Cổng thanh toán giả lập từ chối yêu cầu.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Kết nối tới payment-sim bị gián đoạn.");
        } catch (IOException | IllegalArgumentException e) {
            throw unavailable("Không kết nối được payment-sim.");
        }
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_SIM_KHONG_KHA_DUNG", message);
    }
}
