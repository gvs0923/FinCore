package com.fincore.checkout.api.admin;

import com.fincore.checkout.client.LedgerAdminClient;
import com.fincore.checkout.client.MetricsClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    // One validated metric allowlist — never pass a user-supplied metric
    // name anywhere near SQL or a table name.
    private static final Set<String> ALLOWED_METRICS =
            Set.of("payment_count", "payment_volume");
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final MetricsClient metricsClient;
    private final LedgerAdminClient ledgerClient;

    public AdminController(MetricsClient metricsClient, LedgerAdminClient ledgerClient) {
        this.metricsClient = metricsClient;
        this.ledgerClient = ledgerClient;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(24));
        return Map.of(
                "window", Map.of("from", from.toString(), "to", to.toString()),
                "payments", metricsClient.paymentSummary(from, to));
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
        return metricsClient.paymentTimeseries(from, to);
    }
}