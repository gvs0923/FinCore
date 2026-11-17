package com.fincore.ledger.consumer;

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
public class PaymentRefundedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentRefundedConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public PaymentRefundedConsumer(LedgerService ledgerService,
                                   ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.refunded", groupId = "ledger-service")
    public void onRefunded(String message) {
        try {
            JsonNode e = objectMapper.readTree(message);
            String reference = e.get("reference").asText();
            BigDecimal amount = BigDecimal.valueOf(
                    e.get("amountMinor").asLong(), 2);

            ledgerService.postTransaction(
                    reference,
                    "Refund " + e.get("paymentId").asText()
                            + " (reverses " + e.get("originalRef").asText() + ")",
                    List.of(
                            JournalLine.debit(
                                    "LIABILITY:MERCHANT_RECEIVABLE", amount),
                            JournalLine.credit(
                                    "ASSET:PROCESSOR_CLEARING", amount)));

            log.info("Refund posted for reference {}", reference);
        } catch (DuplicatePostingException e) {
            log.info("Duplicate event, already posted: {}", e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e); // retry → DLT
        }
    }
}