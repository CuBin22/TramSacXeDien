package com.tramsac.mqtt;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MqttGateway {
    void publishJson(String topic, Map<String, ?> payload);
    CompletableFuture<Boolean> hoiAnToan(String maCong);
    void hoanTatHoiAnToan(String maLenh, boolean anToan);
    boolean dangKetNoi();
}
