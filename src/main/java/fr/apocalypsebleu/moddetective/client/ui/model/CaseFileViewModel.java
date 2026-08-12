package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.List;
import java.util.Objects;

/** UI-safe projection of a recurring Case pattern. */
public record CaseFileViewModel(
        String caseId,
        String shortCaseId,
        long firstSeenEpochMs,
        long lastSeenEpochMs,
        int occurrenceCount,
        double averageStallDurationMs,
        double longestStallDurationMs,
        double consistencyPercent,
        double evidenceStrengthPercent,
        List<CaseEvidenceViewModel> recurringEvidence,
        List<CaseOwnerViewModel> recurringOwners,
        List<RelatedIncidentViewModel> relatedIncidents
) {
    public CaseFileViewModel {
        caseId = Objects.requireNonNullElse(caseId, "unknown");
        shortCaseId = Objects.requireNonNullElse(shortCaseId, UiFormatters.shortCaseId(caseId));
        occurrenceCount = Math.max(0, occurrenceCount);
        averageStallDurationMs = finiteNonNegative(averageStallDurationMs);
        longestStallDurationMs = finiteNonNegative(longestStallDurationMs);
        consistencyPercent = boundedPercent(consistencyPercent);
        evidenceStrengthPercent = boundedPercent(evidenceStrengthPercent);
        recurringEvidence = recurringEvidence == null ? List.of() : List.copyOf(recurringEvidence);
        recurringOwners = recurringOwners == null ? List.of() : List.copyOf(recurringOwners);
        relatedIncidents = relatedIncidents == null ? List.of() : List.copyOf(relatedIncidents);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double boundedPercent(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }
}
