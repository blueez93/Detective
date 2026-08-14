package fr.apocalypsebleu.moddetective.core.comparison;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.casefile.CaseSimilarity;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/** Lightweight, deterministic pairwise comparison with no history-wide work or persistence. */
public final class IncidentComparisonEngine {
    private final CaseSimilarity.Weights weights;

    public IncidentComparisonEngine() {
        this(CaseSimilarity.DEFAULT_WEIGHTS);
    }

    public IncidentComparisonEngine(CaseSimilarity.Weights weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
    }

    public IncidentComparison compare(
            String firstIncidentId,
            FreezeIncident first,
            String secondIncidentId,
            FreezeIncident second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return compare(source(firstIncidentId, first), source(secondIncidentId, second));
    }

    IncidentComparison compare(Source first, Source second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        IncidentFingerprint left = first.fingerprint();
        IncidentFingerprint right = second.fingerprint();
        boolean bothEnhanced = left.evidenceSource() == IncidentFingerprint.EvidenceSource.DERIVED_V1
                && right.evidenceSource() == IncidentFingerprint.EvidenceSource.DERIVED_V1;

        Component classes = component(
                IncidentComparison.SimilarityComponentKind.CLASS_SIGNATURES,
                left.classFingerprints(),
                right.classFingerprints(),
                bothEnhanced ? weights.classFingerprints() : weights.structuralTotal(),
                true);
        Component frames = component(
                IncidentComparison.SimilarityComponentKind.FRAME_SIGNATURES,
                left.frameFingerprints(), right.frameFingerprints(),
                weights.frameFingerprints(), bothEnhanced);
        Component paths = component(
                IncidentComparison.SimilarityComponentKind.STACK_PATH_SIGNATURES,
                left.stackPathFingerprints(), right.stackPathFingerprints(),
                weights.stackPathFingerprints(), bothEnhanced);
        Component leaf = component(
                IncidentComparison.SimilarityComponentKind.LEAF_OWNERS,
                left.leafOwners(), right.leafOwners(), weights.leafOwners(), true);
        Component presence = component(
                IncidentComparison.SimilarityComponentKind.STACK_PRESENCE_OWNERS,
                left.stackPresenceOwners(), right.stackPresenceOwners(),
                weights.stackPresenceOwners(), true);
        Component diversity = component(
                IncidentComparison.SimilarityComponentKind.STACK_DIVERSITY_OWNERS,
                left.stackDiversityByOwner(), right.stackDiversityByOwner(),
                weights.stackDiversityByOwner(), true);
        List<Component> components = List.of(classes, frames, paths, leaf, presence, diversity);

        double weightedOverlap = 0.0;
        double comparableWeight = 0.0;
        List<IncidentComparison.SimilarityComponent> exposedComponents = new ArrayList<>();
        for (Component component : components) {
            exposedComponents.add(component.exposed());
            if (component.overlap().isPresent()) {
                weightedOverlap += component.overlap().getAsDouble() * component.weight();
                comparableWeight += component.weight();
            }
        }
        IncidentComparison.TechnicalSimilarity technicalSimilarity = comparableWeight > 0.0
                ? new IncidentComparison.TechnicalSimilarity(
                        IncidentComparison.EvidenceAvailability.AVAILABLE,
                        OptionalDouble.of(clamp(weightedOverlap / comparableWeight)),
                        comparableWeight,
                        exposedComponents)
                : new IncidentComparison.TechnicalSimilarity(
                        IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                        OptionalDouble.empty(), 0.0, exposedComponents);

        Set<String> leftOwners = ownerIds(left);
        Set<String> rightOwners = ownerIds(right);
        return new IncidentComparison(
                descriptor(first),
                descriptor(second),
                longDifference(first.detectedAtEpochMs(), second.detectedAtEpochMs()),
                doubleDifference(first.stallDurationMs(), second.stallDurationMs()),
                intDifference(first.capturedSampleCount(), second.capturedSampleCount()),
                technicalSimilarity,
                evidenceDifference(left.classFingerprints(), right.classFingerprints(), true),
                evidenceDifference(left.frameFingerprints(), right.frameFingerprints(), bothEnhanced),
                evidenceDifference(left.stackPathFingerprints(), right.stackPathFingerprints(), bothEnhanced),
                evidenceDifference(leftOwners, rightOwners, true),
                evidenceDifference(left.leafOwners(), right.leafOwners(), true),
                evidenceDifference(left.stackPresenceOwners(), right.stackPresenceOwners(), true),
                evidenceDifference(left.stackDiversityByOwner(), right.stackDiversityByOwner(), true),
                new IncidentComparison.ContextComparison(
                        longDifference(first.context().usedMemoryBytes(), second.context().usedMemoryBytes()),
                        longDifference(first.context().maximumMemoryBytes(), second.context().maximumMemoryBytes()),
                        textDifference(first.context().dimension(), second.context().dimension()),
                        positionDifference(first.context().playerPosition(), second.context().playerPosition())));
    }

