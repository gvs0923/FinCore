package com.fincore.checkout.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
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

        public List<Map<String, Object>> accounts() {
        return restClient.get()
                .uri("/internal/ledger/accounts")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<Map<String, Object>> accountEntries(String accountRef) {
        return restClient.get()
                .uri("/internal/ledger/accounts/{ref}/entries",
                        java.net.URLEncoder.encode(accountRef,
                                java.nio.charset.StandardCharsets.UTF_8))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> journal(String reference) {
        return restClient.get()
                .uri("/internal/ledger/transactions/{ref}",
                        java.net.URLEncoder.encode(reference,
                                java.nio.charset.StandardCharsets.UTF_8))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }


}