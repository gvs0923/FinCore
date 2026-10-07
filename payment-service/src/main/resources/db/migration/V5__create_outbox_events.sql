-- Transactional outbox: events written in the same transaction as the
-- domain change, then published to Kafka by a background poller.
-- This guarantees at-least-once delivery without two-phase commit.

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64)  NOT NULL,  -- e.g. 'Payment'
    aggregate_id    UUID         NOT NULL,  -- the payment's id
    event_type      VARCHAR(128) NOT NULL,  -- e.g. 'payment.captured'
    payload         JSONB        NOT NULL,  -- full event body as JSON
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ            -- NULL until sent to Kafka
);

-- Partial index: the publisher only queries unpublished rows
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at)
    WHERE published_at IS NULL; 