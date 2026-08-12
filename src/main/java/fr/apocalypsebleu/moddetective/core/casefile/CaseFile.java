package fr.apocalypsebleu.moddetective.core.casefile;

import java.util.List;
import java.util.Objects;

/** Aggregate description of a recurring technical pattern. It is evidence, not a verdict. */
public record CaseFile(
        String caseId,
        List<String> relatedIncidentIds,
        long firstSeenEpochMs,
        long lastSeenEpochMs,
        int occurrenceCount,
        double averageStallDurationMs,
        double longestStallDurationMs,
        List<RecurringEvidence> recurringEvidence,
        List<RecurringOwner> recurringOwners,
        double aggregateSimilarity,
        double aggregateEvidenceStrength
) {
    public CaseFile {
        caseId = Objects.requireNonNull(caseId, "caseId");
        relatedIncidentIds = List.copyOf(Objects.requireNonNull(relatedIncidentIds, "relatedIncidentIds"));
        recurringEvidence = List.copyOf(Objects.requireNonNull(recurringEvidence, "recurringEvidence"));
        recurringOwners = List.copyOf(Objects.requireNonNull(recurringOwners, "recurringOwners"));
        if (caseId.isBlank() || occurrenceCount < 3 || relatedIncidentIds.size() != occurrenceCount) {
            throw new IllegalArgumentException("A Case File requires an id and at least three related incidents");
        }
        if (lastSeenEpochMs < firstSeenEpochMs) {
            throw new IllegalArgumentException("lastSeenEpochMs must not precede firstSeenEpochMs");
        }
        if (!Double.isFinite(averageStallDurationMs) || averageStallDurationMs < 0.0
                || !Double.isFinite(longestStallDurationMs) || longestStallDurationMs < 0.0
                || averageStallDurationMs > longestStallDurationMs) {
            throw new IllegalArgumentException("Stall durations must be finite, non-negative, and ordered");
        }
        requireNormalized(aggregateSimilarity, "aggregateSimilarity");
        requireNormalized(aggregateEvidenceStrength, "aggregateEvidenceStrength");
    }

    public CaseFile withCaseId(String stableCaseId) {
        return new CaseFile(
                stableCaseId, relatedIncidentIds, firstSeenEpochMs, lastSeenEpochMs,
                occurrenceCount, averageStallDurationMs, longestStallDurationMs,
                recurringEvidence, recurringOwners, aggregateSimilarity, aggregateEvidenceStrength);
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }

    public record RecurringEvidence(
            EvidenceKind kind,
            String technicalSignature,
            int supportingIncidents,
            double averageObservedShare
    ) {
        public RecurringEvidence {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(technicalSignature, "technicalSignature");
            if (technicalSignature.isBlank() || supportingIncidents < 1) {
                throw new IllegalArgumentException("Recurring evidence must be named and supported");
            }
            requireNormalized(averageObservedShare, "averageObservedShare");
        }
    }

    public enum EvidenceKind {
        CLASS,
        FRAME,
        STACK_PATH
    }

    public record RecurringOwner(
            String ownerId,
            int supportingIncidents,
            double averageLeafShare,
            double averageStackPresenceShare
    ) {
        public RecurringOwner {
            Objects.requireNonNull(ownerId, "ownerId");
            if (ownerId.isBlank() || supportingIncidents < 1) {
                throw new IllegalArgumentException("Recurring owner evidence must be named and supported");
            }
            requireNormalized(averageLeafShare, "averageLeafShare");
            requireNormalized(averageStackPresenceShare, "averageStackPresenceShare");
        }
    }
}
