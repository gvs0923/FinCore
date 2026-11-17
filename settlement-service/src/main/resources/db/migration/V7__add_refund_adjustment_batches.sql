ALTER TABLE settlement_batches
    ADD COLUMN batch_kind VARCHAR(32) NOT NULL DEFAULT 'DAILY',
    ADD COLUMN original_batch_id UUID REFERENCES settlement_batches(id),
    ADD COLUMN adjustment_refund_id UUID UNIQUE REFERENCES settlement_refunds(id);

ALTER TABLE settlement_batches
    DROP CONSTRAINT settlement_batches_merchant_id_currency_settlement_date_key,
    DROP CONSTRAINT settlement_batches_status_check;

CREATE UNIQUE INDEX uq_settlement_daily_scope
    ON settlement_batches (merchant_id, currency, settlement_date)
    WHERE batch_kind = 'DAILY';

CREATE INDEX idx_settlement_adjustments_original
    ON settlement_batches (original_batch_id)
    WHERE original_batch_id IS NOT NULL;

ALTER TABLE settlement_batches ADD CONSTRAINT settlement_batch_kind_check CHECK (
    (batch_kind = 'DAILY' AND original_batch_id IS NULL AND adjustment_refund_id IS NULL
        AND status IN ('OPEN', 'RECONCILED', 'PAID_OUT'))
    OR
    (batch_kind = 'REFUND_ADJUSTMENT' AND original_batch_id IS NOT NULL
        AND original_batch_id <> id AND adjustment_refund_id IS NOT NULL
        AND capture_count = 0 AND gross_minor = 0 AND fees_minor = 0
        AND refunds_minor > 0 AND net_minor = -refunds_minor
        AND status IN ('OPEN', 'RECONCILED', 'RECOVERED'))
);
