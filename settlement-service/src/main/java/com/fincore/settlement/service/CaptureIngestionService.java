package com.fincore.settlement.service;

import com.fincore.settlement.consumer.CapturedPayment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
public class CaptureIngestionService {

    private final JdbcTemplate jdbc;

    public CaptureIngestionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void ingest(CapturedPayment capture) {
        int inserted = jdbc.update("""
                INSERT INTO settlement_captures (
                    payment_id,
                    merchant_id,
                    merchant_ref,
                    processor_ref,
                    amount_minor,
                    currency,
                    captured_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (payment_id) DO NOTHING
                """,
                capture.paymentId(),
                capture.merchantId(),
                capture.merchantRef(),
                capture.processorRef(),
                capture.amountMinor(),
                capture.currency(),
                Timestamp.from(capture.capturedAt()));

        if (inserted == 1) {
            return;
        }

        Boolean identical = jdbc.queryForObject("""
                SELECT
                    merchant_id = ?
                    AND merchant_ref = ?
                    AND processor_ref = ?
                    AND amount_minor = ?
                    AND currency = ?
                    AND captured_at = ?
                FROM settlement_captures
                WHERE payment_id = ?
                """,
                Boolean.class,
                capture.merchantId(),
                capture.merchantRef(),
                capture.processorRef(),
                capture.amountMinor(),
                capture.currency(),
                Timestamp.from(capture.capturedAt()),
                capture.paymentId());

        if (!Boolean.TRUE.equals(identical)) {
            throw new IllegalStateException(
                    "Conflicting capture for payment "
                            + capture.paymentId());
        }
    }
}