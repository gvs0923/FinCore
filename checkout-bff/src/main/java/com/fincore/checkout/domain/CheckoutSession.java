package com.fincore.checkout.domain;

import java.time.Instant;
import java.util.UUID;

public class CheckoutSession {

    private final String id;
    private final String idempotencyKey;
    private final long amount;
    private final String currency;
    private final String description;
    private final Instant createdAt;

    private CheckoutStatus status;
    private UUID paymentId;
    private String failureReason;

    public CheckoutSession(long amount, String currency, String description) {
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = "checkout:" + this.id;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = CheckoutStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public CheckoutStatus getStatus() { return status; }
    public UUID getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }

    public void markProcessing() {
        this.status = CheckoutStatus.PROCESSING;
    }

    public void markSucceeded(UUID paymentId) {
        this.status = CheckoutStatus.SUCCEEDED;
        this.paymentId = paymentId;
    }

    public void markFailed(String reason) {
        this.status = CheckoutStatus.FAILED;
        this.failureReason = reason;
    }
}