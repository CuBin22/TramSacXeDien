package com.tramsac.phiensac;

import com.tramsac.congsac.CongSacRepository;
import com.tramsac.congsac.PhanBoLaiDatPhienService;
import com.tramsac.mqtt.MqttGateway;
import com.tramsac.mqtt.MqttTopics;
import com.tramsac.suco.SuCoService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceStateService {
    private final DeviceRecoveryTxService tx;
    private final DeviceFaultService faultService;
    private final PhanBoLaiDatPhienService reassignment;
    private final CongSacRepository congRepository;
    private final MqttGateway mqtt;
    private final SuCoService suCoService;
    public DeviceStateService(DeviceRecoveryTxService tx, DeviceFaultService faultService,
                              PhanBoLaiDatPhienService reassignment, CongSacRepository congRepository,
                              MqttGateway mqtt, SuCoService suCoService) {
        this.tx = tx; this.faultService = faultService; this.reassignment = reassignment;
        this.congRepository = congRepository; this.mqtt = mqtt; this.suCoService = suCoService;
    }
    public void heartbeat(String maCong, boolean coXe) {
        if (tx.heartbeat(maCong, coXe)) {
            mqtt.publishJson(MqttTopics.lenh(maCong), Map.of("maLenh", "REC-" + UUID.randomUUID(), "loai", "HOI_TRANG_THAI"));
        }
    }
    public void trangThai(String maCong, String state, BigDecimal dienNang, boolean coXe) {
        trangThai(maCong, state, dienNang, coXe, "");
    }
    public void trangThai(String maCong, String state, BigDecimal dienNang, boolean coXe, String command) {
        DeviceRecoveryTxService.KetQuaPhucHoi result = tx.phucHoi(maCong, state, dienNang, coXe, command);
        if (result.canXuLySuCo()) {
            try { mqtt.publishJson(MqttTopics.lenh(maCong), Map.of("maLenh", "EMG-" + UUID.randomUUID(), "loai", "DUNG")); }
            catch (RuntimeException ignored) { }
            faultService.loi(maCong, "Khôi phục phát hiện lỗi");
        } else if (result.daPhucHoi()) {
            reassignment.phanBoLai(maCong);
        }
    }
    public void loi(String maCong, String loaiLoi) { faultService.loi(maCong, loaiLoi); }
    @Transactional
    public void taoCanhBaoMatKetNoiDai(String maCong) {
        suCoService.taoNeuChuaMo(maCong, "Mất kết nối", "Cao", "Cổng mất heartbeat trên 5 phút.");
    }
}
