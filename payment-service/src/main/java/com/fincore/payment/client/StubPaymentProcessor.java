package com.fincore.payment.client;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub processor that simulates authorize + capture behavior using
 * well-known test card numbers:
 *
 *   4242 4242 4242 4242 → approved
 *   4000 0000 0000 0002 → declined (generic_decline)
 *   anything else       → declined (invalid_card)
 *
 * No real card data is ever processed. This project has zero PCI scope.
 */
@Component
public class StubPaymentProcessor implements PaymentProcessor {

    private static final String APPROVE_PAN = "4242424242424242";
    private static final String DECLINE_PAN = "4000000000000002";

    @Override
    public ProcessorResult authorize(String cardNumber, long amount, String currency) {
        String stripped = cardNumber.replaceAll("\\s+", "");

        if (APPROVE_PAN.equals(stripped)) {
            return ProcessorResult.approved("proc_auth_" + UUID.randomUUID());
        }
        if (DECLINE_PAN.equals(stripped)) {
            return ProcessorResult.declined("generic_decline");
        }
        return ProcessorResult.declined("invalid_card");
    }

    @Override
    public ProcessorResult capture(String processorRef) {
        return ProcessorResult.approved("proc_cap_" + UUID.randomUUID());
    }
}