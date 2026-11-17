package com.fincore.settlement.api;

import com.fincore.settlement.api.BatchModels.BatchItemResponse;
import com.fincore.settlement.api.BatchModels.BatchResponse;
import com.fincore.settlement.api.BatchModels.CreateBatchRequest;
import com.fincore.settlement.service.SettlementBatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/settlement/batches")
public class SettlementBatchController {

    private final SettlementBatchService batchService;

    public SettlementBatchController(
            SettlementBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public BatchResponse create(
            @RequestBody CreateBatchRequest request) {
        return batchService.createOrRefresh(
                request.merchantId(),
                request.currency(),
                request.settlementDate());
    }

    @GetMapping("/{batchId}")
    public BatchResponse get(
            @PathVariable("batchId") UUID batchId) {
        return batchService.get(batchId);
    }

    @GetMapping("/{batchId}/items")
    public List<BatchItemResponse> items(
            @PathVariable("batchId") UUID batchId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") long offset) {
        return batchService.items(batchId, limit, offset);
    }

    @GetMapping("/{batchId}/adjustments")
    public List<BatchResponse> adjustments(@PathVariable UUID batchId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return batchService.adjustments(batchId, limit, offset);
    }

    @PostMapping("/{batchId}/payout")
    public BatchResponse payout(
            @PathVariable("batchId") UUID batchId) {
        return batchService.payout(batchId);
    }

    @GetMapping
    public List<BatchResponse> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return batchService.list(limit, offset);
    }


}