package com.fincore.checkout.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public record Actor(String subject, String displayName, List<String> roles) {

    public static final String SUBJECT_HEADER = "X-Fincore-Actor-Subject";
    public static final String NAME_HEADER = "X-Fincore-Actor-Name";
    public static final String ROLES_HEADER = "X-Fincore-Actor-Roles";

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._@:-]");

    public static Optional<Actor> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(from(auth));
    }

    public static Actor from(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return switch (auth.getPrincipal()) {
            case OidcUser user -> new Actor("user:" + clean(user.getSubject()),
                    clean(user.getPreferredUsername()), roles);
            case Jwt jwt -> new Actor("token:" + clean(jwt.getSubject()),
                    clean(jwt.getClaimAsString("azp")), roles);
            default -> throw new IllegalStateException(
                    "Unsupported principal " + auth.getPrincipal().getClass().getSimpleName());
        };
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String safe = UNSAFE.matcher(value).replaceAll("_");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }
}