-- Prefixed name: every service shares the fincore DB/public schema.
CREATE TABLE payment_audit_events (
    id            UUID PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_subject VARCHAR(160) NOT NULL,
    actor_name    VARCHAR(160) NOT NULL,
    actor_roles   VARCHAR(160) NOT NULL DEFAULT '',
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(32)  NOT NULL,
    resource_id   VARCHAR(128) NOT NULL,
    details       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    trace_id      VARCHAR(32)
);
CREATE INDEX payment_audit_resource_idx
    ON payment_audit_events (resource_type, resource_id, occurred_at DESC);
CREATE INDEX payment_audit_actor_idx
    ON payment_audit_events (actor_subject, occurred_at DESC);

CREATE FUNCTION payment_audit_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

CREATE TRIGGER payment_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON payment_audit_events
    FOR EACH ROW EXECUTE FUNCTION payment_audit_append_only();
CREATE TRIGGER payment_audit_no_truncate
    BEFORE TRUNCATE ON payment_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION payment_audit_append_only();
