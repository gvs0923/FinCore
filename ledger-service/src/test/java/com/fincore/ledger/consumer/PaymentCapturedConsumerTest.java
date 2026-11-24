package com.fincore.ledger.consumer;

import com.fincore.ledger.service.DuplicatePostingException;
import com.fincore.ledger.service.LedgerService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCapturedConsumerTest {

    private final LedgerService ledgerService = mock(LedgerService.class);
    private final PaymentCapturedConsumer consumer =
            new PaymentCapturedConsumer(ledgerService, new ObjectMapper());
    private final String message = """
            {"paymentId":"test-payment","amount":10000,"currency":"USD"}
            """;

    @Test
    void duplicatePosting_isAcknowledged() {
        when(ledgerService.postTransaction(anyString(), anyString(), anyList()))
                .thenThrow(new DuplicatePostingException("payment:test-payment"));

        assertDoesNotThrow(
                () -> consumer.onPaymentCaptured(message));
    }

    @Test
    void unrelatedFailure_propagatesForRetry() {
        RuntimeException failure = new RuntimeException("Database unavailable");
        when(ledgerService.postTransaction(anyString(), anyString(), anyList()))
                .thenThrow(failure);

        assertThatThrownBy(() -> consumer.onPaymentCaptured(message))
                .hasCause(failure);
    }

    @Test
    void missingBalanceRow_propagatesForRetry() {
        IllegalStateException failure = new IllegalStateException("Balance row missing for account");
        when(ledgerService.postTransaction(anyString(), anyString(), anyList()))
                .thenThrow(failure);

        assertThatThrownBy(() -> consumer.onPaymentCaptured(message))
                .hasCause(failure);
    }
}
