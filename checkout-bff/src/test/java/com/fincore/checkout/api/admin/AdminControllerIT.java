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
    @MockitoBean private com.fincore.checkout.client.SettlementClient settlementClient;
    @MockitoBean private com.fincore.checkout.client.PaymentClient paymentClient;

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

    /**
     * Every /v1/admin/** route must reject unauthenticated requests —
     * one matcher guards them all, so prove it on each endpoint.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/v1/admin/overview",
            "/v1/admin/metrics/timeseries?metric=payment_count"
                    + "&from=2025-01-01T00:00:00Z&to=2025-01-02T00:00:00Z",
            "/v1/admin/integrity/balances",
            "/v1/admin/orders",
            "/v1/admin/orders/00000000-0000-0000-0000-000000000001",
            "/v1/admin/ledger/accounts",
            "/v1/admin/ledger/accounts/test/entries",
            "/v1/admin/ledger/transactions/payment:test",
            "/v1/admin/settlement/batches",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/items",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/adjustments",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/reconciliation-runs",
            "/v1/admin/settlement/reconciliation-runs/00000000-0000-0000-0000-000000000001/items"
    })
    void everyGetRoute_withoutToken_returns401(String uri) throws Exception {
        mockMvc.perform(get(uri))
                .andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/v1/admin/settlement/batches",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/payout",
            "/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/reconciliation",
            "/v1/admin/settlement/refunds"
    })
    void mutationsRequireAuthentication(String uri) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reconciliationReadRoutesForwardToSettlement() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(settlementClient.adjustments(id, 50, 0)).thenReturn(List.of());
        mockMvc.perform(get("/v1/admin/settlement/batches/" + id + "/adjustments")
                        .header(AUTH, "test-token")).andExpect(status().isOk());
        org.mockito.Mockito.verify(settlementClient).adjustments(id, 50, 0);
        when(settlementClient.runs(id)).thenReturn(List.of());
        when(settlementClient.runItems(id)).thenReturn(List.of());
        mockMvc.perform(get("/v1/admin/settlement/batches/" + id + "/reconciliation-runs")
                        .header(AUTH, "test-token")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/settlement/reconciliation-runs/" + id + "/items")
                        .header(AUTH, "test-token")).andExpect(status().isOk());
        org.mockito.Mockito.verify(settlementClient).runs(id);
        org.mockito.Mockito.verify(settlementClient).runItems(id);
    }

    @Test
    void invalidRefundIsRejectedBeforeUpstreamCall() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/admin/settlement/refunds")
                        .header(AUTH, "test-token").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(settlementClient);
    }

    @Test
    void upstreamConflictIsSanitizedAndPreserved() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(settlementClient.payout(id)).thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.CONFLICT, "internal-service-detail", org.springframework.http.HttpHeaders.EMPTY,
                "sensitive upstream payload".getBytes(), java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/admin/settlement/batches/" + id + "/payout")
                        .header(AUTH, "test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request conflicts with current state; refresh the record before retrying"));
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