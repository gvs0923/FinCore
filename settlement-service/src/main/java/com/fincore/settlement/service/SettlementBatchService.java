package com.fincore.settlement.service;

import com.fincore.settlement.api.BatchModels.BatchItemResponse;
import com.fincore.settlement.api.BatchModels.BatchResponse;
import com.fincore.settlement.domain.FeePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SettlementBatchService {

    private final JdbcTemplate jdbc;

    public SettlementBatchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BatchResponse createOrRefresh(
            String merchantId,
            String currency,
            LocalDate settlementDate) {
        validateScope(merchantId, currency, settlementDate);
        SettlementLocks.merchant(jdbc, merchantId);

        jdbc.update("""
                INSERT INTO settlement_batches (
                    id,
                    merchant_id,
                    currency,
                    settlement_date
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (
                    merchant_id,
                    currency,
                    settlement_date
                ) DO NOTHING
                """,
                UUID.randomUUID(),
                merchantId,
                currency,
                Date.valueOf(settlementDate));

        BatchResponse batch = jdbc.queryForObject("""
                SELECT *
                FROM settlement_batches
                WHERE merchant_id = ?
                  AND currency = ?
                  AND settlement_date = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new BatchResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("merchant_id"),
                        rs.getString("currency"),
                        rs.getDate("settlement_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getLong("capture_count"),
                        rs.getLong("gross_minor"),
                        rs.getLong("fees_minor"),
                        rs.getLong("refunds_minor"),
                        rs.getLong("net_minor")),
                merchantId,
                currency,
                Date.valueOf(settlementDate));

        if (!"OPEN".equals(batch.status())) {
            return batch;
        }

        Timestamp start = Timestamp.from(
                settlementDate.atStartOfDay(ZoneOffset.UTC).toInstant());

        Timestamp end = Timestamp.from(
                settlementDate.plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());

        List<CaptureCandidate> candidates = jdbc.query("""
                SELECT
                    c.payment_id,
                    c.processor_ref,
                    c.amount_minor
                FROM settlement_captures c
                WHERE c.merchant_id = ?
                  AND c.currency = ?
                  AND c.captured_at >= ?
                  AND c.captured_at < ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM settlement_batch_items i
                      WHERE i.payment_id = c.payment_id
                  )
                ORDER BY c.payment_id
                LIMIT 10001
                """,
                (rs, rowNum) -> new CaptureCandidate(
                        rs.getObject("payment_id", UUID.class),
                        rs.getString("processor_ref"),
                        rs.getLong("amount_minor")),
                merchantId,
                currency,
                start,
                end);

        if (candidates.size() > 10_000) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Batch exceeds the demo processing limit");
        }

        for (CaptureCandidate capture : candidates) {
            long fee = FeePolicy.calculate(
                    capture.amountMinor(),
                    currency);

            jdbc.update("""
                    INSERT INTO settlement_batch_items (
                        payment_id,
                        batch_id,
                        processor_ref,
                        amount_minor,
                        fee_minor,
                        fee_policy_version
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    capture.paymentId(),
                    batch.id(),
                    capture.processorRef(),
                    capture.amountMinor(),
                    fee,
                    FeePolicy.VERSION);
        }

        jdbc.update("""
                UPDATE settlement_batches
                SET capture_count = totals.capture_count,
                    gross_minor = totals.gross_minor,
                    fees_minor = totals.fees_minor,
                    refunds_minor = totals.refunds_minor,
                    net_minor =
                        totals.gross_minor
                        - totals.fees_minor
                        - totals.refunds_minor,
                    updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT
                        COUNT(i.payment_id) AS capture_count,
                        COALESCE(SUM(i.amount_minor), 0)::BIGINT
                            AS gross_minor,
                        COALESCE(SUM(i.fee_minor), 0)::BIGINT
                            AS fees_minor,
                        COALESCE(SUM(r.amount_minor), 0)::BIGINT
                            AS refunds_minor
                    FROM settlement_batch_items i
                    LEFT JOIN settlement_refunds r
                        ON r.payment_id = i.payment_id
                        AND r.status = 'REFUNDED'
                    WHERE i.batch_id = ?
                ) totals
                WHERE settlement_batches.id = ?
                """,
                batch.id(),
                batch.id());

        return get(batch.id());
    }

    @Transactional(readOnly = true)
    public BatchResponse get(UUID batchId) {
        List<BatchResponse> batches = jdbc.query("""
                SELECT *
                FROM settlement_batches
                WHERE id = ?
                """,
                (rs, rowNum) -> new BatchResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("merchant_id"),
                        rs.getString("currency"),
                        rs.getDate("settlement_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getLong("capture_count"),
                        rs.getLong("gross_minor"),
                        rs.getLong("fees_minor"),
                        rs.getLong("refunds_minor"),
                        rs.getLong("net_minor")),
                batchId);

        if (batches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Settlement batch not found");
        }

        return batches.getFirst();
    }

    @Transactional(readOnly = true)
    public List<BatchItemResponse> items(
            UUID batchId,
            int limit,
            long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Limit must be 1–100 and offset must be non-negative");
        }

        get(batchId);

        return jdbc.query("""
                SELECT
                    payment_id,
                    processor_ref,
                    amount_minor,
                    fee_minor,
                    fee_policy_version
                FROM settlement_batch_items
                WHERE batch_id = ?
                ORDER BY payment_id
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new BatchItemResponse(
                        rs.getObject("payment_id", UUID.class),
                        rs.getString("processor_ref"),
                        rs.getLong("amount_minor"),
                        rs.getLong("fee_minor"),
                        rs.getString("fee_policy_version")),
                batchId,
                limit,
                offset);
    }

    @Transactional
    public BatchResponse payout(UUID batchId) {
        SettlementLocks.batch(jdbc, batchId);
        List<Object[]> rows = jdbc.query("""
                SELECT status, merchant_id, currency,
                       settlement_date, gross_minor, fees_minor,
                       refunds_minor, net_minor
                FROM settlement_batches
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new Object[]{
                        rs.getString("status"),
                        rs.getString("merchant_id"),
                        rs.getString("currency"),
                        rs.getDate("settlement_date"),
                        rs.getLong("gross_minor"),
                        rs.getLong("fees_minor"),
                        rs.getLong("refunds_minor"),
                        rs.getLong("net_minor")},
                batchId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Settlement batch not found");
        }

        Object[] b = rows.getFirst();
        if (!"RECONCILED".equals(b[0])) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only RECONCILED batches can be paid out, not " + b[0]);
        }

        long net = (long) b[7];
        if (net <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Batch has no positive payout (net=" + net + ")");
        }

        // Ledger posting is idempotent by this reference — a Kafka
        // redelivery of the same payout event is rejected there.
        String payload = """
                {"schemaVersion":1,"batchId":"%s","reference":"settlement:%s",\
                "merchantId":"%s","currency":"%s","grossMinor":%d,\
                "feesMinor":%d,"refundsMinor":%d,"netMinor":%d}"""
                .formatted(batchId, batchId, b[1], b[2],
                        (long) b[4], (long) b[5], (long) b[6], net);

        jdbc.update("""
                INSERT INTO settlement_outbox_events
                    (id, aggregate_id, event_type, payload)
                VALUES (?, ?, 'settlement.payout', ?::jsonb)
                """, UUID.randomUUID(), batchId, payload);

        jdbc.update("""
                UPDATE settlement_batches
                SET status = 'PAID_OUT',
                    paid_out_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, batchId);

        return get(batchId);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> list(int limit, long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit 1-100, offset >= 0");
        }
        return jdbc.query("""
                SELECT id, merchant_id, currency, settlement_date,
                       status, capture_count, gross_minor, fees_minor,
                       refunds_minor, net_minor
                FROM settlement_batches
                ORDER BY settlement_date DESC, id
                LIMIT ? OFFSET ?
                """,
                (rs, n) -> new BatchResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("merchant_id"),
                        rs.getString("currency"),
                        rs.getDate("settlement_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getLong("capture_count"),
                        rs.getLong("gross_minor"),
                        rs.getLong("fees_minor"),
                        rs.getLong("refunds_minor"),
                        rs.getLong("net_minor")),
                limit, offset);
    }

    

    private static void validateScope(
            String merchantId,
            String currency,
            LocalDate settlementDate) {
        if (merchantId == null || merchantId.isBlank()
                || merchantId.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid merchant ID");
        }

        if (!"USD".equals(currency)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Settlement supports USD only");
        }

        if (settlementDate == null
                || !settlementDate.isBefore(
                        LocalDate.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Settlement date must be a completed UTC day");
        }
    }

    private record CaptureCandidate(
            UUID paymentId,
            String processorRef,
            long amountMinor) {
    }
}