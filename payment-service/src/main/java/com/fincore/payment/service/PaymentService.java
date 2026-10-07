package com.fincore.payment.service;

import com.fincore.payment.client.PaymentProcessor;
import com.fincore.payment.client.ProcessorResult;
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

import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentProcessor paymentProcessor;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAttemptRepository attemptRepository,
                          IdempotencyKeyRepository idempotencyKeyRepository,
                          OutboxEventRepository outboxEventRepository,
                          PaymentProcessor paymentProcessor) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentProcessor = paymentProcessor;
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
 
        if (iKey.hasPayment()) {
            log.info("Idempotency hit for key {}, returning existing payment {}",
                    idempotencyKey, iKey.getPaymentId());
            return paymentRepository.findById(iKey.getPaymentId()).orElseThrow();
        }

        // --- Step 2: Create payment ---
        Payment payment = paymentRepository.save(
                new Payment(idempotencyKey, merchantRef, amount, currency,
                        cardBrand, cardLast4));

        iKey.linkPayment(payment.getId(), null);
        idempotencyKeyRepository.save(iKey);

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
}