package com.fincore.payment.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PanMaskingConverterTest {
    private static final String PAN = "4242".repeat(4);

    @Test
    void masksValidPanAndPreservesOtherNumbers() {
        assertThat(PanMaskingConverter.mask(PAN)).isEqualTo("[PAN ****4242]");
        assertThat(PanMaskingConverter.mask("1234567890123")).isEqualTo("1234567890123");
    }

    @Test
    void masksFormattedMessagesAndSeparatedPans() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("card={} other={} dashed={} spaced={}");
        event.setArgumentArray(new Object[]{PAN, "1234567890123", String.join("-", "4242", "4242", "4242", "4242"), String.join(" ", "4242", "4242", "4242", "4242")});
        assertThat(new PanMaskingConverter().convert(event)).isEqualTo(
                "card=[PAN ****4242] other=1234567890123 dashed=[PAN ****4242] spaced=[PAN ****4242]");
    }
}
