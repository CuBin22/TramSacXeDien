package com.tramsac.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.Map;

/** W3C propagation at asynchronous boundaries. No SDK/exporter is created by application code.
 * The Java agent supplies the SDK; without it instrumentation is safely a no-op. */
public final class TraceContext implements AutoCloseable {
    private static final W3CTraceContextPropagator W3C = W3CTraceContextPropagator.getInstance();
    private static final TextMapGetter<Map<String, ?>> GETTER = new TextMapGetter<>() {
        public Iterable<String> keys(Map<String, ?> carrier) { return carrier.keySet(); }
        public String get(Map<String, ?> carrier, String key) {
            Object value = carrier == null ? null : carrier.get(key);
            return value instanceof String text ? text : null;
        }
    };
    private final Span span;
    private final Scope scope;

    private TraceContext(String name, SpanKind kind, Context parent) {
        span = GlobalOpenTelemetry.getTracer("com.tramsac").spanBuilder(name)
                .setSpanKind(kind).setParent(parent).startSpan();
        scope = span.makeCurrent();
    }
    public static TraceContext start(String name, SpanKind kind) {
        return new TraceContext(name, kind, Context.current());
    }
    public static TraceContext receive(String name, Map<String, ?> carrier) {
        // Never inherit an unrelated callback thread's context when a message has no valid parent.
        return new TraceContext(name, SpanKind.CONSUMER, W3C.extract(Context.root(), carrier, GETTER));
    }
    public static Map<String, String> headers() {
        Map<String, String> values = new HashMap<>();
        W3C.inject(Context.current(), values, Map::put);
        return values;
    }
    public static Map<String, Object> inject(Map<String, ?> payload) {
        Map<String, Object> copy = new HashMap<>(payload);
        copy.putAll(headers());
        return copy;
    }
    public static String parent() { return headers().get("traceparent"); }
    public void error(Throwable error) { span.recordException(error); span.setStatus(StatusCode.ERROR); }
    @Override public void close() { scope.close(); span.end(); }
}
