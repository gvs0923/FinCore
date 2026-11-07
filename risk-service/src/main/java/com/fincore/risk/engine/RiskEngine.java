package com.fincore.risk.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskEngine {

    private static final long HIGH_AMOUNT_MINOR = 1_000_000;  // $10k
    private static final int REVIEW_AT = 30;
    private static final int BLOCK_AT = 60;

    private final VelocityCounter velocity;
    private final JdbcTemplate jdbc;

    public RiskEngine(VelocityCounter velocity, JdbcTemplate jdbc) {
        this.velocity = velocity;
        this.jdbc = jdbc;
    }

    public RiskResult assess(RiskRequest r) {
        int score = 0;
        List<String> fired = new ArrayList<>();

        // Rule 1: big amounts get scrutiny
        if (r.amount() > HIGH_AMOUNT_MINOR) {
            score += 40;
            fired.add("high_amount");
        }

        // Rule 2: card velocity — >5/min blocks, >3/min reviews
        long cardHits = velocity.hit("risk:card:" + r.cardFingerprint());
        if (cardHits > 5) {
            score += 50;
            fired.add("card_velocity_block");
        } else if (cardHits > 3) {
            score += 20;
            fired.add("card_velocity");
        }

        // Rule 3: IP velocity — looser threshold, shared IPs exist
        long ipHits = velocity.hit("risk:ip:" + r.ipAddress());
        if (ipHits > 8) {
            score += 30;
            fired.add("ip_velocity");
        }

        // Rule 4: first-seen device adds a little doubt
        long seen = velocity.hit("risk:device:seen:" + r.deviceId());
        if (seen == 1L) {
            score += 15;
            fired.add("new_device");
        }

        String decision = score >= BLOCK_AT ? "BLOCK"
                        : score >= REVIEW_AT ? "REVIEW" : "ALLOW";

        jdbc.update("""
                INSERT INTO risk_decisions (payment_id, decision, score, fired_rules)
                VALUES (?, ?, ?, ?)""",
                r.paymentId(), decision, score, String.join(",", fired));

        return new RiskResult(decision, score, fired);
    }
}