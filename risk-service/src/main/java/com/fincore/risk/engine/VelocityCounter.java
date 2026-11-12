package com.fincore.risk.engine;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class VelocityCounter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final DefaultRedisScript<Long> HIT =
        new DefaultRedisScript<>("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 or redis.call('TTL', KEYS[1]) == -1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return count
                """, Long.class);

    private final StringRedisTemplate redis;

    public VelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long hit(String key) {
    Long count = redis.execute(
            HIT,
            List.of(key),
            Long.toString(WINDOW.toSeconds()));

    if (count == null) {
        throw new IllegalStateException("Velocity counter returned no result");
    }

    return count;
}
}