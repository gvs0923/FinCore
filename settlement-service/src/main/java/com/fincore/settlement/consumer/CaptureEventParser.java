package com.fincore.settlement.consumer;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Component
public class CaptureEventParser {

    private final ObjectMapper objectMapper;

    public CaptureEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CapturedPayment parse(String message) {
        JsonNode root = objectMapper.readTree(message);

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Capture event must be a JSON object");
        }

        if (integer(root, "schemaVersion") != 2) {
            throw new IllegalArgumentException(
                    "Unsupported capture event version");
        }

        if (!"CAPTURED".equals(text(root, "status"))) {
            throw new IllegalArgumentException(
                    "Capture event must have CAPTURED status");
        }

        return new CapturedPayment(
                UUID.fromString(text(root, "paymentId")),
                text(root, "merchantId"),
                text(root, "merchantRef"),
                text(root, "processorRef"),
                integer(root, "amount"),
                text(root, "currency"),
                Instant.parse(text(root, "capturedAt")));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);

        if (value == null || !value.isTextual()
                || value.asString().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid field: " + field);
        }

        return value.asString();
    }

    private static long integer(JsonNode root, String field) {
        JsonNode value = root.get(field);

        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(
                    "Field must be an integer: " + field);
        }

        return new BigInteger(value.toString()).longValueExact();
    }
}