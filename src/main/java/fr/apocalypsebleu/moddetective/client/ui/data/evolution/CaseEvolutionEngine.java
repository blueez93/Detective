package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Deterministic temporal correlation over explicit Case members and existing snapshot diffs.
 * Work is linear in retained members plus changes, apart from stable sorting. Nearby changes from
 * the same recorded launch sort first, followed by absolute time offset, recorded timestamp,
 * change type, mod id, display name, and versions. None of those positions represents causal
 * confidence.
 */
public final class CaseEvolutionEngine {
    private final CaseEvolutionConfiguration configuration;

    public CaseEvolutionEngine() {
        this(CaseEvolutionConfiguration.DEFAULT);
    }

    public CaseEvolutionEngine(CaseEvolutionConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public CaseEvolution analyze(
            CaseFile caseFile,
            List<IncidentSearchRecord> retainedIncidents,
            RetainedHistoryCoverage retainedCoverage,
            ModpackChangeHistory changeHistory
    ) {
        Objects.requireNonNull(caseFile, "caseFile");
        Objects.requireNonNull(retainedIncidents, "retainedIncidents");
        Objects.requireNonNull(retainedCoverage, "retainedCoverage");
        Objects.requireNonNull(changeHistory, "changeHistory");

        Map<String, IncidentSearchRecord> incidentById = incidentIndex(retainedIncidents);
        List<CaseEvolution.Occurrence> timeline = new ArrayList<>();
        int missingMembers = 0;
        int withoutTimestamp = 0;
        for (String relatedIncidentId : caseFile.relatedIncidentIds()) {
            IncidentSearchRecord incident = incidentById.get(relatedIncidentId);
            if (incident == null) {
                missingMembers++;
            } else if (incident.detectedAtEpochMs().isEmpty()) {
                withoutTimestamp++;
            } else {
                timeline.add(new CaseEvolution.Occurrence(
                        incident.incidentId(),
                        incident.detectedAtEpochMs().getAsLong(),
                        incident.stallDurationMs()));
            }
        }
        timeline.sort(Comparator.comparingLong(CaseEvolution.Occurrence::detectedAtEpochMs)
                .thenComparing(CaseEvolution.Occurrence::incidentId));
        List<CaseEvolution.Occurrence> immutableTimeline = List.copyOf(timeline);
        Optional<CaseEvolution.Occurrence> first = immutableTimeline.isEmpty()
                ? Optional.empty() : Optional.of(immutableTimeline.getFirst());
        Optional<CaseEvolution.Occurrence> last = immutableTimeline.isEmpty()
                ? Optional.empty() : Optional.of(immutableTimeline.getLast());

        CaseEvolution.Coverage coverage = overallCoverage(
                retainedCoverage, changeHistory, missingMembers, withoutTimestamp,
                immutableTimeline.isEmpty());
        List<CaseEvolution.NearbyModpackChange> nearby = first.isEmpty()
                ? List.of()
                : nearbyChanges(first.get(), immutableTimeline, retainedCoverage, changeHistory);
        return new CaseEvolution(
                caseFile.caseId(),
                CaseEvolution.FirstOccurrenceScope.FIRST_RECORDED_IN_RETAINED_HISTORY,
                CaseEvolution.CorrelationMeaning.TEMPORAL_PROXIMITY_DOES_NOT_ESTABLISH_CAUSATION,
                first,
                last,
                caseFile.occurrenceCount(),
                immutableTimeline.size(),
                missingMembers,
                withoutTimestamp,
                immutableTimeline,
                caseFile.aggregateSimilarity(),
                caseFile.aggregateEvidenceStrength(),
                coverage,
                changeHistory.availability(),
                nearby);
    }

    private List<CaseEvolution.NearbyModpackChange> nearbyChanges(
            CaseEvolution.Occurrence first,
            List<CaseEvolution.Occurrence> timeline,
            RetainedHistoryCoverage retainedCoverage,
            ModpackChangeHistory history
    ) {
        long firstAt = first.detectedAtEpochMs();
        long broaderWindowMs = configuration.broaderNearbyWindow().toMillis();
        List<RecordedChange> flattened = flatten(history.diffs());
        long launchAt = latestLaunchAtOrBefore(history.diffs(), firstAt);
        List<CaseEvolution.NearbyModpackChange> result = new ArrayList<>();
        for (RecordedChange change : flattened) {
            long offset = saturatedSubtract(change.recordedAtEpochMs(), firstAt);
            if (safeAbsolute(offset) > broaderWindowMs) {
                continue;
            }
            boolean sameLaunch = change.recordedAtEpochMs() == launchAt
                    && firstAt >= change.recordedAtEpochMs();
            result.add(new CaseEvolution.NearbyModpackChange(
                    change.type(),
                    change.modId(),
                    change.modDisplayName(),
                    change.previousVersion(),
                    change.newVersion(),
                    change.recordedAtEpochMs(),
                    offset,
                    direction(offset),
                    proximity(sameLaunch, safeAbsolute(offset)),
                    sameLaunch,
                    beforeAfter(first, timeline, change.recordedAtEpochMs(), retainedCoverage)));
        }
        result.sort(Comparator
                .comparing(CaseEvolution.NearbyModpackChange::sameRecordedLaunch).reversed()
                .thenComparingLong(value -> safeAbsolute(value.offsetFromFirstRecordedMs()))
                .thenComparingLong(CaseEvolution.NearbyModpackChange::recordedAtEpochMs)
                .thenComparing(CaseEvolution.NearbyModpackChange::type)
                .thenComparing(CaseEvolution.NearbyModpackChange::modId)
                .thenComparing(CaseEvolution.NearbyModpackChange::modDisplayName)
                .thenComparing(value -> value.previousVersion().orElse(""))
                .thenComparing(value -> value.newVersion().orElse("")));
        return List.copyOf(result);
    }

    private CaseEvolution.BeforeAfterEvidence beforeAfter(
            CaseEvolution.Occurrence firstRecorded,
            List<CaseEvolution.Occurrence> timeline,
            long changeAt,
            RetainedHistoryCoverage retainedCoverage
    ) {
        long windowMs = configuration.comparableFrequencyWindow().toMillis();
        long windowStart = saturatedSubtract(changeAt, windowMs);
        long windowEnd = saturatedAdd(changeAt, windowMs);
        int firstAtOrAfter = lowerBound(timeline, changeAt);
        int firstAfter = upperBound(timeline, changeAt);
        int before = firstAtOrAfter;
        int at = firstAfter - firstAtOrAfter;
        int after = timeline.size() - firstAfter;
        int withinBefore = firstAtOrAfter - lowerBound(timeline, windowStart);
        int withinAfter = upperBound(timeline, windowEnd) - firstAfter;
        CaseEvolution.CoverageStatus windowCoverage = windowCoverage(
                retainedCoverage, windowStart, windowEnd);
        CaseEvolution.FrequencyTrend trend = windowCoverage == CaseEvolution.CoverageStatus.SUFFICIENT
                ? frequencyTrend(withinBefore, withinAfter)
                : CaseEvolution.FrequencyTrend.UNAVAILABLE;
        return new CaseEvolution.BeforeAfterEvidence(
                before,
                at,
                after,
                before == 0 ? Optional.empty() : Optional.of(timeline.get(before - 1)),
                after == 0 ? Optional.empty() : Optional.of(timeline.get(firstAfter)),
                saturatedSubtract(firstRecorded.detectedAtEpochMs(), changeAt),
                withinBefore,
                withinAfter,
                windowCoverage,
                trend);
    }

    private static int lowerBound(List<CaseEvolution.Occurrence> timeline, long timestamp) {
        int low = 0;
        int high = timeline.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (timeline.get(middle).detectedAtEpochMs() < timestamp) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static int upperBound(List<CaseEvolution.Occurrence> timeline, long timestamp) {
        int low = 0;
        int high = timeline.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (timeline.get(middle).detectedAtEpochMs() <= timestamp) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private CaseEvolution.Coverage overallCoverage(
            RetainedHistoryCoverage retained,
            ModpackChangeHistory changes,
            int missingMembers,
            int withoutTimestamp,
            boolean emptyTimeline
    ) {
        EnumSet<CaseEvolution.CoverageLimitation> limitations =
                EnumSet.noneOf(CaseEvolution.CoverageLimitation.class);
        if (retained.ageBoundedBefore()) {
            limitations.add(CaseEvolution.CoverageLimitation.AGE_BOUNDED_HISTORY);
        }
        if (retained.countBoundedBefore()) {
            limitations.add(CaseEvolution.CoverageLimitation.COUNT_BOUNDED_HISTORY);
        }
        if (missingMembers > 0) {
            limitations.add(CaseEvolution.CoverageLimitation.MISSING_RELATED_INCIDENTS);
        }
        if (withoutTimestamp > 0) {
            limitations.add(CaseEvolution.CoverageLimitation.INCIDENTS_WITHOUT_TIMESTAMPS);
        }
        if (retained.unreadableIncidentFiles() > 0) {
            limitations.add(CaseEvolution.CoverageLimitation.UNREADABLE_INCIDENTS);
        }
        if (retained.knownCompleteFromEpochMs().isEmpty()
                || retained.knownCompleteThroughEpochMs().isEmpty()) {
            limitations.add(CaseEvolution.CoverageLimitation.UNKNOWN_HISTORY_BOUNDS);
        }
        if (changes.availability() == ModpackChangeHistory.Availability.UNAVAILABLE) {
            limitations.add(CaseEvolution.CoverageLimitation.MISSING_CHANGE_HISTORY);
        } else if (changes.availability() == ModpackChangeHistory.Availability.PARTIAL) {
            limitations.add(CaseEvolution.CoverageLimitation.PARTIAL_CHANGE_HISTORY);
        }
        if (changes.unavailableSnapshotFiles() > 0) {
            limitations.add(CaseEvolution.CoverageLimitation.UNAVAILABLE_SNAPSHOT_FILES);
        }
        if (changes.omittedEarlierSnapshots() > 0) {
            limitations.add(CaseEvolution.CoverageLimitation.OMITTED_EARLIER_SNAPSHOTS);
        }

        CaseEvolution.CoverageStatus status;
        if (emptyTimeline || missingMembers > 0 || withoutTimestamp > 0
                || retained.unreadableIncidentFiles() > 0
                || changes.availability() == ModpackChangeHistory.Availability.UNAVAILABLE) {
            status = CaseEvolution.CoverageStatus.INSUFFICIENT;
        } else if (retained.ageBoundedBefore() || retained.countBoundedBefore()
                || changes.omittedEarlierSnapshots() > 0) {
            status = CaseEvolution.CoverageStatus.LIMITED_BEFORE;
        } else if (!limitations.isEmpty()) {
            status = CaseEvolution.CoverageStatus.UNKNOWN;
        } else {
            status = CaseEvolution.CoverageStatus.SUFFICIENT;
        }
        return new CaseEvolution.Coverage(status, limitations);
    }

    private static CaseEvolution.CoverageStatus windowCoverage(
            RetainedHistoryCoverage coverage,
            long requiredFrom,
            long requiredThrough
    ) {
        if (coverage.unreadableIncidentFiles() > 0) {
            return CaseEvolution.CoverageStatus.INSUFFICIENT;
        }
        if (coverage.knownCompleteFromEpochMs().isEmpty()
                || coverage.knownCompleteThroughEpochMs().isEmpty()) {
            return CaseEvolution.CoverageStatus.UNKNOWN;
        }
        boolean limitedBefore = coverage.knownCompleteFromEpochMs().getAsLong() > requiredFrom;
        boolean limitedAfter = coverage.knownCompleteThroughEpochMs().getAsLong() < requiredThrough;
        if (limitedBefore && limitedAfter) {
            return CaseEvolution.CoverageStatus.LIMITED_BOTH;
        }
        if (limitedBefore) {
            return CaseEvolution.CoverageStatus.LIMITED_BEFORE;
        }
        if (limitedAfter) {
            return CaseEvolution.CoverageStatus.LIMITED_AFTER;
        }
        return CaseEvolution.CoverageStatus.SUFFICIENT;
    }

    private CaseEvolution.ProximityBand proximity(boolean sameLaunch, long absoluteOffset) {
        if (sameLaunch) {
            return CaseEvolution.ProximityBand.SAME_RECORDED_LAUNCH;
        }
        if (absoluteOffset <= configuration.veryNearWindow().toMillis()) {
            return CaseEvolution.ProximityBand.VERY_NEAR;
        }
        if (absoluteOffset <= configuration.withinDayWindow().toMillis()) {
            return CaseEvolution.ProximityBand.WITHIN_24_HOURS;
        }
        return CaseEvolution.ProximityBand.WITHIN_BROADER_NEARBY_WINDOW;
    }

    private static List<RecordedChange> flatten(List<ModSnapshotDiff> diffs) {
        List<RecordedChange> result = new ArrayList<>();
        for (ModSnapshotDiff diff : diffs) {
            long timestamp = diff.current().capturedAtEpochMs();
            for (ModSnapshot.LoadedMod mod : diff.added()) {
                result.add(new RecordedChange(
                        CaseEvolution.ChangeType.ADDED, mod.id(), displayName(mod.name(), mod.id()),
                        Optional.empty(), Optional.of(mod.version()), timestamp));
            }
            for (ModSnapshotDiff.VersionChange change : diff.updated()) {
                result.add(new RecordedChange(
                        CaseEvolution.ChangeType.UPDATED, change.id(),
                        displayName(change.name(), change.id()),
                        Optional.of(change.oldVersion()), Optional.of(change.newVersion()), timestamp));
            }
            for (ModSnapshot.LoadedMod mod : diff.removed()) {
                result.add(new RecordedChange(
                        CaseEvolution.ChangeType.REMOVED, mod.id(), displayName(mod.name(), mod.id()),
                        Optional.of(mod.version()), Optional.empty(), timestamp));
            }
        }
        return result;
    }

    private static Map<String, IncidentSearchRecord> incidentIndex(
            List<IncidentSearchRecord> retainedIncidents
    ) {
        List<IncidentSearchRecord> stable = retainedIncidents.stream()
                .peek(value -> Objects.requireNonNull(value, "retained incident"))
                .sorted(Comparator.comparing(IncidentSearchRecord::incidentId))
                .toList();
        Map<String, IncidentSearchRecord> result = new HashMap<>();
        for (IncidentSearchRecord incident : stable) {
            for (String alias : incident.identityAliases()) {
                result.putIfAbsent(alias, incident);
            }
        }
        return result;
    }

    private static long latestLaunchAtOrBefore(List<ModSnapshotDiff> diffs, long timestamp) {
        long result = Long.MIN_VALUE;
        for (ModSnapshotDiff diff : diffs) {
            long capturedAt = diff.current().capturedAtEpochMs();
            if (capturedAt <= timestamp) {
                result = Math.max(result, capturedAt);
            }
        }
        return result;
    }

    private static CaseEvolution.TemporalDirection direction(long offset) {
        if (offset < 0L) {
            return CaseEvolution.TemporalDirection.BEFORE_FIRST_RECORDED;
        }
        if (offset > 0L) {
            return CaseEvolution.TemporalDirection.AFTER_FIRST_RECORDED;
        }
        return CaseEvolution.TemporalDirection.AT_FIRST_RECORDED;
    }

    private static CaseEvolution.FrequencyTrend frequencyTrend(int before, int after) {
        if (after > before) {
            return CaseEvolution.FrequencyTrend.MORE_RECORDED_AFTER;
        }
        if (after < before) {
            return CaseEvolution.FrequencyTrend.LESS_RECORDED_AFTER;
        }
        return CaseEvolution.FrequencyTrend.SAME_RECORDED_FREQUENCY;
    }

    private static String displayName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.strip();
    }

    private static long saturatedSubtract(long first, long second) {
        try {
            return Math.subtractExact(first, second);
        } catch (ArithmeticException overflow) {
            return first >= second ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long saturatedAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException overflow) {
            return second >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long safeAbsolute(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
    }

    private record RecordedChange(
            CaseEvolution.ChangeType type,
            String modId,
            String modDisplayName,
            Optional<String> previousVersion,
            Optional<String> newVersion,
            long recordedAtEpochMs
    ) {}
}
