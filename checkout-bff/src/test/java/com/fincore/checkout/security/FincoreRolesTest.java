package com.fincore.checkout.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FincoreRolesTest {

    @Test
    void oidcRolesAreAllowlistedAndUnknownOrMissingClaimsDoNotGrantAccess() {
        var mapper = new FincoreRoles();
        var token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "operator", "roles", List.of("fincore-viewer", "fincore-finance-ops", "admin")));
        assertEquals(List.of("ROLE_VIEWER", "ROLE_FINANCE_OPS"),
                mapper.mapAuthorities(List.of(new OidcUserAuthority(token))).stream()
                        .map(GrantedAuthority::getAuthority).toList());
        var noRoles = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("sub", "operator"));
        assertTrue(mapper.mapAuthorities(List.of(new OidcUserAuthority(noRoles))).isEmpty());
    }
}
