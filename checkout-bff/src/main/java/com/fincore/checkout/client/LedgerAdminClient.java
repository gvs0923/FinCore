package com.fincore.checkout.client;

import com.fincore.checkout.api.admin.BackOfficeViews.*;
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

        public List<LedgerAccount> accounts() {
        return restClient.get()
                .uri("/internal/ledger/accounts")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public List<LedgerEntryRow> accountEntries(String accountRef) {
        return restClient.get()
                .uri("/internal/ledger/accounts/{ref}/entries",
                        accountRef)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public LedgerJournal journal(String reference) {
        return restClient.get()
                .uri("/internal/ledger/transactions/{ref}",
                        reference)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }


}