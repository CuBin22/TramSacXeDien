package com.tramsac.datphien;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Bộ định thời giai đoạn 2. Tắt bằng tramsac.scheduler.enabled=false (test dùng để chạy job trực tiếp). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tramsac.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DatPhienScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatPhienScheduler.class);

    private final DatPhienJobs jobs;

    public DatPhienScheduler(DatPhienJobs jobs) {
        this.jobs = jobs;
    }

    /** Quá hạn trước, giữ chỗ sau: đặt chỗ vừa quá hạn không bị kích hoạt lại. */
    @Scheduled(fixedDelayString = "${tramsac.scheduler.chu-ky-ms:60000}",
            initialDelayString = "${tramsac.scheduler.tre-ban-dau-ms:30000}")
    public void quet() {
        try {
            jobs.quetQuaHan();
            jobs.quetGiuCho();
        } catch (RuntimeException e) {
            log.warn("Lần quét định kỳ thất bại: {}", e.toString());
        }
    }
}
