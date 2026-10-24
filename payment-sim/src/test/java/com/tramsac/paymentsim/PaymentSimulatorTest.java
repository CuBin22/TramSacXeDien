package com.tramsac.paymentsim;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PaymentSimulatorTest {
    private HttpServer callback, server;
    private PaymentSimulator sim;
    private final HttpClient http = HttpClient.newHttpClient();
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private final BlockingQueue<Boolean> signatures = new LinkedBlockingQueue<>();
    private String base;

    @BeforeEach void start() throws Exception {
        callback = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        callback.createContext("/callback", ex -> {
            byte[] bytes = ex.getRequestBody().readAllBytes();
            signatures.add(PaymentSimulator.kyHmac("secret", ex.getRequestHeaders().getFirst("X-Timestamp"), bytes)
                    .equals(ex.getRequestHeaders().getFirst("X-Signature")));
            events.add(PaymentSimulator.JSON.readTree(bytes).get("suKien").asText());
            ex.sendResponseHeaders(200, -1); ex.close();
        });
        callback.start();
        sim = new PaymentSimulator("token", "secret", "http://127.0.0.1:"+callback.getAddress().getPort()+"/callback");
        server = sim.khoiDong(0);
        base = "http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop() { if (server != null) server.stop(0); if (sim != null) sim.dung(); if (callback != null) callback.stop(0); }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base+path)).header("X-Sim-Token", "token")
                .POST(HttpRequest.BodyPublishers.ofString(PaymentSimulator.JSON.writeValueAsString(body), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Map<String, Object> order(String mode) {
        return Map.of("maGiaoDich", "GD-1", "maDon", "DON-1", "soTien", 10000, "kichBan", "THANH_CONG", "cheDoCallback", mode);
    }
    @Test void duplicateAndOutOfOrderCallbacksKeepTerminalStateAndAllowOnlyOneRefund() throws Exception {
        assertThat(post("/sim/orders", order("SAI_THU_TU")).statusCode()).isEqualTo(202);
        assertThat(events.poll(5, TimeUnit.SECONDS)).isEqualTo("THANH_CONG");
        assertThat(events.poll(5, TimeUnit.SECONDS)).isEqualTo("DANG_XU_LY");
        assertThat(signatures).containsOnly(true);
        var refund = Map.of("khoaIdempotency", "key-1", "maGiaoDich", "GD-1", "soTien", 1000);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(post("/sim/refunds", refund).statusCode()).isEqualTo(200));
        assertThat(post("/sim/refunds", refund).body()).contains("\"idempotentReplay\":true");
        assertThat(post("/sim/refunds", Map.of("khoaIdempotency", "key-2", "maGiaoDich", "GD-1", "soTien", 1000)).statusCode()).isEqualTo(409);
    }
    @Test void replayedOrderIsIdempotentAndChangedContentConflicts() throws Exception {
        assertThat(post("/sim/orders", order("BINH_THUONG")).statusCode()).isEqualTo(202);
        assertThat(post("/sim/orders", order("BINH_THUONG")).statusCode()).isEqualTo(200);
        assertThat(post("/sim/orders", order("TRUNG")).statusCode()).isEqualTo(409);
        assertThat(events.poll(5, TimeUnit.SECONDS)).isEqualTo("THANH_CONG");
        assertThat(events.poll(400, TimeUnit.MILLISECONDS)).isNull();
    }
    @Test void unknownTransactionCannotBeRefunded() throws Exception {
        assertThat(post("/sim/refunds", Map.of("khoaIdempotency", "key", "maGiaoDich", "missing", "soTien", 1000)).statusCode()).isEqualTo(409);
    }
    @Test void duplicateModeSendsExactlyThreeSignedCallbacks() throws Exception {
        assertThat(post("/sim/orders", order("TRUNG")).statusCode()).isEqualTo(202);
        for (int i=0; i<3; i++) {
            assertThat(events.poll(5, TimeUnit.SECONDS)).isEqualTo("THANH_CONG");
            assertThat(signatures.poll(1, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(events.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }
}
