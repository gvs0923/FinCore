package com.fincore.ledger.api;

import com.fincore.ledger.service.BalanceProjectionService;
import com.fincore.ledger.service.BalanceProjectionService.IntegrityResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/integrity")
public class IntegrityController {

    private final BalanceProjectionService projectionService;

    public IntegrityController(BalanceProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/balances")
    public Map<String, Object> checkBalances() {
        List<IntegrityResult> results = projectionService.checkIntegrity();
        boolean healthy = results.stream().noneMatch(IntegrityResult::drifted);
        List<IntegrityResult> drifted = results.stream()
                .filter(IntegrityResult::drifted).toList();
        return Map.of(
                "healthy", healthy,
                "accountsChecked", results.size(),
                "driftedAccounts", drifted);
    }
}