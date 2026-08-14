package fr.apocalypsebleu.moddetective.core.comparison;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.DerivedIncidentEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison.EvidenceAvailability;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentComparisonEngineTest {
    private static final double TOLERANCE = 0.000_000_1;
    private final IncidentComparisonEngine engine = new IncidentComparisonEngine();

    @Test
    void nearlyIdenticalEnhancedIncidentsHaveHighTechnicalSimilarityAndSharedEvidence() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a'), hash('b')), List.of(hash('c')), List.of(hash('d')));
        FreezeIncident second = enhanced(
                2_000L, 420.0, AttributionEvidence.State.AMBIGUOUS_ATTRIBUTION, "alpha",
                List.of(hash('a'), hash('b'), hash('e')), List.of(hash('c')), List.of(hash('d')));

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(EvidenceAvailability.AVAILABLE, result.technicalSimilarity().availability());
        assertTrue(result.technicalSimilarity().score().orElseThrow() > 0.90);
        assertEquals(List.of(hash('a'), hash('b')), result.classSignatures().shared());
        assertEquals(List.of(hash('e')), result.classSignatures().onlySecond());
        assertEquals(List.of("alpha"), result.owners().shared());
        assertEquals(AttributionEvidence.State.ATTRIBUTED,
                result.firstIncident().attributionState());
        assertEquals(AttributionEvidence.State.AMBIGUOUS_ATTRIBUTION,
                result.secondIncident().attributionState());
    }

    @Test
    void technicallyUnrelatedIncidentsHaveLowSimilarity() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')));
        FreezeIncident second = enhanced(
                2_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "beta",
                List.of(hash('d')), List.of(hash('e')), List.of(hash('f')));

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(0.0, result.technicalSimilarity().score().orElseThrow(), TOLERANCE);
        assertTrue(result.classSignatures().shared().isEmpty());
        assertEquals(List.of(hash('a')), result.classSignatures().onlyFirst());
        assertEquals(List.of(hash('d')), result.classSignatures().onlySecond());
    }

    @Test
    void sameDisplayedPrimarySuspectDoesNotOverrideDifferentTechnicalEvidence() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "same-owner",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')));
        FreezeIncident second = enhanced(
                2_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "same-owner",
                List.of(hash('d')), List.of(hash('e')), List.of(hash('f')));

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals("same-owner", first.suspects().getFirst().modId());
        assertEquals("same-owner", second.suspects().getFirst().modId());
        assertTrue(result.technicalSimilarity().score().orElseThrow() < 0.30);
    }

    @Test
    void differentDisplayedPrimarySuspectsCanRemainHighlySimilarWhenTechnicalEvidenceMatches() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')));
        FreezeIncident second = enhanced(
                2_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "beta",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')));

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertTrue(result.technicalSimilarity().score().orElseThrow() > 0.70);
        assertEquals(List.of(), result.owners().shared());
        assertEquals(List.of("alpha"), result.owners().onlyFirst());
        assertEquals(List.of("beta"), result.owners().onlySecond());
    }

    @Test
    void legacyAndEnhancedUseOnlyTheirCommonClassAndOwnerEvidence() {
        String className = "example.shared.Work";
        FreezeIncident legacy = legacy(1_000L, 400.0, "alpha", className);
        FreezeIncident enhanced = enhanced(
                2_000L, 420.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(DerivedIncidentEvidence.classSignature(className)),
                List.of(hash('b')), List.of(hash('c')));

        IncidentComparison result = engine.compare("legacy", legacy, "enhanced", enhanced);

        assertEquals(IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK,
                result.firstIncident().evidenceSource());
        assertEquals(IncidentFingerprint.EvidenceSource.DERIVED_V1,
                result.secondIncident().evidenceSource());
        assertEquals(1.0, result.technicalSimilarity().score().orElseThrow(), TOLERANCE);
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.frameSignatures().availability());
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.stackPathSignatures().availability());
        assertTrue(result.frameSignatures().shared().isEmpty());
    }

    @Test
    void legacyIncidentsCompareWithFallbackEvidenceWithoutInventingFramesOrPaths() {
        FreezeIncident first = legacy(1_000L, 400.0, "alpha", "example.shared.Work");
        FreezeIncident second = legacy(2_000L, 420.0, "alpha", "example.shared.Work");

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(1.0, result.technicalSimilarity().score().orElseThrow(), TOLERANCE);
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.frameSignatures().availability());
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.stackPathSignatures().availability());
    }

    @Test
    void missingEvidenceIsExplicitlyInsufficientAndProducesNoMisleadingDifferences() {
        FreezeIncident first = sparse(1_000L, 400.0);
        FreezeIncident second = sparse(2_000L, 800.0);

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                result.technicalSimilarity().availability());
        assertTrue(result.technicalSimilarity().score().isEmpty());
        assertEquals(EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                result.classSignatures().availability());
        assertTrue(result.classSignatures().shared().isEmpty());
        assertTrue(result.classSignatures().onlyFirst().isEmpty());
        assertTrue(result.classSignatures().onlySecond().isEmpty());
    }

    @Test
    void repeatedComparisonIsDeterministic() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a'), hash('b')), List.of(hash('c')), List.of(hash('d')));
        FreezeIncident second = enhanced(
                2_000L, 500.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a'), hash('e')), List.of(hash('c')), List.of(hash('d')));

        IncidentComparison expected = engine.compare("first", first, "second", second);

        assertEquals(expected, engine.compare("first", first, "second", second));
        assertEquals(expected, new IncidentComparisonEngine().compare(
                "first", first, "second", second));
    }

    @Test
    void technicalSimilarityIsSymmetric() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a'), hash('b')), List.of(hash('c')), List.of(hash('d')));
        FreezeIncident second = enhanced(
                2_000L, 500.0, AttributionEvidence.State.ATTRIBUTED, "beta",
                List.of(hash('a'), hash('e')), List.of(hash('c')), List.of(hash('f')));

        IncidentComparison forward = engine.compare("first", first, "second", second);
        IncidentComparison reverse = engine.compare("second", second, "first", first);

        assertEquals(forward.technicalSimilarity().score().orElseThrow(),
                reverse.technicalSimilarity().score().orElseThrow(), TOLERANCE);
        assertEquals(forward.technicalSimilarity().components(),
                reverse.technicalSimilarity().components());
    }

    @Test
    void reversingOrderSwapsUniqueEvidence() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a'), hash('b')), List.of(hash('c')), List.of(hash('d')));
        FreezeIncident second = enhanced(
                2_000L, 500.0, AttributionEvidence.State.ATTRIBUTED, "beta",
                List.of(hash('a'), hash('e')), List.of(hash('c')), List.of(hash('f')));

        IncidentComparison forward = engine.compare("first", first, "second", second);
        IncidentComparison reverse = engine.compare("second", second, "first", first);

        assertEquals(forward.classSignatures().onlyFirst(), reverse.classSignatures().onlySecond());
        assertEquals(forward.classSignatures().onlySecond(), reverse.classSignatures().onlyFirst());
        assertEquals(forward.owners().onlyFirst(), reverse.owners().onlySecond());
        assertEquals(forward.owners().onlySecond(), reverse.owners().onlyFirst());
    }

    @Test
    void durationAndContextDifferencesDoNotChangeTechnicalSimilarity() {
        FreezeIncident first = enhanced(
                1_000L, 100.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')));
        FreezeIncident second = enhanced(
                5_000L, 10_000.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('a')), List.of(hash('b')), List.of(hash('c')),
                "minecraft:the_nether", 900L, 2_000L, 10, 70, -20);

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(1.0, result.technicalSimilarity().score().orElseThrow(), TOLERANCE);
        assertEquals(9_900.0, result.stallDurationMs().delta().orElseThrow(), TOLERANCE);
        assertEquals(4_000L, result.detectedAtEpochMs().delta().orElseThrow());
        assertEquals(644L, result.context().usedMemoryBytes().delta().orElseThrow());
        assertFalse(result.context().dimension().equal().orElseThrow());
        assertFalse(result.context().playerPosition().equal().orElseThrow());
    }

    @Test
    void comparisonDoesNotMutateSourceIncidents() {
        FreezeIncident first = enhanced(
                1_000L, 400.0, AttributionEvidence.State.ATTRIBUTED, "alpha",
                List.of(hash('b'), hash('a')), List.of(hash('c')), List.of(hash('d')));
        FreezeIncident second = enhanced(
                2_000L, 500.0, AttributionEvidence.State.ATTRIBUTED, "beta",
                List.of(hash('e'), hash('a')), List.of(hash('c')), List.of(hash('f')));
        FreezeIncident firstSnapshot = copy(first);
        FreezeIncident secondSnapshot = copy(second);

        IncidentComparison result = engine.compare("first", first, "second", second);

        assertEquals(firstSnapshot, first);
        assertEquals(secondSnapshot, second);
        assertNotSame(firstSnapshot, first);
        assertNotSame(secondSnapshot, second);
        assertTrue(result.technicalSimilarity().score().isPresent());
    }

    private static FreezeIncident enhanced(
            long detectedAt,
            double duration,
            AttributionEvidence.State state,
            String owner,
            List<String> classes,
            List<String> frames,
            List<String> paths
    ) {
        return enhanced(detectedAt, duration, state, owner, classes, frames, paths,
                "minecraft:overworld", 256L, 1_024L, 0, 64, 0);
    }

    private static FreezeIncident enhanced(
            long detectedAt,
            double duration,
            AttributionEvidence.State state,
            String owner,
            List<String> classes,
            List<String> frames,
            List<String> paths,
            String dimension,
            long usedMemory,
            long maximumMemory,
            int x,
            int y,
            int z
    ) {
        int samples = 6;
        DerivedIncidentEvidence evidence = new DerivedIncidentEvidence(
                DerivedIncidentEvidence.SCHEMA_VERSION,
                DerivedIncidentEvidence.SIGNATURE_FORMAT,
                samples,
                observations(classes, samples),
                observations(frames, samples),
                observations(paths, samples),
                List.of(new DerivedIncidentEvidence.OwnerObservation(owner, samples, samples, 1)));
        SuspectAnalyzer.Suspect suspect = suspect(owner, samples);
        FrameSample frame = new FrameSample(
                detectedAt, detectedAt, duration, duration == 0.0 ? 0.0 : 1_000.0 / duration,
                usedMemory, maximumMemory, dimension, x, y, z);
        return new FreezeIncident(
                detectedAt, duration, 120.0, frame, samples,
                new AttributionEvidence(state, samples, samples, 0, 0),
                List.of(suspect), List.of(), List.of(frame), evidence);
    }

    private static FreezeIncident legacy(long detectedAt, double duration, String owner, String className) {
        int samples = 6;
        FrameSample frame = new FrameSample(
                detectedAt, detectedAt, duration, 1_000.0 / duration,
                256L, 1_024L, "minecraft:overworld", 0, 64, 0);
        return new FreezeIncident(
                detectedAt, duration, 120.0, frame, samples,
                new AttributionEvidence(AttributionEvidence.State.ATTRIBUTED,
                        samples, samples, 0, 0),
                List.of(suspect(owner, samples)),
                List.of(new SuspectAnalyzer.HotClass(className, samples)),
                List.of(frame));
    }

    private static FreezeIncident sparse(long detectedAt, double duration) {
        FrameSample frame = new FrameSample(
                detectedAt, detectedAt, duration, 1_000.0 / duration,
                0L, 0L, "", 0, 0, 0);
        return new FreezeIncident(
                detectedAt, duration, 120.0, frame, 0,
                new AttributionEvidence(AttributionEvidence.State.INSUFFICIENT_EVIDENCE,
                        0, 0, 0, 0),
                List.of(), List.of(), List.of());
    }

    private static SuspectAnalyzer.Suspect suspect(String owner, int samples) {
        return new SuspectAnalyzer.Suspect(
                owner, owner, "1", samples, 100.0,
                samples, 100.0, 0.0, 0, samples - 1, 0, 1);
    }

    private static List<DerivedIncidentEvidence.SignatureObservation> observations(
            List<String> signatures,
            int samples
    ) {
        return signatures.stream()
                .map(signature -> new DerivedIncidentEvidence.SignatureObservation(signature, samples))
                .toList();
    }

    private static String hash(char digit) {
        return String.valueOf(digit).repeat(32);
    }

    private static FreezeIncident copy(FreezeIncident source) {
        return new FreezeIncident(
                source.detectedAtEpochMs(), source.durationMs(), source.thresholdMs(), source.frame(),
                source.watchdogSamples(), source.attributionEvidence(), source.suspects(),
                source.hotClasses(), source.blackBox(), source.derivedEvidence());
    }
}
