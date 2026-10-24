package com.tramsac.phiensac;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tramsac.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class PhienSacMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(PhienSacMaintenanceJob.class);
    private final PhienSacRepository repository;
    private final PhienSacChoQuaHanService expiry;
    private final Clock clock;
    public PhienSacMaintenanceJob(PhienSacRepository repository, PhienSacChoQuaHanService expiry, Clock clock) {
        this.repository = repository; this.expiry = expiry; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${tramsac.mqtt.session-scan-ms:30000}",
            initialDelayString = "${tramsac.mqtt.session-scan-ms:30000}")
    public void quet() {
        try {
            repository.timMaPhienChoQuaHan(TrangThaiPhien.CHO.giaTri(), java.time.LocalDateTime.now(clock).minusMinutes(15))
                    .forEach(expiry::expire);
        } catch (RuntimeException e) { log.warn("Không quét được phiên chờ bắt đầu: {}", e.toString()); }
    }
}
