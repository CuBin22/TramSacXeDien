package com.tramsac.congsac;

import com.tramsac.mqtt.MqttGateway;
import com.tramsac.mqtt.MqttTopics;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CongSuatPublisher {
    private static final Logger log = LoggerFactory.getLogger(CongSuatPublisher.class);
    private final MqttGateway mqtt;
    private final CongSuatOutbox outbox;
    public CongSuatPublisher(MqttGateway mqtt, CongSuatOutbox outbox) {
        this.mqtt = mqtt; this.outbox = outbox;
    }
    @TransactionalEventListener
    public void publish(CongSuatDaCanBang event) {
        retry();
    }
    public void retry() {
        for (var cap : outbox.pending()) {
            try {
                mqtt.publishJson(MqttTopics.lenh(cap.port()), Map.of("maLenh", cap.id(),
                        "loai", "DAT_CONG_SUAT", "congSuatKw", cap.power(), "revision", cap.revision(),
                        "traceparent", cap.parent() == null ? "" : cap.parent()));
            } catch (RuntimeException e) {
                log.warn("Chưa gửi được cập nhật công suất tới cổng {}: {}", cap.port(), e.getMessage());
            }
        }
    }
}
