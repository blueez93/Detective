package fr.apocalypsebleu.moddetective.snapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, bounded sequence of locally recorded modpack launch boundaries in append order.
 * Append order, rather than wall-clock order, makes oldest-record eviction stable across clock
 * corrections; Case Evolution creates its own timestamp-sorted analytical view.
 */
public record ModpackLaunchHistory(
        List<ModpackLaunchRecord> records,
        long omittedEarlierRecords,
        boolean earlierHistoryUnavailable
) {
    public static final int DEFAULT_MAXIMUM_RECORDS = 64;
    public static final int ABSOLUTE_MAXIMUM_RECORDS = 1_024;
    public ModpackLaunchHistory {
        Objects.requireNonNull(records, "records");
        if (records.size() > ABSOLUTE_MAXIMUM_RECORDS || omittedEarlierRecords < 0L) {
            throw new IllegalArgumentException("Launch history is outside its safety bounds");
        }
        records = List.copyOf(records);
        Set<String> keys = new HashSet<>();
        for (ModpackLaunchRecord record : records) {
            Objects.requireNonNull(record, "record");
            if (!keys.add(record.stableKey())) {
                throw new IllegalArgumentException("Launch history contains a duplicate record");
            }
        }
    }

    public static ModpackLaunchHistory empty() {
        return new ModpackLaunchHistory(List.of(), 0L, true);
    }

    public AppendResult append(ModpackLaunchRecord record, int maximumRecords) {
        Objects.requireNonNull(record, "record");
        validateMaximum(maximumRecords);
        if (records.stream().anyMatch(record::equals)) {
            return new AppendResult(this, false, true, 0);
        }
        List<ModpackLaunchRecord> next = new ArrayList<>(records);
        next.add(record);
        int evicted = 0;
        while (next.size() > maximumRecords) {
            next.removeFirst();
            evicted++;
        }
        return new AppendResult(
                new ModpackLaunchHistory(
                        next, saturatedAdd(omittedEarlierRecords, evicted),
                        earlierHistoryUnavailable),
                true,
                false,
                evicted);
    }

    public BoundedResult bounded(int maximumRecords) {
        validateMaximum(maximumRecords);
        if (records.size() <= maximumRecords) {
            return new BoundedResult(this, 0);
        }
        int removed = records.size() - maximumRecords;
        return new BoundedResult(
                new ModpackLaunchHistory(
                        records.subList(removed, records.size()),
                        saturatedAdd(omittedEarlierRecords, removed),
                        earlierHistoryUnavailable),
                removed);
    }

    private static void validateMaximum(int maximumRecords) {
        if (maximumRecords < 1 || maximumRecords > ABSOLUTE_MAXIMUM_RECORDS) {
            throw new IllegalArgumentException("maximumRecords is outside its safety bounds");
        }
    }

    private static long saturatedAdd(long value, int increment) {
        try {
            return Math.addExact(value, increment);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public record AppendResult(
            ModpackLaunchHistory history,
            boolean changed,
            boolean duplicate,
            int evictedRecords
    ) {
        public AppendResult {
            history = Objects.requireNonNull(history, "history");
            if (evictedRecords < 0) {
                throw new IllegalArgumentException("evictedRecords must be non-negative");
            }
        }
    }

    public record BoundedResult(ModpackLaunchHistory history, int evictedRecords) {
        public BoundedResult {
            history = Objects.requireNonNull(history, "history");
            if (evictedRecords < 0) {
                throw new IllegalArgumentException("evictedRecords must be non-negative");
            }
        }
    }
}
