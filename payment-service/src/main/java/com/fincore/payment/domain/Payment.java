package com.fincore.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "merchant_ref", nullable = false)
    private String merchantRef;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "card_brand", length = 32)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "ledger_ref")
    private String ledgerRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
        // JPA
    }

    public Payment(String idempotencyKey, String merchantRef, long amount,
                   String currency, String cardBrand, String cardLast4) {
        this.idempotencyKey = idempotencyKey;
        this.merchantRef = merchantRef;
        this.amount = amount;
        this.currency = currency;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Transitions the payment to a new status using the state machine
     * guard. Throws IllegalStateException if the transition is invalid.
     */
    public void transitionTo(PaymentStatus newStatus) {
        this.status = this.status.transitionTo(newStatus);
        this.updatedAt = OffsetDateTime.now();
    }

    public void resetForRetry(String cardBrand, String cardLast4) {
        if (!this.status.isTerminalFailure()) {
            throw new IllegalStateException(
                    "Can only retry a payment in a terminal failure state, not " + this.status);
        }
        this.status = PaymentStatus.CREATED;
        this.declineReason = null;
        this.ledgerRef = null;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDeclined(String reason) {
        transitionTo(PaymentStatus.DECLINED);
        this.declineReason = reason;
    }

    public void markFailed(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.declineReason = reason;
    }

    public void markCaptured(String ledgerRef) {
        transitionTo(PaymentStatus.CAPTURED);
        this.ledgerRef = ledgerRef;
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMerchantRef() { return merchantRef; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getDeclineReason() { return declineReason; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getLedgerRef() { return ledgerRef; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}