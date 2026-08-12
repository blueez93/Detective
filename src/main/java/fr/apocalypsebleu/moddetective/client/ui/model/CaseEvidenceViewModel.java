package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

/** Recurring technical observation exposed by a Case without adding an attribution claim. */
public record CaseEvidenceViewModel(
        Kind kind,
        String technicalSignature,
        int supportingIncidents,
        double averageObservedSharePercent
) {
    public CaseEvidenceViewModel {
        kind = Objects.requireNonNull(kind, "kind");
        technicalSignature = Objects.requireNonNullElse(technicalSignature, "");
        supportingIncidents = Math.max(0, supportingIncidents);
        averageObservedSharePercent = boundedPercent(averageObservedSharePercent);
    }

    private static double boundedPercent(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }

    public enum Kind {
        CLASS("detective.ui.cases.evidence.kind.class"),
        FRAME("detective.ui.cases.evidence.kind.frame"),
        STACK_PATH("detective.ui.cases.evidence.kind.stack_path");

        private final String translationKey;

        Kind(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }
}
