package com.fincore.risk.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class Pseudonymizer {

    private final SecretKeySpec key;

    public Pseudonymizer(@Value("${fincore.risk.pseudonym-key}") String secret) {
        if (secret.isBlank()) throw new IllegalArgumentException("Pseudonym key must not be blank");
        key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String of(String namespace, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(namespace.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
