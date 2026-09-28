-- Payment state machine tables.
-- A payment moves through: CREATED → AUTHORIZED → CAPTURED → SETTLED
-- with failure paths at each stage.

CREATE TABLE idempotency_keys (
    key_value     VARCHAR(255) PRIMARY KEY,
    payment_id    UUID,                          -- NULL until the payment is created
    response_body TEXT,                           -- cached response for replay
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '24 hours'
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE REFERENCES idempotency_keys(key_value),
    merchant_ref    VARCHAR(255) NOT NULL,        -- e.g. order id from the merchant
    amount          BIGINT       NOT NULL CHECK (amount > 0),  -- minor units (cents)
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    decline_reason  VARCHAR(255),
    card_brand      VARCHAR(32),                  -- e.g. VISA, MASTERCARD (display only)
    card_last4      VARCHAR(4),                   -- last 4 digits (display only, no full PAN ever)
    ledger_ref      VARCHAR(255),                 -- reference used when posting to ledger
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_merchant_ref ON payments(merchant_ref);
CREATE INDEX idx_payments_status ON payments(status);

CREATE TABLE payment_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID NOT NULL REFERENCES payments(id),
    attempt_number  INT  NOT NULL DEFAULT 1,
    status          VARCHAR(32)  NOT NULL,        -- AUTHORIZED, DECLINED, ERROR, CAPTURED, etc.
    processor_ref   VARCHAR(255),                 -- reference from the mock processor
    decline_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts(payment_id);