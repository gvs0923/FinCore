package com.fincore.checkout.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class FincoreRoles implements GrantedAuthoritiesMapper {

    public static final String VIEWER = "VIEWER";
    public static final String RISK_ANALYST = "RISK_ANALYST";
    public static final String FINANCE_OPS = "FINANCE_OPS";

    // Allowlist: an IdP role we don't know maps to nothing, never to a guess.
    private static final Map<String, String> BY_IDP_ROLE = Map.of(
            "fincore-viewer", VIEWER,
            "fincore-risk-analyst", RISK_ANALYST,
            "fincore-finance-ops", FINANCE_OPS);

    @Override
    public Collection<GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .filter(OidcUserAuthority.class::isInstance)
                .map(a -> ((OidcUserAuthority) a).getIdToken().getClaimAsStringList("roles"))
                .flatMap(roles -> toAuthorities(roles).stream())
                .distinct()
                .toList();
    }

    public JwtAuthenticationToken fromJwt(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, toAuthorities(jwt.getClaimAsStringList("roles")),
                "token:" + jwt.getSubject());
    }

    private static List<GrantedAuthority> toAuthorities(List<String> idpRoles) {
        if (idpRoles == null) return List.of();
        return idpRoles.stream()
                .map(BY_IDP_ROLE::get)
                .filter(Objects::nonNull)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}