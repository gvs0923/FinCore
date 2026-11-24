CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES settlement_batches(id),
    report_ref VARCHAR(255) NOT NULL,
    item_count INTEGER NOT NULL,
    matched_count INTEGER NOT NULL,
    mismatched_count INTEGER NOT NULL,
    missing_internal_count INTEGER NOT NULL,
    missing_external_count INTEGER NOT NULL,
    break_detected BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reconciliation_items (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
    processor_ref TEXT NOT NULL,
    classification VARCHAR(32) NOT NULL,
    internal_amount_minor BIGINT,
    internal_fee_minor BIGINT,
    internal_currency VARCHAR(3),
    external_amount_minor BIGINT,
    external_fee_minor BIGINT,
    external_currency VARCHAR(3),
    CHECK (classification IN (
        'MATCHED', 'MISMATCHED', 'MISSING_INTERNAL', 'MISSING_EXTERNAL'))
);

CREATE INDEX idx_reconciliation_items_run
    ON reconciliation_items (run_id);

CREATE INDEX idx_reconciliation_runs_batch
    ON reconciliation_runs (batch_id, created_at);