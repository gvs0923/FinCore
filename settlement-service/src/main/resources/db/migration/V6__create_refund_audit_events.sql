CREATE TABLE refund_audit_events (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL UNIQUE REFERENCES settlement_refunds(id),
    payment_id UUID NOT NULL REFERENCES settlement_captures(payment_id),
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL CHECK (action = 'FULL_REFUND'),
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
