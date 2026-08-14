package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Bounded view over existing ModSnapshotDiff data; it does not persist a parallel history. */
public record ModpackChangeHistory(
        List<ModSnapshotDiff> diffs,
        Availability availability,
        int unavailableSnapshotFiles,
        int omittedEarlierSnapshots
) {
    public static final int MAXIMUM_DIFFS = 64;

    public ModpackChangeHistory {
        Objects.requireNonNull(diffs, "diffs");
        availability = Objects.requireNonNull(availability, "availability");
        if (diffs.size() > MAXIMUM_DIFFS
                || unavailableSnapshotFiles < 0 || omittedEarlierSnapshots < 0) {
            throw new IllegalArgumentException("Modpack change history is outside its bounds");
        }
        diffs = diffs.stream()
                .peek(value -> Objects.requireNonNull(value, "diff"))
                .sorted(Comparator.comparingLong(value -> value.current().capturedAtEpochMs()))
                .toList();
        if (availability == Availability.UNAVAILABLE && !diffs.isEmpty()) {
            throw new IllegalArgumentException("Unavailable change history cannot expose diffs");
        }
        if ((unavailableSnapshotFiles > 0 || omittedEarlierSnapshots > 0)
                && availability == Availability.COMPLETE) {
            throw new IllegalArgumentException("Incomplete snapshots require partial availability");
        }
    }

    /** The current product retains only the latest launch comparison. */
    public static ModpackChangeHistory latest(ModSnapshotDiff diff) {
        if (diff == null) {
            return unavailable();
        }
        return new ModpackChangeHistory(List.of(diff), Availability.PARTIAL, 0, 0);
    }

    public static ModpackChangeHistory complete(List<ModSnapshotDiff> diffs) {
        return new ModpackChangeHistory(diffs, Availability.COMPLETE, 0, 0);
    }

    public static ModpackChangeHistory partial(
            List<ModSnapshotDiff> diffs,
            int unavailableSnapshotFiles,
            int omittedEarlierSnapshots
    ) {
        return new ModpackChangeHistory(
                diffs, Availability.PARTIAL, unavailableSnapshotFiles, omittedEarlierSnapshots);
    }

    public static ModpackChangeHistory unavailable() {
        return new ModpackChangeHistory(List.of(), Availability.UNAVAILABLE, 0, 0);
    }

    public enum Availability {
        COMPLETE,
        PARTIAL,
        UNAVAILABLE
    }
}
