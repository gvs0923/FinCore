package com.fincore.payment.client;

/**
 * Result from the mock processor. Contains a reference (like a
 * transaction ID from Stripe) and whether it succeeded.
 */
public record ProcessorResult(
        boolean success,
        String processorRef,
        String declineReason
) {
    public static ProcessorResult approved(String processorRef) {
        return new ProcessorResult(true, processorRef, null);
    }

    public static ProcessorResult declined(String reason) {
        return new ProcessorResult(false, null, reason);
    }
}
