package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.RefundResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class RefundService {

    private final JdbcTemplate jdbc;

    public RefundService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public RefundResponse refund(
            UUID paymentId,
            String idempotencyKey,
            String reason,
            String actor) {

        validate(paymentId, idempotencyKey, reason, actor);
        SettlementLocks.key(jdbc, idempotencyKey);

        // --- Step 1: same key replayed? Return the original result.
        var byKey = findByIdempotencyKey(idempotencyKey);
        if (byKey != null) {
            if (!byKey.paymentId().equals(paymentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another payment");
            }
            return toResponse(byKey, true);
        }

        var merchants = jdbc.query("SELECT merchant_id FROM settlement_captures WHERE payment_id = ?",
                (rs, n) -> rs.getString(1), paymentId);
        if (merchants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Captured payment not found");
        }
        SettlementLocks.merchant(jdbc, merchants.getFirst());
        var frozen = jdbc.query("""
                SELECT b.status FROM settlement_batch_items i
                JOIN settlement_batches b ON b.id = i.batch_id
                WHERE i.payment_id = ? AND b.status <> 'OPEN'
                """, (rs, n) -> rs.getString(1), paymentId);
        if (!frozen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Refund requires a settlement adjustment: batch is already frozen");
        }

        // --- Step 2: lock the capture. Two refunds on the same
        // payment serialize here, not on the outbox write.
        var capture = jdbc.query("""
                SELECT payment_id, amount_minor, currency
                FROM settlement_captures
                WHERE payment_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new Object[]{
                        rs.getObject("payment_id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getString("currency")},
                paymentId);

        if (capture.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Captured payment not found — refunds need a capture");
        }

        // --- Step 3: already refunded? (lost the race or second call)
        Long existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM settlement_refunds
                WHERE payment_id = ?""",
                Long.class, paymentId);
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment already refunded");
        }

        // --- Step 4: stub processor result. A real processor failure
        // would persist status='FAILED' and write NO outbox event.
        Object[] c = capture.getFirst();
        long amount = (long) c[1];
        String currency = (String) c[2];
        UUID refundId = UUID.randomUUID();

        int inserted = jdbc.update("""
                INSERT INTO settlement_refunds (
                    id, payment_id, idempotency_key,
                    amount_minor, currency, reason, actor
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                refundId, paymentId, idempotencyKey,
                amount, currency, reason, actor);
        if (inserted == 0) {
            // Same key arrived concurrently on another instance —
            // its row won; return that result.
            var replay = findByIdempotencyKey(idempotencyKey);
            if (replay == null || !replay.paymentId().equals(paymentId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key conflict");
            }
            return toResponse(replay, true);
        }

        // --- Step 5: refund fact + ledger event in ONE transaction.
        String payload = """
                {"schemaVersion":1,"paymentId":"%s","reference":"refund:%s",\
                "amountMinor":%d,"currency":"%s","originalRef":"payment:%s"}"""
                .formatted(paymentId, refundId, amount, currency, paymentId);

        jdbc.update("""
                INSERT INTO settlement_outbox_events
                    (id, aggregate_id, event_type, payload)
                VALUES (?, ?, 'payment.refunded', ?::jsonb)
                """,
                UUID.randomUUID(), paymentId, payload);

        jdbc.update("""
                INSERT INTO refund_audit_events (id, refund_id, payment_id, actor, action, reason)
                VALUES (?, ?, ?, ?, 'FULL_REFUND', ?)
                """, UUID.randomUUID(), refundId, paymentId, actor, reason);

        jdbc.update("""
                UPDATE settlement_batches b
                SET refunds_minor = b.refunds_minor + ?, net_minor = b.net_minor - ?,
                    updated_at = CURRENT_TIMESTAMP
                FROM settlement_batch_items i
                WHERE i.batch_id = b.id AND i.payment_id = ? AND b.status = 'OPEN'
                """, amount, amount, paymentId);

        return new RefundResponse(
                refundId, paymentId, amount, currency, "REFUNDED", false);
    }

    private RefundRow findByIdempotencyKey(String key) {
        var rows = jdbc.query("""
                SELECT id, payment_id, amount_minor, currency, status
                FROM settlement_refunds
                WHERE idempotency_key = ?""",
                (rs, rowNum) -> new RefundRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("payment_id", UUID.class),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        rs.getString("status")),
                key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static RefundResponse toResponse(
            RefundRow r, boolean replayed) {
        return new RefundResponse(
                r.id(), r.paymentId(), r.amountMinor(),
                r.currency(), r.status(), replayed);
    }

    private static void validate(
            UUID paymentId, String key, String reason, String actor) {
        if (paymentId == null
                || key == null || key.isBlank()
                || reason == null || reason.isBlank()
                || actor == null || actor.isBlank()
                || key.length() > 255 || reason.length() > 1000 || actor.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentId, idempotencyKey, reason, actor required");
        }
    }

    private record RefundRow(
            UUID id, UUID paymentId, long amountMinor,
            String currency, String status) {
    }
}