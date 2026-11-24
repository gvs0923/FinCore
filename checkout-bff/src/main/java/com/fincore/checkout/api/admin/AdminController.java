package com.fincore.checkout.api.admin;

import com.fincore.checkout.api.admin.BackOfficeViews.*;
import com.fincore.checkout.client.LedgerAdminClient;
import com.fincore.checkout.client.MetricsClient;
import com.fincore.checkout.client.PaymentClient;
import com.fincore.checkout.client.RiskAdminClient;
import com.fincore.checkout.client.SettlementClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    // One validated metric allowlist — never pass a user-supplied metric
    // name anywhere near SQL or a table name.
    private static final Set<String> ALLOWED_METRICS =
            Set.of("payment_count", "payment_volume",
                    "request_count", "ledger_transactions");
    private static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final Logger log =
        LoggerFactory.getLogger(AdminController.class);

    private final MetricsClient metricsClient;
    private final LedgerAdminClient ledgerClient;
    private final RiskAdminClient riskAdminClient;
    private final PaymentClient paymentClient;
    private final SettlementClient settlementClient;

    public AdminController(MetricsClient metricsClient,
                           LedgerAdminClient ledgerClient,
                           RiskAdminClient riskAdminClient,
                           PaymentClient paymentClient,
                           SettlementClient settlementClient) {
        this.metricsClient = metricsClient;
        this.ledgerClient = ledgerClient;
        this.riskAdminClient = riskAdminClient;
        this.paymentClient = paymentClient;
        this.settlementClient = settlementClient;
    }

    @GetMapping("/overview")
    public RiskViews.Overview overview() {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(24));
        return new RiskViews.Overview(
        Map.of("from", from.toString(), "to", to.toString()),
        metricsClient.paymentSummary(from, to),
        metricsClient.requestSummary(from, to),
        metricsClient.ledgerSummary(from, to),
        metricsClient.riskSummary(from, to),
        settlementSummaryBestEffort(from, to),
        Map.of(
                "status", metricsClient.status(),
                "lag", metricsClient.lag()));
    }

    private SettlementSummary settlementSummaryBestEffort(Instant from, Instant to) {
        try {
            return settlementClient.summary(from, to);
        } catch (RestClientException e) {
            log.warn("Settlement summary is unavailable");
            return null;
        }
    }

    @GetMapping("/integrity/balances")
    public Map<String, Object> ledgerIntegrity() {
        return ledgerClient.checkIntegrity();
    }

    @GetMapping("/metrics/timeseries")
    public List<Map<String, Object>> timeseries(
            @RequestParam String metric,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(defaultValue = "hour") String bucket) {

        if (!ALLOWED_METRICS.contains(metric)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown metric: " + metric);
        }
        if (!"hour".equals(bucket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only hourly buckets are available yet");
        }
        if (!from.isBefore(to)
                || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid range: must be <= 31 days");
        }
        return switch (metric) {
            case "request_count" -> metricsClient.requestTimeseries(from, to);
            case "ledger_transactions" -> metricsClient.ledgerTimeseries(from, to);
            default -> metricsClient.paymentTimeseries(from, to);
        };
    }

    @GetMapping("/risk/queue")
    public List<RiskViews.ReviewItem> riskQueue() {
    List<PaymentClient.PendingReview> pending =
            paymentClient.pendingReviews();

    if (pending == null || pending.isEmpty()) {
        return List.of();
    }

    Map<String, RiskViews.ReviewItem> details = new HashMap<>();

    try {
        List<RiskViews.ReviewItem> assessments =
                riskAdminClient.reviewDetails(
                        pending.stream()
                                .map(PaymentClient.PendingReview::id)
                                .toList());

        if (assessments != null) {
            for (RiskViews.ReviewItem assessment : assessments) {
                details.put(assessment.payment_id(), assessment);
            }
        }
    } catch (RestClientException e) {
        log.warn("Risk review details are unavailable");
    }

    return pending.stream()
            .map(payment -> details.getOrDefault(
                    payment.id(),
                    new RiskViews.ReviewItem(
                            payment.id(),
                            -1,
                            "assessment_details_unavailable",
                            "{\"assessmentDetailsUnavailable\":true}",
                            payment.createdAt())))
            .toList();
    }

    @PostMapping("/risk/{paymentId}/resolve")
    public Map<String, Object> resolveRisk(@PathVariable UUID paymentId,
                                           @RequestParam String action) {
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be 'approve' or 'reject'");
        }
        String id = paymentId.toString();
        try {
            Map<String, Object> payment = paymentClient.resolvePayment(id, action);
            markResolvedBestEffort(id, action);
            return payment;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        } catch (HttpClientErrorException.Conflict e) {
            // Already out of REVIEW (another analyst, or an earlier attempt
            // whose queue update failed): record what actually happened so
            // the stale entry drains, then report the conflict.
            Object status = paymentClient.getPayment(id).get("status");
            if (!"REVIEW".equals(status)) {
                markResolvedBestEffort(
                          id,
                          "CAPTURED".equals(status) ? "approve" : "reject");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment is no longer under review (now " + status + ")");
        }
    }

    private void markResolvedBestEffort(String paymentId, String action) {
    try {
        riskAdminClient.markResolved(paymentId, action);
    } catch (RestClientException e) {
        log.warn("Risk review resolution marker could not be updated");
    }
    }

    @GetMapping("/settlement/batches")
    public List<SettlementBatch> settlementBatches(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return settlementClient.listBatches(limit, offset);
    }

     @PostMapping("/settlement/batches")
    public SettlementBatch createSettlementBatch(
            @RequestParam String merchantId,
            @RequestParam String currency,
            @RequestParam String settlementDate) {
        return settlementClient.createBatch(
                merchantId, currency, settlementDate);
    }
 
    @GetMapping("/settlement/batches/{batchId}")
    public SettlementBatch settlementBatch(
            @PathVariable UUID batchId) {
        return settlementClient.getBatch(batchId);
    }
 
    @GetMapping("/settlement/batches/{batchId}/items")
    public List<BatchItem> settlementBatchItems(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit 1-100, offset >= 0");
        }
        return settlementClient.batchItems(batchId, limit, offset);
    }
 
    @GetMapping("/settlement/batches/{batchId}/adjustments")
    public List<SettlementBatch> settlementAdjustments(@PathVariable UUID batchId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 1-100, offset >= 0");
        }
        return settlementClient.adjustments(batchId, limit, offset);
    }

    @PostMapping("/settlement/batches/{batchId}/payout")
    public SettlementBatch settlementPayout(
            @PathVariable UUID batchId) {
        return settlementClient.payout(batchId);
    }
 
    @PostMapping("/settlement/batches/{batchId}/reconciliation")
    public ReconciliationRun settlementReconcile(
            @PathVariable UUID batchId,
            @RequestBody ProcessorReport report) {
        return settlementClient.importReport(batchId, report);
    }
 
    @GetMapping("/settlement/batches/{batchId}/reconciliation-runs")
    public List<ReconciliationRun> reconciliationRuns(@PathVariable UUID batchId) {
        return settlementClient.runs(batchId);
    }

    @GetMapping("/settlement/reconciliation-runs/{runId}/items")
    public List<ReconciliationItem> reconciliationItems(@PathVariable UUID runId) {
        return settlementClient.runItems(runId);
    }

    @GetMapping("/orders")
    public List<Order> orders(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        }
        return paymentClient.orders(page);
    }

    @GetMapping("/orders/{paymentId}")
    public Order order(@PathVariable UUID paymentId) {
        return paymentClient.order(paymentId);
    }

    @PostMapping("/settlement/refunds")
    public RefundResult settlementRefund(
            @jakarta.validation.Valid @RequestBody RefundRequest refundRequest) {
        // Idempotency-Key minted by the UI per refund attempt —
        // same pattern as checkout, retries reuse it.
        return settlementClient.refund(refundRequest);
    }

        @GetMapping("/ledger/accounts")
    public List<LedgerAccount> ledgerAccounts() {
        return ledgerClient.accounts();
    }

    @GetMapping("/ledger/accounts/{accountRef}/entries")
    public List<LedgerEntryRow> ledgerAccountEntries(
            @PathVariable String accountRef) {
        return ledgerClient.accountEntries(accountRef);
    }

    @GetMapping("/ledger/transactions/{reference:.+}")
    public LedgerJournal ledgerJournal(
            @PathVariable String reference) {
        return ledgerClient.journal(reference);
    }


}