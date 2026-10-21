
CREATE TABLE payment_metrics_hourly (
    id                  BIGSERIAL PRIMARY KEY,
    window_start        TIMESTAMPTZ NOT NULL,
    status              VARCHAR(32) NOT NULL,
    currency            CHAR(3)     NOT NULL,
    decline_reason      VARCHAR(64) NOT NULL DEFAULT '',
    payment_count       BIGINT      NOT NULL DEFAULT 0,
    volume_minor_units  BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (window_start, status, currency, decline_reason)
);


CREATE TABLE ledger_metrics_hourly (
    id                  BIGSERIAL PRIMARY KEY,
    window_start        TIMESTAMPTZ   NOT NULL UNIQUE,
    transaction_count   BIGINT        NOT NULL DEFAULT 0,
    entry_count         BIGINT        NOT NULL DEFAULT 0,
    total_debits        NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_credits       NUMERIC(19,4) NOT NULL DEFAULT 0
);

CREATE TABLE request_metrics_hourly (
    id                  BIGSERIAL PRIMARY KEY,
    window_start        TIMESTAMPTZ  NOT NULL,
    route               VARCHAR(128) NOT NULL,
    status_class        VARCHAR(8)   NOT NULL,
    hit_count           BIGINT NOT NULL DEFAULT 0,
    le_50               BIGINT NOT NULL DEFAULT 0,
    le_100              BIGINT NOT NULL DEFAULT 0,
    le_250              BIGINT NOT NULL DEFAULT 0,
    le_500              BIGINT NOT NULL DEFAULT 0,
    le_1000             BIGINT NOT NULL DEFAULT 0,
    le_2500             BIGINT NOT NULL DEFAULT 0,
    le_inf              BIGINT NOT NULL DEFAULT 0,
    UNIQUE (window_start, route, status_class)
);


CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
