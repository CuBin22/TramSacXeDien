package com.tramsac.paymentsim;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.telemetry.TraceContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cổng thanh toán giả lập (actor phụ của use case Thanh toán, UC22–UC25). Không kết nối ngân hàng hay ví thật và không
 * lưu số thẻ. Dữ liệu giữ trong bộ nhớ.
 *
 * <p>API (header {@code X-Sim-Token}, trừ {@code /health}): {@code POST /sim/orders}, {@code GET /sim/orders/{maGiaoDich}},
 * {@code POST /sim/orders/{maGiaoDich}/gui-lai}, {@code POST /sim/refunds}.
 *
 * <p>Webhook gửi về app ký HMAC-SHA256 trên {@code timestamp + "." + body}, header {@code X-Timestamp} (giây epoch) và
 * {@code X-Signature} (hex). Tên sự kiện trùng tên trạng thái của app: CHO, DANG_XU_LY, THANH_CONG, THAT_BAI, HET_HAN.
 */
public final class PaymentSimulator {

    static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static final Set<String> KICH_BAN = Set.of("THANH_CONG", "THAT_BAI", "KHONG_DU_SO_DU", "HET_HAN", "CHAM_PHAN_HOI");
    static final Set<String> CHE_DO = Set.of("BINH_THUONG", "TRUNG", "SAI_THU_TU", "SAI_CHU_KY");
    static final Set<String> KET_QUA_CUOI = Set.of("THANH_CONG", "THAT_BAI");
    private static final int SO_LAN_THU_LAI = 3;

    /** Một đơn do app tạo; sự kiện đã gửi được ghi lại để tra cứu và gửi lại. */
    static final class Order {
        final String maGiaoDich;
        final String maDon;
        final BigDecimal soTien;
        final String phuongThuc;
        final String kichBan;
        final String cheDoCallback;
        final String ketQuaCuoi;
        final int doTreGiay;
        final Map<String, String> traceHeaders = TraceContext.headers();
        volatile String trangThai = "CHO";
        final List<String> suKienDaGui = new ArrayList<>();

        Order(String maGiaoDich, String maDon, BigDecimal soTien, String phuongThuc, String kichBan,
              String cheDoCallback, String ketQuaCuoi, int doTreGiay) {
            this.maGiaoDich = maGiaoDich; this.maDon = maDon; this.soTien = soTien; this.phuongThuc = phuongThuc;
            this.kichBan = kichBan; this.cheDoCallback = cheDoCallback; this.ketQuaCuoi = ketQuaCuoi;
            this.doTreGiay = doTreGiay;
        }

        synchronized void ghiNhan(String suKien) {
            suKienDaGui.add(suKien);
            // A delayed DANG_XU_LY callback must not undo a final result (including refund eligibility).
            if (!Set.of("THANH_CONG", "THAT_BAI", "HET_HAN").contains(trangThai)) trangThai = suKien;
        }

        synchronized List<String> baoCao() { return List.copyOf(suKienDaGui); }
    }

    /** Một lần hoàn tiền đã nhận, dùng để trả lại đúng kết quả khi gửi lại cùng khóa idempotency. */
    record Refund(String maGiaoDich, BigDecimal soTien) { }

    private final String apiToken;
    private final String webhookSecret;
    private final String webhookUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(4);
    private final java.util.concurrent.ExecutorService httpExecutor = Executors.newCachedThreadPool();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Refund> refunds = new ConcurrentHashMap<>();
    private final Map<String, String> hoanTienTheoGiaoDich = new ConcurrentHashMap<>();

