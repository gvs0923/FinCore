package com.fincore.payment.domain;

import java.util.Map;
import java.util.Set;

/**
 * Payment state machine. Each status knows which statuses it can
 * transition to. Calling transitionTo() with an invalid target
 * throws IllegalStateException — this is the transition guard.
 *
 * CREATED → AUTHORIZED → CAPTURED → SETTLED
 *              ↓            ↓
 *           DECLINED    CAPTURE_FAILED
 *    ↓
 *  FAILED (validation failure, e.g. bad card number)
 */
public enum PaymentStatus {
    CREATED,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    CAPTURE_FAILED,
    SETTLED,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            CREATED,        Set.of(AUTHORIZED, DECLINED, FAILED),
            AUTHORIZED,     Set.of(CAPTURED, CAPTURE_FAILED),
            CAPTURED,       Set.of(SETTLED),
            DECLINED,       Set.of(),
            CAPTURE_FAILED, Set.of(),
            SETTLED,        Set.of(),
            FAILED,         Set.of()
    );

    public PaymentStatus transitionTo(PaymentStatus target) {
        if (!TRANSITIONS.getOrDefault(this, Set.of()).contains(target)) {
            throw new IllegalStateException(
                    "Invalid payment transition: " + this + " → " + target);
        }
        return target;
    }

    public boolean isTerminal() {
        return TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}