package fr.apocalypsebleu.moddetective.client.ui.model;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Display-safe comparison data. Derived signature hashes are deliberately absent. */
public record IncidentComparisonViewModel(
        Side first,
        Side second,
        Similarity similarity,
        EvidenceColumns evidence
) {
    public IncidentComparisonViewModel {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(similarity, "similarity");
        Objects.requireNonNull(evidence, "evidence");
    }

    public record Side(
            IncidentSummaryViewModel incident,
            OptionalLong detectedAtEpochMs,
            OptionalDouble stallDurationMs,
            OptionalInt capturedSampleCount,
            AttributionEvidence.State attributionState,
            Optional<String> dimensionId,
            OptionalLong usedMemoryBytes,
            OptionalLong maximumMemoryBytes
    ) {
        public Side {
            Objects.requireNonNull(incident, "incident");
            Objects.requireNonNull(detectedAtEpochMs, "detectedAtEpochMs");
            Objects.requireNonNull(stallDurationMs, "stallDurationMs");
            Objects.requireNonNull(capturedSampleCount, "capturedSampleCount");
            Objects.requireNonNull(attributionState, "attributionState");
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(usedMemoryBytes, "usedMemoryBytes");
            Objects.requireNonNull(maximumMemoryBytes, "maximumMemoryBytes");
        }
    }

    public record Similarity(
            IncidentComparison.EvidenceAvailability availability,
            OptionalDouble score
    ) {
        public Similarity {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(score, "score");
            if (availability == IncidentComparison.EvidenceAvailability.AVAILABLE
                    && score.isEmpty()) {
                throw new IllegalArgumentException("Available Technical Similarity requires a score");
            }
            if (availability != IncidentComparison.EvidenceAvailability.AVAILABLE
                    && score.isPresent()) {
                throw new IllegalArgumentException("Unavailable Technical Similarity cannot expose a score");
            }
        }

        public boolean available() {
            return availability == IncidentComparison.EvidenceAvailability.AVAILABLE;
        }
    }

    public record EvidenceColumns(
            List<EvidenceItem> shared,
            List<EvidenceItem> onlyFirst,
            List<EvidenceItem> onlySecond,
            boolean comparisonAvailable,
            List<UnavailableCategory> unavailableCategories
    ) {
        public EvidenceColumns {
            shared = List.copyOf(Objects.requireNonNull(shared, "shared"));
            onlyFirst = List.copyOf(Objects.requireNonNull(onlyFirst, "onlyFirst"));
            onlySecond = List.copyOf(Objects.requireNonNull(onlySecond, "onlySecond"));
            unavailableCategories = List.copyOf(Objects.requireNonNull(
                    unavailableCategories, "unavailableCategories"));
        }
    }

    public record UnavailableCategory(
            EvidenceCategory category,
            IncidentComparison.EvidenceAvailability availability
    ) {
        public UnavailableCategory {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(availability, "availability");
            if (availability == IncidentComparison.EvidenceAvailability.AVAILABLE) {
                throw new IllegalArgumentException("Only unavailable evidence belongs in this list");
            }
        }
    }

    public enum EvidenceCategory {
        CLASS_SIGNATURES,
        FRAME_SIGNATURES,
        STACK_PATH_SIGNATURES,
        OWNERS
    }

    public record EvidenceItem(Kind kind, String ownerLabel, int signatureNumber) {
        public EvidenceItem {
            Objects.requireNonNull(kind, "kind");
            ownerLabel = Objects.requireNonNullElse(ownerLabel, "");
            if (kind == Kind.OWNER && ownerLabel.isBlank()) {
                throw new IllegalArgumentException("Owner evidence requires a display label");
            }
            if (kind == Kind.TECHNICAL_SIGNATURE && signatureNumber <= 0) {
                throw new IllegalArgumentException("Technical signature numbers start at one");
            }
        }

        public static EvidenceItem owner(String label) {
            return new EvidenceItem(Kind.OWNER, label, 0);
        }

        public static EvidenceItem technicalSignature(int number) {
            return new EvidenceItem(Kind.TECHNICAL_SIGNATURE, "", number);
        }

        public enum Kind {
            OWNER,
            TECHNICAL_SIGNATURE
        }
    }
}
