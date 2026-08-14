package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import java.util.Objects;
import java.util.OptionalLong;

/** Explicit knowledge about retained incident-history coverage; unknown bounds stay unknown. */
public record RetainedHistoryCoverage(
        OptionalLong knownCompleteFromEpochMs,
        OptionalLong knownCompleteThroughEpochMs,
        boolean ageBoundedBefore,
        boolean countBoundedBefore,
        int unreadableIncidentFiles
) {
    public RetainedHistoryCoverage {
        Objects.requireNonNull(knownCompleteFromEpochMs, "knownCompleteFromEpochMs");
        Objects.requireNonNull(knownCompleteThroughEpochMs, "knownCompleteThroughEpochMs");
        if (unreadableIncidentFiles < 0) {
            throw new IllegalArgumentException("unreadableIncidentFiles must be non-negative");
        }
        if (knownCompleteFromEpochMs.isPresent() && knownCompleteThroughEpochMs.isPresent()
                && knownCompleteFromEpochMs.getAsLong()
                > knownCompleteThroughEpochMs.getAsLong()) {
            throw new IllegalArgumentException("Known history bounds must be ordered");
        }
    }

    public static RetainedHistoryCoverage complete(long fromEpochMs, long throughEpochMs) {
        return new RetainedHistoryCoverage(
                OptionalLong.of(fromEpochMs), OptionalLong.of(throughEpochMs),
                false, false, 0);
    }

    public static RetainedHistoryCoverage bounded(
            boolean ageBoundedBefore,
            boolean countBoundedBefore,
            int unreadableIncidentFiles
    ) {
        return new RetainedHistoryCoverage(
                OptionalLong.empty(), OptionalLong.empty(),
                ageBoundedBefore, countBoundedBefore, unreadableIncidentFiles);
    }

    public static RetainedHistoryCoverage unknown() {
        return bounded(false, false, 0);
    }
}
