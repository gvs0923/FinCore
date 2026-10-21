CREATE TABLE ledger_outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_ledger_outbox_unpublished
    ON ledger_outbox_events (created_at)
    WHERE published_at IS NULL;