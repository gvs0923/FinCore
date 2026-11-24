package com.fincore.settlement.api;

import com.fincore.settlement.audit.Actor;
import com.fincore.settlement.reconciliation.ReconciliationModels.ImportReportRequest;
import com.fincore.settlement.reconciliation.ReconciliationModels.RunResponse;
import com.fincore.settlement.service.ReconciliationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/settlement/batches")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(
            ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/{batchId}/reconciliation")
    public RunResponse importReport(
            @PathVariable("batchId") UUID batchId,
            @RequestBody ImportReportRequest request, Actor actor) {
        return reconciliationService.importReport(
                batchId,
                request.reportRef(),
                request.rows(),
                actor);
    }
}