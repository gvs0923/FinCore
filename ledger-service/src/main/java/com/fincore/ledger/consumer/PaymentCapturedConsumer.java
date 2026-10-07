package com.fincore.ledger.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fincore.ledger.domain.EntryType;
import com.fincore.ledger.service.JournalLine;
import com.fincore.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PaymentCapturedConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentCapturedConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public PaymentCapturedConsumer(LedgerService ledgerService,
                                   ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.captured",
                   groupId = "ledger-service")
    public void onPaymentCaptured(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asString();
            long amountMinorUnits = event.get("amount").asLong();
            String currency = event.get("currency").asString();

            // Convert minor units (cents) to decimal for the ledger
            BigDecimal amount = BigDecimal.valueOf(amountMinorUnits, 2);
            String reference = "payment:" + paymentId;

            ledgerService.postTransaction(
                    reference,
                    "Payment capture " + paymentId,
                    List.of(
                            JournalLine.debit("ASSET:PROCESSOR_CLEARING", amount),
                            JournalLine.credit("LIABILITY:MERCHANT_RECEIVABLE", amount)
                    )
            );

            log.info("Ledger transaction posted for payment {}", paymentId);
        } catch (IllegalStateException e) {
            // "already posted for reference" = idempotent duplicate, safe to ignore
            log.info("Duplicate event, ledger already has this transaction: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process payment.captured event: {}",
                    message, e);
            throw new RuntimeException(e);   // triggers retry / DLT
        }
    }
}