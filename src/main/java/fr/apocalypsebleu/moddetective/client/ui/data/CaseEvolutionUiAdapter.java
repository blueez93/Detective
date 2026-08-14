package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.data.evolution.CaseEvolution;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionUiFormatter;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.HistoryCoverageViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.NearbyChangeViewModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/** Projects backend Case Evolution evidence into bounded, immutable screen data. */
public final class CaseEvolutionUiAdapter {
    public static final int MAXIMUM_VISIBLE_CHANGES = 32;

    private CaseEvolutionUiAdapter() {}

    public static CaseEvolutionViewModel from(CaseEvolution source) {
        Objects.requireNonNull(source, "source");
        Map<String, Set<String>> namesById = displayNamesById(source.nearbyChanges());
        List<NearbyChangeViewModel> changes = source.nearbyChanges().stream()
                .limit(MAXIMUM_VISIBLE_CHANGES)
                .map(value -> project(value, namesById))
                .toList();
        OptionalLong firstRecorded = source.firstRecordedIncident()
                .map(value -> OptionalLong.of(value.detectedAtEpochMs()))
                .orElseGet(OptionalLong::empty);
        return new CaseEvolutionViewModel(
                source.caseId(),
                firstRecorded,
                new HistoryCoverageViewModel(HistoryCoverageViewModel.Status.valueOf(
                        source.retainedHistoryCoverage().status().name())),
                CaseEvolutionViewModel.HistoryAvailability.valueOf(
                        source.changeHistoryAvailability().name()),
                changes,
                source.nearbyChanges().size());
    }

    private static NearbyChangeViewModel project(
            CaseEvolution.NearbyModpackChange source,
            Map<String, Set<String>> namesById
    ) {
        Set<String> names = namesById.getOrDefault(source.modId(), Set.of());
        String displayLabel = names.size() == 1
                ? names.iterator().next() + " (" + source.modId() + ")"
                : source.modId();
        boolean comparable = source.retainedEvidence().comparableWindowCoverage()
                == CaseEvolution.CoverageStatus.SUFFICIENT;
        NearbyChangeViewModel.BeforeAfterViewModel beforeAfter = comparable
                ? NearbyChangeViewModel.BeforeAfterViewModel.available(
                        source.retainedEvidence().availableBeforeChange(),
                        source.retainedEvidence().availableAfterChange())
                : NearbyChangeViewModel.BeforeAfterViewModel.unavailable();
        return new NearbyChangeViewModel(
                NearbyChangeViewModel.Type.valueOf(source.type().name()),
                source.modId(),
                displayLabel,
                source.previousVersion(),
                source.newVersion(),
                source.recordedAtEpochMs(),
                source.offsetFromFirstRecordedMs(),
                switch (source.direction()) {
                    case BEFORE_FIRST_RECORDED -> NearbyChangeViewModel.Direction.BEFORE;
                    case AT_FIRST_RECORDED -> NearbyChangeViewModel.Direction.AT;
                    case AFTER_FIRST_RECORDED -> NearbyChangeViewModel.Direction.AFTER;
                },
                CaseEvolutionUiFormatter.offsetMagnitude(source.offsetFromFirstRecordedMs()),
                source.sameRecordedLaunch(),
                beforeAfter);
    }

    private static Map<String, Set<String>> displayNamesById(
            List<CaseEvolution.NearbyModpackChange> changes
    ) {
        Map<String, Set<String>> result = new HashMap<>();
        for (CaseEvolution.NearbyModpackChange change : changes) {
            String name = change.modDisplayName().strip();
            if (!name.equalsIgnoreCase(change.modId())) {
                result.computeIfAbsent(change.modId(), ignored -> new HashSet<>()).add(name);
            }
        }
        return result;
    }
}
