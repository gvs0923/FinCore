package com.fincore.payment.service;

import com.fincore.payment.client.LedgerClient;
import com.fincore.payment.client.PaymentProcessor;
import com.fincore.payment.client.ProcessorResult;
import com.fincore.payment.domain.IdempotencyKey;
import com.fincore.payment.domain.Payment;
import com.fincore.payment.domain.PaymentAttempt;
import com.fincore.payment.domain.PaymentStatus;
import com.fincore.payment.repository.IdempotencyKeyRepository;
import com.fincore.payment.repository.PaymentAttemptRepository;
import com.fincore.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The payment orchestrator. This is the most complex service in
 * the project — it coordinates:
 *   1. Idempotency check (don't double-charge)
 *   2. Payment creation
 *   3. Authorization (ask processor if card is valid)
 *   4. Capture (tell processor to take the money)
 *   5. Ledger posting (record the financial event)
 *
 * Each step either advances the payment to the next status or
 * moves it to a failure state. The state machine in PaymentStatus
 * enforces that transitions are valid.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentProcessor paymentProcessor;
    private final LedgerClient ledgerClient;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAttemptRepository attemptRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository,
                          PaymentProcessor paymentProcessor,
                          LedgerClient ledgerClient) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.paymentProcessor = paymentProcessor;
        this.ledgerClient = ledgerClient;
    }

    @Transactional
    public Payment processPayment(String idempotencyKey, String merchantRef,
                                  long amount, String currency,
                                  String cardNumber, String cardBrand, String cardLast4) {

        // --- Step 1: Idempotency check ---
        // If we've seen this key before AND it has a payment, return the
        // existing payment. This prevents double-charges from retries.
        IdempotencyKey iKey = idempotencyKeyRepository.findById(idempotencyKey)
                .orElseGet(() -> idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey)));

        if (iKey.hasPayment()) {
            log.info("Idempotency hit for key {}, returning existing payment {}", idempotencyKey, iKey.getPaymentId());
            return paymentRepository.findById(iKey.getPaymentId()).orElseThrow();
        }

        // --- Step 2: Create payment ---
        Payment payment = paymentRepository.save(
                new Payment(idempotencyKey, merchantRef, amount, currency, cardBrand, cardLast4));

        // Link the idempotency key to this payment
        iKey.linkPayment(payment.getId(), null);
        idempotencyKeyRepository.save(iKey);

        // --- Step 3: Authorize ---
        ProcessorResult authResult = paymentProcessor.authorize(cardNumber, amount, currency);
        attemptRepository.save(new PaymentAttempt(
                payment, 1, authResult.success() ? "AUTHORIZED" : "DECLINED",
                authResult.processorRef(), authResult.declineReason()));

        if (!authResult.success()) {
            payment.markDeclined(authResult.declineReason());
            paymentRepository.save(payment);
            log.info("Payment {} declined: {}", payment.getId(), authResult.declineReason());
            return payment;
        }

        payment.transitionTo(PaymentStatus.AUTHORIZED);
        paymentRepository.save(payment);

        // --- Step 4: Capture ---
        ProcessorResult capResult = paymentProcessor.capture(authResult.processorRef());
        attemptRepository.save(new PaymentAttempt(
                payment, 2, capResult.success() ? "CAPTURED" : "CAPTURE_FAILED",
                capResult.processorRef(), capResult.declineReason()));

        if (!capResult.success()) {
            payment.transitionTo(PaymentStatus.CAPTURE_FAILED);
            paymentRepository.save(payment);
            log.warn("Payment {} capture failed: {}", payment.getId(), capResult.declineReason());
            return payment;
        }

        // --- Step 5: Post to ledger ---
        String ledgerRef = "payment:" + payment.getId();
        try {
            ledgerClient.postCapture(payment.getId().toString(), amount, currency);
            payment.markCaptured(ledgerRef);
        } catch (Exception e) {
            log.error("Ledger posting failed for payment {} — needs reconciliation", payment.getId(), e);
            payment.markCaptured(ledgerRef);
        }

        paymentRepository.save(payment);
        log.info("Payment {} completed successfully", payment.getId());
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
