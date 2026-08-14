package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentQueryEngineTest {
    private final IncidentQueryEngine engine = new IncidentQueryEngine();
    private final List<IncidentSearchRecord> history = List.of(
            record("freeze-alpha.json", 300L, 300.0, EvidenceBadge.HIGH_EVIDENCE,
                    "ATTRIBUTED", Set.of("example_machines"), Set.of("Example Machines"),
                    "minecraft:overworld"),
            record("freeze-beta.json", 200L, 600.0, EvidenceBadge.AMBIGUOUS_ATTRIBUTION,
                    "AMBIGUOUS_ATTRIBUTION", Set.of("example_worldgen"),
                    Set.of("Example World Generator"), "minecraft:the_nether"),
            record("freeze-gamma.json", 100L, 150.0, EvidenceBadge.INSUFFICIENT_EVIDENCE,
                    "INSUFFICIENT_EVIDENCE", Set.of(), Set.of(), null));

    @Test
    void emptyQueryPreservesCurrentNewestFirstHistoryOrdering() {
        IncidentQueryResult result = query(IncidentQuery.empty());

        assertEquals(List.of("freeze-alpha.json", "freeze-beta.json", "freeze-gamma.json"), ids(result));
        assertEquals(3, result.totalIncidentCountConsidered());
        assertEquals(3, result.matchingCount());
        assertEquals(IncidentQuery.Sort.NEWEST_FIRST, result.sort());
        assertEquals(IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED,
                result.caseFilterStatus());
    }

    @Test
    void freeTextSearchIsCaseInsensitiveLocaleSafeAndBlankTolerant() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            IncidentQueryResult named = query(IncidentQuery.builder()
                    .freeText("  eXaMpLe MaChInEs ").build());
            IncidentQueryResult state = query(IncidentQuery.builder()
                    .freeText("insufficient evidence").build());
            IncidentQueryResult blank = query(IncidentQuery.builder().freeText("   ").build());

            assertEquals(List.of("freeze-alpha.json"), ids(named));
            assertEquals(List.of("freeze-gamma.json"), ids(state));
            assertEquals(ids(query(IncidentQuery.empty())), ids(blank));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void searchesAndFiltersByOwnerOrModId() {
        assertEquals(List.of("freeze-beta.json"), ids(query(IncidentQuery.builder()
                .freeText("EXAMPLE_WORLDGEN").build())));
        assertEquals(List.of("freeze-alpha.json"), ids(query(IncidentQuery.builder()
                .ownerId("Example_Machines").build())));
        assertTrue(query(IncidentQuery.builder().ownerId("missing_owner").build())
                .matchingIncidents().isEmpty());
    }

    @Test
    void freeTextDoesNotSearchFilesystemSegmentsOrDimensionContext() {
        IncidentSearchRecord nested = record(
                "private-folder/freeze-public.json", 1L, 200.0, EvidenceBadge.UNKNOWN,
                "UNKNOWN", Set.of(), Set.of(), "example:secret_dimension");

        assertEquals(0, engine.query(List.of(nested),
                IncidentQuery.builder().freeText("private folder").build(),
                CaseMembershipIndex.unavailable()).matchingCount());
        assertEquals(0, engine.query(List.of(nested),
                IncidentQuery.builder().freeText("secret dimension").build(),
                CaseMembershipIndex.unavailable()).matchingCount());
        assertEquals(1, engine.query(List.of(nested),
                IncidentQuery.builder().freeText("freeze public").build(),
                CaseMembershipIndex.unavailable()).matchingCount());
    }

    @Test
    void filtersByPreservedEvidenceState() {
        IncidentQuery query = IncidentQuery.builder()
                .evidenceStates(Set.of(
                        EvidenceBadge.HIGH_EVIDENCE,
                        EvidenceBadge.AMBIGUOUS_ATTRIBUTION))
                .build();

        assertEquals(List.of("freeze-alpha.json", "freeze-beta.json"), ids(query(query)));
    }

    @Test
    void everyExistingEvidenceStateRemainsIndependentlyFilterable() {
        List<IncidentSearchRecord> states = Arrays.stream(EvidenceBadge.values())
                .map(value -> record(
                        "freeze-" + value.name().toLowerCase(Locale.ROOT) + ".json",
                        value.ordinal() + 1L, 200.0, value,
                        value.isAttributedTier() ? "ATTRIBUTED" : value.name(),
                        Set.of(), Set.of(), null))
                .toList();

        for (EvidenceBadge state : EvidenceBadge.values()) {
            IncidentQueryResult result = engine.query(states,
                    IncidentQuery.builder().evidenceStates(Set.of(state)).build(),
                    CaseMembershipIndex.unavailable());
            assertEquals(1, result.matchingCount());
            assertEquals(state, result.matchingIncidents().getFirst().summary().evidence());
        }
    }

    @Test
    void filtersByMinimumDurationInclusively() {
        assertEquals(List.of("freeze-beta.json", "freeze-alpha.json"), ids(query(
                IncidentQuery.builder().minimumStallDurationMs(300.0)
                        .sort(IncidentQuery.Sort.LONGEST_STALL_FIRST).build())));
    }

    @Test
    void filtersByMaximumDurationInclusively() {
        assertEquals(List.of("freeze-alpha.json", "freeze-gamma.json"), ids(query(
                IncidentQuery.builder().maximumStallDurationMs(300.0).build())));
    }

    @Test
    void combinesMinimumAndMaximumDuration() {
        assertEquals(List.of("freeze-alpha.json"), ids(query(IncidentQuery.builder()
                .minimumStallDurationMs(200.0)
                .maximumStallDurationMs(500.0)
                .build())));
        assertThrows(IllegalArgumentException.class, () -> IncidentQuery.builder()
                .minimumStallDurationMs(500.0)
                .maximumStallDurationMs(200.0)
                .build());
    }

    @Test
    void filtersByRawDimensionAndExcludesMissingLegacyContext() {
        assertEquals(List.of("freeze-beta.json"), ids(query(IncidentQuery.builder()
                .dimensionId("MINECRAFT:THE_NETHER").build())));
        assertTrue(query(IncidentQuery.builder().dimensionId("minecraft:the_end").build())
                .matchingIncidents().isEmpty());
    }

    @Test
    void filtersByInclusivePersistedDateRange() {
        assertEquals(List.of("freeze-alpha.json", "freeze-beta.json"), ids(query(
                IncidentQuery.builder()
                        .detectedAtOrAfterEpochMs(200L)
                        .detectedAtOrBeforeEpochMs(300L)
                        .build())));
        assertThrows(IllegalArgumentException.class, () -> IncidentQuery.builder()
                .detectedAtOrAfterEpochMs(301L)
                .detectedAtOrBeforeEpochMs(300L)
                .build());
    }

    @Test
    void filtersIncidentsBelongingToAnyActiveCase() {
        IncidentQueryResult result = query(
                IncidentQuery.builder().hasRecurringCase(true).build(), memberships());

        assertEquals(List.of("freeze-alpha.json", "freeze-beta.json"), ids(result));
        assertEquals(IncidentQueryResult.CaseFilterStatus.AVAILABLE, result.caseFilterStatus());
    }

    @Test
    void filtersIncidentsNotBelongingToAnActiveCase() {
        IncidentQueryResult result = query(
                IncidentQuery.builder().hasRecurringCase(false).build(), memberships());

        assertEquals(List.of("freeze-gamma.json"), ids(result));
    }

    @Test
    void filtersBySpecificCaseIdWithoutInferringFromOwnerIdentity() {
        IncidentQueryResult result = query(
                IncidentQuery.builder().caseId("CASE-ALPHA").build(), memberships());

        assertEquals(List.of("freeze-alpha.json"), ids(result));
        assertFalse(result.matchingIncidents().getFirst().ownerIds().contains("case-alpha"));
    }

    @Test
    void missingOrCorruptCaseDataFailsSafely() {
        IncidentQuery caseQuery = IncidentQuery.builder().hasRecurringCase(false).build();

        IncidentQueryResult unavailable = query(caseQuery, CaseMembershipIndex.unavailable());
        IncidentQueryResult ordinary = query(IncidentQuery.empty(), CaseMembershipIndex.unavailable());

        assertTrue(unavailable.matchingIncidents().isEmpty());
        assertEquals(3, unavailable.totalIncidentCountConsidered());
        assertEquals(IncidentQueryResult.CaseFilterStatus.UNAVAILABLE,
                unavailable.caseFilterStatus());
        assertEquals(3, ordinary.matchingCount());
        assertEquals(IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED,
                ordinary.caseFilterStatus());
    }

    @Test
    void combinedFiltersUseAndSemantics() {
        IncidentQuery combined = IncidentQuery.builder()
                .freeText("world generator")
                .evidenceStates(Set.of(EvidenceBadge.AMBIGUOUS_ATTRIBUTION))
                .minimumStallDurationMs(500.0)
                .maximumStallDurationMs(700.0)
                .ownerId("example_worldgen")
                .dimensionId("minecraft:the_nether")
                .detectedAtOrAfterEpochMs(150L)
                .hasRecurringCase(true)
                .caseId("case-beta")
                .build();

        assertEquals(List.of("freeze-beta.json"), ids(query(combined, memberships())));
        assertTrue(query(IncidentQuery.builder()
                .freeText("world generator")
                .dimensionId("minecraft:overworld")
                .build()).matchingIncidents().isEmpty());
    }

    @Test
    void supportsNewestAndOldestOrdering() {
        assertEquals(List.of("freeze-alpha.json", "freeze-beta.json", "freeze-gamma.json"),
                ids(query(IncidentQuery.builder().sort(IncidentQuery.Sort.NEWEST_FIRST).build())));
        assertEquals(List.of("freeze-gamma.json", "freeze-beta.json", "freeze-alpha.json"),
                ids(query(IncidentQuery.builder().sort(IncidentQuery.Sort.OLDEST_FIRST).build())));
    }

    @Test
    void supportsLongestAndShortestDurationOrdering() {
        assertEquals(List.of("freeze-beta.json", "freeze-alpha.json", "freeze-gamma.json"),
                ids(query(IncidentQuery.builder()
                        .sort(IncidentQuery.Sort.LONGEST_STALL_FIRST).build())));
        assertEquals(List.of("freeze-gamma.json", "freeze-alpha.json", "freeze-beta.json"),
                ids(query(IncidentQuery.builder()
                        .sort(IncidentQuery.Sort.SHORTEST_STALL_FIRST).build())));
    }

    @Test
    void tieBreakingUsesTimestampThenIncidentIdDeterministically() {
        List<IncidentSearchRecord> ties = List.of(
                record("freeze-z.json", 10L, 200.0, EvidenceBadge.UNKNOWN,
                        "UNKNOWN", Set.of(), Set.of(), null),
                record("freeze-a.json", 10L, 200.0, EvidenceBadge.UNKNOWN,
                        "UNKNOWN", Set.of(), Set.of(), null),
                record("folder/freeze-a.json", 10L, 200.0, EvidenceBadge.UNKNOWN,
                        "UNKNOWN", Set.of(), Set.of(), null));

        IncidentQueryResult result = engine.query(ties,
                IncidentQuery.builder().sort(IncidentQuery.Sort.LONGEST_STALL_FIRST).build(),
                CaseMembershipIndex.unavailable());

        assertEquals(List.of("folder/freeze-a.json", "freeze-a.json", "freeze-z.json"),
                result.matchingIncidents().stream().map(IncidentSearchRecord::incidentId).toList());
    }

    @Test
    void sparseLegacyIncidentIsIncludedButCannotMatchUnavailableOptionalMetadata() {
        IncidentSummaryViewModel summary = summary(
                "freeze-legacy.json", 0L, 0.0, EvidenceBadge.UNKNOWN, "UNKNOWN");
        IncidentSearchRecord legacy = new IncidentSearchRecord(
                "freeze-legacy.json", summary, Set.of(), Set.of(), Optional.empty(),
                OptionalLong.empty(), OptionalDouble.empty());

        assertEquals(1, engine.query(List.of(legacy), IncidentQuery.empty(),
                CaseMembershipIndex.unavailable()).matchingCount());
        assertEquals(0, engine.query(List.of(legacy), IncidentQuery.builder()
                        .minimumStallDurationMs(0.0).build(), CaseMembershipIndex.unavailable())
                .matchingCount());
        assertEquals(0, engine.query(List.of(legacy), IncidentQuery.builder()
                        .detectedAtOrAfterEpochMs(0L).build(), CaseMembershipIndex.unavailable())
                .matchingCount());
        assertEquals(0, engine.query(List.of(legacy), IncidentQuery.builder()
                        .dimensionId("minecraft:overworld").build(),
                CaseMembershipIndex.unavailable()).matchingCount());

        IncidentQueryResult sorted = engine.query(
                List.of(legacy, history.getLast()),
                IncidentQuery.builder().sort(IncidentQuery.Sort.SHORTEST_STALL_FIRST).build(),
                CaseMembershipIndex.unavailable());
        assertEquals(List.of("freeze-gamma.json", "freeze-legacy.json"), ids(sorted));
    }

    @Test
    void queryingDoesNotMutateIncidentsOrHistory() {
        List<IncidentSearchRecord> mutable = new ArrayList<>(history);
        List<IncidentSearchRecord> snapshot = List.copyOf(mutable);

        IncidentQueryResult result = engine.query(mutable,
                IncidentQuery.builder().freeText("example").build(), memberships());

        assertEquals(snapshot, mutable);
        assertEquals(history, snapshot);
        assertThrows(UnsupportedOperationException.class,
                () -> result.matchingIncidents().add(history.getFirst()));
        assertThrows(UnsupportedOperationException.class,
                () -> history.getFirst().ownerIds().add("mutated"));
    }

    @Test
    void repeatedQueryProducesIdenticalOutput() {
        IncidentQuery requested = IncidentQuery.builder()
                .freeText("example")
                .minimumStallDurationMs(200.0)
                .sort(IncidentQuery.Sort.LONGEST_STALL_FIRST)
                .build();

        IncidentQueryResult expected = query(requested, memberships());

        assertEquals(expected, query(requested, memberships()));
        assertEquals(expected, new IncidentQueryEngine().query(
                history, requested, memberships()));
    }

    @Test
    void benchmarksFilteringSearchCombinedCriteriaAndSortingAtBoundedHistorySizes() {
        for (int size : List.of(50, 250, 500)) {
            List<IncidentSearchRecord> records = benchmarkHistory(size);
            IncidentQuery filter = IncidentQuery.builder().minimumStallDurationMs(300.0).build();
            IncidentQuery text = IncidentQuery.builder().freeText("machines").build();
            IncidentQuery combined = IncidentQuery.builder()
                    .freeText("machines")
                    .minimumStallDurationMs(300.0)
                    .maximumStallDurationMs(700.0)
                    .ownerId("example_machines")
                    .dimensionId("minecraft:overworld")
                    .evidenceStates(Set.of(EvidenceBadge.HIGH_EVIDENCE))
                    .detectedAtOrAfterEpochMs(10L)
                    .build();
            IncidentQuery sorting = IncidentQuery.builder()
                    .sort(IncidentQuery.Sort.LONGEST_STALL_FIRST).build();
            for (int warmup = 0; warmup < 20; warmup++) {
                engine.query(records, filter, CaseMembershipIndex.unavailable());
                engine.query(records, text, CaseMembershipIndex.unavailable());
                engine.query(records, combined, CaseMembershipIndex.unavailable());
                engine.query(records, sorting, CaseMembershipIndex.unavailable());
            }
            long filterNs = medianNanos(records, filter);
            long textNs = medianNanos(records, text);
            long combinedNs = medianNanos(records, combined);
            long sortingNs = medianNanos(records, sorting);
            System.out.printf(Locale.ROOT,
                    "INCIDENT_QUERY_BENCHMARK size=%d filter=%.3fms text=%.3fms "
                            + "combined=%.3fms sorting=%.3fms%n",
                    size, millis(filterNs), millis(textNs), millis(combinedNs), millis(sortingNs));
        }
    }

    private IncidentQueryResult query(IncidentQuery query) {
        return query(query, CaseMembershipIndex.unavailable());
    }

    private IncidentQueryResult query(IncidentQuery query, CaseMembershipIndex cases) {
        return engine.query(history, query, cases);
    }

    private static List<String> ids(IncidentQueryResult result) {
        return result.matchingIncidents().stream()
                .map(value -> value.summary().id()).toList();
    }

    private static CaseMembershipIndex memberships() {
        return CaseMembershipIndex.available(List.of(
                caseFile("case-alpha", List.of(
                        "freeze-alpha.json", "deleted-alpha-1.json", "deleted-alpha-2.json")),
                caseFile("case-beta", List.of(
                        "freeze-beta.json", "deleted-beta-1.json", "deleted-beta-2.json"))));
    }

    private static CaseFile caseFile(String id, List<String> members) {
        return new CaseFile(
                id, members, 1L, 3L, members.size(), 100.0, 100.0,
                List.of(), List.of(), 0.8, 0.8);
    }

    private static IncidentSearchRecord record(
            String id,
            long timestamp,
            double duration,
            EvidenceBadge evidence,
            String rawState,
            Set<String> owners,
            Set<String> names,
            String dimension
    ) {
        String summaryId = Path.of(id).getFileName().toString();
        return new IncidentSearchRecord(
                id,
                summary(summaryId, timestamp, duration, evidence, rawState),
                owners,
                names,
                Optional.ofNullable(dimension),
                OptionalLong.of(timestamp),
                OptionalDouble.of(duration));
    }

    private static IncidentSummaryViewModel summary(
            String id,
            long timestamp,
            double duration,
            EvidenceBadge evidence,
            String rawState
    ) {
        return new IncidentSummaryViewModel(
                id, Path.of(id), timestamp, duration, 120.0, 10,
                evidence, rawState, "", false, "date", "dimension", "coordinates");
    }

    private static List<IncidentSearchRecord> benchmarkHistory(int size) {
        List<IncidentSearchRecord> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            boolean machines = index % 2 == 0;
            result.add(record(
                    "freeze-%04d.json".formatted(index),
                    index + 1L,
                    100.0 + index % 700,
                    machines ? EvidenceBadge.HIGH_EVIDENCE : EvidenceBadge.INSUFFICIENT_EVIDENCE,
                    machines ? "ATTRIBUTED" : "INSUFFICIENT_EVIDENCE",
                    Set.of(machines ? "example_machines" : "example_worldgen"),
                    Set.of(machines ? "Example Machines" : "Example Worldgen"),
                    machines ? "minecraft:overworld" : "minecraft:the_nether"));
        }
        return List.copyOf(result);
    }

    private long medianNanos(List<IncidentSearchRecord> records, IncidentQuery query) {
        long[] timings = new long[51];
        for (int iteration = 0; iteration < timings.length; iteration++) {
            long started = System.nanoTime();
            engine.query(records, query, CaseMembershipIndex.unavailable());
            timings[iteration] = System.nanoTime() - started;
        }
        Arrays.sort(timings);
        return timings[timings.length / 2];
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
