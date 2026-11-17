package com.fincore.ledger.service;

import com.fincore.ledger.TestcontainersConfiguration;
import com.fincore.ledger.consumer.PaymentCapturedConsumer;
import com.fincore.ledger.consumer.PaymentRefundedConsumer;
import com.fincore.ledger.consumer.SettlementPayoutConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
class SettlementLedgerIT {
    @Autowired private PaymentCapturedConsumer captures;
    @Autowired private PaymentRefundedConsumer refunds;
    @Autowired private SettlementPayoutConsumer payouts;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void captureRefundAndPayoutRemainBalancedAndIdempotent(boolean refunded) {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        String capture = """
                {"paymentId":"%s","amount":20000,"currency":"USD"}
                """.formatted(paymentId);
        captures.onPaymentCaptured(capture);
        captures.onPaymentCaptured(capture);
        long refundMinor = refunded ? 10_000 : 0;
        if (refunded) {
            UUID secondPayment = UUID.randomUUID();
            captures.onPaymentCaptured("""
                    {"paymentId":"%s","amount":10000,"currency":"USD"}
                    """.formatted(secondPayment));
            String refund = """
                    {"paymentId":"%s","reference":"refund:%s","amountMinor":10000,
                     "currency":"USD","originalRef":"payment:%s"}
                    """.formatted(secondPayment, refundId, secondPayment);
            refunds.onRefunded(refund);
            refunds.onRefunded(refund);
            assertJournal("refund:" + refundId, 2, "100.00");
            assertJournal("payment:" + secondPayment, 2, "100.00");
        }
        long gross = 20_000 + refundMinor;
        long fees = refunded ? 930 : 610;
        String payout = """
                {"batchId":"%s","reference":"settlement:%s","grossMinor":%d,
                 "feesMinor":%d,"refundsMinor":%d,"netMinor":%d,"currency":"USD"}
                """.formatted(batchId, batchId, gross, fees, refundMinor, gross - fees - refundMinor);
        payouts.onPayout(payout);
        payouts.onPayout(payout);
        assertJournal("settlement:" + batchId, 3, "200.00");
        assertJournal("payment:" + paymentId, 2, "200.00");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateRefundRecoveryIsBalancedAndDoesNotRewritePayout(boolean recoveryFirst) {
        UUID paymentId = UUID.randomUUID(), refundId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID(), adjustmentId = UUID.randomUUID();
        captures.onPaymentCaptured("""
                {"paymentId":"%s","amount":10000,"currency":"USD"}
                """.formatted(paymentId));
        String originalPayout = """
                {"batchId":"%s","reference":"settlement:%s","grossMinor":10000,
                 "feesMinor":320,"refundsMinor":0,"netMinor":9680,"currency":"USD"}
                """.formatted(batchId, batchId);
        String recovery = """
                {"batchId":"%s","reference":"settlement:%s","grossMinor":0,
                 "feesMinor":0,"refundsMinor":10000,"netMinor":-10000,
                 "currency":"USD","batchKind":"REFUND_ADJUSTMENT"}
                """.formatted(adjustmentId, adjustmentId);
        if (!recoveryFirst) payouts.onPayout(originalPayout);
        refunds.onRefunded("""
                {"paymentId":"%s","reference":"refund:%s","amountMinor":10000,
                 "currency":"USD","originalRef":"payment:%s"}
                """.formatted(paymentId, refundId, paymentId));
        payouts.onPayout(recovery);
        payouts.onPayout(recovery);
        payouts.onPayout(originalPayout);
        assertJournal("payment:" + paymentId, 2, "100.00");
        assertJournal("refund:" + refundId, 2, "100.00");
        assertJournal("settlement:" + batchId, 3, "100.00");
        assertJournal("settlement:" + adjustmentId, 2, "100.00");
        String account = jdbc.queryForObject("""
                SELECT a.account_ref FROM ledger_entries e JOIN accounts a ON a.id = e.account_id
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.reference = ? AND e.entry_type = 'DEBIT'
                """, String.class, "settlement:" + adjustmentId);
        assertEquals("LIABILITY:MERCHANT_PAYOUT", account);
    }

    private void assertJournal(String reference, int count, String amount) {
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions WHERE reference = ?", Long.class, reference));
        assertEquals(count, jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.reference = ?
                """, Integer.class, reference));
        BigDecimal difference = jdbc.queryForObject("""
                SELECT SUM(CASE WHEN e.entry_type = 'DEBIT' THEN e.amount ELSE -e.amount END)
                FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id WHERE t.reference = ?
                """, BigDecimal.class, reference);
        assertEquals(0, BigDecimal.ZERO.compareTo(difference));
        BigDecimal debits = jdbc.queryForObject("""
                SELECT SUM(e.amount) FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.reference = ? AND e.entry_type = 'DEBIT'
                """, BigDecimal.class, reference);
        assertEquals(0, new BigDecimal(amount).compareTo(debits));
    }
}
