package com.fincore.settlement.audit;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Component
public class AuditLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Tracer tracer;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper json, Tracer tracer) {
        this.jdbc = jdbc;
        this.json = json;
        this.tracer = tracer;
    }

    /** MANDATORY: throws if called outside a transaction, so the audit row can never
     *  commit without the change it describes, or the change without its audit row. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String action, String resourceType, Object resourceId,
                       Map<String, ?> details) {
        TraceContext trace = tracer.currentTraceContext().context();
        jdbc.update("""
                INSERT INTO settlement_audit_events
                    (id, actor_subject, actor_name, actor_roles, action,
                     resource_type, resource_id, details, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                UUID.randomUUID(), actor.subject(), actor.name(), actor.roles(), action,
                resourceType, String.valueOf(resourceId), json.writeValueAsString(details),
                trace == null ? null : trace.traceId());
    }
}
