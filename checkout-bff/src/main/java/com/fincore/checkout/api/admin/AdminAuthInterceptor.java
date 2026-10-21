package com.fincore.checkout.api.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final String adminToken;

    public AdminAuthInterceptor(
            @Value("${fincore.admin.token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String presented = request.getHeader("X-Admin-Token");
        // Constant-time compare: don't let response timing leak the token
        if (presented == null || !MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"code\":\"UNAUTHORIZED\","
                  + "\"message\":\"Admin authentication required\"}");
            return false;
        }
        return true;
    }
}