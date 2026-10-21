package com.fincore.checkout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class LedgerAdminClient {

    private final RestClient restClient;

    public LedgerAdminClient(@Value("${fincore.ledger.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> checkIntegrity() {
        return restClient.get()
                .uri("/v1/integrity/balances")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}