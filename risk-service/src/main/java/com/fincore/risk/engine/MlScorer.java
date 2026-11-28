package com.fincore.risk.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class MlScorer {

    public record MlScore(double probability, String modelVersion) {}

    private static final Logger log = LoggerFactory.getLogger(MlScorer.class);
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final boolean enabled;
    private volatile long skipUntilMs;

    @Autowired
    public MlScorer(@Value("${fincore.ai.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${fincore.ai.scoring.enabled:true}") boolean enabled,
                    RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(300));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.enabled = enabled;
    }

    private MlScorer() {
        this.restClient = null;
        this.enabled = false;
    }

    public static MlScorer disabled() {
        return new MlScorer();
    }

    public Optional<MlScore> score(Map<String, Object> features) {
        if (!enabled || System.currentTimeMillis() < skipUntilMs) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = restClient.post().uri("/v1/score")
                    .contentType(MediaType.APPLICATION_JSON).body(features)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (body != null && body.get("probability") instanceof Number p
                    && body.get("modelVersion") instanceof String version
                    && !version.isBlank() && version.length() <= 64
                    && Double.isFinite(p.doubleValue())
                    && p.doubleValue() >= 0 && p.doubleValue() <= 1) {
                return Optional.of(new MlScore(p.doubleValue(), version));
            }
        } catch (RestClientException e) {
            log.warn("ML scoring unavailable; rules-only for {}s", COOLDOWN.toSeconds());
        }
        skipUntilMs = System.currentTimeMillis() + COOLDOWN.toMillis();
        return Optional.empty();
    }
}
