package com.fincore.settlement.consumer;

import com.fincore.settlement.service.CaptureIngestionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCapturedConsumer {

    private final CaptureEventParser parser;
    private final CaptureIngestionService ingestionService;

    public PaymentCapturedConsumer(
            CaptureEventParser parser,
            CaptureIngestionService ingestionService) {
        this.parser = parser;
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "payment.captured")
    public void onPaymentCaptured(String message) {
        CapturedPayment capture = parser.parse(message);
        ingestionService.ingest(capture);
    }
}