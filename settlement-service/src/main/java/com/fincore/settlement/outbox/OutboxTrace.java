package com.fincore.settlement.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OutboxTrace {

    public interface Work { void run() throws Exception; }

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTrace(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** Call while writing the outbox row, inside the request's span. */
    public String currentTraceparent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) return null;
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get("traceparent");
    }

    /** Call from the poller: the Kafka send becomes a child of the original request. */
    public void inTrace(String traceparent, String name, Work work) throws Exception {
        Span.Builder builder = traceparent == null
                ? tracer.spanBuilder()
                : propagator.extract(Map.of("traceparent", traceparent), Map::get);
        Span span = builder.name(name).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            work.run();
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
