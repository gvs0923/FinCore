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

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
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
    private final ObjectMapper riskJson = new ObjectMapper();

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
                                  String cardLast4, String deviceFingerprint,
                                  String ipAddress) {
        return processPayment(
                idempotencyKey,
                merchantRef,
                amount,
                currency,
                cardNumber,
                cardBrand,
                cardLast4,
                deviceFingerprint,
                ipAddress,
                Map.of());
    }

    @Transactional
    public Payment processPayment(String idempotencyKey, String merchantRef,
                                  long amount, String currency,
                                  String cardNumber, String cardBrand,
                                  String cardLast4, String deviceFingerprint,
                                  String ipAddress,
                                  Map<String, String> context) {

        // --- Step 1: Idempotency check ---
        idempotencyKeyRepository.provision(idempotencyKey);
        IdempotencyKey iKey = idempotencyKeyRepository.findForUpdate(idempotencyKey)
                .orElseThrow();

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
                cardFingerprint, deviceFingerprint, ipAddress, context);

        String riskDecision = riskDecision(risk, amount);
        publishRiskEvent(payment, riskDecision, risk);
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

    @SuppressWarnings("unchecked")
    private void publishRiskEvent(Payment p, String decision,
                              Map<String, Object> risk) {
    Map<String, Object> payload = new HashMap<>();

    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("occurredAt", Instant.now().toString());
    payload.put("paymentId", p.getId().toString());
    payload.put("decision", decision);

    int score = risk != null && risk.get("score") instanceof Number number
            ? number.intValue() : -1;

    String rules = risk != null && risk.get("firedRules") instanceof List<?> list
            ? String.join(",", list.stream().map(Object::toString).toList())
            : "risk_unavailable";

    payload.put("score", score);
    payload.put("firedRules", rules);

    outboxEventRepository.save(
            new OutboxEvent(
                    "Payment",
                    p.getId(),
                    "risk.assessed",
                    riskJson.writeValueAsString(payload)));
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

        /**
     * Resolve a REVIEW payment. Approve → capture via the stored processor
     * reference; reject → decline. Internal use only (admin queue).
     */
    @Transactional
    public Payment resolveReviewedPayment(UUID paymentId, boolean approve) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow(
                () -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.REVIEW) {
            throw new IllegalStateException(
                    "Payment " + paymentId + " is " + payment.getStatus()
                    + ", not REVIEW");
        }

        if (!approve) {
            payment.markDeclined("RISK_REVIEW_REJECTED");
            paymentRepository.save(payment);
            publishEvent(payment, "payment.declined");
            log.info("Reviewed payment {} rejected by analyst", paymentId);
            return payment;
        }

        // Re-capture with the processor ref recorded at authorize time
        List<PaymentAttempt> attempts =
                attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId);
        String authRef = attempts.stream()
                .filter(a -> "AUTHORIZED".equals(a.getStatus()))
                .map(PaymentAttempt::getProcessorRef)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No authorization attempt for " + paymentId));

        ProcessorResult capResult = paymentProcessor.capture(authRef);
        attemptRepository.save(new PaymentAttempt(
                payment, attempts.size() + 1,
                capResult.success() ? "CAPTURED" : "CAPTURE_FAILED",
                capResult.processorRef(), capResult.declineReason()));
        if (!capResult.success()) {
            payment.transitionTo(PaymentStatus.CAPTURE_FAILED);
            paymentRepository.save(payment);
            publishEvent(payment, "payment.capture_failed");
            return payment;
        }

        payment.markCaptured("payment:" + paymentId);
        paymentRepository.save(payment);
        publishEvent(payment, "payment.captured");
        log.info("Reviewed payment {} approved and captured", paymentId);
        return payment;
    }
}