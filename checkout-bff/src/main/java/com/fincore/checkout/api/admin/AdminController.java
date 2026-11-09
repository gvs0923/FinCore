package com.fincore.checkout.api.admin;

import com.fincore.checkout.client.LedgerAdminClient;
import com.fincore.checkout.client.MetricsClient;
import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.client.RiskAdminClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    // One validated metric allowlist — never pass a user-supplied metric
    // name anywhere near SQL or a table name.
    private static final Set<String> ALLOWED_METRICS =
            Set.of("payment_count", "payment_volume",
                    "request_count", "ledger_transactions");
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final MetricsClient metricsClient;
    private final LedgerAdminClient ledgerClient;
    private final RiskAdminClient riskAdminClient;
    private final PaymentClient paymentClient;

    public AdminController(MetricsClient metricsClient,
                           LedgerAdminClient ledgerClient,
                           RiskAdminClient riskAdminClient,
                           PaymentClient paymentClient) {
        this.metricsClient = metricsClient;
        this.ledgerClient = ledgerClient;
        this.riskAdminClient = riskAdminClient;
        this.paymentClient = paymentClient;
    }

    @GetMapping("/overview")
    public RiskViews.Overview overview() {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(24));
        return new RiskViews.Overview(
        Map.of("from", from.toString(), "to", to.toString()),
        metricsClient.paymentSummary(from, to),
        metricsClient.requestSummary(from, to),
        metricsClient.ledgerSummary(from, to),
        metricsClient.riskSummary(from, to),
        Map.of(
                "status", metricsClient.status(),
                "lag", metricsClient.lag()));
    }

    @GetMapping("/integrity/balances")
    public Map<String, Object> ledgerIntegrity() {
        return ledgerClient.checkIntegrity();
    }

    @GetMapping("/metrics/timeseries")
    public List<Map<String, Object>> timeseries(
            @RequestParam String metric,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(defaultValue = "hour") String bucket) {

        if (!ALLOWED_METRICS.contains(metric)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown metric: " + metric);
        }
        if (!"hour".equals(bucket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only hourly buckets are available yet");
        }
        if (!from.isBefore(to)
                || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid range: must be <= 31 days");
        }
        return switch (metric) {
            case "request_count" -> metricsClient.requestTimeseries(from, to);
            case "ledger_transactions" -> metricsClient.ledgerTimeseries(from, to);
            default -> metricsClient.paymentTimeseries(from, to);
        };
    }

    @GetMapping("/risk/queue")
    public List<RiskViews.ReviewItem> riskQueue() {
        return riskAdminClient.reviewQueue();
    }

    @PostMapping("/risk/{paymentId}/resolve")
    public Map<String, Object> resolveRisk(@PathVariable UUID paymentId,
                                           @RequestParam String action) {
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be 'approve' or 'reject'");
        }
        String id = paymentId.toString();
        try {
            Map<String, Object> payment = paymentClient.resolvePayment(id, action);
            riskAdminClient.markResolved(id, action);
            return payment;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        } catch (HttpClientErrorException.Conflict e) {
            // Already out of REVIEW (another analyst, or an earlier attempt
            // whose queue update failed): record what actually happened so
            // the stale entry drains, then report the conflict.
            Object status = paymentClient.getPayment(id).get("status");
            if (!"REVIEW".equals(status)) {
                riskAdminClient.markResolved(id,
                        "CAPTURED".equals(status) ? "approve" : "reject");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment is no longer under review (now " + status + ")");
        }
    }
}