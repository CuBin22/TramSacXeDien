package com.tramsac.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TraceContextTest {
    @Test void mqttRoundTripAcrossThreadsAndRetryKeepsTraceAndRestoresScope() throws Exception {
        var exporter = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            String traceId;
            Map<String, Object> command;
            try (var root = TraceContext.start("phien.batDau", SpanKind.SERVER)) {
                traceId = Span.current().getSpanContext().getTraceId();
                command = TraceContext.inject(Map.of("maLenh", "L-1"));
            }
            try (var worker = Executors.newSingleThreadExecutor()) {
                Map<String, Object> ack = worker.submit(() -> {
                    try (var device = TraceContext.receive("thiet_bi.xuLyLenh", command)) {
                        assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(traceId);
                        return TraceContext.inject(Map.of("maLenh", "L-1", "ketQua", "OK"));
                    }
                }).get();
                try (var receive = TraceContext.receive("lenh.nhanAck", ack)) {
                    assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(traceId);
                }
                try (var retry = TraceContext.receive("lenh.guiLai", command)) {
                    assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(traceId);
                }
            }
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            var spans = exporter.getFinishedSpanItems();
            assertThat(spans).hasSize(4).allMatch(s -> s.getTraceId().equals(traceId));
            assertThat(spans.get(1).getParentSpanId()).isEqualTo(spans.get(0).getSpanId());
            assertThat(spans.get(2).getParentSpanId()).isEqualTo(spans.get(1).getSpanId());
        } finally { GlobalOpenTelemetry.resetForTest(); }
    }

    @Test void invalidCarrierDoesNotInheritUnrelatedThreadContext() {
        var exporter = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            GlobalOpenTelemetry.resetForTest();
            GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            try (var root = TraceContext.start("unrelated", SpanKind.INTERNAL)) {
                String unrelated = Span.current().getSpanContext().getTraceId();
                try (var received = TraceContext.receive("invalid", Map.of("traceparent", "invalid"))) {
                    assertThat(Span.current().getSpanContext().getTraceId()).isNotEqualTo(unrelated);
                }
                assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(unrelated);
            }
        } finally { GlobalOpenTelemetry.resetForTest(); }
    }
}
