package fr.apocalypsebleu.moddetective.client.ui.model;

import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQuery;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryEngine;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryResult;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.client.ui.data.query.CaseMembershipIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentInvestigationStateTest {
    @Test
    void blankSearchRestoresAnUnconstrainedBackendQuery() {
        IncidentInvestigationState state = new IncidentInvestigationState();

        state.setSearchText("example mod");
        assertEquals("example mod", state.query().freeText().orElseThrow());

        state.setSearchText("   ");
        assertTrue(state.query().freeText().isEmpty());
    }

    @Test
    void resultRetainsFilteredAndTotalCounts() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        IncidentSearchRecord match = incident("a", 2_000L, 500.0);
        IncidentQuery query = IncidentQuery.builder().freeText("a").build();

        state.applyResult(new IncidentQueryResult(
                List.of(match), 12, 1, query, query.sort(),
                IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED));

        assertEquals(1, state.result().orElseThrow().matchingCount());
        assertEquals(12, state.result().orElseThrow().totalIncidentCountConsidered());
        assertEquals(List.of(match), state.matchingIncidents());
    }

    @Test
    void activeFilterCountExcludesSearchAndSortAndClearResetsFilters() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setSearchText("alpha");
        state.setSort(IncidentQuery.Sort.LONGEST_STALL_FIRST);
        state.setEvidenceFilter(IncidentInvestigationState.EvidenceFilter.HIGH);
        state.setDurationFilter(IncidentInvestigationState.DurationFilter.AT_LEAST_500_MS);
        state.setCaseFilter(IncidentInvestigationState.CaseFilter.IN_CASE);

        assertEquals(3, state.activeFilterCount());
        state.clearFilters();

        assertEquals(0, state.activeFilterCount());
        assertEquals("alpha", state.query().freeText().orElseThrow());
        assertEquals(IncidentQuery.Sort.LONGEST_STALL_FIRST, state.query().sort());
        assertTrue(state.query().evidenceStates().isEmpty());
        assertTrue(state.query().minimumStallDurationMs().isEmpty());
        assertTrue(state.query().hasRecurringCase().isEmpty());
    }

    @Test
    void filtersMapExactlyToExistingBackendCriteria() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setEvidenceFilter(IncidentInvestigationState.EvidenceFilter.SYSTEM);
        state.setDurationFilter(IncidentInvestigationState.DurationFilter.AT_LEAST_1_SECOND);
        state.setCaseFilter(IncidentInvestigationState.CaseFilter.NOT_IN_CASE);
        state.setSort(IncidentQuery.Sort.SHORTEST_STALL_FIRST);

        IncidentQuery query = state.query();

        assertEquals(Set.of(
                EvidenceBadge.JVM_GC_SUSPECTED,
                EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE), query.evidenceStates());
        assertEquals(1_000.0, query.minimumStallDurationMs().orElseThrow());
        assertEquals(false, query.hasRecurringCase().orElseThrow());
        assertEquals(IncidentQuery.Sort.SHORTEST_STALL_FIRST, query.sort());
    }

    @Test
    void selectionAllowsZeroOneAndExactlyTwoIncidents() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        IncidentSearchRecord first = incident("a", 3_000L, 300.0);
        IncidentSearchRecord second = incident("b", 2_000L, 400.0);

        assertEquals(0, state.selectionCount());
        assertFalse(state.canCompare());
        assertEquals(IncidentInvestigationState.SelectionChange.SELECTED,
                state.toggleSelection(first));
        assertEquals(1, state.selectionCount());
        assertFalse(state.canCompare());
        assertEquals(IncidentInvestigationState.SelectionChange.SELECTED,
                state.toggleSelection(second));
        assertEquals(2, state.selectionCount());
        assertTrue(state.canCompare());
        assertEquals(List.of(first, second), state.selectedIncidents());
    }

    @Test
    void thirdSelectionIsRejectedWithoutChangingABOrder() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        IncidentSearchRecord first = incident("a", 3_000L, 300.0);
        IncidentSearchRecord second = incident("b", 2_000L, 400.0);
        IncidentSearchRecord third = incident("c", 1_000L, 500.0);
        state.toggleSelection(first);
        state.toggleSelection(second);

        assertEquals(IncidentInvestigationState.SelectionChange.LIMIT_REACHED,
                state.toggleSelection(third));
        assertEquals(List.of(first, second), state.selectedIncidents());
        assertFalse(state.isSelected(third.incidentId()));
    }

    @Test
    void selectedIncidentCanBeDeselectedAndCancellationClearsBoth() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        IncidentSearchRecord first = incident("a", 3_000L, 300.0);
        IncidentSearchRecord second = incident("b", 2_000L, 400.0);
        state.toggleSelection(first);
        state.toggleSelection(second);

        assertEquals(IncidentInvestigationState.SelectionChange.DESELECTED,
                state.toggleSelection(first));
        assertEquals(List.of(second), state.selectedIncidents());

        state.clearSelection();
        assertEquals(0, state.selectionCount());
        assertFalse(state.canCompare());
    }

    @Test
    void changingAnyQueryCriterionClearsInvisibleSelections() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.toggleSelection(incident("a", 3_000L, 300.0));
        state.toggleSelection(incident("b", 2_000L, 400.0));

        state.setSort(IncidentQuery.Sort.OLDEST_FIRST);

        assertEquals(0, state.selectionCount());
    }

    @Test
    void repeatedBackendResultsKeepStableRequestedOrderingInTheUiState() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setSort(IncidentQuery.Sort.OLDEST_FIRST);
        List<IncidentSearchRecord> history = List.of(
                incident("new", 3_000L, 300.0),
                incident("old", 1_000L, 500.0),
                incident("middle", 2_000L, 400.0));
        IncidentQueryEngine engine = new IncidentQueryEngine();

        IncidentQueryResult first = engine.query(
                history, state.query(), CaseMembershipIndex.unavailable());
        IncidentQueryResult second = engine.query(
                history, state.query(), CaseMembershipIndex.unavailable());
        state.applyResult(first);

        assertEquals(List.of("old", "middle", "new"), state.matchingIncidents().stream()
                .map(IncidentSearchRecord::incidentId).toList());
        assertEquals(first, second);
    }

    private static IncidentSearchRecord incident(String id, long detectedAt, double duration) {
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                id, Path.of("build", "test-incidents", id + ".json"), detectedAt,
                duration, 120.0, 10, EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED",
                "Example Mod", true, "2026-08-14 12:00:00", "Overworld", "0, 64, 0");
        return new IncidentSearchRecord(
                id, summary, Set.of("example_mod"), Set.of("Example Mod"),
                Optional.of("minecraft:overworld"), OptionalLong.of(detectedAt),
                OptionalDouble.of(duration));
    }
}
