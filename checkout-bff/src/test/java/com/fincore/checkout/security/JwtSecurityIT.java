package com.fincore.checkout.security;

import com.fincore.checkout.config.TestKafkaConfig;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestKafkaConfig.class)
class JwtSecurityIT {

    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    private static final String ISSUER = "http://localhost:1/realms/test";

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("security-test").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/certs", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            JWKS.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/certs");
    }

    @Autowired MockMvc mvc;

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Test
    void realSignedJwtUsesAllowlistedRoleClaim() throws Exception {
        mvc.perform(get("/v1/admin/me").header("Authorization", "Bearer " + token(KEY, ISSUER,
                        "fincore-bff", Instant.now().plusSeconds(300), "fincore-viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"));
        mvc.perform(get("/v1/admin/me").header("Authorization", "Bearer " + token(KEY, ISSUER,
                        "fincore-bff", Instant.now().plusSeconds(300), "superuser")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongIssuerAudienceExpiryAndSignature() throws Exception {
        String[] invalid = {
                token(KEY, "http://invalid.example/realm", "fincore-bff", Instant.now().plusSeconds(300), "fincore-viewer"),
                token(KEY, ISSUER, "another-service", Instant.now().plusSeconds(300), "fincore-viewer"),
                token(KEY, ISSUER, "fincore-bff", Instant.now().minusSeconds(300), "fincore-viewer"),
                token(new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate(), ISSUER,
                        "fincore-bff", Instant.now().plusSeconds(300), "fincore-viewer")
        };
        for (String token : invalid) {
            mvc.perform(get("/v1/admin/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static String token(RSAKey key, String issuer, String audience, Instant expires, String role) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("service-account-test")
                .audience(audience).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(expires)).claim("azp", "fincore-loadtest")
                .claim("roles", List.of(role)).build();
        var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), claims);
        signed.sign(new RSASSASigner(key));
        return signed.serialize();
    }
}
