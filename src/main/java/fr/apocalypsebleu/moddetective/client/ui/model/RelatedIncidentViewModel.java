package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

/** A Case membership reference, which may outlive the retained incident detail file. */
public record RelatedIncidentViewModel(
        String incidentId,
        IncidentSummaryViewModel incident
) {
    public RelatedIncidentViewModel {
        incidentId = Objects.requireNonNullElse(incidentId, "unknown");
    }

    public boolean isAvailable() {
        return incident != null;
    }
}
