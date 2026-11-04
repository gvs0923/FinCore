CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID         NOT NULL,
    payment_id   VARCHAR(64)  NOT NULL,
    topic        VARCHAR(64)  NOT NULL,
    merchant_ref VARCHAR(64),
    amount_minor_units BIGINT,
    currency     CHAR(3),
    sent_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (event_id)
);