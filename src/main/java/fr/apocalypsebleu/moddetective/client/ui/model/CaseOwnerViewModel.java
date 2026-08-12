package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

/** Aggregate owner presence retained as evidence, never as a finding of fault. */
public record CaseOwnerViewModel(
        String ownerId,
        int supportingIncidents,
        double averageLeafSharePercent,
        double averageStackPresenceSharePercent
) {
    public CaseOwnerViewModel {
        ownerId = Objects.requireNonNullElse(ownerId, "");
        supportingIncidents = Math.max(0, supportingIncidents);
        averageLeafSharePercent = boundedPercent(averageLeafSharePercent);
        averageStackPresenceSharePercent = boundedPercent(averageStackPresenceSharePercent);
    }

    private static double boundedPercent(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }
}
