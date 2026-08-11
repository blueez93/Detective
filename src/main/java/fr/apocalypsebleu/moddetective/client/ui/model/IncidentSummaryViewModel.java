package fr.apocalypsebleu.moddetective.client.ui.model;

import java.nio.file.Path;
import java.util.Objects;

public record IncidentSummaryViewModel(
        String id,
        Path source,
        long detectedAtEpochMs,
        double durationMs,
        double thresholdMs,
        int watchdogSamples,
        EvidenceBadge evidence,
        String rawEvidenceState,
        String primarySuspect,
        boolean hasPrimarySuspect,
        String occurredAt,
        String dimension,
        String coordinates
) {
    public IncidentSummaryViewModel {
        id = Objects.requireNonNullElse(id, "unknown");
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        evidence = Objects.requireNonNullElse(evidence, EvidenceBadge.UNKNOWN);
        rawEvidenceState = Objects.requireNonNullElse(rawEvidenceState, "UNKNOWN");
        primarySuspect = Objects.requireNonNullElse(primarySuspect, "");
        occurredAt = Objects.requireNonNullElse(occurredAt, "—");
        dimension = Objects.requireNonNullElse(dimension, "—");
        coordinates = Objects.requireNonNullElse(coordinates, "—");
    }
}
