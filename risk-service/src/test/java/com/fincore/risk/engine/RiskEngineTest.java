package com.fincore.risk.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock
    private VelocityCounter velocity;

    @Mock
    private DeviceRegistry devices;

    @Mock
    private JdbcTemplate jdbc;

    private RiskEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskEngine(velocity, devices, jdbc);
        when(velocity.hit(anyString())).thenReturn(1L);
    }

    @Test
    void allowsLowAmountOnKnownDevice() {
        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("ALLOW");
        assertThat(result.score()).isZero();
        assertThat(result.firedRules()).isEmpty();
    }

    @Test
    void addsNewDeviceSignalWithoutAutomaticallyReviewing() {
        when(devices.isFirstSeen("device-1")).thenReturn(true);

        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("ALLOW");
        assertThat(result.score()).isEqualTo(15);
        assertThat(result.firedRules()).containsExactly("new_device");
    }

    @Test
    void reviewsHighAmount() {
        RiskResult result = engine.assess(request(1_000_001));

        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.score()).isEqualTo(40);
        assertThat(result.firedRules()).containsExactly("high_amount");
    }

    @Test
    void amountThresholdIsExclusive() {
        RiskResult result = engine.assess(request(1_000_000));

        assertThat(result.decision()).isEqualTo("ALLOW");
        assertThat(result.score()).isZero();
    }

    @Test
    void combinesModerateCardVelocityAndNewDeviceIntoReview() {
        when(velocity.hit("risk:card:card-1")).thenReturn(4L);
        when(devices.isFirstSeen("device-1")).thenReturn(true);

        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.score()).isEqualTo(35);
        assertThat(result.firedRules())
                .containsExactly("card_velocity", "new_device");
    }

    @Test
    void blocksSevereCardVelocityCombinedWithNewDevice() {
        when(velocity.hit("risk:card:card-1")).thenReturn(6L);
        when(devices.isFirstSeen("device-1")).thenReturn(true);

        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("BLOCK");
        assertThat(result.score()).isEqualTo(65);
        assertThat(result.firedRules())
                .containsExactly("card_velocity_block", "new_device");
    }

    @Test
    void severeCardVelocityAloneCurrentlyProducesReview() {
        when(velocity.hit("risk:card:card-1")).thenReturn(6L);

        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void reviewsHighIpVelocity() {
        when(velocity.hit("risk:ip:127.0.0.1")).thenReturn(9L);

        RiskResult result = engine.assess(request(10_000));

        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.firedRules()).containsExactly("ip_velocity");
    }

    @Test
    void changedCountryProducesReview() {
        when(devices.contextChanged(anyString(), anyString(), isNull()))
                .thenReturn(false);
        when(devices.contextChanged("device-1", "country", "CA"))
                .thenReturn(true);

        RiskResult result = engine.assess(new RiskRequest(
                "payment-1",
                10_000,
                "USD",
                "card-1",
                "device-1",
                "127.0.0.1",
                Map.of("country", "CA")));

        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.score()).isEqualTo(30);
        assertThat(result.firedRules()).containsExactly("country_changed");
    }

    @Test
    void timezoneAndLanguageChangesAreWeakSignals() {
        when(devices.contextChanged(anyString(), anyString(), isNull()))
                .thenReturn(false);
        when(devices.contextChanged(
                "device-1", "timezone", "Europe/London"))
                .thenReturn(true);

        when(devices.contextChanged(
                "device-1", "language", "en-GB"))
                .thenReturn(true);

        RiskResult result = engine.assess(new RiskRequest(
                "payment-1",
                10_000,
                "USD",
                "card-1",
                "device-1",
                "127.0.0.1",
                Map.of(
                        "timezone", "Europe/London",
                        "language", "en-GB")));

        assertThat(result.decision()).isEqualTo("ALLOW");
        assertThat(result.score()).isEqualTo(15);
        assertThat(result.firedRules())
                .containsExactly("timezone_changed", "language_changed");
    }

    private RiskRequest request(long amount) {
        return new RiskRequest(
                "payment-1",
                amount,
                "USD",
                "card-1",
                "device-1",
                "127.0.0.1");
    }
}