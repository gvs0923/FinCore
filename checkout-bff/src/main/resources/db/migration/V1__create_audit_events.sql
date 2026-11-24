CREATE TABLE checkout_bff.audit_events (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_subject VARCHAR(160) NOT NULL,
    actor_roles VARCHAR(160) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_id UUID,
    outcome VARCHAR(20) NOT NULL,
    http_status INTEGER NOT NULL
);

CREATE INDEX audit_events_request_idx ON checkout_bff.audit_events (request_id);
CREATE INDEX audit_events_occurred_idx ON checkout_bff.audit_events (occurred_at);
