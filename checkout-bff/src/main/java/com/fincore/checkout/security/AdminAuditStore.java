package com.fincore.checkout.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class AdminAuditStore {

    private final JdbcClient jdbc;

    AdminAuditStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void append(UUID requestId, Actor actor, String action, UUID resourceId, String outcome, int status) {
        jdbc.sql("""
                INSERT INTO checkout_bff.audit_events
                    (id, request_id, actor_subject, actor_roles, action, resource_id, outcome, http_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(UUID.randomUUID(), requestId, actor.subject(), String.join(",", actor.roles()),
                        action, resourceId, outcome, status)
                .update();
    }
}
