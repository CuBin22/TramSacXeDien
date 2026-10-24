package com.tramsac.devicesim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.telemetry.TraceContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;

/** Actor thiết bị cho profile dev: ack idempotent, telemetry, heartbeat và nút giả lập lỗi. */
public final class DeviceSimulator {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Map<String, Device> DEVICES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> ACK_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> UNSAFE = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService TIMER = Executors.newScheduledThreadPool(2);
    private static final AtomicBoolean CONNECTING = new AtomicBoolean();
    private static MqttAsyncClient client;
    private static MqttConnectOptions connectOptions;

    private DeviceSimulator() { }

    public static void main(String[] args) throws Exception {
        String ids = env("DEVICE_IDS", "C-AC-01,C-AC-02,C-DC-01,C-DC-02");
        Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isBlank()).forEach(id -> DEVICES.put(id, new Device()));
        Arrays.stream(env("UNSAFE_DEVICES", "").split(",")).map(String::trim).filter(s -> !s.isBlank()).forEach(UNSAFE::add);
        connect();
        startControlApi();
        TIMER.scheduleAtFixedRate(DeviceSimulator::sendHeartbeats, 0, 30, TimeUnit.SECONDS);
        TIMER.scheduleAtFixedRate(DeviceSimulator::tryConnect, 5, 5, TimeUnit.SECONDS);
    }

    private static void connect() throws Exception {
        String uri = env("MQTT_URI", "tcp://localhost:1883");
        client = new MqttAsyncClient(uri, "device-sim-" + java.util.UUID.randomUUID(), null);
        client.setBufferOpts(new DisconnectedBufferOptions());
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String serverURI) {
                DEVICES.keySet().forEach(id -> {
                    try { client.subscribe("tramsac/cong/" + id + "/lenh", 1); }
                    catch (Exception e) { System.err.println("Subscribe lỗi: " + e.getMessage()); }
                });
            }
            @Override public void connectionLost(Throwable cause) { System.err.println("MQTT disconnected: " + cause); }
            @Override public void messageArrived(String topic, MqttMessage message) throws Exception {
                String[] parts = topic.split("/");
                if (parts.length != 4 || !DEVICES.containsKey(parts[2])) return;
                Map<String, Object> command = JSON.readValue(message.getPayload(), new TypeReference<>() { });
                try (var trace = TraceContext.receive("thiet_bi.xuLyLenh", command)) {
                    try { xuLyLenh(parts[2], command); }
                    catch (Exception e) { trace.error(e); throw e; }
                }
            }
            @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) { }
        });
        connectOptions = new MqttConnectOptions();
        connectOptions.setAutomaticReconnect(true); connectOptions.setCleanSession(false); connectOptions.setKeepAliveInterval(30);
        connectOptions.setUserName(env("MQTT_USER", "tramsac"));
        connectOptions.setPassword(env("MQTT_PASSWORD", "tramsac-dev").toCharArray());
        tryConnect();
    }

    private static void tryConnect() {
        if (client == null || client.isConnected() || connectOptions == null || !CONNECTING.compareAndSet(false, true)) return;
        try {
            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) { CONNECTING.set(false); }
                @Override public void onFailure(IMqttToken token, Throwable cause) {
                    CONNECTING.set(false);
                    System.err.println("MQTT broker chưa sẵn sàng: " + cause.getMessage());
                }
            });
        } catch (Exception e) {
            CONNECTING.set(false);
            System.err.println("MQTT reconnect: " + e.getMessage());
        }
    }

    private static void xuLyLenh(String id, Map<String, Object> command) throws Exception {
        Device device = DEVICES.get(id);
        if (!device.online) return;
        String maLenh = String.valueOf(command.get("maLenh"));
        Map<String, Object> previous = ACK_CACHE.get(maLenh);
        if (previous != null && !"HOI_TRANG_THAI".equals(command.get("loai"))) { publish(id, "ack", previous); return; }
        String action = String.valueOf(command.get("loai"));
        Map<String, Object> ack;
        synchronized (device) {
            switch (action) {
                case "HOI_AN_TOAN" -> ack = baseAck(maLenh, UNSAFE.contains(id) ? "UNSAFE" : "SAFE");
                case "HOI_TRANG_THAI" -> {
                    publish(id, "trangthai", Map.of("maLenh", maLenh, "trangThai", device.state, "dienNangKWh", device.energy(),
                            "coXe", device.occupied, "nhanLuc", Instant.now().toString()));
                    ack = baseAck(maLenh, "OK");
                }
                case "BAT_DAU" -> {
                    if (UNSAFE.contains(id)) ack = baseAck(maLenh, "UNSAFE");
                    else {
                        device.start();
                        publish(id, "trangthai", Map.of("trangThai", device.state, "dienNangKWh", device.energy(),
                                "coXe", true, "nhanLuc", Instant.now().toString()));
                        ack = baseAck(maLenh, "OK");
                    }
                }
                case "DUNG" -> {
                    BigDecimal meter = device.stop("Rảnh");
                    ack = new java.util.HashMap<>(baseAck(maLenh, "OK"));
                    ack.put("dienNangKWh", meter);
                    ack.put("trangThai", device.state);
                    publish(id, "trangthai", Map.of("trangThai", device.state, "dienNangKWh", device.energy(),
                            "coXe", device.occupied, "nhanLuc", Instant.now().toString()));
                }
                case "DAT_CONG_SUAT" -> {
                    BigDecimal power = new BigDecimal(String.valueOf(command.get("congSuatKw")));
                    long revision = Long.parseLong(String.valueOf(command.get("revision")));
                    if (power.signum() < 0) ack = baseAck(maLenh, "INVALID_POWER");
                    else {
                        if (revision > device.powerRevision) {
                            device.accrue();
                            device.powerKw = power;
                            device.powerRevision = revision;
                        }
                        ack = baseAck(maLenh, "OK");
                    }
                }
                default -> ack = baseAck(maLenh, "UNKNOWN_COMMAND");
            }
        }
        ACK_CACHE.put(maLenh, ack);
        publish(id, "ack", ack);
    }

    private static Map<String, Object> baseAck(String maLenh, String result) {
        return Map.of("maLenh", maLenh, "ketQua", result, "ackLuc", Instant.now().toString());
    }

    private static void sendHeartbeats() {
        DEVICES.forEach((id, device) -> {
            if (device.online && client != null && client.isConnected()) {
                publish(id, "heartbeat", Map.of("trangThai", device.state, "dienNangKWh", device.energy(),
                        "coXe", device.occupied, "powerRevision", device.powerRevision, "nhanLuc", Instant.now().toString()));
            }
        });
    }

    private static void publish(String id, String suffix, Map<String, ?> payload) {
        try {
            MqttMessage message = new MqttMessage(JSON.writeValueAsBytes(TraceContext.inject(payload)));
            message.setQos(suffix.equals("heartbeat") ? 0 : 1);
            client.publish("tramsac/cong/" + id + "/" + suffix, message);
        } catch (Exception e) { System.err.println("MQTT publish lỗi: " + e.getMessage()); }
    }

    private static void startControlApi() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8090), 0);
        server.createContext("/sim/devices/", DeviceSimulator::control);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private static void control(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (!"POST".equals(exchange.getRequestMethod()) || parts.length != 5 || !DEVICES.containsKey(parts[3])) {
            reply(exchange, 404, Map.of("error", "Not found")); return;
        }
        String id = parts[3]; String action = parts[4]; Device device = DEVICES.get(id);
        switch (action) {
            case "disconnect" -> device.online = false;
            case "reconnect" -> device.online = true;
            case "fault" -> {
                String type = exchange.getRequestURI().getRawQuery();
                String fault = type == null ? "RO_DIEN" : type.replace("type=", "").toUpperCase();
                UNSAFE.add(id);
                device.stop("Lỗi");
                publish(id, "loi", Map.of("loaiLoi", fault, "nhanLuc", Instant.now().toString()));
            }
            case "safe" -> { UNSAFE.remove(id); device.stop("Rảnh"); }
            case "vehicle-left" -> {
                device.occupied = false;
                publish(id, "trangthai", Map.of("trangThai", device.state, "dienNangKWh", device.energy(),
                        "coXe", false, "nhanLuc", Instant.now().toString()));
            }
            default -> { reply(exchange, 400, Map.of("error", "Unknown action")); return; }
        }
        reply(exchange, 200, Map.of("maCong", id, "online", device.online, "trangThai", device.state));
    }

    private static void reply(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value;
    }

    private static final class Device {
        volatile boolean online = true;
        volatile boolean occupied;
        volatile String state = "Rảnh";
        private Instant meteredAt = Instant.now();
        private BigDecimal accumulated = BigDecimal.ZERO;
        private BigDecimal powerKw = BigDecimal.ZERO;
        private volatile long powerRevision = -1;

        synchronized void start() {
            if ("Đang sạc".equals(state)) return;
            accumulated = BigDecimal.ZERO;
            meteredAt = Instant.now();
            state = "Đang sạc";
            occupied = true;
        }
        synchronized BigDecimal stop(String nextState) {
            accrue();
            state = nextState;
            return accumulated.setScale(3, RoundingMode.HALF_UP);
        }
        synchronized void accrue() {
            Instant now = Instant.now();
            accumulated = reading(now);
            meteredAt = now;
        }
        private BigDecimal reading(Instant now) {
            if (!"Đang sạc".equals(state)) return accumulated;
            long millis = Math.max(0, java.time.Duration.between(meteredAt, now).toMillis());
            return accumulated.add(powerKw.multiply(BigDecimal.valueOf(millis))
                    .divide(BigDecimal.valueOf(3600000), 12, RoundingMode.HALF_UP));
        }
        synchronized BigDecimal energy() {
            return reading(Instant.now()).setScale(3, RoundingMode.HALF_UP);
        }
    }
}
