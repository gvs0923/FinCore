package com.fincore.checkout.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
class AdminAccessDeniedAudit {

    private static final Logger audit = LoggerFactory.getLogger("fincore.audit");

    @EventListener
    void onDenied(AuthorizationDeniedEvent<?> event) {
        if (!(event.getObject() instanceof HttpServletRequest request)
                || !request.getRequestURI().startsWith("/v1/admin/")) {
            return;
        }
        Authentication auth = event.getAuthentication().get();
        String actor = auth == null || auth instanceof AnonymousAuthenticationToken
                ? "anonymous" : Actor.from(auth).subject();
        audit.warn("admin_access_denied actor={} method={} path={}",
                actor, request.getMethod(), "/v1/admin/**");
    }
}
