package com.fincore.checkout.service;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) {
        super("Checkout session not found: " + sessionId);
    }
}
