package com.fincore.settlement.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

final class SettlementLocks {
    private SettlementLocks() {}

    static void key(JdbcTemplate jdbc, String key) {
        lock(jdbc, 1, key);
    }

    static void merchant(JdbcTemplate jdbc, String merchant) {
        lock(jdbc, 2, merchant);
    }

    static void batch(JdbcTemplate jdbc, UUID batchId) {
        var merchants = jdbc.query("SELECT merchant_id FROM settlement_batches WHERE id = ?",
                (rs, n) -> rs.getString(1), batchId);
        if (merchants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement batch not found");
        }
        merchant(jdbc, merchants.getFirst());
    }

    private static void lock(JdbcTemplate jdbc, int namespace, String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, hashtext(?))", rs -> {}, namespace, key);
    }
}
