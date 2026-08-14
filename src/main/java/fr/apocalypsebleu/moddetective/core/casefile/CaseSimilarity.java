package fr.apocalypsebleu.moddetective.core.casefile;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic normalized comparison of two captured incident fingerprints. */
public final class CaseSimilarity {
    public static final Weights DEFAULT_WEIGHTS = new Weights(
            0.25,
            0.15,
            0.25,
            0.15,
            0.07,
            0.03,
            0.04,
            0.03,
            0.03);

    private final Weights weights;

    public CaseSimilarity() {
        this(DEFAULT_WEIGHTS);
    }

    public CaseSimilarity(Weights weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
    }

    public double score(IncidentFingerprint first, IncidentFingerprint second) {
        return compare(first, second).score();
    }

    public Breakdown compare(IncidentFingerprint first, IncidentFingerprint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double classes = weightedJaccard(first.classFingerprints(), second.classFingerprints());
        double frames = weightedJaccard(first.frameFingerprints(), second.frameFingerprints());
        double paths = weightedJaccard(first.stackPathFingerprints(), second.stackPathFingerprints());
        double leaf = weightedJaccard(first.leafOwners(), second.leafOwners());
        double presence = weightedJaccard(first.stackPresenceOwners(), second.stackPresenceOwners());
        double diversity = weightedJaccard(
                first.stackDiversityByOwner(), second.stackDiversityByOwner());
        double samples = ratio(first.capturedSampleCount(), second.capturedSampleCount());
        double stallType = first.stallType() == second.stallType() ? 1.0 : 0.0;
        double duration = ratio(first.stallDurationMs(), second.stallDurationMs());

        double structuralContribution;
        if (first.evidenceSource() == IncidentFingerprint.EvidenceSource.DERIVED_V1
                && second.evidenceSource() == IncidentFingerprint.EvidenceSource.DERIVED_V1) {
            structuralContribution = classes * weights.classFingerprints()
                    + frames * weights.frameFingerprints()
                    + paths * weights.stackPathFingerprints();
        } else {
            structuralContribution = classes * weights.structuralTotal();
        }
        double technicalContribution = structuralContribution
                + leaf * weights.leafOwners()
                + presence * weights.stackPresenceOwners()
                + diversity * weights.stackDiversityByOwner();
        double score = technicalContribution
                + samples * weights.capturedSampleCount()
                + stallType * weights.stallType()
                + duration * weights.stallDuration();
        double technicalEvidence = technicalContribution / weights.technicalTotal();
        return new Breakdown(
                clamp(score),
                clamp(technicalEvidence),
                classes,
                frames,
                paths,
                leaf,
                presence,
                diversity,
                samples,
                stallType,
                duration);
    }

    /**
     * The evidence-overlap primitive shared with pairwise incident comparison.
     * Existing Case scoring and clustering continue to call this exact implementation.
     */
    public static double weightedJaccard(Map<String, Double> first, Map<String, Double> second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Set<String> keys = new TreeSet<>(first.keySet());
        keys.addAll(second.keySet());
        double intersection = 0.0;
        double union = 0.0;
        for (String key : keys) {
            double left = first.getOrDefault(key, 0.0);
            double right = second.getOrDefault(key, 0.0);
            intersection += Math.min(left, right);
            union += Math.max(left, right);
        }
        return union == 0.0 ? 0.0 : clamp(intersection / union);
    }

    private static double ratio(int first, int second) {
        if (first <= 0 || second <= 0) {
            return 0.0;
        }
        return Math.min(first, second) / (double) Math.max(first, second);
    }

    private static double ratio(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || first <= 0.0 || second <= 0.0) {
            return 0.0;
        }
        return clamp(Math.min(first, second) / Math.max(first, second));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Weights(
            double classFingerprints,
            double frameFingerprints,
            double stackPathFingerprints,
            double leafOwners,
            double stackPresenceOwners,
            double stackDiversityByOwner,
            double capturedSampleCount,
            double stallType,
            double stallDuration
    ) {
        public Weights {
            double total = classFingerprints + frameFingerprints + stackPathFingerprints
                    + leafOwners + stackPresenceOwners
                    + stackDiversityByOwner + capturedSampleCount + stallType + stallDuration;
            double technicalTotal = classFingerprints + frameFingerprints + stackPathFingerprints
                    + leafOwners
                    + stackPresenceOwners + stackDiversityByOwner;
            if (!allFiniteAndNonNegative(classFingerprints, frameFingerprints,
                    stackPathFingerprints, leafOwners, stackPresenceOwners,
                    stackDiversityByOwner, capturedSampleCount, stallType, stallDuration)
                    || Math.abs(total - 1.0) > 0.000_000_1) {
                throw new IllegalArgumentException("Similarity weights must be non-negative and total 1.0");
            }
            if (technicalTotal <= 0.0) {
                throw new IllegalArgumentException("At least one technical evidence weight is required");
            }
        }

        public double technicalTotal() {
            return structuralTotal() + leafOwners + stackPresenceOwners + stackDiversityByOwner;
        }

        public double structuralTotal() {
            return classFingerprints + frameFingerprints + stackPathFingerprints;
        }

        private static boolean allFiniteAndNonNegative(double... values) {
            for (double value : values) {
                if (!Double.isFinite(value) || value < 0.0) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Breakdown(
            double score,
            double technicalEvidence,
            double classFingerprintOverlap,
            double frameFingerprintOverlap,
            double stackPathFingerprintOverlap,
            double leafOwnerOverlap,
            double stackPresenceOwnerOverlap,
            double stackDiversityOverlap,
            double capturedSampleCountSimilarity,
            double stallTypeSimilarity,
            double stallDurationSimilarity
    ) {}
}
