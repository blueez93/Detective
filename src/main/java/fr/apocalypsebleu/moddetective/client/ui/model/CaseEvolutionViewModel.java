package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Immutable Case Evolution data consumed by the Case detail screen. */
public record CaseEvolutionViewModel(
        String caseId,
        OptionalLong firstRecordedOccurrenceEpochMs,
        HistoryCoverageViewModel historyCoverage,
        HistoryAvailability historyAvailability,
        List<NearbyChangeViewModel> nearbyChanges,
        int totalNearbyChangeCount
) {
    public CaseEvolutionViewModel {
        caseId = Objects.requireNonNull(caseId, "caseId").strip();
        firstRecordedOccurrenceEpochMs = Objects.requireNonNull(
                firstRecordedOccurrenceEpochMs, "firstRecordedOccurrenceEpochMs");
        historyCoverage = Objects.requireNonNull(historyCoverage, "historyCoverage");
        historyAvailability = Objects.requireNonNull(historyAvailability, "historyAvailability");
        nearbyChanges = List.copyOf(Objects.requireNonNull(nearbyChanges, "nearbyChanges"));
        if (caseId.isEmpty() || totalNearbyChangeCount < nearbyChanges.size()) {
            throw new IllegalArgumentException("Case Evolution UI counts are inconsistent");
        }
    }

    public int omittedNearbyChangeCount() {
        return totalNearbyChangeCount - nearbyChanges.size();
    }

    public boolean canViewModpackChanges() {
        return historyAvailability != HistoryAvailability.UNAVAILABLE;
    }

    public enum HistoryAvailability {
        COMPLETE,
        PARTIAL,
        UNAVAILABLE
    }
}
