package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentComparisonViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentComparisonUiAdapterTest {
    @Test
    void mapsFirstAndSecondWithoutSwappingAB() {
        IncidentSummaryViewModel first = summary("a", "Example Alpha");
        IncidentSummaryViewModel second = summary("b", "Example Beta");

        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                comparison(available(0.82), availableEvidence(
                        List.of(hash('z')), List.of(hash('a')), List.of(hash('b')))),
                first, second);

        assertEquals(first, result.first().incident());
        assertEquals(second, result.second().incident());
        assertEquals(1_000L, result.first().detectedAtEpochMs().orElseThrow());
        assertEquals(2_000L, result.second().detectedAtEpochMs().orElseThrow());
        assertEquals(400.0, result.first().stallDurationMs().orElseThrow());
        assertEquals(700.0, result.second().stallDurationMs().orElseThrow());
    }

    @Test
    void sharedAndUniqueEvidenceUseOwnerLabelsAndDeterministicSignatureNumbers() {
        IncidentComparison comparison = comparison(
                available(0.82),
                availableEvidence(List.of(hash('z')), List.of(hash('a')), List.of(hash('b'))),
                availableEvidence(List.of(hash('m')), List.of(), List.of()),
                availableEvidence(List.of(), List.of(), List.of(hash('c'))),
                availableEvidence(List.of("example_alpha"), List.of("example_beta"),
                        List.of("example_gamma")));

        IncidentComparisonViewModel first = IncidentComparisonUiAdapter.from(
                comparison, summary("a", "Alpha"), summary("b", "Beta"), Map.of(
                        "example_alpha", "Example Alpha",
                        "example_beta", "Example Beta",
                        "example_gamma", "Example Gamma"));
        IncidentComparisonViewModel second = IncidentComparisonUiAdapter.from(
                comparison, summary("a", "Alpha"), summary("b", "Beta"), Map.of(
                        "example_alpha", "Example Alpha",
                        "example_beta", "Example Beta",
                        "example_gamma", "Example Gamma"));

        assertEquals(first, second);
        assertEquals(List.of(
                        IncidentComparisonViewModel.EvidenceItem.owner("Example Alpha (example_alpha)"),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(3),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(4)),
                first.evidence().shared());
        assertEquals(List.of(
                        IncidentComparisonViewModel.EvidenceItem.owner("Example Beta (example_beta)"),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(1)),
                first.evidence().onlyFirst());
        assertEquals(List.of(
                        IncidentComparisonViewModel.EvidenceItem.owner("Example Gamma (example_gamma)"),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(2),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(5)),
                first.evidence().onlySecond());
    }

    @Test
    void rawDerivedHashesNeverReachDisplayViewModel() {
        String rawHash = hash('x');
        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                comparison(available(1.0), availableEvidence(List.of(rawHash), List.of(), List.of())),
                summary("a", "Alpha"), summary("b", "Beta"));

        String renderedModel = result.toString();

        assertFalse(renderedModel.contains(rawHash));
        assertEquals(IncidentComparisonViewModel.EvidenceItem.technicalSignature(1),
                result.evidence().shared().getFirst());
    }

    @Test
    void unavailableSimilarityRemainsUnavailableInsteadOfDisplayingZero() {
        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                comparison(unavailable(), unavailableEvidence(
                        IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE)),
                summary("a", "Alpha"), summary("b", "Beta"));

        assertFalse(result.similarity().available());
        assertTrue(result.similarity().score().isEmpty());
        assertFalse(result.evidence().comparisonAvailable());
        assertEquals(4, result.evidence().unavailableCategories().size());
        assertTrue(result.evidence().shared().isEmpty());
        assertTrue(result.evidence().onlyFirst().isEmpty());
        assertTrue(result.evidence().onlySecond().isEmpty());
    }

    @Test
    void legacyMissingFrameAndPathEvidenceDoesNotManufactureLabels() {
        IncidentComparison legacy = comparison(
                available(1.0),
                availableEvidence(List.of(hash('a')), List.of(), List.of()),
                unavailableEvidence(IncidentComparison.EvidenceAvailability.NOT_CAPTURED),
                unavailableEvidence(IncidentComparison.EvidenceAvailability.NOT_CAPTURED),
                availableEvidence(List.of("example_mod"), List.of(), List.of()));

        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                legacy, summary("legacy", "Example Mod"), summary("new", "Example Mod"),
                Map.of("example_mod", "Example Mod"));

        assertEquals(List.of(
                        IncidentComparisonViewModel.EvidenceItem.owner("Example Mod (example_mod)"),
                        IncidentComparisonViewModel.EvidenceItem.technicalSignature(1)),
                result.evidence().shared());
        assertTrue(result.evidence().onlyFirst().isEmpty());
        assertTrue(result.evidence().onlySecond().isEmpty());
        assertEquals(List.of(
                        new IncidentComparisonViewModel.UnavailableCategory(
                                IncidentComparisonViewModel.EvidenceCategory.FRAME_SIGNATURES,
                                IncidentComparison.EvidenceAvailability.NOT_CAPTURED),
                        new IncidentComparisonViewModel.UnavailableCategory(
                                IncidentComparisonViewModel.EvidenceCategory.STACK_PATH_SIGNATURES,
                                IncidentComparison.EvidenceAvailability.NOT_CAPTURED)),
                result.evidence().unavailableCategories());
    }

    @Test
    void veryLongOwnerIdentifierIsHandledWithoutFailureOrRawSignatureExposure() {
        String owner = "fictional_" + "long_name_".repeat(30) + "mod";
        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                comparison(available(0.5), availableEvidence(
                        List.of(), List.of(), List.of()),
                        availableEvidence(List.of(), List.of(), List.of()),
                        availableEvidence(List.of(), List.of(), List.of()),
                        availableEvidence(List.of(owner), List.of(), List.of())),
                summary("a", "Alpha"), summary("b", "Beta"));

        String label = result.evidence().shared().getFirst().ownerLabel();
        assertEquals(owner, label);
    }

    @Test
    void missingLegacyContextAndMetricsRemainEmpty() {
        IncidentComparison original = comparison(
                available(1.0),
                availableEvidence(List.of(hash('a')), List.of(), List.of()));
        IncidentComparison sparse = new IncidentComparison(
                original.firstIncident(), original.secondIncident(),
                new IncidentComparison.LongDifference(
                        OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty()),
                new IncidentComparison.DoubleDifference(
                        OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty()),
                new IncidentComparison.IntDifference(
                        OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()),
                original.technicalSimilarity(), original.classSignatures(),
                original.frameSignatures(), original.stackPathSignatures(), original.owners(),
                original.leafOwners(), original.stackPresenceOwners(), original.stackDiversityOwners(),
                new IncidentComparison.ContextComparison(
                        new IncidentComparison.LongDifference(
                                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty()),
                        new IncidentComparison.LongDifference(
                                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty()),
                        new IncidentComparison.TextDifference(
                                Optional.empty(), Optional.empty(), Optional.empty()),
                        new IncidentComparison.PositionDifference(
                                Optional.empty(), Optional.empty(), Optional.empty())));

        IncidentComparisonViewModel result = IncidentComparisonUiAdapter.from(
                sparse, summary("legacy-a", ""), summary("legacy-b", ""));

        assertTrue(result.first().detectedAtEpochMs().isEmpty());
        assertTrue(result.first().stallDurationMs().isEmpty());
        assertTrue(result.first().capturedSampleCount().isEmpty());
        assertTrue(result.first().dimensionId().isEmpty());
        assertTrue(result.first().usedMemoryBytes().isEmpty());
        assertTrue(result.first().maximumMemoryBytes().isEmpty());
    }

    private static IncidentComparison comparison(
            IncidentComparison.TechnicalSimilarity similarity,
            IncidentComparison.EvidenceSetComparison classes
    ) {
        return comparison(similarity, classes,
                unavailableEvidence(IncidentComparison.EvidenceAvailability.NOT_CAPTURED),
                unavailableEvidence(IncidentComparison.EvidenceAvailability.NOT_CAPTURED),
                unavailableEvidence(IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE));
    }

    private static IncidentComparison comparison(
            IncidentComparison.TechnicalSimilarity similarity,
            IncidentComparison.EvidenceSetComparison classes,
            IncidentComparison.EvidenceSetComparison frames,
            IncidentComparison.EvidenceSetComparison paths,
            IncidentComparison.EvidenceSetComparison owners
    ) {
        IncidentComparison.EvidenceSetComparison unavailableOwners =
                unavailableEvidence(IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE);
        return new IncidentComparison(
                descriptor("a", AttributionEvidence.State.ATTRIBUTED),
                descriptor("b", AttributionEvidence.State.AMBIGUOUS_ATTRIBUTION),
                new IncidentComparison.LongDifference(
                        OptionalLong.of(1_000L), OptionalLong.of(2_000L), OptionalLong.of(1_000L)),
                new IncidentComparison.DoubleDifference(
                        OptionalDouble.of(400.0), OptionalDouble.of(700.0), OptionalDouble.of(300.0)),
                new IncidentComparison.IntDifference(
                        OptionalInt.of(10), OptionalInt.of(12), OptionalInt.of(2)),
                similarity,
                classes,
                frames,
                paths,
                owners,
                unavailableOwners,
                unavailableOwners,
                unavailableOwners,
                new IncidentComparison.ContextComparison(
                        new IncidentComparison.LongDifference(
                                OptionalLong.of(256L), OptionalLong.of(512L), OptionalLong.of(256L)),
                        new IncidentComparison.LongDifference(
                                OptionalLong.of(1_024L), OptionalLong.of(1_024L), OptionalLong.of(0L)),
                        new IncidentComparison.TextDifference(
                                Optional.of("minecraft:overworld"),
                                Optional.of("minecraft:the_nether"), Optional.of(false)),
                        new IncidentComparison.PositionDifference(
                                Optional.empty(), Optional.empty(), Optional.empty())));
    }

    private static IncidentComparison.IncidentDescriptor descriptor(
            String id,
            AttributionEvidence.State state
    ) {
        return new IncidentComparison.IncidentDescriptor(
                id, state, IncidentFingerprint.StallType.RENDER_THREAD_STALL,
                IncidentFingerprint.EvidenceSource.DERIVED_V1);
    }

    private static IncidentComparison.TechnicalSimilarity available(double score) {
        return new IncidentComparison.TechnicalSimilarity(
                IncidentComparison.EvidenceAvailability.AVAILABLE,
                OptionalDouble.of(score), 1.0, List.of());
    }

    private static IncidentComparison.TechnicalSimilarity unavailable() {
        return new IncidentComparison.TechnicalSimilarity(
                IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                OptionalDouble.empty(), 0.0, List.of());
    }

    private static IncidentComparison.EvidenceSetComparison availableEvidence(
            List<String> shared,
            List<String> first,
            List<String> second
    ) {
        return new IncidentComparison.EvidenceSetComparison(
                IncidentComparison.EvidenceAvailability.AVAILABLE, shared, first, second);
    }

    private static IncidentComparison.EvidenceSetComparison unavailableEvidence(
            IncidentComparison.EvidenceAvailability availability
    ) {
        return new IncidentComparison.EvidenceSetComparison(availability, List.of(), List.of(), List.of());
    }

    private static IncidentSummaryViewModel summary(String id, String suspect) {
        return new IncidentSummaryViewModel(
                id, Path.of("build", "test-incidents", id + ".json"), 1_000L,
                400.0, 120.0, 10, EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED",
                suspect, true, "2026-08-14 12:00:00", "Overworld", "0, 64, 0");
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
