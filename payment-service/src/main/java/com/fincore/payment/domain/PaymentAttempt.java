package com.fincore.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records each attempt to process a payment through the external
 * processor. A payment may have multiple attempts (e.g. authorize
 * attempt, then capture attempt).
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "processor_ref")
    private String processorRef;

    @Column(name = "decline_reason")
    private String declineReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PaymentAttempt() {
        // JPA
    }

    public PaymentAttempt(Payment payment, int attemptNumber, String status,
                          String processorRef, String declineReason) {
        this.payment = payment;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.processorRef = processorRef;
        this.declineReason = declineReason;
    }

    public UUID getId() { return id; }
    public Payment getPayment() { return payment; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getStatus() { return status; }
    public String getProcessorRef() { return processorRef; }
    public String getDeclineReason() { return declineReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
