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

    public MetricsController(MetricsQueryService queries) {
        this.queries = queries;
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
}