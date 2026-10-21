package com.fincore.checkout.api.admin;

import com.fincore.checkout.client.LedgerAdminClient;
import com.fincore.checkout.client.MetricsClient;
import com.fincore.checkout.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "fincore.admin.token=test-token")
@AutoConfigureMockMvc
@Import(TestKafkaConfig.class)
class AdminControllerIT {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MetricsClient metricsClient;
    @MockitoBean private LedgerAdminClient ledgerAdminClient;

    private static final String AUTH = "X-Admin-Token";

    @Test
    void overview_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void overview_wrongToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/admin/overview").header(AUTH, "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_withToken_returns200() throws Exception {
        when(metricsClient.paymentSummary(any(), any())).thenReturn(Map.of(
                "byStatus", List.of(Map.of(
                        "status", "CAPTURED", "count", 3, "volume_minor_units", 15000)),
                "topDeclines", List.of()));

        mockMvc.perform(get("/v1/admin/overview").header(AUTH, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.byStatus[0].status").value("CAPTURED"));
    }

    @Test
    void timeseries_unknownMetric_returns400() throws Exception {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(3600);
        mockMvc.perform(get("/v1/admin/metrics/timeseries")
                        .header(AUTH, "test-token")
                        .param("metric", "credit_card_numbers")   // not on allowlist
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeseries_rangeTooLarge_returns400() throws Exception {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(60L * 24 * 3600);  // 60 days
        mockMvc.perform(get("/v1/admin/metrics/timeseries")
                        .header(AUTH, "test-token")
                        .param("metric", "payment_count")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }
}