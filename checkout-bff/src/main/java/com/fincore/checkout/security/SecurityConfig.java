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
                                            ApiSecurityErrors errors) throws Exception {
        var authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(
                clients, "/oauth2/authorization");
        authorizationRequests.setAuthorizationRequestCustomizer(
                OAuth2AuthorizationRequestCustomizers.withPkce());

        var oidcLogout = new OidcClientInitiatedLogoutSuccessHandler(clients);
        oidcLogout.setPostLogoutRedirectUri("{baseUrl}/admin");
        oidcLogout.setRedirectStrategy(errors::logoutRedirectAsJson);

        http
            .authorizeHttpRequests(auth -> auth
                // Most specific first. Mutations name their role explicitly.
                .requestMatchers(HttpMethod.POST, "/v1/admin/risk/*/resolve").hasRole(RISK_ANALYST)
                .requestMatchers(HttpMethod.POST, "/v1/admin/settlement/**").hasRole(FINANCE_OPS)
                .requestMatchers(HttpMethod.GET, "/v1/admin/**").hasRole(VIEWER)
                // Fail closed: a new admin POST/PUT/DELETE is denied until someone
                // decides which role owns it.
                .requestMatchers("/v1/admin/**").denyAll()
                .anyRequest().permitAll())
            .oauth2Login(login -> login
                .authorizationEndpoint(a -> a.authorizationRequestResolver(authorizationRequests))
                .userInfoEndpoint(u -> u.userAuthoritiesMapper(roles))
                .defaultSuccessUrl("/admin", true))
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