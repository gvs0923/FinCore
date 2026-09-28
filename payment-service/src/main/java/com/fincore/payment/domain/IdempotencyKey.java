package com.fincore.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores idempotency keys to prevent double-charges. When a client
 * sends the same Idempotency-Key header twice, we return the cached
 * response instead of creating a second payment.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key_value", length = 255)
    private String keyValue;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected IdempotencyKey() {
        // JPA
    }

    public IdempotencyKey(String keyValue) {
        this.keyValue = keyValue;
        this.expiresAt = OffsetDateTime.now().plusHours(24);
    }

    public void linkPayment(UUID paymentId, String responseBody) {
        this.paymentId = paymentId;
        this.responseBody = responseBody;
    }

    public String getKeyValue() { return keyValue; }
    public UUID getPaymentId() { return paymentId; }
    public String getResponseBody() { return responseBody; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public boolean hasPayment() { return paymentId != null; }
}