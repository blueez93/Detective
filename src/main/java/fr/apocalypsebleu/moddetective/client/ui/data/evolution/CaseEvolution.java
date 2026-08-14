package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Observed evolution of one stable Case within retained local evidence.
 * Temporal proximity is exposed for explanation only and never represents causation.
 */
public record CaseEvolution(
        String caseId,
        FirstOccurrenceScope firstOccurrenceScope,
        CorrelationMeaning correlationMeaning,
        Optional<Occurrence> firstRecordedIncident,
        Optional<Occurrence> lastRecordedIncident,
        int caseOccurrenceCount,
        int availableTimelineCount,
        int missingRelatedIncidentCount,
        int relatedIncidentsWithoutTimestamp,
        List<Occurrence> occurrenceTimeline,
        double aggregateTechnicalSimilarity,
        double aggregateEvidenceStrength,
        Coverage retainedHistoryCoverage,
        ModpackChangeHistory.Availability changeHistoryAvailability,
        List<NearbyModpackChange> nearbyChanges
) {
    public CaseEvolution {
        caseId = requireText(caseId, "caseId");
        firstOccurrenceScope = Objects.requireNonNull(firstOccurrenceScope, "firstOccurrenceScope");
        correlationMeaning = Objects.requireNonNull(correlationMeaning, "correlationMeaning");
        firstRecordedIncident = Objects.requireNonNull(firstRecordedIncident, "firstRecordedIncident");
        lastRecordedIncident = Objects.requireNonNull(lastRecordedIncident, "lastRecordedIncident");
        occurrenceTimeline = List.copyOf(Objects.requireNonNull(
                occurrenceTimeline, "occurrenceTimeline"));
        retainedHistoryCoverage = Objects.requireNonNull(
                retainedHistoryCoverage, "retainedHistoryCoverage");
        changeHistoryAvailability = Objects.requireNonNull(
                changeHistoryAvailability, "changeHistoryAvailability");
        nearbyChanges = List.copyOf(Objects.requireNonNull(nearbyChanges, "nearbyChanges"));
        if (caseOccurrenceCount < 3
                || availableTimelineCount != occurrenceTimeline.size()
                || missingRelatedIncidentCount < 0
                || relatedIncidentsWithoutTimestamp < 0
                || availableTimelineCount + missingRelatedIncidentCount
                + relatedIncidentsWithoutTimestamp != caseOccurrenceCount) {
            throw new IllegalArgumentException("Case evolution occurrence counts are inconsistent");
        }
        requireNormalized(aggregateTechnicalSimilarity, "aggregateTechnicalSimilarity");
        requireNormalized(aggregateEvidenceStrength, "aggregateEvidenceStrength");
        if (occurrenceTimeline.isEmpty()) {
            if (firstRecordedIncident.isPresent() || lastRecordedIncident.isPresent()) {
                throw new IllegalArgumentException("An empty timeline has no first or last incident");
            }
        } else if (!firstRecordedIncident.filter(occurrenceTimeline.getFirst()::equals).isPresent()
                || !lastRecordedIncident.filter(occurrenceTimeline.getLast()::equals).isPresent()) {
            throw new IllegalArgumentException("First and last incidents must bound the timeline");
        }
    }

    public record Occurrence(
            String incidentId,
            long detectedAtEpochMs,
            OptionalDouble stallDurationMs
    ) {
        public Occurrence {
            incidentId = requireText(incidentId, "incidentId");
            Objects.requireNonNull(stallDurationMs, "stallDurationMs");
            stallDurationMs.ifPresent(value -> {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException("stallDurationMs must be finite and non-negative");
                }
            });
        }
    }

    public record NearbyModpackChange(
            ChangeType type,
            String modId,
            String modDisplayName,
            Optional<String> previousVersion,
            Optional<String> newVersion,
            long recordedAtEpochMs,
            long offsetFromFirstRecordedMs,
            TemporalDirection direction,
            ProximityBand proximityBand,
            boolean sameRecordedLaunch,
            BeforeAfterEvidence retainedEvidence
    ) {
        public NearbyModpackChange {
            type = Objects.requireNonNull(type, "type");
            modId = requireText(modId, "modId");
            modDisplayName = requireText(modDisplayName, "modDisplayName");
            previousVersion = normalizedOptional(previousVersion);
            newVersion = normalizedOptional(newVersion);
            direction = Objects.requireNonNull(direction, "direction");
            proximityBand = Objects.requireNonNull(proximityBand, "proximityBand");
            retainedEvidence = Objects.requireNonNull(retainedEvidence, "retainedEvidence");
            if ((type == ChangeType.ADDED && (previousVersion.isPresent() || newVersion.isEmpty()))
                    || (type == ChangeType.REMOVED
                    && (previousVersion.isEmpty() || newVersion.isPresent()))
                    || (type == ChangeType.UPDATED
                    && (previousVersion.isEmpty() || newVersion.isEmpty()))) {
                throw new IllegalArgumentException("Version fields do not match the change type");
            }
        }
    }

    public record BeforeAfterEvidence(
            int availableBeforeChange,
            int availableAtChange,
            int availableAfterChange,
            Optional<Occurrence> lastAvailableBeforeChange,
            Optional<Occurrence> firstAvailableAfterChange,
            long firstRecordedIncidentOffsetFromChangeMs,
            int recordedInComparableWindowBefore,
            int recordedInComparableWindowAfter,
            CoverageStatus comparableWindowCoverage,
            FrequencyTrend frequencyTrend
    ) {
        public BeforeAfterEvidence {
            Objects.requireNonNull(lastAvailableBeforeChange, "lastAvailableBeforeChange");
            Objects.requireNonNull(firstAvailableAfterChange, "firstAvailableAfterChange");
            comparableWindowCoverage = Objects.requireNonNull(
                    comparableWindowCoverage, "comparableWindowCoverage");
            frequencyTrend = Objects.requireNonNull(frequencyTrend, "frequencyTrend");
            if (availableBeforeChange < 0 || availableAtChange < 0 || availableAfterChange < 0
                    || recordedInComparableWindowBefore < 0
                    || recordedInComparableWindowAfter < 0) {
                throw new IllegalArgumentException("Before/after counts must be non-negative");
            }
            if (comparableWindowCoverage != CoverageStatus.SUFFICIENT
                    && frequencyTrend != FrequencyTrend.UNAVAILABLE) {
                throw new IllegalArgumentException("Frequency trend requires comparable coverage");
            }
        }
    }

    public record Coverage(CoverageStatus status, Set<CoverageLimitation> limitations) {
        public Coverage {
            status = Objects.requireNonNull(status, "status");
            Objects.requireNonNull(limitations, "limitations");
            limitations = limitations.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(limitations));
            if (status == CoverageStatus.SUFFICIENT && !limitations.isEmpty()) {
                throw new IllegalArgumentException("Sufficient coverage cannot have limitations");
            }
        }
    }

    public enum FirstOccurrenceScope {
        FIRST_RECORDED_IN_RETAINED_HISTORY
    }

    public enum CorrelationMeaning {
        TEMPORAL_PROXIMITY_DOES_NOT_ESTABLISH_CAUSATION
    }

    public enum ChangeType {
        ADDED,
        UPDATED,
        REMOVED
    }

    public enum TemporalDirection {
        BEFORE_FIRST_RECORDED,
        AT_FIRST_RECORDED,
        AFTER_FIRST_RECORDED
    }

    public enum ProximityBand {
        SAME_RECORDED_LAUNCH,
        VERY_NEAR,
        WITHIN_24_HOURS,
        WITHIN_BROADER_NEARBY_WINDOW
    }

    public enum CoverageStatus {
        SUFFICIENT,
        LIMITED_BEFORE,
        LIMITED_AFTER,
        LIMITED_BOTH,
        INSUFFICIENT,
        UNKNOWN
    }

    public enum CoverageLimitation {
        AGE_BOUNDED_HISTORY,
        COUNT_BOUNDED_HISTORY,
        MISSING_RELATED_INCIDENTS,
        INCIDENTS_WITHOUT_TIMESTAMPS,
        UNREADABLE_INCIDENTS,
        UNKNOWN_HISTORY_BOUNDS,
        PARTIAL_CHANGE_HISTORY,
        MISSING_CHANGE_HISTORY,
        UNAVAILABLE_SNAPSHOT_FILES,
        OMITTED_EARLIER_SNAPSHOTS
    }

    public enum FrequencyTrend {
        MORE_RECORDED_AFTER,
        LESS_RECORDED_AFTER,
        SAME_RECORDED_FREQUENCY,
        UNAVAILABLE
    }

    private static Optional<String> normalizedOptional(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return value.map(String::strip).filter(text -> !text.isEmpty());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }
}
