package com.fincore.payment.domain;

import com.fincore.payment.outbox.OutboxPublisher;
import com.fincore.payment.repository.OutboxEventRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OutboxTraceTest {

    @Test
    @SuppressWarnings("unchecked")
    void persistsAndRestoresTraceAcrossTheScheduledOutboxBoundary() {
        String traceId = "1234567890abcdef1234567890abcdef";
        String spanId = "1234567890abcdef";
        var span = Span.wrap(SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
        var event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.captured", "{}");
        try (var scope = span.makeCurrent()) {
            event.captureTraceContext();
        }
        assertEquals("00-" + traceId + "-" + spanId + "-01", event.getTraceparent());
        var repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(repository.findUnpublishedForUpdate()).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertEquals(traceId, Span.current().getSpanContext().getTraceId());
            assertEquals(spanId, Span.current().getSpanContext().getSpanId());
            return CompletableFuture.completedFuture(null);
        });
        new OutboxPublisher(repository, kafka).publishPendingEvents();
        assertNotNull(event.getPublishedAt());
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void oldEventsWithoutTraceContextStillPublish() {
        var event = new OutboxEvent("Payment", UUID.randomUUID(), "payment.captured", "{}");
        event.captureTraceContext();
        assertNull(event.getTraceparent());
    }
}
