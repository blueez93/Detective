package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.List;
import java.util.Objects;

public record IncidentDetailViewModel(
        IncidentSummaryViewModel summary,
        List<SuspectViewModel> suspects,
        List<BlackBoxPoint> blackBox,
        int originalBlackBoxSamples,
        boolean blackBoxPartial
) {
    public IncidentDetailViewModel {
        summary = Objects.requireNonNull(summary, "summary");
        suspects = List.copyOf(Objects.requireNonNull(suspects, "suspects"));
        blackBox = List.copyOf(Objects.requireNonNull(blackBox, "blackBox"));
    }
}
