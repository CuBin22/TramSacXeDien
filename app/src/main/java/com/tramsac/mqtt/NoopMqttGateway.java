package com.tramsac.mqtt;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tramsac.mqtt.enabled", havingValue = "false")
public class NoopMqttGateway implements MqttGateway {
    @Override public void publishJson(String topic, Map<String, ?> payload) {
        throw new IllegalStateException("MQTT đang tắt.");
    }
    @Override public CompletableFuture<Boolean> hoiAnToan(String maCong) {
        return CompletableFuture.completedFuture(false);
    }
    @Override public void hoanTatHoiAnToan(String maLenh, boolean anToan) { }
    @Override public boolean dangKetNoi() { return false; }
}
