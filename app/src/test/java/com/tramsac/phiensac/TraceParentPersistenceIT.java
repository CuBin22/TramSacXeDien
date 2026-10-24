package com.tramsac.phiensac;

import io.opentelemetry.api.trace.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TraceParentPersistenceIT extends PhienSacBaseIT {
    @Autowired PhienSacCommandService publisher;
    @Test void schedulerRetryRetainsOriginalTraceInDatabaseAndPayload() throws Exception {
        var c = dungPhien("AC-1");
        String traceId = "0123456789abcdef0123456789abcdef";
        String spanId = "0123456789abcdef";
        String command;
        try (var scope = Span.wrap(SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault())).makeCurrent()) {
            command = maLenh(batDau(c.token(), c.maPhien(), khoaMoi()).andExpect(status().isAccepted()));
        }
        String saved = jdbc.queryForObject("SELECT TraceParent FROM LenhDieuKhien WHERE MaLenh=?", String.class, command);
        assertThat(saved).isEqualTo("00-"+traceId+"-"+spanId+"-01");
        publisher.guiLai(command);
        assertThat(mqtt.danhSachGui().stream().filter(m -> command.equals(m.payload().get("maLenh"))))
                .allMatch(m -> saved.equals(m.payload().get("traceparent")));
        assertThat(jdbc.queryForObject("SELECT TraceParent FROM LenhDieuKhien WHERE MaLenh=?", String.class, command)).isEqualTo(saved);
        ack(c.maCong(), command, "OK", null);
        ack(c.maCong(), command, "OK", null);
        assertThat(dem("SELECT count(*) FROM LichSuTrangThaiPhien WHERE MaPhien=? AND TrangThaiMoi='Đang sạc'", c.maPhien())).isEqualTo(1);
    }
}
