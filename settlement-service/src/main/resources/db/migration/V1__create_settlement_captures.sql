CREATE TABLE settlement_captures (
    payment_id UUID PRIMARY KEY,
    merchant_id VARCHAR(128) NOT NULL,
    merchant_ref TEXT NOT NULL,
    processor_ref TEXT NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    captured_at TIMESTAMPTZ NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (merchant_id, processor_ref)
);

CREATE INDEX idx_settlement_captures_batch_lookup
    ON settlement_captures (
        merchant_id,
        currency,
        captured_at,
        payment_id
    );