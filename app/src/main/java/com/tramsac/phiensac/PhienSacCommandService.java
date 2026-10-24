package com.tramsac.phiensac;

import com.tramsac.common.ApiException;
import com.tramsac.common.security.NguoiDungHienTai;
import com.tramsac.mqtt.MqttGateway;
import com.tramsac.mqtt.MqttTopics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Điều phối probe MQTT bên ngoài giao dịch rồi mở giao dịch ngắn để ghi lệnh. */
@Service
public class PhienSacCommandService {
    private static final Logger log = LoggerFactory.getLogger(PhienSacCommandService.class);
    private final PhienSacReadService readService;
    private final LenhDieuKhienService lenhService;
    private final LenhDieuKhienRepository lenhRepository;
    private final PhienSacRepository phienRepository;
    private final com.tramsac.congsac.CongSacRepository congRepository;
    private final MqttGateway mqtt;
    private final SafetyAuditService safetyAuditService;

    public PhienSacCommandService(PhienSacReadService readService, LenhDieuKhienService lenhService,
                                  LenhDieuKhienRepository lenhRepository, PhienSacRepository phienRepository,
                                  com.tramsac.congsac.CongSacRepository congRepository, MqttGateway mqtt,
                                  SafetyAuditService safetyAuditService) {
        this.readService = readService; this.lenhService = lenhService; this.lenhRepository = lenhRepository;
        this.phienRepository = phienRepository; this.congRepository = congRepository; this.mqtt = mqtt;
        this.safetyAuditService = safetyAuditService;
    }

    @io.opentelemetry.instrumentation.annotations.WithSpan("phien.batDau")
    public LenhDieuKhienResponse batDau(String maPhien, String key) {
        validateKey(key);
        if (!readService.coQuyenTruyCap(maPhien)) throw khongTimThay();
        var old = lenhService.timReplay(maPhien, "BAT_DAU", key);
        if (old.isPresent()) {
            resendIfPending(old.get().lenh());
            return LenhDieuKhienResponse.tu(old.get().lenh(), true);
        }
        String maCong = readService.layMaCong(maPhien);
        boolean safe = false;
        if (mqtt.dangKetNoi()) {
            try { safe = mqtt.hoiAnToan(maCong).get(4, TimeUnit.SECONDS); }
            catch (Exception ignored) { safe = false; }
        }
        if (!safe) {
            safetyAuditService.ghi("TU_CHOI_CAP_DIEN", "Cổng " + maCong + ": không có xác nhận an toàn từ thiết bị.");
            throw new ApiException(HttpStatus.CONFLICT, "TU_CHOI_AN_TOAN",
                    "Không cấp điện: thiết bị không xác nhận an toàn hoặc không phản hồi.");
        }
        LenhDieuKhienService.KetQua result = lenhService.batDau(maPhien, key, true);
        publish(result.lenh());
        return LenhDieuKhienResponse.tu(result.lenh(), result.replay());
    }

    @io.opentelemetry.instrumentation.annotations.WithSpan("phien.dungSac")
    public LenhDieuKhienResponse dung(String maPhien, String key) {
        validateKey(key);
        if (!readService.coQuyenTruyCap(maPhien)) throw khongTimThay();
        var old = lenhService.timReplay(maPhien, "DUNG", key);
        if (old.isPresent()) {
            resendIfPending(old.get().lenh());
            return LenhDieuKhienResponse.tu(old.get().lenh(), true);
        }
        LenhDieuKhienService.KetQua result = lenhService.dung(maPhien, key, null);
        publish(result.lenh());
        return LenhDieuKhienResponse.tu(result.lenh(), result.replay());
    }

    public void guiLai(String maLenh) {
        lenhService.timTheoId(maLenh).ifPresent(this::publish);
    }

    private void resendIfPending(LenhDieuKhien command) {
        if (command.dangChoAck()) publish(command);
    }

    private void publish(LenhDieuKhien command) {
        PhienSac phien = phienRepository.findById(command.getMaPhien()).orElse(null);
        if (phien == null) return;
        String mqttAction = "BAT_DAU".equals(command.getLoaiLenh()) ? "BAT_DAU" : "DUNG";
        try {
            mqtt.publishJson(MqttTopics.lenh(phien.getMaCong()), Map.of(
                    "maLenh", command.getMaLenh(), "loai", mqttAction, "maPhien", phien.getMaPhien(),
                    "guiLuc", (command.getGuiLuc() == null ? LocalDateTime.now() : command.getGuiLuc()).toString(),
                    "traceparent", command.getTraceParent() == null ? "" : command.getTraceParent()));
        } catch (RuntimeException e) {
            log.warn("Lệnh {} đã được lưu, đang chờ gửi lại khi MQTT sẵn sàng: {}", command.getMaLenh(), e.getMessage());
        }
    }

    private static void validateKey(String key) {
        try {
            if (key == null || !UUID.fromString(key).toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_KHONG_HOP_LE",
                    "Header Idempotency-Key phải là UUID.");
        }
    }

    private static ApiException khongTimThay() {
        return new ApiException(HttpStatus.NOT_FOUND, "KHONG_TIM_THAY_PHIEN", "Không tìm thấy phiên sạc.");
    }
}
