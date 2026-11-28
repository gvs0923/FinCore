package com.fincore.checkout.api.admin;

import com.fincore.checkout.client.LedgerAdminClient;
import com.fincore.checkout.client.MetricsClient;
import com.fincore.checkout.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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

    private static RequestPostProcessor as(String... roles) {
        List<GrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return oidcLogin().authorities(authorities);
    }

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

        mockMvc.perform(get("/v1/admin/overview").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.byStatus[0].status").value("CAPTURED"));
    }

    @Test
    void overview_includesSettlementSummary() throws Exception {
        when(settlementClient.summary(any(), any())).thenReturn(new BackOfficeViews.SettlementSummary(
                "USD", List.of(new BackOfficeViews.BatchStatusCount("DAILY", "OPEN", 2, 19_360)),
                1, 10_000, 1, 9_680, 3, 1));

        mockMvc.perform(get("/v1/admin/overview").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.batches[0].status").value("OPEN"))
                .andExpect(jsonPath("$.settlement.refundMinor").value(10_000))
                .andExpect(jsonPath("$.settlement.reconciliationBreaks").value(1));
    }

    @Test
    void overview_settlementDown_stillReturns200() throws Exception {
        when(settlementClient.summary(any(), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        mockMvc.perform(get("/v1/admin/overview").with(as("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement").doesNotExist());
    }

    @Test
    void timeseries_unknownMetric_returns400() throws Exception {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(3600);
        mockMvc.perform(get("/v1/admin/metrics/timeseries")
                        .with(as("VIEWER"))
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
            "/v1/admin/settlement/reconciliation-runs/00000000-0000-0000-0000-000000000001/items",
            "/v1/admin/orders/00000000-0000-0000-0000-000000000001/risk-explanation",
            "/v1/admin/settlement/reconciliation-runs/00000000-0000-0000-0000-000000000001/anomaly-summary"
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
            "/v1/admin/settlement/refunds",
            "/v1/admin/ai/copilot"
    })
    void mutationsRequireAuthentication(String uri) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri)
                        .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reconciliationReadRoutesForwardToSettlement() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(settlementClient.adjustments(id, 50, 0)).thenReturn(List.of());
        mockMvc.perform(get("/v1/admin/settlement/batches/" + id + "/adjustments")
                        .with(as("VIEWER"))).andExpect(status().isOk());
        org.mockito.Mockito.verify(settlementClient).adjustments(id, 50, 0);
        when(settlementClient.runs(id)).thenReturn(List.of());
        when(settlementClient.runItems(id)).thenReturn(List.of());
        mockMvc.perform(get("/v1/admin/settlement/batches/" + id + "/reconciliation-runs")
                        .with(as("VIEWER"))).andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/settlement/reconciliation-runs/" + id + "/items")
                        .with(as("VIEWER"))).andExpect(status().isOk());
        org.mockito.Mockito.verify(settlementClient).runs(id);
        org.mockito.Mockito.verify(settlementClient).runItems(id);
    }

    @Test
    void invalidRefundIsRejectedBeforeUpstreamCall() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/admin/settlement/refunds")
                        .with(as("VIEWER", "FINANCE_OPS")).with(csrf()).contentType("application/json").content("{}"))
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
                        .with(as("VIEWER", "FINANCE_OPS")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request conflicts with current state; refresh the record before retrying"));
    }

    @Test
    void timeseries_rangeTooLarge_returns400() throws Exception {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(60L * 24 * 3600);  // 60 days
        mockMvc.perform(get("/v1/admin/metrics/timeseries")
                        .with(as("VIEWER"))
                        .param("metric", "payment_count")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "POST,/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/payout,VIEWER,403",
            "POST,/v1/admin/settlement/batches/00000000-0000-0000-0000-000000000001/payout,RISK_ANALYST,403",
            "POST,/v1/admin/risk/00000000-0000-0000-0000-000000000001/resolve?action=approve,FINANCE_OPS,403",
            "POST,/v1/admin/risk/00000000-0000-0000-0000-000000000001/resolve?action=approve,VIEWER,403",
            "DELETE,/v1/admin/orders,FINANCE_OPS,403",
            "GET,/v1/admin/orders,NONE,403"
    })
    void rbacMatrix(String method, String uri, String role, int expected) throws Exception {
        var principal = "NONE".equals(role) ? as() : as(role);
        mockMvc.perform(request(HttpMethod.valueOf(method), uri).with(principal).with(csrf()))
                .andExpect(status().is(expected));
    }

    @Test
    void mutationWithoutCsrfToken_isRejectedBeforeUpstream() throws Exception {
        var id = java.util.UUID.randomUUID();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/admin/settlement/batches/" + id + "/payout")
                        .with(as("VIEWER", "FINANCE_OPS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        org.mockito.Mockito.verifyNoInteractions(settlementClient);
    }

    @Test
    void machineClientWithViewerRole_canReadOverview() throws Exception {
        mockMvc.perform(get("/v1/admin/overview")
                        .with(jwt().jwt(j -> j.claim("azp", "fincore-loadtest"))
                                  .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }
}