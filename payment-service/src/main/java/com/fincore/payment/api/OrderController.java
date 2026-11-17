package com.fincore.payment.api;

import com.fincore.payment.repository.PaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class OrderController {
    private final PaymentRepository payments;

    public OrderController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/internal/orders")
    @Transactional(readOnly = true)
    public List<PaymentResponse> list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        }
        return payments.findAll(PageRequest.of(page, 50,
                        Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(PaymentResponse::from).getContent();
    }
}
