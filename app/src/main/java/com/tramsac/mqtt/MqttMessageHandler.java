package com.tramsac.mqtt;

import com.tramsac.phiensac.LenhDieuKhienRepository;
import com.tramsac.phiensac.LenhDieuKhienService;
import com.tramsac.phiensac.PhienSacRepository;
import com.tramsac.phiensac.DeviceStateService;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);
    private final MqttGateway mqtt;
    private final LenhDieuKhienService lenhService;
    private final LenhDieuKhienRepository lenhRepository;
    private final PhienSacRepository phienRepository;
    private final DeviceStateService deviceStateService;
    private final com.tramsac.congsac.CongSuatOutbox powerOutbox;
    public MqttMessageHandler(MqttGateway mqtt, LenhDieuKhienService lenhService,
                              LenhDieuKhienRepository lenhRepository, PhienSacRepository phienRepository,
                              DeviceStateService deviceStateService, com.tramsac.congsac.CongSuatOutbox powerOutbox) {
        this.powerOutbox = powerOutbox;
        this.mqtt = mqtt; this.lenhService = lenhService; this.lenhRepository = lenhRepository;
        this.phienRepository = phienRepository; this.deviceStateService = deviceStateService;
    }
    public void nhan(String topic, Map<String, Object> payload) {
        String[] parts = topic.split("/");
        if (parts.length != 4) return;
        String maCong = parts[2];
        String suffix = parts[3];
        try {
            switch (suffix) {
                case "ack" -> ack(maCong, payload);
                case "heartbeat" -> {
                    deviceStateService.heartbeat(maCong, bool(payload.get("coXe")));
                    if (payload.get("powerRevision") instanceof Number revision)
                        powerOutbox.reconcile(maCong, revision.longValue());
                }
                case "trangthai" -> deviceStateService.trangThai(maCong,
                        string(payload, "trangThai", "Rảnh"), decimal(payload.get("dienNangKWh")), bool(payload.get("coXe")),
                        string(payload, "maLenh", ""));
                case "loi" -> deviceStateService.loi(maCong,
                        string(payload, "loaiLoi", "Lỗi thiết bị"));
                default -> { }
            }
        } catch (RuntimeException e) {
            io.opentelemetry.api.trace.Span.current().recordException(e);
            io.opentelemetry.api.trace.Span.current().setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            log.warn("Xử lý MQTT {} thất bại: {}", topic, e.getMessage());
        }
    }
    private void ack(String maCong, Map<String, Object> payload) {
        String maLenh = string(payload, "maLenh", "");
        String result = string(payload, "ketQua", "UNKNOWN");
        if (maLenh.startsWith("POWER-")) {
            powerOutbox.ack(maCong, maLenh, result);
            return;
        }
        if (maLenh.startsWith("SAFE-")) {
            mqtt.hoanTatHoiAnToan(maLenh, "SAFE".equalsIgnoreCase(result));
            return;
        }
        boolean late = lenhService.nhanAck(maLenh, result, decimal(payload.get("dienNangKWh")));
        if (late) {
            lenhRepository.findById(maLenh).flatMap(l -> phienRepository.findById(l.getMaPhien()))
                    .ifPresent(p -> mqtt.publishJson(MqttTopics.lenh(maCong), Map.of(
                            "maLenh", "LATE-" + maLenh, "loai", "HOI_TRANG_THAI", "maPhien", p.getMaPhien())));
        }
    }
    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key); return value == null ? fallback : String.valueOf(value);
    }
    private static BigDecimal decimal(Object value) {
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (value instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }
}
