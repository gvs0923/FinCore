package com.fincore.payment.api;

import com.fincore.payment.domain.Payment;
import com.fincore.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Idempotency-Key comes as an HTTP header, not in the body
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        Payment payment = paymentService.processPayment(
                idempotencyKey,
                request.merchantRef(),
                request.amount(),
                request.currency(),
                request.cardNumber(),
                request.cardBrand(),
                request.cardLast4(),
                request.deviceFingerprint() != null ? request.deviceFingerprint() : "unknown-device",
                request.ipAddress() != null ? request.ipAddress() : "unknown-ip"
        );

        return PaymentResponse.from(payment);
    }

    // Look up a payment by its ID
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable UUID paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
