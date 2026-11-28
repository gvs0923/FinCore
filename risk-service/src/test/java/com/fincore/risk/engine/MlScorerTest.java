package com.fincore.risk.engine;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MlScorerTest {
    private HttpServer server;
    private MlScorer scorer;
    private final AtomicInteger requests = new AtomicInteger();
    private String response = "{\"probability\":0.9,\"modelVersion\":\"test-v1\"}";
    private int status = 200;
    private long delayNanos;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/score", exchange -> {
            requests.incrementAndGet();
            java.util.concurrent.locks.LockSupport.parkNanos(delayNanos);
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        scorer = new MlScorer("http://127.0.0.1:" + server.getAddress().getPort(), true, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsValidScore() {
        assertThat(scorer.score(Map.of("amountMinor", 100)))
                .contains(new MlScorer.MlScore(0.9, "test-v1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "not-json", "[]",
            "{\"probability\":-0.1,\"modelVersion\":\"v1\"}",
            "{\"probability\":1.1,\"modelVersion\":\"v1\"}",
            "{\"probability\":\"0.9\",\"modelVersion\":\"v1\"}",
            "{\"probability\":0.9,\"modelVersion\":\" \"}"
    })
    void invalidResponseFallsBackAndEntersCooldown(String body) {
        response = body;
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void rejectsModelVersionTooLongForPersistence() {
        response = "{\"probability\":0.9,\"modelVersion\":\"" + "v".repeat(65) + "\"}";
        assertThat(scorer.score(Map.of())).isEmpty();
    }

    @Test
    void serverFailureFallsBackAndEntersCooldown() {
        status = 503;
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void slowServerTimesOutAndFallsBack() {
        delayNanos = java.time.Duration.ofSeconds(1).toNanos();
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(scorer.score(Map.of())).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void connectionFailureFallsBack() {
        server.stop(0);
        assertThat(scorer.score(Map.of())).isEmpty();
    }

    @Test
    void disabledScoringMakesNoRequest() {
        assertThat(MlScorer.disabled().score(Map.of())).isEmpty();
        MlScorer disabled = new MlScorer("http://127.0.0.1:" + server.getAddress().getPort(),
                false, RestClient.builder());
        assertThat(disabled.score(Map.of())).isEmpty();
        assertThat(requests.get()).isZero();
    }
}
