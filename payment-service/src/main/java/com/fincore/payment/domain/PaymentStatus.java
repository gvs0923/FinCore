package com.fincore.payment.domain;

import java.util.Map;
import java.util.Set;

/**
 * Payment state machine. Each status knows which statuses it can
 * transition to. Calling transitionTo() with an invalid target
 * throws IllegalStateException — this is the transition guard.
 *
 * CREATED → AUTHORIZED → CAPTURED → SETTLED
 *    ↓         ↓     ↓        ↓
 *  DECLINED  REVIEW DECLINED CAPTURE_FAILED
 *    ↓         ↓     ↓
 *  FAILED   CAPTURED DECLINED
 * (validation failure, e.g. bad card number)
 *
 * AUTHORIZED → DECLINED is legal: a risk BLOCK after authorization is
 * a decline, not a FAILED (which means "validation broke").
 *
 * REVIEW is a real state, not a flag: a risk REVIEW verdict parks the
 * payment here until an analyst approves (→ CAPTURED) or rejects
 * (→ DECLINED). It is non-terminal but sealed — the machine guarantees
 * you can't skip review straight to settlement.
 */
public enum PaymentStatus {
    CREATED,
    AUTHORIZED,
    REVIEW,
    DECLINED,
    CAPTURED,
    CAPTURE_FAILED,
    SETTLED,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            CREATED,        Set.of(AUTHORIZED, DECLINED, FAILED),
            AUTHORIZED,     Set.of(CAPTURED, CAPTURE_FAILED, REVIEW, DECLINED),
            REVIEW,         Set.of(CAPTURED, DECLINED, CAPTURE_FAILED),
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

    public boolean isTerminalFailure() {
        return this == DECLINED || this == CAPTURE_FAILED || this == FAILED;
    }
}