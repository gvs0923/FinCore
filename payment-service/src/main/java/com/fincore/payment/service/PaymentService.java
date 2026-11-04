package com.fincore.payment.service;

import com.fincore.payment.client.PaymentProcessor;
import com.fincore.payment.client.ProcessorResult;
import com.fincore.payment.client.RiskClient;
import com.fincore.payment.domain.IdempotencyKey;
import com.fincore.payment.domain.OutboxEvent;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.domain.PaymentAttempt;
import com.fincore.payment.domain.PaymentStatus;
import com.fincore.payment.repository.IdempotencyKeyRepository;
import com.fincore.payment.repository.OutboxEventRepository;
import com.fincore.payment.repository.PaymentAttemptRepository;
import com.fincore.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentProcessor paymentProcessor;
    private final RiskClient riskClient;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAttemptRepository attemptRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository,
                          OutboxEventRepository outboxEventRepository,
                          PaymentProcessor paymentProcessor,
                        RiskClient riskClient) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentProcessor = paymentProcessor;
        this.riskClient = riskClient;
    }

    @Transactional
    public Payment processPayment(String idempotencyKey, String merchantRef,
                                  long amount, String currency,
                                  String cardNumber, String cardBrand,
                                  String cardLast4) {

        // --- Step 1: Idempotency check ---
        IdempotencyKey iKey = idempotencyKeyRepository.findById(idempotencyKey)
                .orElseGet(() -> idempotencyKeyRepository.save(
                        new IdempotencyKey(idempotencyKey)));

        Payment payment;
        if (iKey.hasPayment()) {
            Payment existing = paymentRepository.findById(iKey.getPaymentId()).orElseThrow();
            if (!existing.getStatus().isTerminalFailure()) {
                log.info("Idempotency hit for key {}, returning existing payment {}",
                        idempotencyKey, iKey.getPaymentId());
                return existing;
            }
            // Previous attempt failed — reset and retry with the same payment row
            log.info("Idempotency key {} linked to failed payment {} ({}), retrying",
                    idempotencyKey, iKey.getPaymentId(), existing.getStatus());
            existing.resetForRetry(cardBrand, cardLast4);
            payment = paymentRepository.save(existing);
        } else {
            // --- Step 2: Create payment ---
            payment = paymentRepository.save(
                    new Payment(idempotencyKey, merchantRef, amount, currency,
                            cardBrand, cardLast4));
            iKey.linkPayment(payment.getId(), null);
            idempotencyKeyRepository.save(iKey);
        }

        publishEvent(payment, "payment.created");

        // --- Step 3: Authorize ---
        ProcessorResult authResult = paymentProcessor.authorize(
                cardNumber, amount, currency);
        attemptRepository.save(new PaymentAttempt(
                payment, 1,
                authResult.success() ? "AUTHORIZED" : "DECLINED",
                authResult.processorRef(), authResult.declineReason()));

        if (!authResult.success()) {
            payment.markDeclined(authResult.declineReason());
            paymentRepository.save(payment);
            publishEvent(payment, "payment.declined");
            log.info("Payment {} declined: {}",
                    payment.getId(), authResult.declineReason());
            return payment;
        }

        payment.transitionTo(PaymentStatus.AUTHORIZED);
        paymentRepository.save(payment);
        publishEvent(payment, "payment.authorized");

        // --- Step 3.5: Risk check (synchronous — blocks capture) ---
        String cardFingerprint = fingerprint(cardNumber);
        Map<String, Object> risk = riskClient.assess(
                payment.getId().toString(), amount, currency,
                cardFingerprint, "unknown-device", "unknown-ip");

        String riskDecision = riskDecision(risk, amount);
        if ("BLOCK".equals(riskDecision)) {
            payment.markDeclined("RISK_BLOCK");
            paymentRepository.save(payment);
            publishEvent(payment, "payment.declined");
            log.info("Payment {} blocked by risk engine", payment.getId());
            return payment;
        }
        if ("REVIEW".equals(riskDecision)) {
            payment.transitionTo(PaymentStatus.REVIEW);
            paymentRepository.save(payment);
            publishEvent(payment, "payment.review");
            log.info("Payment {} parked for manual review", payment.getId());
            return payment;
        }

        // --- Step 4: Capture ---
        ProcessorResult capResult = paymentProcessor.capture(
                authResult.processorRef());
        attemptRepository.save(new PaymentAttempt(
                payment, 2,
                capResult.success() ? "CAPTURED" : "CAPTURE_FAILED",
                capResult.processorRef(), capResult.declineReason()));

        if (!capResult.success()) {
            payment.transitionTo(PaymentStatus.CAPTURE_FAILED);
            paymentRepository.save(payment);
            publishEvent(payment, "payment.capture_failed");
            log.warn("Payment {} capture failed: {}",
                    payment.getId(), capResult.declineReason());
            return payment;
        }

        // --- Step 5: Mark captured (ledger posting is now async via Kafka) ---
        String ledgerRef = "payment:" + payment.getId();
        payment.markCaptured(ledgerRef);
        paymentRepository.save(payment);
        publishEvent(payment, "payment.captured");
        log.info("Payment {} captured, outbox event written",
                payment.getId());

        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private void publishEvent(Payment p, String eventType) {
        String payload = """
                {"paymentId":"%s","merchantRef":"%s","amount":%d,\
                "currency":"%s","status":"%s","cardBrand":"%s",\
                "cardLast4":"%s"}"""
                .formatted(p.getId(), p.getMerchantRef(), p.getAmount(),
                        p.getCurrency(), p.getStatus().name(),
                        p.getCardBrand(), p.getCardLast4());
        outboxEventRepository.save(
                new OutboxEvent("Payment", p.getId(), eventType, payload));
    }

    /**
     * Fail-closed for large amounts, fail-open for small ones: when the
     * risk service is down, a cheap local rule stands in. An outage must
     * never silently bypass risk on a big payment — nor stop all sales.
     */
    private String riskDecision(Map<String, Object> risk, long amount) {
        if (risk == null) {
            return amount > 500_000 ? "REVIEW" : "ALLOW";
        }
        return (String) risk.get("decision");
    }

    /** SHA-256 fingerprint of the PAN — identity for velocity rules, never the card itself. */
    private String fingerprint(String cardNumber) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(cardNumber.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}