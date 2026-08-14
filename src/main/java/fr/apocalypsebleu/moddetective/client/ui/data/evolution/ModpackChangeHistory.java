package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchHistory;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchHistoryState;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Bounded Case Evolution view over Detective's recorded modpack launch boundaries. */
public record ModpackChangeHistory(
        List<ModpackLaunchRecord> launches,
        Availability availability,
        int unavailableSourceFiles,
        long omittedEarlierLaunches,
        boolean earlierLaunchHistoryUnavailable
) {
    public static final int MAXIMUM_LAUNCHES = ModpackLaunchHistory.DEFAULT_MAXIMUM_RECORDS;
    /** Compatibility name retained for the Phase 0.9-C backend API. */
    public static final int MAXIMUM_DIFFS = MAXIMUM_LAUNCHES;

    public ModpackChangeHistory {
        Objects.requireNonNull(launches, "launches");
        availability = Objects.requireNonNull(availability, "availability");
        if (launches.size() > MAXIMUM_LAUNCHES
                || unavailableSourceFiles < 0 || omittedEarlierLaunches < 0L) {
            throw new IllegalArgumentException("Modpack change history is outside its bounds");
        }
        launches = launches.stream()
                .peek(value -> Objects.requireNonNull(value, "launch"))
                .sorted(Comparator.comparingLong(ModpackLaunchRecord::launchAtEpochMs)
                        .thenComparing(ModpackLaunchRecord::stableKey))
                .toList();
        if (availability == Availability.UNAVAILABLE && !launches.isEmpty()) {
            throw new IllegalArgumentException("Unavailable change history cannot expose launches");
        }
        if (availability == Availability.COMPLETE
                && (unavailableSourceFiles > 0 || omittedEarlierLaunches > 0L
                || earlierLaunchHistoryUnavailable)) {
            throw new IllegalArgumentException("Incomplete launch history cannot be complete");
        }
    }

    public static ModpackChangeHistory from(ModpackLaunchHistoryState state) {
        if (state == null || state.history().records().isEmpty()
                && state.unavailableSourceFiles() > 0) {
            return unavailable();
        }
        ModpackLaunchHistory history = state.history();
        boolean partial = history.earlierHistoryUnavailable()
                || history.omittedEarlierRecords() > 0L
                || state.unavailableSourceFiles() > 0
                || !state.currentLaunchPersisted();
        return new ModpackChangeHistory(
                history.records(),
                partial ? Availability.PARTIAL : Availability.COMPLETE,
                state.unavailableSourceFiles(),
                history.omittedEarlierRecords(),
                history.earlierHistoryUnavailable());
    }

    /** Compatibility adapter for callers that only have the latest in-memory diff. */
    public static ModpackChangeHistory latest(ModSnapshotDiff diff) {
        if (diff == null) {
            return unavailable();
        }
        return new ModpackChangeHistory(
                List.of(ModpackLaunchRecord.from(diff)),
                Availability.PARTIAL,
                0,
                0L,
                true);
    }

    public static ModpackChangeHistory complete(List<ModSnapshotDiff> diffs) {
        return new ModpackChangeHistory(
                records(diffs), Availability.COMPLETE, 0, 0L, false);
    }

    public static ModpackChangeHistory partial(
            List<ModSnapshotDiff> diffs,
            int unavailableSourceFiles,
            int omittedEarlierLaunches
    ) {
        return new ModpackChangeHistory(
                records(diffs), Availability.PARTIAL, unavailableSourceFiles,
                omittedEarlierLaunches, true);
    }

    public static ModpackChangeHistory unavailable() {
        return new ModpackChangeHistory(
                List.of(), Availability.UNAVAILABLE, 0, 0L, true);
    }

    private static List<ModpackLaunchRecord> records(List<ModSnapshotDiff> diffs) {
        return Objects.requireNonNull(diffs, "diffs").stream()
                .map(ModpackLaunchRecord::from)
                .toList();
    }

    public enum Availability {
        COMPLETE,
        PARTIAL,
        UNAVAILABLE
    }
}
