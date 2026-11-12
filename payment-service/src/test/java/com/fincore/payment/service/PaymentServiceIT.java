package com.fincore.payment.service;

import com.fincore.payment.TestcontainersConfiguration;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.domain.PaymentAttempt;
import com.fincore.payment.domain.PaymentStatus;
import com.fincore.payment.repository.PaymentAttemptRepository;
import com.fincore.payment.repository.PaymentRepository;
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
class PaymentServiceIT {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository; 

    @Autowired
    private PaymentAttemptRepository attemptRepository;


    // By default Mockito mocks do nothing for void methods, which is what we want.
    @MockitoBean
   private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void successfulPayment_isCaptured() {
        Payment payment = paymentService.processPayment(
                "idem-" + System.nanoTime(),
                "ORDER-001",
                5000L, "USD",
                "4242424242424242", "VISA", "4242", "unknown-device", "unknown-ip"
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getAmount()).isEqualTo(5000L);
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getCardBrand()).isEqualTo("VISA");
        assertThat(payment.getCardLast4()).isEqualTo("4242");
        assertThat(payment.getLedgerRef()).startsWith("payment:");
        assertThat(payment.getDeclineReason()).isNull();

        // Verify two attempts were recorded: authorize + capture
        List<PaymentAttempt> attempts = attemptRepository
                .findByPaymentIdOrderByAttemptNumberAsc(payment.getId());
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).getStatus()).isEqualTo("AUTHORIZED");
        assertThat(attempts.get(0).getProcessorRef()).startsWith("proc_auth_");
        assertThat(attempts.get(1).getStatus()).isEqualTo("CAPTURED");
        assertThat(attempts.get(1).getProcessorRef()).startsWith("proc_cap_");
    }

    @Test
    void declinedCard_isDeclined() {
        Payment payment = paymentService.processPayment(
                "idem-" + System.nanoTime(),
                "ORDER-002",
                3000L, "USD",
                "4000000000000002", "VISA", "0002", "unknown-device", "unknown-ip"
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("generic_decline");
        assertThat(payment.getLedgerRef()).isNull();

        // Only one attempt: the declined auth
        List<PaymentAttempt> attempts = attemptRepository
                .findByPaymentIdOrderByAttemptNumberAsc(payment.getId());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getStatus()).isEqualTo("DECLINED");
    }

    @Test
    void invalidCard_isDeclined() {
        Payment payment = paymentService.processPayment(
                "idem-" + System.nanoTime(),
                "ORDER-003",
                2000L, "USD",
                "1111111111111111", "UNKNOWN", "1111", "unknown-device", "unknown-ip"
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("invalid_card");
    }

    @Test
    void sameIdempotencyKey_returnsSamePayment() {
        String key = "idem-" + System.nanoTime();

        Payment first = paymentService.processPayment(
                key, "ORDER-004", 7500L, "USD",
                "4242424242424242", "VISA", "4242", "unknown-device", "unknown-ip"
        );

        Payment second = paymentService.processPayment(
                key, "ORDER-004", 7500L, "USD",
                "4242424242424242", "VISA", "4242", "unknown-device", "unknown-ip"
        );

        // Same payment returned — no double charge
        assertThat(second.getId()).isEqualTo(first.getId());

        // Still only one payment in the DB for this key
        assertThat(paymentRepository.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void concurrentSameKeyCreatesOneCapture() throws Exception {
        String key = "concurrent-" + java.util.UUID.randomUUID();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Payment> submit = () -> {
                ready.countDown();
                assertThat(start.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return paymentService.processPayment(key, "ORDER-CONCURRENT", 1000L, "USD",
                        "4242".repeat(4), "VISA", "4242", "unknown-device", "unknown-ip");
            };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Payment payment = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            Payment duplicate = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(duplicate.getId()).isEqualTo(payment.getId());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(duplicate.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()))
                    .extracting(PaymentAttempt::getStatus).containsExactly("AUTHORIZED", "CAPTURED");
        }
    }

    @Test
    void getPayment_returnsExistingPayment() {
        Payment created = paymentService.processPayment(
                "idem-" + System.nanoTime(),
                "ORDER-005", 1000L, "USD",
                "4242424242424242", "VISA", "4242", "unknown-device", "unknown-ip"
        );

        Payment fetched = paymentService.getPayment(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void getPayment_nonExistent_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(
                PaymentNotFoundException.class,
                () -> paymentService.getPayment(java.util.UUID.randomUUID())
        );
    }

    @Test
    void stateTransitionGuard_preventsInvalidTransitions() {
        // DECLINED is terminal — can't transition further
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> PaymentStatus.DECLINED.transitionTo(PaymentStatus.CAPTURED)
        );

        // Can't skip from CREATED to CAPTURED
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> PaymentStatus.CREATED.transitionTo(PaymentStatus.CAPTURED)
        );

        // SETTLED is terminal
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> PaymentStatus.SETTLED.transitionTo(PaymentStatus.CREATED)
        );
    }
}
