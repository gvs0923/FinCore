package com.fincore.checkout.consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fincore.checkout.service.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(
            PaymentEventConsumer.class);

    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(CheckoutService checkoutService,
                                ObjectMapper objectMapper) {
        this.checkoutService = checkoutService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"payment.captured", "payment.declined",
                              "payment.capture_failed", "payment.failed"},
                   groupId = "checkout-bff")
    public void onPaymentEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asString();
            String merchantRef = event.get("merchantRef").asString();
            String status = event.get("status").asString();
            String reason = event.has("declineReason")
                    ? event.get("declineReason").asString(null) : null;

            checkoutService.updateSessionFromEvent(
                    merchantRef, status, paymentId, reason);
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", message, e);
        }
    }
}