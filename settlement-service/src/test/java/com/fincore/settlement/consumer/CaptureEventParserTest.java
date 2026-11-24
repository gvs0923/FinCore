package com.fincore.settlement.consumer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptureEventParserTest {

    private final CaptureEventParser parser =
            new CaptureEventParser(new ObjectMapper());

    @Test
    void parsesValidCapture() {
        UUID paymentId = UUID.randomUUID();

        CapturedPayment capture = parser.parse(
                event(paymentId, "10000", "USD"));

        assertEquals(paymentId, capture.paymentId());
        assertEquals("demo-merchant", capture.merchantId());
        assertEquals("checkout-example", capture.merchantRef());
        assertEquals("capture-example", capture.processorRef());
        assertEquals(10_000, capture.amountMinor());
        assertEquals(
                Instant.parse("2026-11-16T12:00:00Z"),
                capture.capturedAt());
    }

    @Test
    void rejectsFractionalMinorUnits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        event(UUID.randomUUID(), "100.5", "USD")));
    }

    @Test
    void rejectsQuotedAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        event(UUID.randomUUID(), "\"100\"", "USD")));
    }

    @Test
    void rejectsOutOfRangeAmount() {
        assertThrows(
                ArithmeticException.class,
                () -> parser.parse(
                        event(
                                UUID.randomUUID(),
                                "9223372036854775808",
                                "USD")));
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(
                        event(UUID.randomUUID(), "10000", "EUR")));
    }

    @Test
    void rejectsLegacyCaptureWithoutSettlementFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("""
                        {
                          "paymentId": "%s",
                          "amount": 10000,
                          "currency": "USD",
                          "status": "CAPTURED"
                        }
                        """.formatted(UUID.randomUUID())));
    }

    private static String event(
            UUID paymentId,
            String amount,
            String currency) {
        return """
                {
                  "schemaVersion": 2,
                  "paymentId": "%s",
                  "merchantId": "demo-merchant",
                  "merchantRef": "checkout-example",
                  "processorRef": "capture-example",
                  "amount": %s,
                  "currency": "%s",
                  "status": "CAPTURED",
                  "capturedAt": "2026-11-16T12:00:00Z"
                }
                """.formatted(paymentId, amount, currency);
    }
}