    PaymentSimulator(String apiToken, String webhookSecret, String webhookUrl) {
        this.apiToken = apiToken;
        this.webhookSecret = webhookSecret;
        this.webhookUrl = webhookUrl;
    }

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String token = env("PAYMENT_SIM_API_TOKEN", "");
        String secret = env("PAYMENT_WEBHOOK_SECRET", "");
        String url = env("PAYMENT_WEBHOOK_URL", "http://localhost:8080/api/webhook/thanh-toan");
        int port = Integer.parseInt(env("PAYMENT_SIM_PORT", "8091"));
        if (token.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("Cần đặt PAYMENT_SIM_API_TOKEN và PAYMENT_WEBHOOK_SECRET.");
        }
        PaymentSimulator sim = new PaymentSimulator(token, secret, url);
        HttpServer server = sim.khoiDong(port);
        System.out.println("payment-sim [CHẾ ĐỘ GIẢ LẬP] lắng nghe cổng " + server.getAddress().getPort()
                + ", webhook -> " + url);
    }

    HttpServer khoiDong(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> gui(ex, 200, Map.of("status", "UP", "cheDo", "GIA_LAP")));
        server.createContext("/sim/orders", ex -> {
            String parent = ex.getRequestHeaders().getFirst("traceparent");
            try (var trace = TraceContext.receive("payment.nhanDon", Map.of("traceparent", parent == null ? "" : parent))) {
                xuLyOrders(ex);
            }
        });
        server.createContext("/sim/refunds", this::xuLyRefunds);
        server.setExecutor(httpExecutor);
        server.start();
        return server;
    }

    void dung() { timer.shutdownNow(); httpExecutor.shutdownNow(); }

    // ---------- /sim/orders ----------

    private void xuLyOrders(HttpExchange ex) throws IOException {
        try {
            if (!duocPhep(ex)) return;
            String[] phan = ex.getRequestURI().getPath().split("/");   // "", "sim", "orders", [ma], [gui-lai]
            String method = ex.getRequestMethod();
            if (phan.length == 3 && method.equals("POST")) {
                taoOrder(ex);
            } else if (phan.length == 4 && method.equals("GET")) {
                xemOrder(ex, phan[3]);
            } else if (phan.length == 5 && phan[4].equals("gui-lai") && method.equals("POST")) {
                guiLai(ex, phan[3]);
            } else {
                gui(ex, 404, loi("KHONG_TIM_THAY", "Đường dẫn không tồn tại."));
            }
        } catch (RuntimeException e) {
            gui(ex, 500, loi("LOI_NOI_BO", "Lỗi xử lý trong payment-sim."));
        }
    }

    private void taoOrder(HttpExchange ex) throws IOException {
        JsonNode body = doc(ex);
        if (body == null) return;
        String maGiaoDich = text(body, "maGiaoDich");
        String maDon = text(body, "maDon");
        String kichBan = text(body, "kichBan");
        String cheDo = text(body, "cheDoCallback");
        String ketQuaCuoi = text(body, "ketQuaCuoi");
        JsonNode tien = body.get("soTien");
        if (maGiaoDich == null || maDon == null || tien == null || !tien.isNumber() || kichBan == null) {
            gui(ex, 400, loi("THIEU_TRUONG", "Thiếu maGiaoDich, maDon, soTien hoặc kichBan."));
            return;
        }
        if (cheDo == null) cheDo = "BINH_THUONG";
        if (ketQuaCuoi == null) ketQuaCuoi = "THANH_CONG";
        int doTre = body.hasNonNull("doTreGiay") ? body.get("doTreGiay").asInt(0) : 0;
        if (!KICH_BAN.contains(kichBan) || !CHE_DO.contains(cheDo) || !KET_QUA_CUOI.contains(ketQuaCuoi)
                || doTre < 0 || doTre > 60 || tien.decimalValue().signum() < 0) {
            gui(ex, 400, loi("GIA_TRI_KHONG_HOP_LE", "Kịch bản, chế độ callback, kết quả cuối hoặc độ trễ không hợp lệ."));
            return;
        }
        Order moi = new Order(maGiaoDich, maDon, tien.decimalValue(), text(body, "phuongThuc"), kichBan, cheDo,
                ketQuaCuoi, doTre);
        Order cu = orders.putIfAbsent(maGiaoDich, moi);
        if (cu != null) {
            if (!cu.maDon.equals(moi.maDon) || cu.soTien.compareTo(moi.soTien) != 0
                    || !java.util.Objects.equals(cu.phuongThuc, moi.phuongThuc) || !cu.kichBan.equals(moi.kichBan)
                    || !cu.cheDoCallback.equals(moi.cheDoCallback) || !cu.ketQuaCuoi.equals(moi.ketQuaCuoi) || cu.doTreGiay != moi.doTreGiay) {
                gui(ex, 409, loi("IDEMPOTENCY_XUNG_DOT", "Mã giao dịch đã được dùng với nội dung khác."));
                return;
            }
            // Gửi lại cùng đơn (app thử lại sau lỗi tạm thời): trả trạng thái hiện có, không phát lại callback.
            gui(ex, 200, tomTat(cu, true));
            return;
        }
        lenhLich(moi);
        gui(ex, 202, tomTat(moi, false));
    }

    private void xemOrder(HttpExchange ex, String maGiaoDich) throws IOException {
        Order o = orders.get(maGiaoDich);
        if (o == null) {
            gui(ex, 404, loi("KHONG_TIM_THAY", "Không có giao dịch này trong payment-sim."));
            return;
        }
        Map<String, Object> kq = tomTat(o, false);
        kq.put("suKienDaGui", o.baoCao());
        gui(ex, 200, kq);
    }

    private void guiLai(HttpExchange ex, String maGiaoDich) throws IOException {
        Order o = orders.get(maGiaoDich);
        if (o == null) {
            gui(ex, 404, loi("KHONG_TIM_THAY", "Không có giao dịch này trong payment-sim."));
            return;
        }
        List<String> daGui = o.baoCao();
        if (daGui.isEmpty()) {
            gui(ex, 409, loi("CHUA_CO_SU_KIEN", "Giao dịch chưa phát sinh sự kiện nào để gửi lại."));
            return;
        }
        String suKien = daGui.get(daGui.size() - 1);
        timer.schedule(() -> guiWebhook(o, suKien, false), 100, TimeUnit.MILLISECONDS);
        gui(ex, 202, Map.of("maGiaoDich", maGiaoDich, "guiLai", suKien));
    }

    /** Lên lịch các sự kiện theo kịch bản và chế độ callback (thiết kế mục 8.2). */
    private void lenhLich(Order o) {
        List<String> chuoi = new ArrayList<>();
        List<Long> doTre = new ArrayList<>();
        long ngan = 200;
        switch (o.kichBan) {
            case "THANH_CONG" -> { chuoi.add("THANH_CONG"); doTre.add(ngan); }
            case "THAT_BAI", "KHONG_DU_SO_DU" -> { chuoi.add("THAT_BAI"); doTre.add(ngan); }
            case "HET_HAN" -> { chuoi.add("HET_HAN"); doTre.add(Math.max(1, o.doTreGiay) * 1000L); }
            case "CHAM_PHAN_HOI" -> {
                chuoi.add("DANG_XU_LY"); doTre.add(ngan);
                chuoi.add(o.ketQuaCuoi); doTre.add(Math.max(1, o.doTreGiay) * 1000L);
            }
            default -> { }
        }
        if (o.cheDoCallback.equals("SAI_THU_TU")) {
            // Kết quả cuối gửi trước, trạng thái xử lý gửi sau.
            String cuoi = chuoi.get(chuoi.size() - 1);
            chuoi = new ArrayList<>(List.of(cuoi, "DANG_XU_LY"));
            doTre = new ArrayList<>(List.of(ngan, ngan + 500));
        }
        boolean saiChuKy = o.cheDoCallback.equals("SAI_CHU_KY");
        int lan = o.cheDoCallback.equals("TRUNG") ? 3 : 1;
        for (int i = 0; i < chuoi.size(); i++) {
            String suKien = chuoi.get(i);
            long troMs = doTre.get(i);
            for (int k = 0; k < lan; k++) {
                timer.schedule(() -> guiWebhook(o, suKien, saiChuKy), troMs + k * 100L, TimeUnit.MILLISECONDS);
            }
        }
    }

    // ---------- webhook ----------

    private void guiWebhook(Order o, String suKien, boolean saiChuKy) {
        try (var trace = TraceContext.receive("payment.guiCallback", o.traceHeaders)) {
            guiWebhookTrongTrace(o, suKien, saiChuKy);
        }
    }

    private void guiWebhookTrongTrace(Order o, String suKien, boolean saiChuKy) {
        String payload;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("maGiaoDich", o.maGiaoDich);
            body.put("maDon", o.maDon);
            body.put("suKien", suKien);
            body.put("soTien", o.soTien);
            payload = JSON.writeValueAsString(body);
        } catch (IOException e) {
            return;
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        for (int lan = 1; lan <= SO_LAN_THU_LAI; lan++) {
            String ts = Long.toString(Instant.now().getEpochSecond());
            String khoa = saiChuKy ? webhookSecret + "-sai" : webhookSecret;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("X-Timestamp", ts)
                        .header("X-Signature", kyHmac(khoa, ts, bytes))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
                HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
                o.ghiNhan(suKien);
                // Lỗi 5xx: app yêu cầu gửi lại (UC24 bước 5a). Lỗi 4xx (ví dụ sai chữ ký) thì dừng.
                if (res.statusCode() < 500) return;
            } catch (IOException e) {
                // app chưa sẵn sàng: thử lại
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                Thread.sleep(1000L * lan);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static String kyHmac(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được chữ ký.", e);
        }
    }

    // ---------- /sim/refunds ----------

    private synchronized void xuLyRefunds(HttpExchange ex) throws IOException {
        try {
            if (!duocPhep(ex)) return;
            if (!ex.getRequestMethod().equals("POST")) {
                gui(ex, 404, loi("KHONG_TIM_THAY", "Đường dẫn không tồn tại."));
                return;
            }
            JsonNode body = doc(ex);
            if (body == null) return;
            String khoa = text(body, "khoaIdempotency");
            String maGiaoDich = text(body, "maGiaoDich");
            JsonNode tien = body.get("soTien");
            if (khoa == null || maGiaoDich == null || tien == null || !tien.isNumber()
                    || tien.decimalValue().signum() <= 0) {
                gui(ex, 400, loi("THIEU_TRUONG", "Thiếu khoaIdempotency, maGiaoDich hoặc soTien > 0."));
                return;
            }
            Refund moi = new Refund(maGiaoDich, tien.decimalValue());
            Refund cu = refunds.get(khoa);
            if (cu != null) {
                if (cu.maGiaoDich().equals(moi.maGiaoDich()) && cu.soTien().compareTo(moi.soTien()) == 0) {
                    gui(ex, 200, Map.of("trangThai", "DA_HOAN", "idempotentReplay", true));
                } else {
                    gui(ex, 409, loi("IDEMPOTENCY_XUNG_DOT", "Khóa idempotency đã dùng với dữ liệu khác."));
                }
                return;
            }
            Order o = orders.get(maGiaoDich);
            if (o == null || !o.trangThai.equals("THANH_CONG") || moi.soTien().compareTo(o.soTien) > 0) {
                gui(ex, 409, loi("KHONG_DU_DIEU_KIEN", "Giao dịch chưa thành công hoặc số tiền hoàn vượt số đã trả."));
                return;
            }
            // Mỗi giao dịch chỉ hoàn một lần, kể cả khi dùng khóa khác.
            String khoaTruoc = hoanTienTheoGiaoDich.putIfAbsent(maGiaoDich, khoa);
            if (khoaTruoc != null) {
                gui(ex, 409, loi("DA_HOAN", "Giao dịch này đã được hoàn tiền."));
                return;
            }
            refunds.put(khoa, moi);
            gui(ex, 200, Map.of("trangThai", "DA_HOAN", "idempotentReplay", false));
        } catch (RuntimeException e) {
            gui(ex, 500, loi("LOI_NOI_BO", "Lỗi xử lý trong payment-sim."));
        }
    }

    // ---------- tiện ích ----------

    private boolean duocPhep(HttpExchange ex) throws IOException {
        String gui = ex.getRequestHeaders().getFirst("X-Sim-Token");
        boolean ok = gui != null && MessageDigest.isEqual(gui.getBytes(StandardCharsets.UTF_8),
                apiToken.getBytes(StandardCharsets.UTF_8));
        if (!ok) gui(ex, 401, loi("KHONG_XAC_THUC", "Thiếu hoặc sai X-Sim-Token."));
        return ok;
    }

    private static JsonNode doc(HttpExchange ex) throws IOException {
        try {
            JsonNode node = JSON.readTree(ex.getRequestBody());
            if (node == null || !node.isObject()) throw new IOException("không phải đối tượng JSON");
            return node;
        } catch (IOException e) {
            gui(ex, 400, loi("JSON_KHONG_HOP_LE", "Nội dung yêu cầu không phải JSON hợp lệ."));
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static Map<String, Object> tomTat(Order o, boolean replay) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maGiaoDich", o.maGiaoDich);
        m.put("maDon", o.maDon);
        m.put("trangThai", o.trangThai);
        m.put("kichBan", o.kichBan);
        m.put("cheDoCallback", o.cheDoCallback);
        m.put("cheDo", "GIA_LAP");
        m.put("idempotentReplay", replay);
        return m;
    }

    private static Map<String, Object> loi(String ma, String thongBao) {
        return Map.of("ma", ma, "thongBao", thongBao);
    }

    private static void gui(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
