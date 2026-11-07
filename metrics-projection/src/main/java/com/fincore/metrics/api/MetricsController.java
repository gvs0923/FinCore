package com.fincore.metrics.api;

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

@RestController
@RequestMapping("/metrics")
public class MetricsController {

    // Hard cap: nobody may ask for more than 31 days of hourly buckets in
    // one request. Unbounded ranges are how dashboards take down their
    // own backend.
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final MetricsQueryService queries;
    private final com.fincore.metrics.rollup.RebuildService rebuildService;
    private final com.fincore.metrics.consumer.LagService lagService;

    public MetricsController(MetricsQueryService queries,
                             com.fincore.metrics.rollup.RebuildService rebuildService,
                             com.fincore.metrics.consumer.LagService lagService) {
        this.queries = queries;
        this.rebuildService = rebuildService;
        this.lagService = lagService;
    }

    @GetMapping("/payments/summary")
    public Map<String, Object> paymentSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return Map.of(
                "byStatus", queries.paymentSummary(from, to),
                "topDeclines", queries.topDeclineReasons(from, to, 5));
    }

    @GetMapping("/payments/timeseries")
    public List<Map<String, Object>> paymentTimeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return queries.paymentTimeseries(from, to);
    }

    private void checkRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid range: from must be before to and span <= 31 days");
        }
    }

    // ---------------- Request traffic ----------------

    @GetMapping("/requests/timeseries")
    public List<Map<String, Object>> requestTimeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return queries.requestTimeseries(from, to);
    }

    @GetMapping("/requests/summary")
    public Map<String, Object> requestSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        long[] buckets = queries.requestLatencyBuckets(from, to);
        return Map.of(
                "byRoute", queries.requestsByRoute(from, to),
                "errorRate", queries.errorRate(from, to),
                "p50ms", HistogramQuantiles.quantile(0.50, buckets),
                "p95ms", HistogramQuantiles.quantile(0.95, buckets),
                "p99ms", HistogramQuantiles.quantile(0.99, buckets));
    }

    // ---------------- Ledger activity ----------------

    @GetMapping("/ledger/summary")
    public Map<String, Object> ledgerSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return queries.ledgerSummary(from, to);
    }

    @GetMapping("/ledger/timeseries")
    public List<Map<String, Object>> ledgerTimeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return queries.ledgerTimeseries(from, to);
    }

    /** Kafka consumer lag — dashboard health signal. */
    @GetMapping("/lag")
    public Map<String, Object> lag() {
        return lagService.lag();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Instant last = queries.lastProcessedAt();
        return Map.of(
                "lastProcessedAt", last == null ? "" : last.toString(),
                "stale", last == null
                        || Duration.between(last, Instant.now()).toMinutes() > 5);
    }

    /**
     * Destructive operational endpoint: truncates every rollup and replays
     * the Kafka event log from the beginning. Only reachable internally —
     * the BFF exposes it exclusively under /v1/admin/**.
     */
    @org.springframework.web.bind.annotation.PostMapping("/rebuild")
    public Map<String, Object> rebuild() throws Exception {
        rebuildService.rebuildAll();
        return Map.of("rebuild", "started");
    }

    @GetMapping("/risk/summary")
    public Map<String, Object> riskSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        checkRange(from, to);
        return Map.of(
                "byDecision", queries.riskByDecision(from, to),
                "topRules", queries.riskTopRules(from, to, 5));
    }

}