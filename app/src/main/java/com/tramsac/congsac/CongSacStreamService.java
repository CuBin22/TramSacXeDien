package com.tramsac.congsac;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CongSacStreamService {
    private final CongSacAdminTxService congService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    public CongSacStreamService(CongSacAdminTxService congService) { this.congService = congService; }
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        send(emitter, congService.danhSach());
        return emitter;
    }
    @Scheduled(fixedDelayString = "${tramsac.mqtt.stream-ms:3000}")
    public void broadcast() {
        if (emitters.isEmpty()) return;
        List<CongSacResponse> snapshot = congService.danhSach();
        emitters.forEach(e -> send(e, snapshot));
    }
    private void send(SseEmitter emitter, List<CongSacResponse> payload) {
        try { emitter.send(SseEmitter.event().name("cong-sac").data(payload)); }
        catch (IOException e) { emitters.remove(emitter); emitter.completeWithError(e); }
    }
}
