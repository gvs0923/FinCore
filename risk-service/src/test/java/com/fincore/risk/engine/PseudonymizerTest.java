package com.fincore.risk.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PseudonymizerTest {
    @Test
    void stableNamespacedKeyedTokens() {
        Pseudonymizer p = new Pseudonymizer("test-key");
        String token = p.of("ip", "127.0.0.1");
        assertThat(token).matches("[0-9a-f]{32}").isEqualTo(p.of("ip", "127.0.0.1"))
                .isEqualTo("ed8d12b883af82367ea8d24eb02c00d8");
        assertThat(token).isNotEqualTo(p.of("device", "127.0.0.1"));
        assertThat(token).isNotEqualTo(new Pseudonymizer("other-key").of("ip", "127.0.0.1"));
        assertThat(p.of("ab", "c")).isNotEqualTo(p.of("a", "bc"));
    }

    @Test
    void rejectsBlankKeys() {
        assertThatThrownBy(() -> new Pseudonymizer(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
