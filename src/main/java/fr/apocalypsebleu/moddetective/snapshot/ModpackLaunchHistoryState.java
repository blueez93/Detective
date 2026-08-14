package fr.apocalypsebleu.moddetective.snapshot;

import java.util.Objects;

/** In-memory status of the persisted launch history used by Case Evolution. */
public record ModpackLaunchHistoryState(
        ModpackLaunchHistory history,
        int unavailableSourceFiles,
        boolean currentLaunchPersisted
) {
    public ModpackLaunchHistoryState {
        history = Objects.requireNonNull(history, "history");
        if (unavailableSourceFiles < 0) {
            throw new IllegalArgumentException("unavailableSourceFiles must be non-negative");
        }
    }

    public static ModpackLaunchHistoryState unavailable() {
        return new ModpackLaunchHistoryState(ModpackLaunchHistory.empty(), 1, false);
    }
}
