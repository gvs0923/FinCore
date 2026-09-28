package com.fincore.payment.client;

/**
 * Interface for external payment processor calls (authorize, capture).
 *  We use a stub that
 * returns success for test card 4242... and decline for 4000...0002.
 */
public interface PaymentProcessor {

    ProcessorResult authorize(String cardNumber, long amount, String currency);

    ProcessorResult capture(String processorRef);
}