    static Source source(
            IncidentFingerprint fingerprint,
            AttributionEvidence.State attributionState,
            OptionalLong detectedAtEpochMs,
            OptionalDouble stallDurationMs,
            OptionalInt capturedSampleCount,
            Context context
    ) {
        return new Source(
                fingerprint,
                Objects.requireNonNullElse(attributionState, AttributionEvidence.State.UNKNOWN),
                detectedAtEpochMs,
                stallDurationMs,
                capturedSampleCount,
                context);
    }

    private static Source source(String incidentId, FreezeIncident incident) {
        FrameSample frame = incident.frame();
        return source(
                IncidentFingerprint.from(incidentId, incident),
                incident.attributionEvidence().state(),
                OptionalLong.of(incident.detectedAtEpochMs()),
                finiteNonNegative(incident.durationMs()),
                OptionalInt.of(Math.max(0, incident.watchdogSamples())),
                new Context(
                        positive(frame.usedMemoryBytes()),
                        positive(frame.maxMemoryBytes()),
                        text(frame.dimension()),
                        Optional.of(new IncidentComparison.Position(
                                frame.playerX(), frame.playerY(), frame.playerZ()))));
    }

    private static IncidentComparison.IncidentDescriptor descriptor(Source source) {
        IncidentFingerprint fingerprint = source.fingerprint();
        return new IncidentComparison.IncidentDescriptor(
                fingerprint.incidentId(), source.attributionState(),
                fingerprint.stallType(), fingerprint.evidenceSource());
    }

    private Component component(
            IncidentComparison.SimilarityComponentKind kind,
            Map<String, Double> first,
            Map<String, Double> second,
            double weight,
            boolean capturedOnBoth
    ) {
        IncidentComparison.EvidenceAvailability availability = availability(
                first.keySet(), second.keySet(), capturedOnBoth);
        OptionalDouble overlap = availability == IncidentComparison.EvidenceAvailability.AVAILABLE
                ? OptionalDouble.of(CaseSimilarity.weightedJaccard(first, second))
                : OptionalDouble.empty();
        return new Component(kind, availability, overlap, weight);
    }

    private static IncidentComparison.EvidenceSetComparison evidenceDifference(
            Map<String, Double> first,
            Map<String, Double> second,
            boolean capturedOnBoth
    ) {
        return evidenceDifference(first.keySet(), second.keySet(), capturedOnBoth);
    }

    private static IncidentComparison.EvidenceSetComparison evidenceDifference(
            Set<String> first,
            Set<String> second,
            boolean capturedOnBoth
    ) {
        IncidentComparison.EvidenceAvailability availability = availability(
                first, second, capturedOnBoth);
        if (availability != IncidentComparison.EvidenceAvailability.AVAILABLE) {
            return new IncidentComparison.EvidenceSetComparison(
                    availability, List.of(), List.of(), List.of());
        }
        TreeSet<String> shared = new TreeSet<>(first);
        shared.retainAll(second);
        TreeSet<String> onlyFirst = new TreeSet<>(first);
        onlyFirst.removeAll(second);
        TreeSet<String> onlySecond = new TreeSet<>(second);
        onlySecond.removeAll(first);
        return new IncidentComparison.EvidenceSetComparison(
                availability,
                List.copyOf(shared),
                List.copyOf(onlyFirst),
                List.copyOf(onlySecond));
    }

