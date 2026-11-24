package com.fincore.settlement.api;

import com.fincore.settlement.api.BatchModels.RefundRequest;
import com.fincore.settlement.api.BatchModels.RefundResponse;
import com.fincore.settlement.service.RefundService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/settlement/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public RefundResponse refund(@RequestBody RefundRequest request) {
        return refundService.refund(
                request.paymentId(),
                request.idempotencyKey(),
                request.reason(),
                request.actor());
    }
}