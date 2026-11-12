package com.fincore.checkout.api;

import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.client.RiskAdminClient;
import com.fincore.checkout.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "fincore.admin.token=test-admin-token")
@AutoConfigureMockMvc
@Import(TestKafkaConfig.class)
class AdminRiskIT {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentClient payments;

    @MockitoBean
    private RiskAdminClient risk;

    @Test
    void queueRequiresAuthentication() throws Exception {
        mvc.perform(get("/v1/admin/risk/queue"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(payments, risk);
    }

    @Test
    void resolutionRequiresAuthentication() throws Exception {
        mvc.perform(post("/v1/admin/risk/"
                        + UUID.randomUUID() + "/resolve")
                        .param("action", "approve"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(payments, risk);
    }

    @Test
    void pendingPaymentRemainsVisibleWhenRiskServiceIsDown() throws Exception {
        String id = UUID.randomUUID().toString();

        when(payments.pendingReviews()).thenReturn(List.of(
                new PaymentClient.PendingReview(
                        id, "2026-11-07T12:00:00Z")));

        when(risk.reviewDetails(anyList()))
                .thenThrow(new ResourceAccessException("Unavailable"));

        mvc.perform(get("/v1/admin/risk/queue")
                        .header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payment_id").value(id))
                .andExpect(jsonPath("$[0].score").value(-1))
                .andExpect(jsonPath("$[0].fired_rules")
                        .value("assessment_details_unavailable"));
    }

    @Test
    void committedApprovalIsReturnedWhenRiskMarkerUpdateFails()
            throws Exception {
        String id = UUID.randomUUID().toString();

        when(payments.resolvePayment(id, "approve"))
                .thenReturn(Map.of("id", id, "status", "CAPTURED"));

        doThrow(new ResourceAccessException("Unavailable"))
                .when(risk).markResolved(id, "approve");

        mvc.perform(post("/v1/admin/risk/" + id + "/resolve")
                        .header("X-Admin-Token", "test-admin-token")
                        .param("action", "approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }
}
