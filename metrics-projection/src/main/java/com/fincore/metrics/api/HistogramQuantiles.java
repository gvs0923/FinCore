package com.fincore.metrics.api;

/**
 * Computes approximate quantiles from cumulative histogram bucket
 * counters — the same math as Prometheus' histogram_quantile().
 *
 * <p>Bucket counts are cumulative: {@code counts[i]} = number of
 * observations with latency <= {@code upperBoundsMs[i]}, and the last
 * element is always the +Inf bucket (total count). A quantile q finds
 * the first bucket whose cumulative count >= q * total, then linearly
 * interpolates inside that bucket between its lower and upper bound.
 *
 * <p>Accuracy is limited by bucket resolution — a p99 that lands in the
 * 1000–2500ms bucket can only be placed somewhere inside it. This is
 * exactly why the admin conventions mandate storing buckets instead of
 * averages: buckets can be summed across windows and still yield
 * quantiles; averages cannot be averaged.
 */
public final class HistogramQuantiles {

    /** Upper bounds in ms for le_50 … le_2500; index 6 is +Inf. */
    public static final long[] UPPER_BOUNDS_MS = {50, 100, 250, 500, 1000, 2500};

    private HistogramQuantiles() {}

    /**
     * @param q          quantile in (0, 1], e.g. 0.95
     * @param cumulative counts per bucket, same order as UPPER_BOUNDS_MS
     *                   plus a final +Inf element (length 7)
     * @return estimated latency in ms, or NaN if there are no observations
     */
    public static double quantile(double q, long[] cumulative) {
        if (cumulative.length != UPPER_BOUNDS_MS.length + 1) {
            throw new IllegalArgumentException(
                    "expected " + (UPPER_BOUNDS_MS.length + 1) + " buckets");
        }
        long total = cumulative[cumulative.length - 1];   // +Inf = total
        if (total <= 0) return Double.NaN;

        double rank = q * total;
        // First bucket whose cumulative count reaches the rank
        int b = 0;
        while (b < UPPER_BOUNDS_MS.length && cumulative[b] < rank) {
            b++;
        }

        if (b == UPPER_BOUNDS_MS.length) {
            // rank lands in the +Inf tail: return the highest finite bound
            return UPPER_BOUNDS_MS[UPPER_BOUNDS_MS.length - 1];
        }

        double bucketStart = (b == 0) ? 0 : UPPER_BOUNDS_MS[b - 1];
        double bucketEnd = UPPER_BOUNDS_MS[b];
        double bucketCount = cumulative[b] - (b == 0 ? 0 : cumulative[b - 1]);
        if (bucketCount == 0) return bucketEnd;

        // Fraction of the way through this bucket
        double prevCumulative = (b == 0) ? 0 : cumulative[b - 1];
        double fraction = (rank - prevCumulative) / bucketCount;
        return bucketStart + fraction * (bucketEnd - bucketStart);
    }
}
