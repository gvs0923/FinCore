package com.fincore.checkout.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;

@Configuration
@Profile("!local & !test")
class SecurityDeploymentChecks {

    SecurityDeploymentChecks(@Value("${fincore.oidc.public-base}") String publicBase,
                             @Value("${fincore.oidc.browser-base}") String issuer,
                             @Value("${fincore.oidc.client-secret}") String secret,
                             @Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        if (!secure || !https(publicBase) || !https(issuer)
                || secret.isBlank() || secret.startsWith("dev-")) {
            throw new IllegalStateException("Non-local deployments require HTTPS, secure cookies and a non-demo OIDC secret");
        }
    }

    private static boolean https(String value) {
        URI uri = URI.create(value);
        return "https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
    }
}
