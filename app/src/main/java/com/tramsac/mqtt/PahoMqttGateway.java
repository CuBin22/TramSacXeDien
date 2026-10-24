package com.tramsac.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tramsac.telemetry.TraceContext;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
@ConditionalOnProperty(name = "tramsac.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class PahoMqttGateway implements MqttGateway {
    private static final Logger log = LoggerFactory.getLogger(PahoMqttGateway.class);
    private final ObjectMapper mapper;
    private final ObjectProvider<MqttMessageHandler> handlerProvider;
    private final String uri;
    private final String username;
    private final String password;
    private final String configuredClientId;
    private final Duration safetyTimeout;
    private final Map<String, CompletableFuture<Boolean>> safetyRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private MqttAsyncClient client;
    private MqttConnectOptions connectOptions;

    public PahoMqttGateway(ObjectMapper mapper, ObjectProvider<MqttMessageHandler> handlerProvider,
                           @Value("${tramsac.mqtt.uri}") String uri,
                           @Value("${tramsac.mqtt.username}") String username,
                           @Value("${tramsac.mqtt.password}") String password,
                           @Value("${tramsac.mqtt.client-id}") String clientId,
                           @Value("${tramsac.mqtt.safety-timeout:3s}") Duration safetyTimeout) {
        this.mapper = mapper; this.handlerProvider = handlerProvider; this.uri = uri; this.username = username;
        this.password = password; this.configuredClientId = clientId; this.safetyTimeout = safetyTimeout;
    }

    @PostConstruct
    void connect() {
        try {
            client = new MqttAsyncClient(uri, configuredClientId + "-" + UUID.randomUUID(), null);
            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    connecting.set(false);
                    try {
                        client.subscribe("tramsac/cong/+/ack", 1);
                        client.subscribe("tramsac/cong/+/trangthai", 1);
                        client.subscribe("tramsac/cong/+/heartbeat", 0);
                        client.subscribe("tramsac/cong/+/loi", 1);
                        log.info("Đã kết nối MQTT broker {}{}", serverURI, reconnect ? " (kết nối lại)" : "");
                    } catch (MqttException e) {
                        log.warn("Không đăng ký được MQTT topic: {}", e.getMessage());
                    }
                }
                @Override public void connectionLost(Throwable cause) { log.warn("MQTT mất kết nối: {}", cause.toString()); }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    try {
                        Map<String, Object> payload = mapper.readValue(message.getPayload(), new TypeReference<>() { });
                        try (var trace = TraceContext.receive("mqtt.nhan", payload)) {
                            try {
                                handlerProvider.getObject().nhan(topic, payload);
                            } catch (Exception e) {
                                trace.error(e);
                                throw e;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Bỏ qua MQTT payload sai định dạng ở {}", topic, e);
                    }
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(3);
            options.setKeepAliveInterval(30);
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            connectOptions = options;
            client.setBufferOpts(new DisconnectedBufferOptions());
            tryConnect();
        } catch (MqttException e) {
            connecting.set(false);
            log.warn("MQTT chưa sẵn sàng (client sẽ tự thử lại): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${tramsac.mqtt.reconnect-ms:5000}",
            initialDelayString = "${tramsac.mqtt.reconnect-ms:5000}")
    public void reconnectIfNeeded() {
        if (client != null && !client.isConnected()) tryConnect();
    }

    private void tryConnect() {
        if (client == null || connectOptions == null || !connecting.compareAndSet(false, true)) return;
        try {
            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override public void onSuccess(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken) {
                    connecting.set(false);
                }
                @Override public void onFailure(org.eclipse.paho.client.mqttv3.IMqttToken asyncActionToken, Throwable exception) {
                    connecting.set(false);
                    log.warn("Kết nối MQTT thất bại, sẽ thử lại: {}", exception.getMessage());
                }
            });
        } catch (MqttException e) {
            connecting.set(false);
            log.debug("Chưa thể kết nối MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void publishJson(String topic, Map<String, ?> payload) {
        // A persisted command parent survives scheduler retries; ordinary messages use the current request.
        try (var parent = payload.get("traceparent") instanceof String p && !p.isBlank()
                    ? TraceContext.receive("lenh.guiLenh", payload) : null;
             var trace = TraceContext.start("lenh.guiMqtt", SpanKind.PRODUCER)) {
            try {
                if (client == null) throw new IllegalStateException("MQTT client chưa khởi tạo");
                MqttMessage message = new MqttMessage(mapper.writeValueAsBytes(TraceContext.inject(payload)));
                message.setQos(topic.endsWith("/heartbeat") ? 0 : 1);
                client.publish(topic, message);
            } catch (Exception e) {
                trace.error(e);
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không gửi được thông điệp MQTT.", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> hoiAnToan(String maCong) {
        String id = "SAFE-" + UUID.randomUUID();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        safetyRequests.put(id, future);
        try {
            publishJson(MqttTopics.lenh(maCong), Map.of("maLenh", id, "loai", "HOI_AN_TOAN"));
        } catch (RuntimeException e) {
            safetyRequests.remove(id);
            future.completeExceptionally(e);
            return future;
        }
        return future.orTimeout(safetyTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> safetyRequests.remove(id));
    }

    @Override
    public void hoanTatHoiAnToan(String maLenh, boolean anToan) {
        CompletableFuture<Boolean> request = safetyRequests.get(maLenh);
        if (request != null) request.complete(anToan);
    }

    @Override public boolean dangKetNoi() { return client != null && client.isConnected(); }

    @PreDestroy
    void close() {
        try { if (client != null) client.disconnectForcibly(100, 500); } catch (MqttException ignored) { }
    }
}
