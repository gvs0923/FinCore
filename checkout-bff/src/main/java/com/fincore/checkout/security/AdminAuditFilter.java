package com.fincore.checkout.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

class AdminAuditFilter extends OncePerRequestFilter {

    private static final Pattern RESOURCE = Pattern.compile("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:/|$)");
    private static final Actor ANONYMOUS = new Actor("anonymous", "anonymous", List.of());
    private final AdminAuditStore store;

    AdminAuditFilter(AdminAuditStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID requestId = UUID.randomUUID();
        boolean mutation = !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        var match = RESOURCE.matcher(request.getRequestURI());
        UUID resource = match.find() ? UUID.fromString(match.group().substring(1, 37)) : null;
        String action = action(request);
        if (mutation) {
            try {
                store.append(requestId, Actor.current().orElse(ANONYMOUS), action, resource, "ATTEMPT", 0);
            } catch (DataAccessException e) {
                response.setStatus(503);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"AUDIT_UNAVAILABLE\",\"message\":\"Admin changes temporarily unavailable\"}");
                return;
            }
        }
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : 500;
            if (mutation || status >= 400) {
                try {
                    store.append(requestId, Actor.current().orElse(ANONYMOUS), action, resource,
                            status < 400 ? "SUCCESS" : "REJECTED", status);
                } catch (DataAccessException e) {
                    LoggerFactory.getLogger("fincore.audit").error(
                            "admin_audit_outcome_unavailable request_id={} status={}", requestId, status);
                }
            }
        }
    }

    private static String action(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.matches("/v1/admin/risk/[0-9a-fA-F-]{36}/resolve")) {
            return switch (String.valueOf(request.getParameter("action"))) {
                case "approve" -> "RISK_APPROVE";
                case "reject" -> "RISK_REJECT";
                default -> "RISK_RESOLVE_INVALID";
            };
        }
        if (path.equals("/v1/admin/settlement/refunds")) return "REFUND";
        if (path.equals("/v1/admin/settlement/batches")) return "SETTLEMENT_BATCH";
        if (path.matches("/v1/admin/settlement/batches/[0-9a-fA-F-]{36}/payout")) return "SETTLEMENT_PAYOUT";
        if (path.matches("/v1/admin/settlement/batches/[0-9a-fA-F-]{36}/reconciliation")) return "RECONCILIATION";
        return "ADMIN_REQUEST";
    }
}
