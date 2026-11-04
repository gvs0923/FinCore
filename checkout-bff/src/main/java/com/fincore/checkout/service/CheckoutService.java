package com.fincore.checkout.service;

import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.domain.CheckoutSession;
import com.fincore.checkout.domain.CheckoutStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ConcurrentHashMap<String, CheckoutSession> sessions =
            new ConcurrentHashMap<>();
    private final PaymentClient paymentClient;

    public CheckoutService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    public CheckoutSession createSession(long amount, String currency,
                                          String description) {
        CheckoutSession session = new CheckoutSession(amount, currency,
                description);
        sessions.put(session.getId(), session);
        log.info("Created checkout session {} for {} {}",
                session.getId(), amount, currency);
        return session;
    }

    public CheckoutSession pay(String sessionId, String cardNumber,
                                String cardBrand, String cardLast4) {
        CheckoutSession session = getSession(sessionId);

        if (session.getStatus() == CheckoutStatus.SUCCEEDED) {
            return session;
        }

        // Reset failure state so the session can be retried
        if (session.getStatus() == CheckoutStatus.FAILED) {
            session.clearFailure();
        }

        session.markProcessing();

        try {
            // Fire the payment — payment-service still returns the
            // synchronous result, but ledger posting is now async via
            // Kafka.  We read the immediate response so the UI gets
            // fast feedback for declines/failures; capture confirmation
            // can also arrive via the Kafka consumer.
            Map<String, Object> result = paymentClient.createPayment(
                    session.getIdempotencyKey(),
                    "checkout-" + sessionId,
                    session.getAmount(),
                    session.getCurrency(),
                    cardNumber, cardBrand, cardLast4
            );

            String status = (String) result.get("status");
            if ("CAPTURED".equals(status)) {
                String id = (String) result.get("id");
                session.markSucceeded(UUID.fromString(id));
            } else if ("DECLINED".equals(status) || "FAILED".equals(status)
                       || "CAPTURE_FAILED".equals(status)) {
                String reason = (String) result.getOrDefault(
                        "declineReason", status);
                session.markFailed(reason);
            } else if ("REVIEW".equals(status)) {
                String id = (String) result.get("id");
                session.markReview(UUID.fromString(id));
            }
            // Any other status (AUTHORIZED, CREATED) stays PROCESSING —
            // the Kafka consumer will update when a terminal event arrives
        } catch (Exception e) {
            log.error("Payment request failed for session {}", sessionId, e);
            session.markFailed("Payment service unavailable");
        }

        return session;
    }

    public CheckoutSession getSession(String sessionId) {
        CheckoutSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return session;
    }

    // Called by the Kafka consumer when a terminal event arrives
    public void updateSessionFromEvent(String merchantRef, String status,
                                        String paymentId, String reason) {
        // merchantRef format is "checkout-{sessionId}"
        String sessionId = merchantRef.replace("checkout-", "");
        CheckoutSession session = sessions.get(sessionId);
        if (session == null) {
            log.debug("No active session for merchantRef {}", merchantRef);
            return;
        }

        // Only update if the session is still waiting for a result.
        // Ignore stale events from a previous attempt if the session
        // has already been retried (PROCESSING) or already reached a
        // terminal state via the synchronous pay response.
        if (session.getStatus() != CheckoutStatus.PROCESSING) {
            log.debug("Ignoring Kafka event for session {} in state {}",
                    sessionId, session.getStatus());
            return;
        }

        if ("CAPTURED".equals(status)) {
            session.markSucceeded(UUID.fromString(paymentId));
            log.info("Session {} succeeded via Kafka event", sessionId);
        } else if ("DECLINED".equals(status) || "CAPTURE_FAILED".equals(status)
                   || "FAILED".equals(status)) {
            session.markFailed(reason != null ? reason : status);
            log.info("Session {} failed via Kafka event: {}",
                    sessionId, reason);
        }         if ("CAPTURED".equals(status)) {
            session.markSucceeded(UUID.fromString(paymentId));
            log.info("Session {} succeeded via Kafka event", sessionId);
        } else if ("REVIEW".equals(status)) {
            session.markReview(UUID.fromString(paymentId));
            log.info("Session {} under review via Kafka event", sessionId);
        } else if ("DECLINED".equals(status) || "CAPTURE_FAILED".equals(status)
                   || "FAILED".equals(status)) {
            session.markFailed(reason != null ? reason : status);
            log.info("Session {} failed via Kafka event: {}",
                    sessionId, reason);
        }
    }
}