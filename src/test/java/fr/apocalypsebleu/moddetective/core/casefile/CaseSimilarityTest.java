package fr.apocalypsebleu.moddetective.core.casefile;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseSimilarityTest {
    private static final double TOLERANCE = 0.000_000_1;

    @Test
    void appliesTheDocumentedNormalizedFormulaDeterministically() {
        IncidentFingerprint first = fingerprint(
                "first", 100.0, 10,
                Map.of("frame-a", 1.0, "frame-b", 0.5),
                Map.of("alpha", 1.0),
                Map.of("alpha", 1.0),
                Map.of("alpha", 0.2));
        IncidentFingerprint second = fingerprint(
                "second", 200.0, 20,
                Map.of("frame-a", 0.5, "frame-c", 1.0),
                Map.of("alpha", 0.5),
                Map.of("alpha", 1.0),
                Map.of("alpha", 0.1));

        CaseSimilarity.Breakdown result = new CaseSimilarity().compare(first, second);

        // Weighted Jaccard components: frames=.2, leaf=.5, presence=1, diversity=.5.
        // Ratios: samples=.5, stall type=1, duration=.5.
        assertEquals(0.355, result.score(), TOLERANCE);
        assertEquals(0.29 / 0.90, result.technicalEvidence(), TOLERANCE);
        assertEquals(0.2, result.classFingerprintOverlap(), TOLERANCE);
        assertEquals(0.0, result.frameFingerprintOverlap(), TOLERANCE);
        assertEquals(0.0, result.stackPathFingerprintOverlap(), TOLERANCE);
        assertEquals(0.5, result.leafOwnerOverlap(), TOLERANCE);
        assertEquals(1.0, result.stackPresenceOwnerOverlap(), TOLERANCE);
        assertEquals(0.5, result.stackDiversityOverlap(), TOLERANCE);
        assertEquals(0.5, result.capturedSampleCountSimilarity(), TOLERANCE);
        assertEquals(1.0, result.stallTypeSimilarity(), TOLERANCE);
        assertEquals(0.5, result.stallDurationSimilarity(), TOLERANCE);
        assertEquals(result.score(), new CaseSimilarity().score(first, second), TOLERANCE);
    }

    @Test
    void rejectsWeightsThatAreNotNormalized() {
        assertThrows(IllegalArgumentException.class, () -> new CaseSimilarity.Weights(
                0.5, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1));
    }

    private static IncidentFingerprint fingerprint(
            String id,
            double duration,
            int samples,
            Map<String, Double> frames,
            Map<String, Double> leaf,
            Map<String, Double> presence,
            Map<String, Double> diversity
    ) {
        return new IncidentFingerprint(
                id, 1L, duration, IncidentFingerprint.StallType.RENDER_THREAD_STALL,
                samples, frames, leaf, presence, diversity);
    }
}
