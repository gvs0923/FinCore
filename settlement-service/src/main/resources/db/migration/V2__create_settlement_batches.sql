CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY,
    merchant_id VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    settlement_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    capture_count BIGINT NOT NULL DEFAULT 0 CHECK (capture_count >= 0),
    gross_minor BIGINT NOT NULL DEFAULT 0 CHECK (gross_minor >= 0),
    fees_minor BIGINT NOT NULL DEFAULT 0 CHECK (fees_minor >= 0),
    refunds_minor BIGINT NOT NULL DEFAULT 0 CHECK (refunds_minor >= 0),
    net_minor BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id, currency, settlement_date),
    CHECK (status IN ('OPEN', 'RECONCILED', 'PAID_OUT')),
    CHECK (net_minor = gross_minor - fees_minor - refunds_minor)
);

CREATE TABLE settlement_batch_items (
    payment_id UUID PRIMARY KEY REFERENCES settlement_captures(payment_id),
    batch_id UUID NOT NULL REFERENCES settlement_batches(id),
    processor_ref TEXT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    fee_minor BIGINT NOT NULL CHECK (fee_minor >= 0),
    fee_policy_version VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_settlement_batch_items_batch
    ON settlement_batch_items (batch_id, payment_id);