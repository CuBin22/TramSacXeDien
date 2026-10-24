package com.tramsac.phiensac;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tramsac.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class LenhRetryJob {
    private static final Logger log = LoggerFactory.getLogger(LenhRetryJob.class);
    private final LenhDieuKhienRepository repository;
    private final LenhDieuKhienService tx;
    private final PhienSacCommandService publisher;
    private final java.time.Clock clock;
    public LenhRetryJob(LenhDieuKhienRepository repository, LenhDieuKhienService tx,
                        PhienSacCommandService publisher, java.time.Clock clock) {
        this.repository = repository; this.tx = tx; this.publisher = publisher; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${tramsac.mqtt.command-scan-ms:2000}",
            initialDelayString = "${tramsac.mqtt.command-scan-ms:2000}")
    public void quet() {
        try {
            List<String> commands = repository.timLenhQuaHan(java.time.LocalDateTime.now(clock).minusSeconds(10));
            for (String id : commands) if (tx.xuLyLaiLenh(id)) publisher.guiLai(id);
        } catch (RuntimeException e) { log.warn("Không quét được lệnh MQTT chờ ack: {}", e.toString()); }
    }
}
