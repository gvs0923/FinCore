package com.fincore.checkout.api;

import com.fincore.checkout.domain.CheckoutSession;
import com.fincore.checkout.domain.CheckoutStatus;
import com.fincore.checkout.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
                               @Valid @RequestBody PaySessionRequest request,
                               HttpServletRequest http) {
        CheckoutSession session = checkoutService.pay(
                sessionId, request.cardNumber(), request.cardBrand(),
                request.cardLast4(),
                request.deviceId() != null ? request.deviceId() : "unknown",
                http.getRemoteAddr());
        return SessionResponse.from(session);
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(@PathVariable String sessionId) {
        return SessionResponse.from(checkoutService.getSession(sessionId));
    }

    @GetMapping(value = "/{sessionId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter sessionEvents(@PathVariable String sessionId) {
    CheckoutSession session = checkoutService.getSession(sessionId);
    SseEmitter emitter = new SseEmitter(30_000L);  // 30s timeout

    // If already terminal, send immediately and complete
    if (session.getStatus() == CheckoutStatus.SUCCEEDED
        || session.getStatus() == CheckoutStatus.REVIEW
        || session.getStatus() == CheckoutStatus.FAILED) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(SessionResponse.from(session)));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    // Poll in background until terminal or timeout
    // (A production system would use an event bus; polling is fine for now)
    Thread.startVirtualThread(() -> {
        try {
            for (int i = 0; i < 60; i++) {  // 60 × 500ms = 30s
                Thread.sleep(500);
                CheckoutSession s = checkoutService.getSession(sessionId);
                if (s.getStatus() == CheckoutStatus.SUCCEEDED
                        || s.getStatus() == CheckoutStatus.FAILED) {
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(SessionResponse.from(s)));
                    emitter.complete();
                    return;
                }
            }
            // Timeout — tell the client it's still processing
            emitter.send(SseEmitter.event()
                    .name("timeout")
                    .data("{\"message\":\"Still processing\"}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    return emitter;
}
}
