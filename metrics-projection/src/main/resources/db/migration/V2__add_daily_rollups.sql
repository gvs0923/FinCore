CREATE TABLE payment_metrics_daily (
    id                  BIGSERIAL PRIMARY KEY,
    day                 DATE        NOT NULL,
    status              VARCHAR(32) NOT NULL,
    currency            CHAR(3)     NOT NULL,
    decline_reason      VARCHAR(64) NOT NULL DEFAULT '',
    payment_count       BIGINT      NOT NULL DEFAULT 0,
    volume_minor_units  BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (day, status, currency, decline_reason)
);

CREATE TABLE ledger_metrics_daily (
    id                BIGSERIAL PRIMARY KEY,
    day               DATE          NOT NULL UNIQUE,
    transaction_count BIGINT        NOT NULL DEFAULT 0,
    entry_count       BIGINT        NOT NULL DEFAULT 0,
    total_debits      NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_credits     NUMERIC(19,4) NOT NULL DEFAULT 0
);

CREATE TABLE request_metrics_daily (
    id           BIGSERIAL PRIMARY KEY,
    day          DATE         NOT NULL,
    route        VARCHAR(128) NOT NULL,
    status_class VARCHAR(8)   NOT NULL,
    hit_count    BIGINT NOT NULL DEFAULT 0,
    le_50        BIGINT NOT NULL DEFAULT 0,
    le_100       BIGINT NOT NULL DEFAULT 0,
    le_250       BIGINT NOT NULL DEFAULT 0,
    le_500       BIGINT NOT NULL DEFAULT 0,
    le_1000      BIGINT NOT NULL DEFAULT 0,
    le_2500      BIGINT NOT NULL DEFAULT 0,
    le_inf       BIGINT NOT NULL DEFAULT 0,
    UNIQUE (day, route, status_class)
);