package fr.apocalypsebleu.moddetective.core.comparison;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeSet;

/**
 * Evidence-only comparison of two captured incidents.
 *
 * <p>All deltas are {@code second - first}. Empty optionals and non-available evidence categories
 * mean that the persisted incidents do not support that comparison; values are never fabricated.
 * Attribution states are retained for display context only and do not contribute to Technical
 * Similarity.</p>
 */
public record IncidentComparison(
        IncidentDescriptor firstIncident,
        IncidentDescriptor secondIncident,
        LongDifference detectedAtEpochMs,
        DoubleDifference stallDurationMs,
        IntDifference capturedSampleCount,
        TechnicalSimilarity technicalSimilarity,
        EvidenceSetComparison classSignatures,
        EvidenceSetComparison frameSignatures,
        EvidenceSetComparison stackPathSignatures,
        EvidenceSetComparison owners,
        EvidenceSetComparison leafOwners,
        EvidenceSetComparison stackPresenceOwners,
        EvidenceSetComparison stackDiversityOwners,
        ContextComparison context
) {
    public IncidentComparison {
        Objects.requireNonNull(firstIncident, "firstIncident");
        Objects.requireNonNull(secondIncident, "secondIncident");
        Objects.requireNonNull(detectedAtEpochMs, "detectedAtEpochMs");
        Objects.requireNonNull(stallDurationMs, "stallDurationMs");
        Objects.requireNonNull(capturedSampleCount, "capturedSampleCount");
        Objects.requireNonNull(technicalSimilarity, "technicalSimilarity");
        Objects.requireNonNull(classSignatures, "classSignatures");
        Objects.requireNonNull(frameSignatures, "frameSignatures");
        Objects.requireNonNull(stackPathSignatures, "stackPathSignatures");
        Objects.requireNonNull(owners, "owners");
        Objects.requireNonNull(leafOwners, "leafOwners");
        Objects.requireNonNull(stackPresenceOwners, "stackPresenceOwners");
        Objects.requireNonNull(stackDiversityOwners, "stackDiversityOwners");
        Objects.requireNonNull(context, "context");
    }

    public record IncidentDescriptor(
            String incidentId,
            AttributionEvidence.State attributionState,
            IncidentFingerprint.StallType stallType,
            IncidentFingerprint.EvidenceSource evidenceSource
    ) {
        public IncidentDescriptor {
            incidentId = requireText(incidentId, "incidentId");
            Objects.requireNonNull(attributionState, "attributionState");
            Objects.requireNonNull(stallType, "stallType");
            Objects.requireNonNull(evidenceSource, "evidenceSource");
        }
    }

    public record LongDifference(
            OptionalLong firstValue,
            OptionalLong secondValue,
            OptionalLong delta
    ) {
        public LongDifference {
            Objects.requireNonNull(firstValue, "firstValue");
            Objects.requireNonNull(secondValue, "secondValue");
            Objects.requireNonNull(delta, "delta");
            requireDeltaAvailability(firstValue.isPresent(), secondValue.isPresent(), delta.isPresent());
        }
    }

    public record DoubleDifference(
            OptionalDouble firstValue,
            OptionalDouble secondValue,
            OptionalDouble delta
    ) {
        public DoubleDifference {
            Objects.requireNonNull(firstValue, "firstValue");
            Objects.requireNonNull(secondValue, "secondValue");
            Objects.requireNonNull(delta, "delta");
            firstValue.ifPresent(value -> requireFinite(value, "firstValue"));
            secondValue.ifPresent(value -> requireFinite(value, "secondValue"));
            delta.ifPresent(value -> requireFinite(value, "delta"));
            requireDeltaAvailability(firstValue.isPresent(), secondValue.isPresent(), delta.isPresent());
        }
    }

    public record IntDifference(
            OptionalInt firstValue,
            OptionalInt secondValue,
            OptionalInt delta
    ) {
        public IntDifference {
            Objects.requireNonNull(firstValue, "firstValue");
            Objects.requireNonNull(secondValue, "secondValue");
            Objects.requireNonNull(delta, "delta");
            requireDeltaAvailability(firstValue.isPresent(), secondValue.isPresent(), delta.isPresent());
        }
    }

    public record TechnicalSimilarity(
            EvidenceAvailability availability,
            OptionalDouble score,
            double comparableWeightTotal,
            List<SimilarityComponent> components
    ) {
        public TechnicalSimilarity {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(score, "score");
            components = List.copyOf(Objects.requireNonNull(components, "components"));
            if (!Double.isFinite(comparableWeightTotal) || comparableWeightTotal < 0.0) {
                throw new IllegalArgumentException("comparableWeightTotal must be finite and non-negative");
            }
            if (availability == EvidenceAvailability.AVAILABLE) {
                if (score.isEmpty() || comparableWeightTotal <= 0.0) {
                    throw new IllegalArgumentException("Available similarity requires a score and evidence weight");
                }
                requireNormalized(score.getAsDouble(), "score");
            } else if (score.isPresent() || comparableWeightTotal != 0.0) {
                throw new IllegalArgumentException("Unavailable similarity must not expose a score");
            }
        }
    }

    public record SimilarityComponent(
            SimilarityComponentKind kind,
            EvidenceAvailability availability,
            OptionalDouble overlap,
            double formulaWeight
    ) {
        public SimilarityComponent {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(overlap, "overlap");
            if (!Double.isFinite(formulaWeight) || formulaWeight < 0.0) {
                throw new IllegalArgumentException("formulaWeight must be finite and non-negative");
            }
            if (availability == EvidenceAvailability.AVAILABLE) {
                if (overlap.isEmpty()) {
                    throw new IllegalArgumentException("Available component requires overlap");
                }
                requireNormalized(overlap.getAsDouble(), "overlap");
            } else if (overlap.isPresent()) {
                throw new IllegalArgumentException("Unavailable component must not expose overlap");
            }
        }
    }

    public record EvidenceSetComparison(
            EvidenceAvailability availability,
            List<String> shared,
            List<String> onlyFirst,
            List<String> onlySecond
    ) {
        public EvidenceSetComparison {
            Objects.requireNonNull(availability, "availability");
            shared = immutableSorted(shared, "shared");
            onlyFirst = immutableSorted(onlyFirst, "onlyFirst");
            onlySecond = immutableSorted(onlySecond, "onlySecond");
            if (availability != EvidenceAvailability.AVAILABLE
                    && (!shared.isEmpty() || !onlyFirst.isEmpty() || !onlySecond.isEmpty())) {
                throw new IllegalArgumentException("Unavailable evidence must not imply differences");
            }
        }
    }

    public record ContextComparison(
            LongDifference usedMemoryBytes,
            LongDifference maximumMemoryBytes,
            TextDifference dimension,
            PositionDifference playerPosition
    ) {
        public ContextComparison {
            Objects.requireNonNull(usedMemoryBytes, "usedMemoryBytes");
            Objects.requireNonNull(maximumMemoryBytes, "maximumMemoryBytes");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(playerPosition, "playerPosition");
        }
    }

    public record TextDifference(
            Optional<String> firstValue,
            Optional<String> secondValue,
            Optional<Boolean> equal
    ) {
        public TextDifference {
            firstValue = immutableText(firstValue, "firstValue");
            secondValue = immutableText(secondValue, "secondValue");
            Objects.requireNonNull(equal, "equal");
            requireDeltaAvailability(firstValue.isPresent(), secondValue.isPresent(), equal.isPresent());
        }
    }

    public record Position(int x, int y, int z) {}

    public record PositionDifference(
            Optional<Position> firstValue,
            Optional<Position> secondValue,
            Optional<Boolean> equal
    ) {
        public PositionDifference {
            Objects.requireNonNull(firstValue, "firstValue");
            Objects.requireNonNull(secondValue, "secondValue");
            Objects.requireNonNull(equal, "equal");
            requireDeltaAvailability(firstValue.isPresent(), secondValue.isPresent(), equal.isPresent());
        }
    }

    public enum EvidenceAvailability {
        AVAILABLE,
        NOT_CAPTURED,
        INSUFFICIENT_EVIDENCE
    }

    public enum SimilarityComponentKind {
        CLASS_SIGNATURES,
        FRAME_SIGNATURES,
        STACK_PATH_SIGNATURES,
        LEAF_OWNERS,
        STACK_PRESENCE_OWNERS,
        STACK_DIVERSITY_OWNERS
    }

    private static List<String> immutableSorted(List<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> sorted = new TreeSet<>();
        for (String value : source) {
            sorted.add(requireText(value, name + " value"));
        }
        return Collections.unmodifiableList(new ArrayList<>(sorted));
    }

    private static Optional<String> immutableText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(text -> requireText(text, name + " value"));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireNormalized(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }

    private static void requireDeltaAvailability(boolean first, boolean second, boolean derived) {
        if (derived != (first && second)) {
            throw new IllegalArgumentException("A derived comparison value requires both source values");
        }
    }
}
