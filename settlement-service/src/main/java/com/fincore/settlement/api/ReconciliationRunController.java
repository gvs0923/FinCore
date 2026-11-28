package com.fincore.settlement.api;

import com.fincore.settlement.reconciliation.AnomalyAggregates;
import com.fincore.settlement.reconciliation.ReconciliationModels.ItemResponse;
import com.fincore.settlement.reconciliation.ReconciliationModels.RunResponse;
import com.fincore.settlement.service.ReconciliationAnomalyService;
import com.fincore.settlement.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/settlement")
public class ReconciliationRunController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationAnomalyService anomalyService;

    public ReconciliationRunController(
            ReconciliationService reconciliationService,
            ReconciliationAnomalyService anomalyService) {
        this.reconciliationService = reconciliationService;
        this.anomalyService = anomalyService;
    }

    @GetMapping("/batches/{batchId}/reconciliation-runs")
    public List<RunResponse> runs(@PathVariable UUID batchId) {
        return reconciliationService.runsForBatch(batchId);
    }

    @GetMapping("/reconciliation-runs/{runId}/items")
    public List<ItemResponse> runItems(@PathVariable UUID runId) {
        return reconciliationService.runItems(runId);
    }

    @GetMapping("/reconciliation-runs/{runId}/anomaly-aggregates")
    public AnomalyAggregates anomalyAggregates(@PathVariable UUID runId) {
        return anomalyService.aggregates(runId);
    }
}
