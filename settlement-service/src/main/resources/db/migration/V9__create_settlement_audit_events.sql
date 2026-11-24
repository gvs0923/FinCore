-- Prefixed name: every service shares the fincore DB/public schema.
CREATE TABLE settlement_audit_events (
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
CREATE INDEX settlement_audit_resource_idx
    ON settlement_audit_events (resource_type, resource_id, occurred_at DESC);
CREATE INDEX settlement_audit_actor_idx
    ON settlement_audit_events (actor_subject, occurred_at DESC);

CREATE FUNCTION settlement_audit_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

CREATE TRIGGER settlement_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON settlement_audit_events
    FOR EACH ROW EXECUTE FUNCTION settlement_audit_append_only();
CREATE TRIGGER settlement_audit_no_truncate
    BEFORE TRUNCATE ON settlement_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION settlement_audit_append_only();
