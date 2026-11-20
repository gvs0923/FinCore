package com.fincore.settlement.api;

import com.fincore.settlement.api.BatchModels.SettlementSummary;
import com.fincore.settlement.service.SettlementSummaryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SettlementSummaryController {

    private final SettlementSummaryService summaryService;

    public SettlementSummaryController(SettlementSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/internal/settlement/summary")
    public SettlementSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return summaryService.summary(from, to);
    }
}
