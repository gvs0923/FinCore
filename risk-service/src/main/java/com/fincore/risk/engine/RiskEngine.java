package com.fincore.risk.engine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

@Service
public class RiskEngine {

    private static final long HIGH_AMOUNT_MINOR = 1_000_000;  // $10k
    private static final int REVIEW_AT = 30;
    private static final int BLOCK_AT = 60;
    private final DeviceRegistry devices;
    private final ObjectMapper json = new ObjectMapper();

    private final VelocityCounter velocity;
    private final JdbcTemplate jdbc;

    public RiskEngine(VelocityCounter velocity,
                  DeviceRegistry devices,
                  JdbcTemplate jdbc) {
        this.velocity = velocity;
        this.devices = devices;
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
        boolean newDevice = devices.isFirstSeen(r.deviceId());

        if (newDevice) {
            score += 15;
          fired.add("new_device");
        }

        // Rule 5: device context drift — weak signals, add scrutiny not blocks
        boolean countryChanged = devices.contextChanged(
                r.deviceId(), "country", r.context().get("country"));
        boolean timezoneChanged = devices.contextChanged(
                r.deviceId(), "timezone", r.context().get("timezone"));
        boolean languageChanged = devices.contextChanged(
                r.deviceId(), "language", r.context().get("language"));

        if (countryChanged) {
            score += 30;
            fired.add("country_changed");
        }
        if (timezoneChanged) {
            score += 10;
            fired.add("timezone_changed");
        }
        if (languageChanged) {
            score += 5;
            fired.add("language_changed");
        }

        String decision = score >= BLOCK_AT ? "BLOCK"
                        : score >= REVIEW_AT ? "REVIEW" : "ALLOW";

        Map<String, Object> signals = Map.of(
        "cardHits", cardHits,
        "ipHits", ipHits,
        "newDevice", newDevice,
        "highAmount", r.amount() > HIGH_AMOUNT_MINOR,
        "countryChanged", countryChanged,
        "timezoneChanged", timezoneChanged,
        "languageChanged", languageChanged);

        jdbc.update("""
        INSERT INTO risk_decisions
            (payment_id, decision, score, fired_rules, signals)
        VALUES (?, ?, ?, ?, CAST(? AS jsonb))
        """,
        r.paymentId(),
        decision,
        score,
        String.join(",", fired),
        json.writeValueAsString(signals));

        return new RiskResult(decision, score, fired, signals);
    }
}