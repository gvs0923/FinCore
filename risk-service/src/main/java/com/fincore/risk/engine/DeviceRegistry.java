package com.fincore.risk.engine;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DeviceRegistry {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public DeviceRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isFirstSeen(String deviceId) {
        Boolean created = redis.opsForValue().setIfAbsent(
                "risk:device:known:" + deviceId,
                "1",
                RETENTION);

        if (created == null) {
            throw new IllegalStateException("Device recognition returned no result");
        }

        return created;
    }

    public boolean contextChanged(String deviceId, String dimension, String value) {
    if (value == null || value.isBlank()
            || deviceId == null
            || deviceId.startsWith("unknown")) {
        return false;
    }

    if (!java.util.Set.of("country", "timezone", "language")
            .contains(dimension)) {
        throw new IllegalArgumentException("Unsupported context dimension");
    }

    String key = "risk:device:context:" + deviceId + ":" + dimension;

    Boolean created = redis.opsForValue()
            .setIfAbsent(key, value, RETENTION);

    if (created == null) {
        throw new IllegalStateException("Context registration returned no result");
    }

    if (created) {
        return false;
    }

    String baseline = redis.opsForValue().get(key);

    return baseline != null && !baseline.equals(value);
    }
}