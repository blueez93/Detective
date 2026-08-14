package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

/** Localized presentation state for the retained history around a Case. */
public record HistoryCoverageViewModel(Status status) {
    public HistoryCoverageViewModel {
        status = Objects.requireNonNull(status, "status");
    }

    public String messageKey() {
        return switch (status) {
            case SUFFICIENT -> "detective.ui.case.evolution.coverage.sufficient";
            case LIMITED_BEFORE -> "detective.ui.case.evolution.coverage.limited_before";
            case LIMITED_AFTER -> "detective.ui.case.evolution.coverage.limited_after";
            case LIMITED_BOTH -> "detective.ui.case.evolution.coverage.limited_both";
            case INSUFFICIENT -> "detective.ui.case.evolution.coverage.insufficient";
            case UNKNOWN -> "detective.ui.case.evolution.coverage.unknown";
        };
    }

    public enum Status {
        SUFFICIENT,
        LIMITED_BEFORE,
        LIMITED_AFTER,
        LIMITED_BOTH,
        INSUFFICIENT,
        UNKNOWN
    }
}
