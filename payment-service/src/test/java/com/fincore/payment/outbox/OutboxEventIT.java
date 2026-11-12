package com.fincore.payment.outbox;

import com.fincore.payment.TestcontainersConfiguration;
import com.fincore.payment.domain.OutboxEvent;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.repository.OutboxEventRepository;
import com.fincore.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OutboxEventIT {

    @Autowired private PaymentService paymentService;
    @Autowired private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void successfulPayment_writesOutboxEvents() {
        Payment payment = paymentService.processPayment(
                "outbox-test-" + System.nanoTime(),
                "ORDER-OB1", 5000L, "USD",
                "4242424242424242", "VISA", "4242", "unknown-device", "unknown-ip"
        );

        // Filter events for this specific payment
        List<String> types = outboxEventRepository.findUnpublished().stream()
                .filter(e -> e.getAggregateId().equals(payment.getId()))
                .map(OutboxEvent::getEventType).toList();

        assertThat(types).containsExactly(
                "payment.created", "payment.authorized", "risk.assessed",
                "payment.captured");
    }

    @Test
    void declinedPayment_writesDeclinedEvent() {
        Payment payment = paymentService.processPayment(
                "outbox-test-" + System.nanoTime(),
                "ORDER-OB2", 3000L, "USD",
                "4000000000000002", "VISA", "0002", "unknown-device", "unknown-ip"
        );

        // Filter events for this specific payment
        List<String> types = outboxEventRepository.findUnpublished().stream()
                .filter(e -> e.getAggregateId().equals(payment.getId()))
                .map(OutboxEvent::getEventType).toList();

        assertThat(types).containsExactly("payment.created", "payment.declined");
    }
}