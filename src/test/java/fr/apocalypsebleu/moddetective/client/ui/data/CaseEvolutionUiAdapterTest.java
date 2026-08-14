package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.data.evolution.CaseEvolution;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.ModpackChangeHistory;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.HistoryCoverageViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.NearbyChangeViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiTextFitter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseEvolutionUiAdapterTest {
    private static final long FIRST = 1_786_530_000_000L;

    @Test
    void projectsFirstRecordedTimestampAndAllNeutralChangeTypes() {
        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.SUFFICIENT,
                ModpackChangeHistory.Availability.COMPLETE,
                List.of(
                        change(CaseEvolution.ChangeType.ADDED, "example_storage", "Example Storage",
                                -3_600_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT),
                        change(CaseEvolution.ChangeType.UPDATED, "example_machines", "Example Machines",
                                0L, true, CaseEvolution.CoverageStatus.SUFFICIENT),
                        change(CaseEvolution.ChangeType.REMOVED, "example_worldgen", "Example Worldgen",
                                7_200_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT))));

        assertEquals(FIRST, result.firstRecordedOccurrenceEpochMs().orElseThrow());
        assertEquals(List.of(
                        NearbyChangeViewModel.Type.ADDED,
                        NearbyChangeViewModel.Type.UPDATED,
                        NearbyChangeViewModel.Type.REMOVED),
                result.nearbyChanges().stream().map(NearbyChangeViewModel::type).toList());
        assertEquals(List.of(
                        NearbyChangeViewModel.Direction.BEFORE,
                        NearbyChangeViewModel.Direction.AT,
                        NearbyChangeViewModel.Direction.AFTER),
                result.nearbyChanges().stream().map(NearbyChangeViewModel::direction).toList());
        assertTrue(result.nearbyChanges().get(1).sameRecordedLaunch());
        assertEquals(Optional.of("1.0"), result.nearbyChanges().get(1).previousVersion());
        assertEquals(Optional.of("2.0"), result.nearbyChanges().get(1).newVersion());
    }

    @Test
    void preservesBackendOrderAndProducesIdenticalImmutableOutput() {
        CaseEvolution backend = source(
                CaseEvolution.CoverageStatus.SUFFICIENT,
                ModpackChangeHistory.Availability.COMPLETE,
                List.of(
                        change(CaseEvolution.ChangeType.UPDATED, "example_worldgen", "Example Worldgen",
                                -1_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT),
                        change(CaseEvolution.ChangeType.ADDED, "example_storage", "Example Storage",
                                -2_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT)));

        CaseEvolutionViewModel first = CaseEvolutionUiAdapter.from(backend);
        CaseEvolutionViewModel second = CaseEvolutionUiAdapter.from(backend);

        assertEquals(first, second);
        assertEquals(List.of("example_worldgen", "example_storage"), first.nearbyChanges().stream()
                .map(NearbyChangeViewModel::modId).toList());
    }

    @Test
    void exposesAllCoverageStatesWithoutInternalCopy() {
        for (CaseEvolution.CoverageStatus status : CaseEvolution.CoverageStatus.values()) {
            CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                    status,
                    status == CaseEvolution.CoverageStatus.INSUFFICIENT
                            ? ModpackChangeHistory.Availability.UNAVAILABLE
                            : ModpackChangeHistory.Availability.PARTIAL,
                    List.of()));

            assertEquals(HistoryCoverageViewModel.Status.valueOf(status.name()),
                    result.historyCoverage().status());
            assertTrue(result.historyCoverage().messageKey().startsWith(
                    "detective.ui.case.evolution.coverage."));
        }
    }

    @Test
    void unavailableHistoryDoesNotEnableNavigationOrInventNearbyChanges() {
        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.INSUFFICIENT,
                ModpackChangeHistory.Availability.UNAVAILABLE,
                List.of()));

        assertFalse(result.canViewModpackChanges());
        assertTrue(result.nearbyChanges().isEmpty());
        assertEquals(0, result.totalNearbyChangeCount());
    }

    @Test
    void beforeAfterCountsRequireSufficientComparableCoverage() {
        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.LIMITED_BEFORE,
                ModpackChangeHistory.Availability.PARTIAL,
                List.of(
                        change(CaseEvolution.ChangeType.UPDATED, "example_machines", "Example Machines",
                                -1_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT),
                        change(CaseEvolution.ChangeType.UPDATED, "example_storage", "Example Storage",
                                -2_000L, false, CaseEvolution.CoverageStatus.LIMITED_BEFORE))));

        assertEquals(1, result.nearbyChanges().get(0).beforeAfter().before().orElseThrow());
        assertEquals(2, result.nearbyChanges().get(0).beforeAfter().after().orElseThrow());
        assertFalse(result.nearbyChanges().get(1).beforeAfter().available());
    }

    @Test
    void ambiguousDisplayNameAssociationFallsBackToExactModId() {
        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.SUFFICIENT,
                ModpackChangeHistory.Availability.COMPLETE,
                List.of(
                        change(CaseEvolution.ChangeType.UPDATED, "example_machines", "Example Machines",
                                -1_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT),
                        change(CaseEvolution.ChangeType.UPDATED, "example_machines", "Example Mod",
                                1_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT))));

        assertEquals(List.of("example_machines", "example_machines"), result.nearbyChanges().stream()
                .map(NearbyChangeViewModel::displayLabel).toList());
    }

    @Test
    void unambiguousLongNameAndIdRemainExactUntilWidthAwareRendering() {
        String id = "example_machines_with_an_intentionally_long_identifier_for_validation";
        String name = "Example Machines";
        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.SUFFICIENT,
                ModpackChangeHistory.Availability.COMPLETE,
                List.of(change(CaseEvolution.ChangeType.UPDATED, id, name,
                        -1_000L, false, CaseEvolution.CoverageStatus.SUFFICIENT))));
        String label = result.nearbyChanges().getFirst().displayLabel();

        assertEquals(name + " (" + id + ")", label);
        String fitted = UiTextFitter.ellipsize(label, 24, String::length);
        assertTrue(fitted.endsWith("…"));
        assertTrue(fitted.length() <= 24);
    }

    @Test
    void projectionBoundsPathologicalNearbyListsAndReportsOmittedCount() {
        List<CaseEvolution.NearbyModpackChange> changes = new ArrayList<>();
        for (int index = 0; index < CaseEvolutionUiAdapter.MAXIMUM_VISIBLE_CHANGES + 4; index++) {
            changes.add(change(CaseEvolution.ChangeType.UPDATED,
                    "example_mod_" + index, "Example Mod", index,
                    false, CaseEvolution.CoverageStatus.SUFFICIENT));
        }

        CaseEvolutionViewModel result = CaseEvolutionUiAdapter.from(source(
                CaseEvolution.CoverageStatus.SUFFICIENT,
                ModpackChangeHistory.Availability.COMPLETE,
                changes));

        assertEquals(CaseEvolutionUiAdapter.MAXIMUM_VISIBLE_CHANGES, result.nearbyChanges().size());
        assertEquals(4, result.omittedNearbyChangeCount());
    }

    @Test
    void missingVersionPresentationRemainsExplicitlyMissing() {
        NearbyChangeViewModel sparse = new NearbyChangeViewModel(
                NearbyChangeViewModel.Type.UPDATED,
                "example_mod",
                "example_mod",
                Optional.empty(),
                Optional.empty(),
                FIRST,
                0L,
                NearbyChangeViewModel.Direction.AT,
                "0m",
                false,
                NearbyChangeViewModel.BeforeAfterViewModel.unavailable());

        assertTrue(sparse.previousVersion().isEmpty());
        assertTrue(sparse.newVersion().isEmpty());
    }

    private static CaseEvolution source(
            CaseEvolution.CoverageStatus coverage,
            ModpackChangeHistory.Availability availability,
            List<CaseEvolution.NearbyModpackChange> changes
    ) {
        List<CaseEvolution.Occurrence> timeline = List.of(
                occurrence("incident-a", FIRST),
                occurrence("incident-b", FIRST + 1_000L),
                occurrence("incident-c", FIRST + 2_000L));
        return new CaseEvolution(
                "case-stable",
                CaseEvolution.FirstOccurrenceScope.FIRST_RECORDED_IN_RETAINED_HISTORY,
                CaseEvolution.CorrelationMeaning.TEMPORAL_PROXIMITY_DOES_NOT_ESTABLISH_CAUSATION,
                Optional.of(timeline.getFirst()),
                Optional.of(timeline.getLast()),
                3,
                3,
                0,
                0,
                timeline,
                0.88,
                0.82,
                new CaseEvolution.Coverage(coverage, Set.of()),
                availability,
                changes);
    }

    private static CaseEvolution.Occurrence occurrence(String id, long timestamp) {
        return new CaseEvolution.Occurrence(id, timestamp, OptionalDouble.of(500.0));
    }

    private static CaseEvolution.NearbyModpackChange change(
            CaseEvolution.ChangeType type,
            String modId,
            String displayName,
            long offset,
            boolean sameLaunch,
            CaseEvolution.CoverageStatus comparisonCoverage
    ) {
        Optional<String> previous = type == CaseEvolution.ChangeType.ADDED
                ? Optional.empty() : Optional.of("1.0");
        Optional<String> next = type == CaseEvolution.ChangeType.REMOVED
                ? Optional.empty() : Optional.of("2.0");
        CaseEvolution.FrequencyTrend trend = comparisonCoverage == CaseEvolution.CoverageStatus.SUFFICIENT
                ? CaseEvolution.FrequencyTrend.MORE_RECORDED_AFTER
                : CaseEvolution.FrequencyTrend.UNAVAILABLE;
        return new CaseEvolution.NearbyModpackChange(
                type,
                modId,
                displayName,
                previous,
                next,
                FIRST + offset,
                offset,
                offset < 0L ? CaseEvolution.TemporalDirection.BEFORE_FIRST_RECORDED
                        : offset > 0L ? CaseEvolution.TemporalDirection.AFTER_FIRST_RECORDED
                        : CaseEvolution.TemporalDirection.AT_FIRST_RECORDED,
                sameLaunch ? CaseEvolution.ProximityBand.SAME_RECORDED_LAUNCH
                        : CaseEvolution.ProximityBand.VERY_NEAR,
                sameLaunch,
                new CaseEvolution.BeforeAfterEvidence(
                        1, 0, 2,
                        Optional.of(occurrence("incident-a", FIRST)),
                        Optional.of(occurrence("incident-b", FIRST + 1_000L)),
                        -offset,
                        1,
                        2,
                        comparisonCoverage,
                        trend));
    }
}
