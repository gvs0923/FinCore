package com.fincore.risk.api;

import com.fincore.risk.engine.RiskEngine;
import com.fincore.risk.engine.RiskRequest;
import com.fincore.risk.engine.RiskResult;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/risk")
public class RiskController {

    private final RiskEngine engine;
    private final JdbcTemplate jdbc;

    public RiskController(RiskEngine engine, JdbcTemplate jdbc) {
        this.engine = engine;
        this.jdbc = jdbc;
    }

    @PostMapping("/assess")
    public RiskResult assess(@RequestBody RiskRequest request) {
        return engine.assess(request);
    }

    /** Internal: payments parked for manual review (the admin queue). */
    @GetMapping("/review-queue")
    public List<Map<String, Object>> reviewQueue() {
        return jdbc.queryForList("""
                SELECT payment_id, score, fired_rules, created_at
                FROM risk_decisions
                WHERE decision = 'REVIEW' AND resolution IS NULL
                ORDER BY created_at DESC
                LIMIT 50
                """);
    }

    /**
     * Internal: record the analyst's verdict so the payment leaves the queue.
     * Idempotent — resolving an already-resolved review is a no-op.
     */
    @PostMapping("/review-queue/{paymentId}/resolution")
    public Map<String, Object> resolve(@PathVariable String paymentId,
                                       @RequestParam String action) {
        String resolution = switch (action) {
            case "approve" -> "APPROVE";
            case "reject" -> "REJECT";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be 'approve' or 'reject'");
        };
        int updated = jdbc.update("""
                UPDATE risk_decisions SET resolution = ?, resolved_at = now()
                WHERE payment_id = ? AND decision = 'REVIEW' AND resolution IS NULL
                """, resolution, paymentId);
        return Map.of("paymentId", paymentId, "resolved", updated > 0);
    }
}