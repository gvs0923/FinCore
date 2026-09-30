package com.fincore.checkout.api;

import com.fincore.checkout.domain.CheckoutSession;
import com.fincore.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/checkout/sessions")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@Valid @RequestBody CreateSessionRequest request) {
        CheckoutSession session = checkoutService.createSession(
                request.amount(), request.currency(), request.description());
        return SessionResponse.from(session);
    }

    @PostMapping("/{sessionId}/pay")
    public SessionResponse pay(@PathVariable String sessionId,
                               @Valid @RequestBody PaySessionRequest request) {
        CheckoutSession session = checkoutService.pay(
                sessionId, request.cardNumber(), request.cardBrand(), request.cardLast4());
        return SessionResponse.from(session);
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(@PathVariable String sessionId) {
        return SessionResponse.from(checkoutService.getSession(sessionId));
    }
}
