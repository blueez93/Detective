package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

public record SuspectViewModel(
        String modId,
        String modName,
        String version,
        int presenceSamples,
        double presenceSharePercent,
        int leafOwnershipCount,
        double leafOwnershipSharePercent,
        double averageFirstFrameDepth,
        int minimumFirstFrameDepth,
        int repeatedLeafOwnership,
        int callerOnlySamples,
        int stackDiversity
) {
    public SuspectViewModel {
        modId = Objects.requireNonNullElse(modId, "unknown");
        modName = Objects.requireNonNullElse(modName, modId);
        version = Objects.requireNonNullElse(version, "unknown");
    }
}
