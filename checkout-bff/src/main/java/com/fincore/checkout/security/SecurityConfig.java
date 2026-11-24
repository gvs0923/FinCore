package com.fincore.checkout.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;

import static com.fincore.checkout.security.FincoreRoles.*;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClientRegistrationRepository clients,
                                            FincoreRoles roles,
                                            ApiSecurityErrors errors,
                                            AdminAuditStore audit,
                                            @org.springframework.beans.factory.annotation.Value("${fincore.oidc.public-base}") String publicBase) throws Exception {
        var authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(
                clients, "/oauth2/authorization");
        authorizationRequests.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());

        var oidcLogout = new OidcClientInitiatedLogoutSuccessHandler(clients);
        oidcLogout.setPostLogoutRedirectUri(publicBase + "/admin");
        oidcLogout.setRedirectStrategy(errors::logoutRedirectAsJson);

        http
            .addFilterBefore(new AdminAuditFilter(audit), org.springframework.security.web.csrf.CsrfFilter.class)
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                            + "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")))
            .authorizeHttpRequests(auth -> auth
                // Most specific first. Mutations name their role explicitly.
                .requestMatchers(HttpMethod.POST, "/v1/admin/risk/*/resolve").hasRole(RISK_ANALYST)
                .requestMatchers(HttpMethod.POST, "/v1/admin/settlement/batches",
                        "/v1/admin/settlement/batches/*/payout",
                        "/v1/admin/settlement/batches/*/reconciliation",
                        "/v1/admin/settlement/refunds").hasRole(FINANCE_OPS)
                .requestMatchers(HttpMethod.GET, "/v1/admin/**").hasRole(VIEWER)
                // Fail closed: a new admin POST/PUT/DELETE is denied until someone
                // decides which role owns it.
                .requestMatchers("/v1/admin/**").denyAll()
                .anyRequest().permitAll())
            .oauth2Login(login -> login
                .authorizationEndpoint(a -> a.authorizationRequestResolver(authorizationRequests))
                .userInfoEndpoint(u -> u.userAuthoritiesMapper(roles))
                .defaultSuccessUrl(publicBase + "/admin", true))
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(roles::fromJwt)))
            .logout(logout -> logout.logoutSuccessHandler(oidcLogout))
            // Shopper endpoints carry no ambient credential, so CSRF adds nothing there.
            // Bearer-token requests are exempted automatically by the resource server.
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/v1/checkout/**"))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errors::unauthorized)
                .accessDeniedHandler(errors::forbidden));
        return http.build();
    }

    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }
}