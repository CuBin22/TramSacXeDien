package com.tramsac.phiensac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tramsac.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CanhBaoChiemCongJob {
    private static final Logger log = LoggerFactory.getLogger(CanhBaoChiemCongJob.class);
    private final CanhBaoChiemCongService service;
    public CanhBaoChiemCongJob(CanhBaoChiemCongService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${tramsac.mqtt.occupancy-scan-ms:60000}",
            initialDelayString = "${tramsac.mqtt.occupancy-scan-ms:60000}")
    public void quet() {
        try { service.quet(); } catch (RuntimeException e) { log.warn("Không quét được cổng đang bị chiếm: {}", e.toString()); }
    }
}
