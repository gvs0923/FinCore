package com.fincore.ledger.consumer;

import com.fincore.ledger.domain.EntryType;
import com.fincore.ledger.service.DuplicatePostingException;
import com.fincore.ledger.service.JournalLine;
import com.fincore.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SettlementPayoutConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(SettlementPayoutConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public SettlementPayoutConsumer(LedgerService ledgerService,
                                    ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "settlement.payout", groupId = "ledger-service")
    public void onPayout(String message) {
        try {
            JsonNode e = objectMapper.readTree(message);
            String reference = e.get("reference").asText();
            BigDecimal gross = BigDecimal.valueOf(
                    e.get("grossMinor").asLong(), 2);
            BigDecimal fees = BigDecimal.valueOf(
                    e.get("feesMinor").asLong(), 2);
            BigDecimal net = BigDecimal.valueOf(
                    e.get("netMinor").asLong(), 2);
            BigDecimal refunds = e.has("refundsMinor")
                    ? BigDecimal.valueOf(e.get("refundsMinor").asLong(), 2)
                    : gross.subtract(fees).subtract(net);
            if (refunds.signum() < 0 || net.signum() <= 0
                    || gross.subtract(refunds).compareTo(fees.add(net)) != 0) {
                throw new IllegalArgumentException("Invalid payout totals");
            }

            ledgerService.postTransaction(
                    reference,
                    "Settlement payout " + e.get("batchId").asText(),
                    List.of(
                        JournalLine.debit(
                            "LIABILITY:MERCHANT_RECEIVABLE", gross.subtract(refunds)),
                        JournalLine.credit(
                            "REVENUE:PROCESSOR_FEES", fees),
                        JournalLine.credit(
                            "LIABILITY:MERCHANT_PAYOUT", net)));

            log.info("Payout posted for reference {}", reference);
        } catch (DuplicatePostingException e) {
            // Duplicate delivery — ledger already has this reference.
            log.info("Duplicate event, already posted: {}", e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e); // retry → DLT
        }
    }
}