package com.fincore.checkout.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;

@Configuration
public class OidcClientConfig {

    private static final String OIDC = "/protocol/openid-connect";

    @Bean
    ClientRegistrationRepository clientRegistrations(
            @Value("${fincore.oidc.browser-base}") String browserBase,
            @Value("${fincore.oidc.backchannel-base}") String backchannelBase,
            @Value("${fincore.oidc.client-id}") String clientId,
            @Value("${fincore.oidc.client-secret}") String clientSecret,
            @Value("${fincore.oidc.public-base}") String publicBase) {
        // Explicit endpoints instead of issuer-uri discovery: the BFF boots (and
        // shoppers can check out) even when Keycloak is down.
        return new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId("keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(publicBase + "/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .issuerUri(browserBase)
                .authorizationUri(browserBase + OIDC + "/auth")
                .tokenUri(backchannelBase + OIDC + "/token")
                .jwkSetUri(backchannelBase + OIDC + "/certs")
                .userInfoUri(backchannelBase + OIDC + "/userinfo")
                .userNameAttributeName("preferred_username")
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", browserBase + OIDC + "/logout"))
                .build());
    }
}