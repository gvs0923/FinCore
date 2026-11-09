package com.fincore.risk.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private DeviceRegistry devices;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        devices = new DeviceRegistry(redis);
    }

    @Test
    void recognizesFirstAndSubsequentVisits() {
        when(values.setIfAbsent(
                "risk:device:known:device-1",
                "1",
                Duration.ofDays(30)))
                .thenReturn(true, false);

        assertThat(devices.isFirstSeen("device-1")).isTrue();
        assertThat(devices.isFirstSeen("device-1")).isFalse();
    }

    @Test
    void doesNotTreatMissingRedisResultAsKnownDevice() {
        when(values.setIfAbsent(
                "risk:device:known:device-1",
                "1",
                Duration.ofDays(30)))
                .thenReturn(null);

        assertThatThrownBy(() -> devices.isFirstSeen("device-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Device recognition returned no result");
    }
}