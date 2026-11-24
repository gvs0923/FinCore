package com.fincore.payment.outbox;

import com.fincore.payment.domain.OutboxEvent;
import com.fincore.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)   // poll every 500ms
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findUnpublishedForUpdate();
        for (OutboxEvent event : events) {
            try (var scope = io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()
                    .extract(io.opentelemetry.context.Context.root(), event.getTraceparent(),
                            new io.opentelemetry.context.propagation.TextMapGetter<String>() {
                                public Iterable<String> keys(String carrier) { return List.of("traceparent"); }
                                public String get(String carrier, String key) {
                                    return "traceparent".equals(key) ? carrier : null;
                                }
                            }).makeCurrent()) {
                // Topic = event type (e.g. "payment.captured")
                // Key   = aggregate ID (ensures ordering per payment)
                kafkaTemplate.send(
                        event.getEventType(),
                        event.getAggregateId().toString(),
                        event.getPayload()
                ).get();   // .get() blocks until Kafka acks — safe here

                event.markPublished();
                outboxEventRepository.save(event);
                log.debug("Published outbox event {} to topic {}",
                        event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}, will retry",
                        event.getId(), e);
                break;   // stop this batch, retry on next poll
            }
        }
    }
}