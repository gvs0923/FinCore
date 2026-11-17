CREATE TABLE settlement_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE
        REFERENCES settlement_captures(payment_id),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    reason TEXT NOT NULL,
    actor VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REFUNDED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('REFUNDED', 'FAILED'))
);