package com.fincore.checkout.api.admin;

import com.fincore.checkout.client.AiClient;
import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.client.RiskAdminClient;
import com.fincore.checkout.client.SettlementClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Read-only AI surfaces. Facts always come from the owning service; model
 * output is an annotation on those facts and degrades to null, never to an error.
 */
@RestController
@RequestMapping("/v1/admin")
public class AiAdminController {

    private static final Logger log = LoggerFactory.getLogger(AiAdminController.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final byte[] ERROR_FRAME =
            "event: error\ndata: {\"message\":\"Copilot is temporarily unavailable\"}\n\n"
                    .getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper json = new ObjectMapper();
    private final AiClient ai;
    private final RiskAdminClient risk;
    private final PaymentClient payments;
    private final SettlementClient settlement;

    public AiAdminController(AiClient ai, RiskAdminClient risk, PaymentClient payments,
                             SettlementClient settlement) {
        this.ai = ai;
        this.risk = risk;
        this.payments = payments;
        this.settlement = settlement;
    }

    @GetMapping("/orders/{paymentId}/risk-explanation")
    public AiViews.RiskExplanationView riskExplanation(@PathVariable UUID paymentId) {
        AiViews.RiskDecisionRow d = risk.decision(paymentId);
        List<String> rules = d.fired_rules() == null || d.fired_rules().isBlank()
                ? List.of() : List.of(d.fired_rules().split(","));
        Map<String, Object> signals = parseSignals(d.signals());
        AiViews.RiskExplanationText explanation = null;
        String unavailable = null;
        try {
            explanation = ai.explainRisk(new AiViews.ExplainRequest(
                    d.decision(), d.score(), rules, signals, d.ml_probability(), d.ml_model_version()));
        } catch (RestClientException e) {
            log.warn("Risk explanation unavailable for payment {}", paymentId);
            unavailable = "AI service unavailable";
        }
        return new AiViews.RiskExplanationView(paymentId.toString(), d.decision(), d.score(), rules,
                signals, d.ml_probability(), d.ml_model_version(), d.created_at(), explanation, unavailable);
    }

    @GetMapping("/settlement/reconciliation-runs/{runId}/anomaly-summary")
    public AiViews.ReconAnomalyView anomalySummary(@PathVariable UUID runId) {
        AiViews.ReconAggregates aggregates = settlement.anomalyAggregates(runId);
        try {
            return new AiViews.ReconAnomalyView(aggregates, ai.summarizeRecon(aggregates), null);
        } catch (RestClientException e) {
            log.warn("Reconciliation summary unavailable for run {}", runId);
            return new AiViews.ReconAnomalyView(aggregates, null, "AI service unavailable");
        }
    }

    // POST so the question never lands in access logs or browser history as a query string.
    @PostMapping(value = "/ai/copilot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> copilot(@Valid @RequestBody AiViews.CopilotQuestion question) {
        Map<String, Object> request = Map.of("question", question.question(), "records", records(question.question()));
        StreamingResponseBody body = out -> {
            try {
                ai.copilotStream(request, out);
            } catch (RuntimeException e) {
                log.warn("Copilot stream failed: {}", e.getClass().getSimpleName());
                try {
                    out.write(ERROR_FRAME);
                    out.flush();
                } catch (IOException ignored) {
                    // client already disconnected
                }
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache").header("X-Accel-Buffering", "no").body(body);
    }

    /** Authoritative records for payment ids named in the question, fetched with the admin's own authz. */
    private List<Map<String, String>> records(String question) {
        List<Map<String, String>> records = new ArrayList<>();
        UUID_PATTERN.matcher(question).results().map(MatchResult::group).map(String::toLowerCase)
                .distinct().limit(3).map(UUID::fromString).forEach(id -> {
                    bestEffort(records, "order", id, () -> payments.order(id));
                    bestEffort(records, "risk_decision", id, () -> risk.decision(id));
                });
        return records;
    }

    private void bestEffort(List<Map<String, String>> records, String kind, UUID id, Supplier<Object> lookup) {
        try {
            records.add(Map.of("kind", kind, "ref", id.toString(), "content", json.writeValueAsString(lookup.get())));
        } catch (RestClientException e) {
            log.debug("No {} record for {}", kind, id);
        }
    }

    private Map<String, Object> parseSignals(String signals) {
        if (signals == null || signals.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(signals, new TypeReference<>() {});
        } catch (JacksonException e) {
            return Map.of();
        }
    }
}
