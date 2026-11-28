package com.fincore.risk.engine;

import com.fincore.risk.api.RiskController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RiskController controller = new RiskController(mock(RiskEngine.class), jdbc);

    @Test
    void latestDecisionUsesDeterministicOrderingAndReturnsModelFields() {
        UUID paymentId = UUID.randomUUID();
        Map<String, Object> row = Map.of("payment_id", paymentId.toString(),
                "decision", "REVIEW", "ml_probability", 0.9, "ml_model_version", "v1");
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), eq(paymentId.toString())))
                .thenReturn(List.of(row));

        assertThat(controller.latestDecision(paymentId)).isEqualTo(row);
        verify(jdbc).queryForList(argThat(sql -> sql.contains("ORDER BY created_at DESC, id DESC")
                && sql.contains("LIMIT 1") && sql.contains("WHERE payment_id = ?")), eq(paymentId.toString()));
    }

    @Test
    void missingDecisionReturnsNotFound() {
        assertThatThrownBy(() -> controller.latestDecision(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
}
