package com.fincore.payment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final RestClient restClient;

    /**
     * @Value reads from application.properties:
     * fincore.ledger.base-url=http://localhost:9090
     *
     * RestClient.builder() creates a pre-configured HTTP client
     * with that base URL baked in, so every call is relative.
     */
    public LedgerClient(@Value("${fincore.ledger.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Posts a balanced ledger transaction for a captured payment.
     * The reference is the payment ID — this makes it idempotent.
     * If the ledger already has this reference, it returns 409 and
     * we don't double-post.
     *
     * The accounting entries:
     *   DEBIT  processor_clearing   (money coming from processor)
     *   CREDIT merchant_receivable  (money owed to merchant)
     */
    public void postCapture(String paymentId, long amountMinorUnits, String currency) {
        // Convert minor units (cents) to decimal for the ledger
        // e.g. 5000 cents → 50.00
        BigDecimal amount = BigDecimal.valueOf(amountMinorUnits, 2);

        Map<String, Object> body = Map.of(
                "reference", "payment:" + paymentId,
                "description", "Payment capture " + paymentId,
                "lines", List.of(
                        Map.of("accountRef", "ASSET:PROCESSOR_CLEARING",
                                "entryType", "DEBIT",
                                "amount", amount),
                        Map.of("accountRef", "LIABILITY:MERCHANT_RECEIVABLE",
                                "entryType", "CREDIT",
                                "amount", amount)
                )
        );

        restClient.post()
                .uri("/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Posted ledger transaction for payment {}: {} {} (minor units)",
                paymentId, amountMinorUnits, currency);
    }
}
