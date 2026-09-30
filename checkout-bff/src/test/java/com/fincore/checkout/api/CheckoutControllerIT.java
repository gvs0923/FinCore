package com.fincore.checkout.api;

import com.fincore.checkout.client.PaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerIT {

    @Autowired
    private MockMvc mockMvc;

    // Mock the PaymentClient so we don't need payment-service running
    @MockitoBean
    private PaymentClient paymentClient;

    @Test
    void createSession_returnsOpenSession() throws Exception {
        mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 5000,
                                  "currency": "USD",
                                  "description": "Premium Widget"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.description").value("Premium Widget"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.paymentId").isEmpty())
                .andExpect(jsonPath("$.failureReason").isEmpty());
    }

    @Test
    void paySession_successfulCard_returnsSucceeded() throws Exception {
        // Mock payment-service returning CAPTURED
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "id", paymentId.toString(),
                        "status", "CAPTURED",
                        "amount", 5000
                ));

        // First create a session
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract session ID
        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        // Pay with successful card
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardNumber": "4242424242424242",
                                  "cardBrand": "VISA",
                                  "cardLast4": "4242"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.failureReason").isEmpty());
    }

    @Test
    void paySession_declinedCard_returnsFailed() throws Exception {
        // Mock payment-service returning DECLINED
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "status", "DECLINED",
                        "declineReason", "generic_decline"
                ));

        // Create session
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 3000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        // Pay with declined card
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardNumber": "4000000000000002",
                                  "cardBrand": "VISA",
                                  "cardLast4": "0002"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("generic_decline"));
    }

    @Test
    void paySession_paymentServiceDown_returnsFailed() throws Exception {
        // Mock payment-service throwing an exception
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        // Create session
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 1000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        // Pay — payment-service is down
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardNumber": "4242424242424242",
                                  "cardBrand": "VISA",
                                  "cardLast4": "4242"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Payment service unavailable"));
    }

    @Test
    void getSession_afterPayment_returnsCurrentStatus() throws Exception {
        // Mock successful payment
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", paymentId.toString(), "status", "CAPTURED"));

        // Create session
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        // Pay
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cardNumber": "4242424242424242", "cardBrand": "VISA", "cardLast4": "4242" }
                                """))
                .andExpect(status().isOk());

        // Poll — should return SUCCEEDED
        mockMvc.perform(get("/v1/checkout/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));
    }

    @Test
    void getSession_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/v1/checkout/sessions/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void paySession_alreadySucceeded_returnsSameResult() throws Exception {
        // Mock successful payment
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", paymentId.toString(), "status", "CAPTURED"));

        // Create and pay
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cardNumber": "4242424242424242", "cardBrand": "VISA", "cardLast4": "4242" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // Pay again with same session — should return same result, not re-charge
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cardNumber": "4242424242424242", "cardBrand": "VISA", "cardLast4": "4242" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));
    }

    @Test
    void createSession_invalidRequest_returns400() throws Exception {
        // Missing currency
        mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000, "description": "Test" }
                                """))
                .andExpect(status().isBadRequest());

        // Zero amount
        mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 0, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paySession_missingCardNumber_returns400() throws Exception {
        // Create session first
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 1000, "currency": "USD", "description": "Test" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.sessionId");

        // Pay without card number
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "cardBrand": "VISA", "cardLast4": "4242" }
                                """))
                .andExpect(status().isBadRequest());
    }
}