    private static IncidentComparison.EvidenceAvailability availability(
            Set<String> first,
            Set<String> second,
            boolean capturedOnBoth
    ) {
        if (!capturedOnBoth) {
            return IncidentComparison.EvidenceAvailability.NOT_CAPTURED;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE;
        }
        return IncidentComparison.EvidenceAvailability.AVAILABLE;
    }

    private static Set<String> ownerIds(IncidentFingerprint fingerprint) {
        TreeSet<String> result = new TreeSet<>();
        result.addAll(fingerprint.leafOwners().keySet());
        result.addAll(fingerprint.stackPresenceOwners().keySet());
        result.addAll(fingerprint.stackDiversityByOwner().keySet());
        return result;
    }

    private static IncidentComparison.LongDifference longDifference(
            OptionalLong first,
            OptionalLong second
    ) {
        OptionalLong delta = first.isPresent() && second.isPresent()
                ? OptionalLong.of(second.getAsLong() - first.getAsLong())
                : OptionalLong.empty();
        return new IncidentComparison.LongDifference(first, second, delta);
    }

    private static IncidentComparison.DoubleDifference doubleDifference(
            OptionalDouble first,
            OptionalDouble second
    ) {
        OptionalDouble delta = first.isPresent() && second.isPresent()
                ? OptionalDouble.of(second.getAsDouble() - first.getAsDouble())
                : OptionalDouble.empty();
        return new IncidentComparison.DoubleDifference(first, second, delta);
    }

    private static IncidentComparison.IntDifference intDifference(
            OptionalInt first,
            OptionalInt second
    ) {
        OptionalInt delta = first.isPresent() && second.isPresent()
                ? OptionalInt.of(second.getAsInt() - first.getAsInt())
                : OptionalInt.empty();
        return new IncidentComparison.IntDifference(first, second, delta);
    }

    private static IncidentComparison.TextDifference textDifference(
            Optional<String> first,
            Optional<String> second
    ) {
        Optional<Boolean> equal = first.isPresent() && second.isPresent()
                ? Optional.of(first.get().equals(second.get()))
                : Optional.empty();
        return new IncidentComparison.TextDifference(first, second, equal);
    }

    private static IncidentComparison.PositionDifference positionDifference(
            Optional<IncidentComparison.Position> first,
            Optional<IncidentComparison.Position> second
    ) {
        Optional<Boolean> equal = first.isPresent() && second.isPresent()
                ? Optional.of(first.get().equals(second.get()))
                : Optional.empty();
        return new IncidentComparison.PositionDifference(first, second, equal);
    }

    private static OptionalLong positive(long value) {
        return value > 0L ? OptionalLong.of(value) : OptionalLong.empty();
    }

    private static OptionalDouble finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0
                ? OptionalDouble.of(value)
                : OptionalDouble.empty();
    }

    private static Optional<String> text(String value) {
        return value == null || value.isBlank()
                ? Optional.empty()
                : Optional.of(value.strip());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    static record Context(
            OptionalLong usedMemoryBytes,
            OptionalLong maximumMemoryBytes,
            Optional<String> dimension,
            Optional<IncidentComparison.Position> playerPosition
    ) {
        Context {
            Objects.requireNonNull(usedMemoryBytes, "usedMemoryBytes");
            Objects.requireNonNull(maximumMemoryBytes, "maximumMemoryBytes");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(playerPosition, "playerPosition");
        }
    }

    static record Source(
            IncidentFingerprint fingerprint,
            AttributionEvidence.State attributionState,
            OptionalLong detectedAtEpochMs,
            OptionalDouble stallDurationMs,
            OptionalInt capturedSampleCount,
            Context context
    ) {
        Source {
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(attributionState, "attributionState");
            Objects.requireNonNull(detectedAtEpochMs, "detectedAtEpochMs");
            Objects.requireNonNull(stallDurationMs, "stallDurationMs");
            Objects.requireNonNull(capturedSampleCount, "capturedSampleCount");
            Objects.requireNonNull(context, "context");
        }
    }

    private record Component(
            IncidentComparison.SimilarityComponentKind kind,
            IncidentComparison.EvidenceAvailability availability,
            OptionalDouble overlap,
            double weight
    ) {
        private IncidentComparison.SimilarityComponent exposed() {
            return new IncidentComparison.SimilarityComponent(kind, availability, overlap, weight);
        }
    }
}
