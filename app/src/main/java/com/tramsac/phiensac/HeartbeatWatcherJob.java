package com.tramsac.phiensac;

import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.PhanBoLaiDatPhienService;
import com.tramsac.congsac.TrangThaiCong;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** UC16: quét heartbeat mỗi 30 giây; thiết bị quá 90 giây thì mất kết nối, quá 5 phút tạo sự cố. */
@Component
@ConditionalOnProperty(name = "tramsac.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatWatcherJob {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatWatcherJob.class);
    private final CongSacRepository congRepository;
    private final DeviceRecoveryTxService recovery;
    private final DeviceStateService stateService;
    private final PhanBoLaiDatPhienService reassignment;
    private final Clock clock;
    private static final List<String> IGNORED = List.of(TrangThaiCong.LOI.giaTri(),
            TrangThaiCong.BAO_TRI.giaTri(), TrangThaiCong.MAT_KET_NOI.giaTri());
    public HeartbeatWatcherJob(CongSacRepository congRepository, DeviceRecoveryTxService recovery,
                              DeviceStateService stateService, PhanBoLaiDatPhienService reassignment, Clock clock) {
        this.congRepository = congRepository; this.recovery = recovery; this.stateService = stateService;
        this.reassignment = reassignment; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${tramsac.mqtt.heartbeat-scan-ms:30000}",
            initialDelayString = "${tramsac.mqtt.heartbeat-scan-ms:30000}")
    public void quet() {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            congRepository.timMaCongMatKetNoi(now.minusSeconds(90), IGNORED).forEach(maCong -> {
                if (recovery.danhDauMatKetNoi(maCong)) reassignment.phanBoLai(maCong);
            });
            congRepository.timMaCongMatKetNoiQuaHan(TrangThaiCong.MAT_KET_NOI.giaTri(), now.minusMinutes(5))
                    .forEach(stateService::taoCanhBaoMatKetNoiDai);
        } catch (RuntimeException e) {
            log.warn("Không quét được trạng thái heartbeat: {}", e.toString());
        }
    }
}
