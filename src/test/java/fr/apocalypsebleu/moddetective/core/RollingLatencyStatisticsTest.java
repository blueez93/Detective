package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingLatencyStatisticsTest {
    @Test
    void calculatesNearestRankPercentiles() {
        RollingLatencyStatistics statistics = new RollingLatencyStatistics(100);
        for (long value = 1; value <= 100; value++) {
            statistics.record(value);
        }

        RollingLatencyStatistics.Snapshot snapshot = statistics.snapshot();

        assertEquals(100, snapshot.samples());
        assertEquals(50L, snapshot.p50Nanos());
        assertEquals(95L, snapshot.p95Nanos());
        assertEquals(99L, snapshot.p99Nanos());
    }

    @Test
    void remainsBoundedAndKeepsTheNewestValues() {
        RollingLatencyStatistics statistics = new RollingLatencyStatistics(3);
        statistics.record(1L);
        statistics.record(2L);
        statistics.record(3L);
        statistics.record(100L);

        RollingLatencyStatistics.Snapshot snapshot = statistics.snapshot();

        assertEquals(3, snapshot.samples());
        assertEquals(3L, snapshot.p50Nanos());
        assertEquals(100L, snapshot.p95Nanos());
        assertEquals(100L, snapshot.p99Nanos());
    }
}
