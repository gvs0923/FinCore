ALTER TABLE settlement_outbox_events ADD COLUMN traceparent VARCHAR(55);
CREATE INDEX settlement_outbox_pending_idx
    ON settlement_outbox_events (created_at) WHERE NOT published;
