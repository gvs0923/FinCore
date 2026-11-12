package com.fincore.checkout.service;

import com.fincore.checkout.metrics.RequestMetricsInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestMetricsInterceptorTest {

    @Test
    void differentSessionsShareTheMatchedRouteLabel() {
        KafkaTemplate<String, String> kafka = mock();
        var interceptor = new RequestMetricsInterceptor(kafka);
        for (String id : new String[]{"session-one", "session-two"}) {
            var request = new MockHttpServletRequest("GET", "/v1/checkout/sessions/" + id);
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/v1/checkout/sessions/{id}");
            interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
            interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        }
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("request.metrics"),
                eq("GET /v1/checkout/sessions/{id}"), payload.capture());
        assertThat(payload.getAllValues()).allSatisfy(value -> {
            assertThat(value).contains("\"route\":\"GET /v1/checkout/sessions/{id}\"");
            assertThat(value).doesNotContain("session-one", "session-two");
        });
    }

    @Test
    void missingRoutePatternUsesBoundedFallback() {
        KafkaTemplate<String, String> kafka = mock();
        var interceptor = new RequestMetricsInterceptor(kafka);
        var request = new MockHttpServletRequest("GET", "/unknown/private-id");
        var response = new MockHttpServletResponse();
        response.setStatus(404);
        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("request.metrics"), eq("GET UNMATCHED"), payload.capture());
        assertThat(payload.getValue()).contains("\"statusClass\":\"4xx\"")
                .doesNotContain("private-id");
    }

    @Test
    void completionWithoutStartDoesNotPublish() {
        KafkaTemplate<String, String> kafka = mock();
        new RequestMetricsInterceptor(kafka).afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);
        verifyNoInteractions(kafka);
    }
}
