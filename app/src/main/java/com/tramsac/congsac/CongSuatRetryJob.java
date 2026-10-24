package com.tramsac.congsac;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="tramsac.scheduler.enabled", havingValue="true", matchIfMissing=true)
public class CongSuatRetryJob {
    private final CongSuatPublisher publisher;
    public CongSuatRetryJob(CongSuatPublisher publisher) { this.publisher = publisher; }
    @Scheduled(fixedDelayString="${tramsac.mqtt.power-retry-ms:5000}", initialDelay=5000)
    public void retry() { publisher.retry(); }
}
