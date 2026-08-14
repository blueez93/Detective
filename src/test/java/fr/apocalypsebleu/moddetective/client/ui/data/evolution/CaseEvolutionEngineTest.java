package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchHistory;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchHistoryState;
import fr.apocalypsebleu.moddetective.snapshot.ModpackLaunchRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseEvolutionEngineTest {
    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long FIRST = 100L * DAY;

    private final CaseEvolutionEngine engine = new CaseEvolutionEngine();

    @Test
    void modUpdateShortlyBeforeFirstRecordedOccurrence() {
        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST - HOUR, "machines"))));

        CaseEvolution.NearbyModpackChange change = result.nearbyChanges().getFirst();
        assertEquals(CaseEvolution.ChangeType.UPDATED, change.type());
        assertEquals(Optional.of("1.0"), change.previousVersion());
        assertEquals(Optional.of("2.0"), change.newVersion());
        assertEquals(-HOUR, change.offsetFromFirstRecordedMs());
        assertEquals(CaseEvolution.TemporalDirection.BEFORE_FIRST_RECORDED, change.direction());
        assertEquals(CaseEvolution.CorrelationMeaning.TEMPORAL_PROXIMITY_DOES_NOT_ESTABLISH_CAUSATION,
                result.correlationMeaning());
    }

    @Test
    void modAddedShortlyBeforeFirstRecordedOccurrence() {
        CaseEvolution.NearbyModpackChange change = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(added(FIRST - HOUR, "new_mod"))))
                .nearbyChanges().getFirst();

        assertEquals(CaseEvolution.ChangeType.ADDED, change.type());
        assertTrue(change.previousVersion().isEmpty());
        assertEquals(Optional.of("1.0"), change.newVersion());
    }

    @Test
    void modRemovedShortlyBeforeFirstRecordedOccurrence() {
        CaseEvolution.NearbyModpackChange change = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(removed(FIRST - HOUR, "old_mod"))))
                .nearbyChanges().getFirst();

        assertEquals(CaseEvolution.ChangeType.REMOVED, change.type());
        assertEquals(Optional.of("1.0"), change.previousVersion());
        assertTrue(change.newVersion().isEmpty());
    }

    @Test
    void changeAfterFirstRecordedOccurrenceUsesPositiveCaseRelativeOffset() {
        CaseEvolution.NearbyModpackChange change = analyze(
                records(FIRST, FIRST + 2L * HOUR, FIRST + 3L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST + HOUR, "machines"))))
                .nearbyChanges().getFirst();

        assertEquals(HOUR, change.offsetFromFirstRecordedMs());
        assertEquals(CaseEvolution.TemporalDirection.AFTER_FIRST_RECORDED, change.direction());
        assertEquals(-HOUR, change.retainedEvidence().firstRecordedIncidentOffsetFromChangeMs());
    }

    @Test
    void multipleNearbyChangesHaveStableNonCausalOrdering() {
        ModSnapshot before = snapshot(FIRST - HOUR - 1L, List.of(
                mod("alpha", "Alpha", "1.0"),
                mod("zeta", "Zeta", "1.0")));
        ModSnapshot after = snapshot(FIRST - HOUR, List.of(
                mod("alpha", "Alpha", "2.0"),
                mod("beta", "Beta", "1.0")));
        ModSnapshotDiff sameLaunch = ModSnapshotDiff.between(before, after);
        ModSnapshotDiff closerButEarlierLaunch = added(FIRST - Duration.ofMinutes(10).toMillis(), "gamma");

        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(sameLaunch, closerButEarlierLaunch)));

        assertEquals(List.of("gamma", "beta", "alpha", "zeta"), result.nearbyChanges().stream()
                .map(CaseEvolution.NearbyModpackChange::modId).toList());
        assertTrue(result.nearbyChanges().getFirst().sameRecordedLaunch());
        assertEquals(List.of(
                CaseEvolution.ChangeType.ADDED,
                CaseEvolution.ChangeType.ADDED,
                CaseEvolution.ChangeType.UPDATED,
                CaseEvolution.ChangeType.REMOVED), result.nearbyChanges().stream()
                .map(CaseEvolution.NearbyModpackChange::type).toList());
    }

    @Test
    void changeOutsideBroaderWindowIsNotNearby() {
        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST - 8L * DAY, "machines"))));

        assertTrue(result.nearbyChanges().isEmpty());
    }

    @Test
    void retainedEvidenceShowsCaseIncidentsBeforeCandidateChange() {
        List<IncidentSearchRecord> incidents = records(
                FIRST, FIRST + HOUR, FIRST + 2L * HOUR, FIRST + 3L * HOUR);
        CaseEvolution.BeforeAfterEvidence evidence = analyze(
                incidents,
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST + 90L * 60L * 1000L, "machines"))))
                .nearbyChanges().getFirst().retainedEvidence();

        assertEquals(2, evidence.availableBeforeChange());
        assertEquals(2, evidence.availableAfterChange());
        assertEquals("incident-1.json", evidence.lastAvailableBeforeChange().orElseThrow().incidentId());
        assertEquals("incident-2.json", evidence.firstAvailableAfterChange().orElseThrow().incidentId());
    }

    @Test
    void equalTimestampsAreSeparatedFromStrictBeforeAndAfterCounts() {
        CaseEvolution.BeforeAfterEvidence evidence = analyze(
                records(FIRST, FIRST + HOUR, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST + HOUR, "machines"))))
                .nearbyChanges().getFirst().retainedEvidence();

        assertEquals(1, evidence.availableBeforeChange());
        assertEquals(2, evidence.availableAtChange());
        assertEquals(1, evidence.availableAfterChange());
        assertEquals("incident-0.json", evidence.lastAvailableBeforeChange().orElseThrow().incidentId());
        assertEquals("incident-3.json", evidence.firstAvailableAfterChange().orElseThrow().incidentId());
    }

    @Test
    void onlyAfterInCompleteRetainedWindowProducesComparableFrequencyEvidence() {
        long changeAt = FIRST - HOUR;
        CaseEvolution.BeforeAfterEvidence evidence = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(changeAt, "machines"))))
                .nearbyChanges().getFirst().retainedEvidence();

        assertEquals(0, evidence.availableBeforeChange());
        assertEquals(3, evidence.availableAfterChange());
        assertEquals(CaseEvolution.CoverageStatus.SUFFICIENT, evidence.comparableWindowCoverage());
        assertEquals(CaseEvolution.FrequencyTrend.MORE_RECORDED_AFTER, evidence.frequencyTrend());
    }

    @Test
    void insufficientRetainedHistoryDoesNotInferFrequencyBeforeChange() {
        long changeAt = FIRST - HOUR;
        RetainedHistoryCoverage incomplete = new RetainedHistoryCoverage(
                OptionalLong.of(changeAt - 2L * HOUR),
                OptionalLong.of(FIRST + 30L * HOUR),
                true,
                false,
                0);

        CaseEvolution.BeforeAfterEvidence evidence = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                incomplete,
                ModpackChangeHistory.complete(List.of(updated(changeAt, "machines"))))
                .nearbyChanges().getFirst().retainedEvidence();

        assertEquals(CaseEvolution.CoverageStatus.LIMITED_BEFORE,
                evidence.comparableWindowCoverage());
        assertEquals(CaseEvolution.FrequencyTrend.UNAVAILABLE, evidence.frequencyTrend());
    }

    @Test
    void missingChangeHistoryIsExplicitlyUnavailable() {
        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.unavailable());

        assertTrue(result.nearbyChanges().isEmpty());
        assertEquals(ModpackChangeHistory.Availability.UNAVAILABLE,
                result.changeHistoryAvailability());
        assertTrue(result.retainedHistoryCoverage().limitations().contains(
                CaseEvolution.CoverageLimitation.MISSING_CHANGE_HISTORY));
    }

    @Test
    void incidentsWithoutUsableTimestampsFailSafely() {
        List<IncidentSearchRecord> incidents = List.of(
                recordWithoutTimestamp("incident-0.json"),
                recordWithoutTimestamp("incident-1.json"),
                recordWithoutTimestamp("incident-2.json"));

        CaseEvolution result = analyze(
                incidents, RetainedHistoryCoverage.unknown(),
                ModpackChangeHistory.complete(List.of()));

        assertTrue(result.occurrenceTimeline().isEmpty());
        assertTrue(result.firstRecordedIncident().isEmpty());
        assertEquals(3, result.relatedIncidentsWithoutTimestamp());
        assertEquals(CaseEvolution.CoverageStatus.INSUFFICIENT,
                result.retainedHistoryCoverage().status());
    }

    @Test
    void customNearbyWindowControlsInclusionWithoutHardcodedThresholds() {
        CaseEvolutionEngine configured = new CaseEvolutionEngine(new CaseEvolutionConfiguration(
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Duration.ofMinutes(20)));
        List<IncidentSearchRecord> incidents = records(
                FIRST, FIRST + HOUR, FIRST + 2L * HOUR);

        CaseEvolution result = configured.analyze(
                caseFile(incidents.stream().map(IncidentSearchRecord::incidentId).toList(), List.of()),
                incidents,
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(
                        updated(FIRST - Duration.ofMinutes(31).toMillis(), "machines"))));

        assertTrue(result.nearbyChanges().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CaseEvolutionConfiguration(
                Duration.ofHours(2), Duration.ofHours(1), Duration.ofDays(1), Duration.ofHours(1)));
    }

    @Test
    void legacyIncidentWithoutDurationRemainsInTimelineWithoutFabrication() {
        List<IncidentSearchRecord> incidents = new ArrayList<>(records(
                FIRST, FIRST + HOUR, FIRST + 2L * HOUR));
        incidents.set(0, record("incident-0.json", FIRST, OptionalDouble.empty(), Set.of()));

        CaseEvolution result = analyze(
                incidents, completeCoverage(), ModpackChangeHistory.complete(List.of()));

        assertEquals(CaseEvolution.FirstOccurrenceScope.FIRST_RECORDED_IN_RETAINED_HISTORY,
                result.firstOccurrenceScope());
        assertTrue(result.firstRecordedIncident().orElseThrow().stallDurationMs().isEmpty());
        assertEquals(3, result.availableTimelineCount());
    }

    @Test
    void missingRetainedMemberIsReportedAndCannotImplyCompleteHistory() {
        List<IncidentSearchRecord> retained = records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR);
        CaseFile caseFile = caseFile(List.of(
                "incident-0.json", "incident-1.json", "incident-2.json", "removed-incident.json"),
                List.of());

        CaseEvolution result = engine.analyze(
                caseFile,
                retained,
                RetainedHistoryCoverage.bounded(true, true, 0),
                ModpackChangeHistory.complete(List.of()));

        assertEquals(1, result.missingRelatedIncidentCount());
        assertEquals(CaseEvolution.CoverageStatus.INSUFFICIENT,
                result.retainedHistoryCoverage().status());
        assertTrue(result.retainedHistoryCoverage().limitations().contains(
                CaseEvolution.CoverageLimitation.MISSING_RELATED_INCIDENTS));
    }

    @Test
    void partialAndRemovedSnapshotHistoryAreReported() {
        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.partial(
                        List.of(updated(FIRST - HOUR, "machines")), 2, 3));

        assertEquals(ModpackChangeHistory.Availability.PARTIAL,
                result.changeHistoryAvailability());
        assertTrue(result.retainedHistoryCoverage().limitations().containsAll(Set.of(
                CaseEvolution.CoverageLimitation.PARTIAL_CHANGE_HISTORY,
                CaseEvolution.CoverageLimitation.UNAVAILABLE_CHANGE_HISTORY_SOURCES,
                CaseEvolution.CoverageLimitation.OMITTED_EARLIER_LAUNCH_RECORDS,
                CaseEvolution.CoverageLimitation.EARLIER_LAUNCH_HISTORY_UNAVAILABLE)));
    }

    @Test
    void persistedLaunchHistoryFeedsMultipleChangesAndEvictionLimitsCoverage() {
        ModpackLaunchHistory persisted = new ModpackLaunchHistory(
                List.of(
                        ModpackLaunchRecord.from(updated(FIRST - 6L * HOUR, "machines")),
                        ModpackLaunchRecord.from(added(FIRST - 2L * HOUR, "extra")),
                        ModpackLaunchRecord.from(unchanged(FIRST - HOUR, "machines"))),
                4L,
                true);

        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.from(
                        new ModpackLaunchHistoryState(persisted, 0, true)));

        assertEquals(List.of("extra", "machines"), result.nearbyChanges().stream()
                .map(CaseEvolution.NearbyModpackChange::modId).toList());
        assertEquals(CaseEvolution.CoverageStatus.LIMITED_BEFORE,
                result.retainedHistoryCoverage().status());
        assertTrue(result.retainedHistoryCoverage().limitations().contains(
                CaseEvolution.CoverageLimitation.OMITTED_EARLIER_LAUNCH_RECORDS));
    }

    @Test
    void matchingOwnerDoesNotAlterTemporalCorrelationOrCreateCausalStatus() {
        List<String> members = List.of("incident-0.json", "incident-1.json", "incident-2.json");
        CaseFile withMatchingOwner = caseFile(members, List.of(
                new CaseFile.RecurringOwner("machines", 3, 0.7, 0.8)));
        CaseFile withoutOwner = caseFile(members, List.of());
        List<IncidentSearchRecord> incidents = records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR);
        ModpackChangeHistory changes = ModpackChangeHistory.complete(
                List.of(updated(FIRST - HOUR, "machines")));

        CaseEvolution matching = engine.analyze(
                withMatchingOwner, incidents, completeCoverage(), changes);
        CaseEvolution neutral = engine.analyze(
                withoutOwner, incidents, completeCoverage(), changes);

        assertEquals(neutral, matching);
        assertEquals(CaseEvolution.CorrelationMeaning.TEMPORAL_PROXIMITY_DOES_NOT_ESTABLISH_CAUSATION,
                matching.correlationMeaning());
    }

    @Test
    void unrelatedOwnerChangeStillAppearsAsNearbyTemporalContext() {
        CaseEvolution result = engine.analyze(
                caseFile(List.of("incident-0.json", "incident-1.json", "incident-2.json"), List.of(
                        new CaseFile.RecurringOwner("machines", 3, 0.7, 0.8))),
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.complete(List.of(updated(FIRST - HOUR, "worldgen"))));

        assertEquals("worldgen", result.nearbyChanges().getFirst().modId());
    }

    @Test
    void identicalInputIsDeterministicAndSourceObjectsAreNotMutated() {
        List<IncidentSearchRecord> mutableIncidents = new ArrayList<>(records(
                FIRST + 2L * HOUR, FIRST, FIRST + HOUR));
        List<ModSnapshotDiff> mutableDiffs = new ArrayList<>(List.of(
                updated(FIRST - HOUR, "machines"),
                added(FIRST - 2L * HOUR, "extra")));
        List<IncidentSearchRecord> incidentOrderBefore = List.copyOf(mutableIncidents);
        List<ModSnapshotDiff> diffOrderBefore = List.copyOf(mutableDiffs);
        CaseFile sourceCase = caseFile(List.of(
                "incident-0.json", "incident-1.json", "incident-2.json"), List.of());
        ModpackChangeHistory history = ModpackChangeHistory.complete(mutableDiffs);

        CaseEvolution first = engine.analyze(
                sourceCase, mutableIncidents, completeCoverage(), history);
        CaseEvolution second = engine.analyze(
                sourceCase, mutableIncidents, completeCoverage(), history);

        assertEquals(first, second);
        assertEquals(incidentOrderBefore, mutableIncidents);
        assertEquals(diffOrderBefore, mutableDiffs);
        assertNotSame(mutableIncidents, first.occurrenceTimeline());
        assertThrows(UnsupportedOperationException.class,
                () -> first.occurrenceTimeline().add(first.occurrenceTimeline().getFirst()));
        assertThrows(UnsupportedOperationException.class,
                () -> first.nearbyChanges().clear());
    }

    @Test
    void emptyLaunchBoundaryPreventsOlderChangeFromBeingMarkedSameLaunch() {
        ModSnapshotDiff olderChange = updated(FIRST - 2L * HOUR, "machines");
        ModSnapshot unchangedPrevious = snapshot(FIRST - HOUR - 1L,
                List.of(mod("machines", "Machines", "2.0")));
        ModSnapshot unchangedCurrent = snapshot(FIRST - HOUR,
                List.of(mod("machines", "Machines", "2.0")));
        ModSnapshotDiff emptyLaterLaunch = ModSnapshotDiff.between(
                unchangedPrevious, unchangedCurrent);

        CaseEvolution result = analyze(
                records(FIRST, FIRST + HOUR, FIRST + 2L * HOUR),
                completeCoverage(),
                ModpackChangeHistory.from(new ModpackLaunchHistoryState(
                        new ModpackLaunchHistory(List.of(
                                ModpackLaunchRecord.from(olderChange),
                                ModpackLaunchRecord.from(emptyLaterLaunch)), 0L, true),
                        0,
                        true)));

        assertFalse(result.nearbyChanges().getFirst().sameRecordedLaunch());
        assertEquals(CaseEvolution.ProximityBand.VERY_NEAR,
                result.nearbyChanges().getFirst().proximityBand());
    }

    @Test
    void boundedCaseEvolutionBenchmark() {
        for (int size : List.of(50, 250, 500)) {
            List<IncidentSearchRecord> incidents = benchmarkRecords(size);
            CaseFile caseFile = caseFile(
                    incidents.stream().map(IncidentSearchRecord::incidentId).toList(), List.of());
            ModpackChangeHistory changes = ModpackChangeHistory.complete(List.of(
                    updated(FIRST - HOUR, "machines"),
                    added(FIRST + 4L * HOUR, "extra"),
                    removed(FIRST + 8L * HOUR, "old")));
            for (int warmup = 0; warmup < 20; warmup++) {
                engine.analyze(caseFile, incidents, completeCoverage(), changes);
            }
            long[] samples = new long[51];
            for (int sample = 0; sample < samples.length; sample++) {
                long started = System.nanoTime();
                CaseEvolution result = engine.analyze(
                        caseFile, incidents, completeCoverage(), changes);
                samples[sample] = System.nanoTime() - started;
                assertEquals(size, result.availableTimelineCount());
            }
            java.util.Arrays.sort(samples);
            System.out.printf("CASE_EVOLUTION_BENCHMARK size=%d median=%.3fms%n",
                    size, samples[samples.length / 2] / 1_000_000.0);
        }
    }

    private CaseEvolution analyze(
            List<IncidentSearchRecord> incidents,
            RetainedHistoryCoverage coverage,
            ModpackChangeHistory changes
    ) {
        return engine.analyze(
                caseFile(incidents.stream().map(IncidentSearchRecord::incidentId).toList(), List.of()),
                incidents,
                coverage,
                changes);
    }

    private static RetainedHistoryCoverage completeCoverage() {
        return RetainedHistoryCoverage.complete(FIRST - 40L * DAY, FIRST + 40L * DAY);
    }

    private static List<IncidentSearchRecord> records(long... timestamps) {
        List<IncidentSearchRecord> result = new ArrayList<>();
        for (int index = 0; index < timestamps.length; index++) {
            result.add(record(
                    "incident-%d.json".formatted(index),
                    timestamps[index],
                    OptionalDouble.of(100.0 + index),
                    Set.of("machines")));
        }
        return List.copyOf(result);
    }

    private static List<IncidentSearchRecord> benchmarkRecords(int size) {
        List<IncidentSearchRecord> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(record(
                    "benchmark-%03d.json".formatted(index),
                    FIRST + index * Duration.ofMinutes(10).toMillis(),
                    OptionalDouble.of(100.0 + index % 25),
                    Set.of(index % 2 == 0 ? "machines" : "worldgen")));
        }
        return List.copyOf(result);
    }

    private static IncidentSearchRecord record(
            String id,
            long timestamp,
            OptionalDouble duration,
            Set<String> owners
    ) {
        double summaryDuration = duration.orElse(0.0);
        return new IncidentSearchRecord(
                id,
                new IncidentSummaryViewModel(
                        id, Path.of(id), timestamp, summaryDuration, 120.0, 10,
                        EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED", "", false,
                        "date", "dimension", "coordinates"),
                owners,
                Set.of(),
                Optional.empty(),
                OptionalLong.of(timestamp),
                duration);
    }

    private static IncidentSearchRecord recordWithoutTimestamp(String id) {
        return new IncidentSearchRecord(
                id,
                new IncidentSummaryViewModel(
                        id, Path.of(id), 0L, 0.0, 120.0, 0,
                        EvidenceBadge.INSUFFICIENT_EVIDENCE, "INSUFFICIENT_EVIDENCE", "", false,
                        "date", "dimension", "coordinates"),
                Set.of(),
                Set.of(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalDouble.empty());
    }

    private static CaseFile caseFile(
            List<String> members,
            List<CaseFile.RecurringOwner> owners
    ) {
        return new CaseFile(
                "case-stable",
                members,
                FIRST,
                FIRST + Math.max(0, members.size() - 1L) * HOUR,
                members.size(),
                100.0,
                200.0,
                List.of(),
                owners,
                0.88,
                0.82);
    }

    private static ModSnapshotDiff added(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L, List.of()),
                snapshot(capturedAt, List.of(mod(id, displayName(id), "1.0"))));
    }

    private static ModSnapshotDiff updated(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L, List.of(mod(id, displayName(id), "1.0"))),
                snapshot(capturedAt, List.of(mod(id, displayName(id), "2.0"))));
    }

    private static ModSnapshotDiff removed(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L, List.of(mod(id, displayName(id), "1.0"))),
                snapshot(capturedAt, List.of()));
    }

    private static ModSnapshotDiff unchanged(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L, List.of(mod(id, displayName(id), "1.0"))),
                snapshot(capturedAt, List.of(mod(id, displayName(id), "1.0"))));
    }

    private static ModSnapshot snapshot(long capturedAt, List<ModSnapshot.LoadedMod> mods) {
        return new ModSnapshot(capturedAt, "1.21.1", "21", "fingerprint-" + capturedAt, mods);
    }

    private static ModSnapshot.LoadedMod mod(String id, String name, String version) {
        return new ModSnapshot.LoadedMod(id, name, version, id + ".jar");
    }

    private static String displayName(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
