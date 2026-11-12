package com.fincore.payment.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/payments")
public class PendingReviewController {

    private final JdbcTemplate jdbc;

    public PendingReviewController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PendingReview(String id, String createdAt) {}

    @GetMapping("/pending-reviews")
    @Transactional(readOnly = true)
    public List<PendingReview> pendingReviews() {
        return jdbc.query("""
                SELECT id, created_at
                FROM payments
                WHERE status = 'REVIEW'
                ORDER BY created_at, id
                LIMIT 50
                """,
                (rs, row) -> new PendingReview(
                        rs.getString("id"),
                        rs.getTimestamp("created_at").toInstant().toString()));
    }
}