package com.fincore.checkout.security;

import com.fincore.checkout.api.admin.BackOfficeViews;
import com.fincore.checkout.client.SettlementClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RefundActorTest {

    @Test
    void refundUsesTheAuthenticatedSubjectNotAStubActor() throws Exception {
        var actorHeader = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/settlement/refunds", exchange -> {
            actorHeader.set(exchange.getRequestHeaders().getFirst(Actor.SUBJECT_HEADER));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] response = ("{\"id\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + UUID.randomUUID()
                    + "\",\"amountMinor\":100,\"currency\":\"USD\",\"status\":\"REFUNDED\",\"replayed\":false,\"adjustmentBatchId\":null}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var token = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                    Map.of("sub", "operator-123", "preferred_username", "admin"));
            var principal = new DefaultOidcUser(List.of(), token);
            SecurityContextHolder.getContext().setAuthentication(
                    new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak"));
            var builder = RestClient.builder();
            new ActorPropagation().actorHeaders().customize(builder);
            var client = new SettlementClient("http://127.0.0.1:" + server.getAddress().getPort(), builder);
            client.refund(new BackOfficeViews.RefundRequest(UUID.randomUUID(), UUID.randomUUID().toString(), "test"));
            // Identity travels in the trusted header, never in the request body.
            assertEquals("user:operator-123", actorHeader.get());
            assertFalse(body.get().contains("actor"));
        } finally {
            SecurityContextHolder.clearContext();
            server.stop(0);
        }
    }
}
