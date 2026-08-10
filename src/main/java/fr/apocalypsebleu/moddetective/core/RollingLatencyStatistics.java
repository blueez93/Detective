package fr.apocalypsebleu.moddetective.core;

import java.util.Arrays;

/** A bounded rolling window used only when development diagnostics are enabled. */
public final class RollingLatencyStatistics {
    private final long[] values;
    private int size;
    private int next;

    public RollingLatencyStatistics(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        values = new long[capacity];
    }

    public synchronized void record(long valueNanos) {
        if (valueNanos < 0L) {
            throw new IllegalArgumentException("valueNanos must not be negative");
        }
        values[next] = valueNanos;
        next = (next + 1) % values.length;
        if (size < values.length) {
            size++;
        }
    }

    public synchronized Snapshot snapshot() {
        if (size == 0) {
            return new Snapshot(0, 0L, 0L, 0L);
        }
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return new Snapshot(
                size,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99));
    }

    private static long percentile(long[] sorted, double quantile) {
        int index = Math.max(0, (int) Math.ceil(quantile * sorted.length) - 1);
        return sorted[Math.min(index, sorted.length - 1)];
    }

    public record Snapshot(int samples, long p50Nanos, long p95Nanos, long p99Nanos) {}
}
