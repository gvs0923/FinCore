package com.fincore.metrics.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HistogramQuantilesTest {

    /** Bucket upper bounds, must mirror HistogramQuantiles.UPPER_BOUNDS_MS. */
    private static final long[] BOUNDS = HistogramQuantiles.UPPER_BOUNDS_MS;

    private static long[] histogram(List<Long> samples) {
        long[] cumulative = new long[BOUNDS.length + 1];
        for (long s : samples) {
            for (int i = 0; i < BOUNDS.length; i++) {
                if (s <= BOUNDS[i]) cumulative[i]++;
            }
            cumulative[BOUNDS.length]++;   // +Inf = total
        }
        return cumulative;
    }

    /** Nearest-rank percentile of the raw sample — the brute-force truth. */
    private static double bruteForcePercentile(List<Long> sorted, double q) {
        int idx = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    private static long bucketWidthContaining(double estimate) {
        for (int i = 0; i < BOUNDS.length; i++) {
            if (estimate <= BOUNDS[i]) {
                return BOUNDS[i] - (i == 0 ? 0 : BOUNDS[i - 1]);
            }
        }
        return Long.MAX_VALUE;
    }

    @Test
    void emptyHistogram_returnsNaN() {
        assertThat(HistogramQuantiles.quantile(0.95,
                new long[]{0, 0, 0, 0, 0, 0, 0})).isNaN();
    }

    @Test
    void allInOneBucket_quantilesInsideThatBucket() {
        // every sample <= 50ms
        long[] h = {1000, 1000, 1000, 1000, 1000, 1000, 1000};
        double p95 = HistogramQuantiles.quantile(0.95, h);
        assertThat(p95).isBetween(0.0, 50.0);
    }

    @Test
    void quantilesMatchBruteForce_withinBucketResolution() {
        // Deterministic sample spread across all buckets
        Random rng = new Random(42);
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            // log-normal-ish spread: mostly fast, a slow tail
            samples.add(Math.min(3000, Math.round(
                    Math.exp(rng.nextGaussian() * 0.9 + 4.5))));
        }
        List<Long> sorted = samples.stream().sorted().toList();
        long[] h = histogram(samples);

        for (double q : new double[]{0.50, 0.95, 0.99}) {
            double estimate = HistogramQuantiles.quantile(q, h);
            double truth = bruteForcePercentile(sorted, q);

            // The estimate must land within one bucket-width of the truth —
            // that's the strongest guarantee bucketed data can give.
            assertThat(Math.abs(estimate - truth))
                    .as("q=%s estimate=%s truth=%s", q, estimate, truth)
                    .isLessThanOrEqualTo(bucketWidthContaining(truth));
        }
    }

    @Test
    void quantileInInfTail_returnsHighestFiniteBound() {
        // half the traffic slower than the largest finite bucket
        long[] h = {10, 10, 10, 10, 10, 10, 20};
        double p99 = HistogramQuantiles.quantile(0.99, h);
        assertThat(p99).isEqualTo(2500);
    }

    @Test
    void wrongBucketCount_rejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> HistogramQuantiles.quantile(0.5, new long[]{1, 2, 3}));
    }

    @Test
    void monotonicity_p50LeP95LeP99() {
        long[] h = {50, 70, 85, 95, 99, 100, 100};
        double p50 = HistogramQuantiles.quantile(0.50, h);
        double p95 = HistogramQuantiles.quantile(0.95, h);
        double p99 = HistogramQuantiles.quantile(0.99, h);
        assertThat(p50).isLessThanOrEqualTo(p95);
        assertThat(p95).isLessThanOrEqualTo(p99);
    }
}
