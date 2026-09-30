package com.fincore.checkout.api;

import com.fincore.checkout.client.PaymentClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

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
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", paymentId.toString(), "status", "CAPTURED", "amount", 5000));

        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 5000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

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
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", UUID.randomUUID().toString(), "status", "DECLINED", "declineReason", "generic_decline"));

        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 3000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

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
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 1000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

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
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", paymentId.toString(), "status", "CAPTURED"));

        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 5000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cardNumber\": \"4242424242424242\", \"cardBrand\": \"VISA\", \"cardLast4\": \"4242\" }\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));
    }

    @Test
    void paySession_alreadySucceeded_returnsSameResult() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentClient.createPayment(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("id", paymentId.toString(), "status", "CAPTURED"));

        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 5000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cardNumber\": \"4242424242424242\", \"cardBrand\": \"VISA\", \"cardLast4\": \"4242\" }\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        // Second pay attempt on same session should return the same result
        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cardNumber\": \"4242424242424242\", \"cardBrand\": \"VISA\", \"cardLast4\": \"4242\" }\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));
    }

    @Test
    void createSession_invalidRequest_returns400() throws Exception {
        // Missing currency
        mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 5000, \"description\": \"Test\" }\n"))
                .andExpect(status().isBadRequest());

        // Zero amount
        mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 0, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paySession_missingCardNumber_returns400() throws Exception {
        String createResponse = mockMvc.perform(post("/v1/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": 1000, \"currency\": \"USD\", \"description\": \"Test\" }\n"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String sessionId = JsonPath.read(createResponse, "$.sessionId");

        mockMvc.perform(post("/v1/checkout/sessions/" + sessionId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cardBrand\": \"VISA\", \"cardLast4\": \"4242\" }\n"))
                .andExpect(status().isBadRequest());
    }
}
