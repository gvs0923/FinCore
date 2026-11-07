package com.fincore.risk.engine;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class VelocityCounter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    public VelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Fixed-window counter: how many events for `key` in the last WINDOW. */
    public long hit(String key) {
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) {
            redis.expire(key, WINDOW.toSeconds(), TimeUnit.SECONDS);
        }
        return n == null ? 0 : n;
    }
}