package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Prepared, deterministically ordered Case data consumed directly by screens. */
public record CaseIndexViewModel(
        List<CaseFileViewModel> cases,
        int unreadableCaseEntries
) {
    public CaseIndexViewModel {
        cases = Objects.requireNonNullElse(cases, List.<CaseFileViewModel>of()).stream()
                .sorted(Comparator.comparingLong(CaseFileViewModel::lastSeenEpochMs).reversed()
                        .thenComparing(CaseFileViewModel::caseId))
                .toList();
        unreadableCaseEntries = Math.max(0, unreadableCaseEntries);
    }

    public static CaseIndexViewModel empty() {
        return new CaseIndexViewModel(List.of(), 0);
    }

    public boolean isEmpty() {
        return cases.isEmpty();
    }
}
