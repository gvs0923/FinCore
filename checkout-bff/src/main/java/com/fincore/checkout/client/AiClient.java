package com.fincore.checkout.client;

import com.fincore.checkout.api.admin.AiViews;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class AiClient {

    private final RestClient restClient;

    public AiClient(@Value("${fincore.ai.base-url:http://localhost:8000}") String baseUrl,
                    RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        // Bounds time-to-first-byte from a (slow, local) model, not the length of a stream.
        factory.setReadTimeout(Duration.ofSeconds(35));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public AiViews.RiskExplanationText explainRisk(AiViews.ExplainRequest request) {
        return restClient.post().uri("/v1/explain/risk").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(AiViews.RiskExplanationText.class);
    }

    public AiViews.AnomalySummary summarizeRecon(AiViews.ReconAggregates aggregates) {
        return restClient.post().uri("/v1/recon/summarize").contentType(MediaType.APPLICATION_JSON)
                .body(aggregates).retrieve().body(AiViews.AnomalySummary.class);
    }

    /** Relays ai-service's SSE frames byte-for-byte, flushing each chunk so tokens arrive as generated. */
    public void copilotStream(Map<String, Object> request, OutputStream out) {
        restClient.post().uri("/v1/copilot/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).body(request)
                .exchange((req, res) -> {
                    if (!res.getStatusCode().is2xxSuccessful()) {
                        throw new RestClientException("Copilot upstream returned " + res.getStatusCode().value());
                    }
                    try (InputStream in = res.getBody()) {
                        byte[] buffer = new byte[1024];
                        for (int n; (n = in.read(buffer)) != -1; ) {
                            out.write(buffer, 0, n);
                            out.flush();
                        }
                    }
                    return null;
                });
    }
}
