package com.fincore.payment.service;

import com.fincore.payment.TestcontainersConfiguration;
import com.fincore.payment.client.RiskClient;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.domain.PaymentStatus;
import com.fincore.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentRiskIT {

    @Autowired
    private PaymentService payments;

    @Autowired
    private PaymentAttemptRepository attempts;

    @MockitoBean
    private RiskClient risk;

    @MockitoBean
    private KafkaTemplate<String, String> kafka;

    @Test
    void allowCaptures() {
        decision("ALLOW", 0);

        assertThat(pay(5_000).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void blockDoesNotCapture() {
        decision("BLOCK", 80);

        Payment payment = pay(5_000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("RISK_BLOCK");
        assertThat(payment.getLedgerRef()).isNull();

        assertThat(attempts.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()))
                .noneMatch(attempt -> "CAPTURED".equals(attempt.getStatus()));
    }

    @Test
    void reviewCanBeApprovedOnlyOnce() {
        decision("REVIEW", 40);

        Payment payment = pay(5_000);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REVIEW);
        assertThat(payment.getLedgerRef()).isNull();

        Payment approved =
                payments.resolveReviewedPayment(payment.getId(), true);

        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.CAPTURED);

        assertThatThrownBy(() ->
                payments.resolveReviewedPayment(payment.getId(), true))
                .isInstanceOf(IllegalStateException.class);

        assertThat(attempts.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()))
                .filteredOn(attempt -> "CAPTURED".equals(attempt.getStatus()))
                .hasSize(1);
    }

    @Test
    void reviewCanBeRejectedWithoutCapture() {
        decision("REVIEW", 40);

        Payment payment = pay(5_000);

        Payment rejected =
                payments.resolveReviewedPayment(payment.getId(), false);

        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(rejected.getDeclineReason())
                .isEqualTo("RISK_REVIEW_REJECTED");
        assertThat(rejected.getLedgerRef()).isNull();

        assertThat(attempts.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()))
                .noneMatch(attempt -> "CAPTURED".equals(attempt.getStatus()));
    }

    @Test
    void unavailableRiskAllowsSmallAmount() {
        assertThat(pay(5_000).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void unavailableRiskReviewsLargeAmount() {
        assertThat(pay(500_001).getStatus())
                .isEqualTo(PaymentStatus.REVIEW);
    }

    private void decision(String value, int score) {
        when(risk.assess(
                anyString(), anyLong(), anyString(),
                anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Map.of(
                        "decision", value,
                        "score", score,
                        "firedRules", List.of()));
    }

    private Payment pay(long amount) {
        return payments.processPayment(
                UUID.randomUUID().toString(),
                "risk-test",
                amount,
                "USD",
                "4242424242424242",
                "VISA",
                "4242",
                "test-device",
                "127.0.0.1");
    }
}