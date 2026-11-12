package com.fincore.checkout.service;

import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.domain.CheckoutStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutReviewTest {

    @Test
    void reviewedSessionCanReceiveApproval() {
        PaymentClient client = mock(PaymentClient.class);
        CheckoutService checkout = new CheckoutService(client);

        String paymentId = UUID.randomUUID().toString();

        when(client.createPayment(
                anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(Map.of(
                        "id", paymentId,
                        "status", "REVIEW"));

        var session = checkout.createSession(5_000, "USD", "Review test");

        checkout.pay(
                session.getId(),
                "4242424242424242",
                "VISA",
                "4242",
                "test-device",
                "127.0.0.1",
                Map.of());

        assertThat(session.getStatus()).isEqualTo(CheckoutStatus.REVIEW);

        checkout.updateSessionFromEvent(
                "checkout-" + session.getId(),
                "CAPTURED",
                paymentId,
                null);

        assertThat(session.getStatus()).isEqualTo(CheckoutStatus.SUCCEEDED);
    }
}