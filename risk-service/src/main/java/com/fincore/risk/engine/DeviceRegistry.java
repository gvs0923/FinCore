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
}