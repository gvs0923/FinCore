package com.fincore.checkout.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

@Component
public class ApiSecurityErrors {

    private final JsonMapper json;

    public ApiSecurityErrors(JsonMapper json) {
        this.json = json;
    }

    void unauthorized(HttpServletRequest request, HttpServletResponse response,
                      AuthenticationException e) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, Map.of("type", "about:blank",
                "code", "UNAUTHORIZED", "message", "Admin authentication required"));
    }

    void forbidden(HttpServletRequest request, HttpServletResponse response,
                   AccessDeniedException e) throws IOException {
        // A missing CSRF token is also an AccessDeniedException; don't tell the
        // user "your role" is the problem when it's a stale page.
        boolean csrf = e instanceof CsrfException;
        write(response, HttpStatus.FORBIDDEN, Map.of("type", "about:blank",
                "code", csrf ? "CSRF_TOKEN_INVALID" : "FORBIDDEN",
                "message", csrf ? "Session token missing or expired; reload the page"
                                : "Your role does not permit this action"));
    }

    /** Logout is called via fetch, which can't follow a cross-origin redirect. */
    void logoutRedirectAsJson(HttpServletRequest request, HttpServletResponse response,
                              String url) throws IOException {
        write(response, HttpStatus.OK, Map.of("redirectUrl", url));
    }

    private void write(HttpServletResponse response, HttpStatus status,
                       Map<String, String> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(body));
    }
}