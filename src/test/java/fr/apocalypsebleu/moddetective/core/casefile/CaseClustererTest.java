package fr.apocalypsebleu.moddetective.core.casefile;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseClustererTest {
    @Test
    void threeHighlySimilarIncidentsProduceOneCase() {
        List<IncidentFingerprint> incidents = List.of(
                related("freeze-a.json", 1_000L, 400.0),
                related("freeze-b.json", 2_000L, 420.0),
                related("freeze-c.json", 3_000L, 450.0));

        List<CaseFile> cases = new CaseClusterer().cluster(incidents);

        assertEquals(1, cases.size());
        CaseFile caseFile = cases.getFirst();
        assertEquals(List.of("freeze-a.json", "freeze-b.json", "freeze-c.json"),
                caseFile.relatedIncidentIds());
        assertEquals(3, caseFile.occurrenceCount());
        assertEquals(1_000L, caseFile.firstSeenEpochMs());
        assertEquals(3_000L, caseFile.lastSeenEpochMs());
        assertEquals(1_270.0 / 3.0, caseFile.averageStallDurationMs(), 0.000_001);
        assertEquals(450.0, caseFile.longestStallDurationMs());
        assertFalse(caseFile.recurringEvidence().isEmpty());
        assertEquals("alpha", caseFile.recurringOwners().getFirst().ownerId());
        assertTrue(caseFile.aggregateSimilarity() >= CaseClusterer.DEFAULT_CONFIGURATION.similarityThreshold());
        assertTrue(caseFile.aggregateEvidenceStrength() > 0.9);
    }

    @Test
    void tenUnrelatedIncidentsProduceNoCase() {
        List<IncidentFingerprint> incidents = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            incidents.add(fingerprint(
                    "freeze-unrelated-" + index + ".json", index, 400.0,
                    "example.pattern" + index + ".Work", "owner-" + index,
                    AttributionEvidence.State.ATTRIBUTED, 6));
        }

        assertTrue(new CaseClusterer().cluster(incidents).isEmpty());
    }

    @Test
    void twoHighlySimilarIncidentsDoNotProduceARecurringCase() {
        assertTrue(new CaseClusterer().cluster(List.of(
                related("freeze-a.json", 1_000L, 400.0),
                related("freeze-b.json", 2_000L, 410.0))).isEmpty());
    }

    @Test
    void sixRelatedIncidentsMixedWithThreeUnrelatedProduceOneSixIncidentCase() {
        List<IncidentFingerprint> incidents = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            incidents.add(related("freeze-related-" + index + ".json", 100L + index, 400.0 + index));
        }
        for (int index = 0; index < 3; index++) {
            incidents.add(fingerprint(
                    "freeze-noise-" + index + ".json", 200L + index, 600.0,
                    "example.noise" + index + ".Work", "noise-" + index,
                    AttributionEvidence.State.ATTRIBUTED, 6));
        }
        Collections.shuffle(incidents, new java.util.Random(42L));

        List<CaseFile> cases = new CaseClusterer().cluster(incidents);

        assertEquals(1, cases.size());
        assertEquals(6, cases.getFirst().occurrenceCount());
        assertTrue(cases.getFirst().relatedIncidentIds().stream().allMatch(id -> id.contains("related")));
    }

    @Test
    void sameAttributedSuspectWithDifferentTechnicalFingerprintsDoesNotCreateACase() {
        List<IncidentFingerprint> incidents = List.of(
                fingerprint("freeze-a.json", 1L, 400.0, "example.path.A", "alpha",
                        AttributionEvidence.State.ATTRIBUTED, 6),
                fingerprint("freeze-b.json", 2L, 400.0, "example.path.B", "alpha",
                        AttributionEvidence.State.ATTRIBUTED, 6),
                fingerprint("freeze-c.json", 3L, 400.0, "example.path.C", "alpha",
                        AttributionEvidence.State.ATTRIBUTED, 6));

        assertTrue(new CaseClusterer().cluster(incidents).isEmpty());
    }

    @Test
    void differentAttributedSuspectsMayClusterWhenUnderlyingTechnicalEvidenceMatches() {
        List<IncidentFingerprint> incidents = List.of(
                fingerprint("freeze-a.json", 1L, 400.0, "example.shared.LibraryWork", "alpha",
                        AttributionEvidence.State.ATTRIBUTED, 6),
                fingerprint("freeze-b.json", 2L, 400.0, "example.shared.LibraryWork", "beta",
                        AttributionEvidence.State.ATTRIBUTED, 6),
                fingerprint("freeze-c.json", 3L, 400.0, "example.shared.LibraryWork", "gamma",
                        AttributionEvidence.State.ATTRIBUTED, 6));

        List<CaseFile> cases = new CaseClusterer().cluster(incidents);

        assertEquals(1, cases.size());
        assertEquals(3, cases.getFirst().occurrenceCount());
        assertTrue(cases.getFirst().recurringOwners().isEmpty());
        assertEquals(CaseFile.EvidenceKind.CLASS,
                cases.getFirst().recurringEvidence().getFirst().kind());
        assertTrue(cases.getFirst().recurringEvidence().getFirst().technicalSignature()
                .matches("[0-9a-f]{32}"));
    }

    @Test
    void identicalInputProducesStableOutputRegardlessOfInputOrder() {
        List<IncidentFingerprint> ordered = List.of(
                related("freeze-a.json", 3_000L, 450.0),
                related("freeze-b.json", 1_000L, 400.0),
                related("freeze-c.json", 2_000L, 420.0));
        List<IncidentFingerprint> reversed = new ArrayList<>(ordered);
        Collections.reverse(reversed);

        assertEquals(new CaseClusterer().cluster(ordered), new CaseClusterer().cluster(reversed));
    }

    @Test
    void caseIdRemainsStableWhenAFourthLaterIncidentJoinsTheFoundingThree() {
        List<IncidentFingerprint> founding = List.of(
                related("freeze-a.json", 1_000L, 400.0),
                related("freeze-b.json", 2_000L, 410.0),
                related("freeze-c.json", 3_000L, 420.0));
        List<IncidentFingerprint> grown = new ArrayList<>(founding);
        grown.add(related("freeze-d.json", 4_000L, 430.0));

        CaseFile original = new CaseClusterer().cluster(founding).getFirst();
        CaseFile updated = new CaseClusterer().cluster(grown).getFirst();

        assertEquals(original.caseId(), updated.caseId());
        assertEquals(4, updated.occurrenceCount());
    }

    @Test
    void borderlineIncidentCannotBridgeCompleteLinkIncompatibleGroups() {
        List<IncidentFingerprint> incidents = List.of(
                manual("a-1", 1L, Map.of("path-a", 1.0)),
                manual("a-2", 2L, Map.of("path-a", 1.0)),
                manual("a-3", 3L, Map.of("path-a", 1.0)),
                manual("m-bridge", 4L, Map.of("path-a", 1.0, "path-b", 1.0)),
                manual("z-1", 5L, Map.of("path-b", 1.0)),
                manual("z-2", 6L, Map.of("path-b", 1.0)),
                manual("z-3", 7L, Map.of("path-b", 1.0)));
        CaseClusterer.Configuration configuration = new CaseClusterer.Configuration(
                0.65, 3, 3, 500, 2.0 / 3.0);

        List<CaseFile> cases = new CaseClusterer(new CaseSimilarity(), configuration).cluster(incidents);

        assertEquals(2, cases.size());
        assertEquals(List.of(3, 4), cases.stream().map(CaseFile::occurrenceCount).sorted().toList());
        assertTrue(cases.stream().noneMatch(value -> value.occurrenceCount() == 7));
    }

    @Test
    void emptySparseAndInsufficientEvidenceFailsSafelyWithoutCreatingCases() {
        List<IncidentFingerprint> incidents = List.of(
                empty("empty-a"), empty("empty-b"), empty("empty-c"),
                fingerprint("sparse-a", 1L, 400.0, "example.Sparse", "alpha",
                        AttributionEvidence.State.INSUFFICIENT_EVIDENCE, 2),
                fingerprint("sparse-b", 2L, 400.0, "example.Sparse", "alpha",
                        AttributionEvidence.State.INSUFFICIENT_EVIDENCE, 2),
                fingerprint("sparse-c", 3L, 400.0, "example.Sparse", "alpha",
                        AttributionEvidence.State.INSUFFICIENT_EVIDENCE, 2));

        assertTrue(new CaseClusterer().cluster(List.of()).isEmpty());
        assertTrue(new CaseClusterer().cluster(incidents).isEmpty());
    }

    @Test
    void thresholdIsCentralConfigurationAndCannotRelaxTheThreeIncidentRule() {
        List<IncidentFingerprint> incidents = List.of(
                related("freeze-a.json", 1L, 400.0),
                related("freeze-b.json", 2L, 500.0),
                related("freeze-c.json", 3L, 600.0));
        CaseClusterer.Configuration exactOnly = new CaseClusterer.Configuration(
                1.0, 3, 3, 500, 2.0 / 3.0);

        assertEquals(1, new CaseClusterer().cluster(incidents).size());
        assertTrue(new CaseClusterer(new CaseSimilarity(), exactOnly).cluster(incidents).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CaseClusterer.Configuration(
                0.72, 2, 3, 500, 2.0 / 3.0));
    }

    private static IncidentFingerprint related(String id, long detectedAt, double duration) {
        return fingerprint(id, detectedAt, duration, "example.recurring.Work", "alpha",
                AttributionEvidence.State.ATTRIBUTED, 6);
    }

    private static IncidentFingerprint manual(String id, long detectedAt, Map<String, Double> classes) {
        return new IncidentFingerprint(
                id, detectedAt, 400.0, IncidentFingerprint.StallType.RENDER_THREAD_STALL,
                6, classes, Map.of("shared-owner", 1.0),
                Map.of("shared-owner", 1.0), Map.of("shared-owner", 0.2));
    }

    private static IncidentFingerprint empty(String id) {
        return new IncidentFingerprint(
                id, 0L, 0.0, IncidentFingerprint.StallType.RENDER_THREAD_STALL,
                0, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
    }

    private static IncidentFingerprint fingerprint(
            String id,
            long detectedAt,
            double duration,
            String hotClass,
            String owner,
            AttributionEvidence.State state,
            int samples
    ) {
        FrameSample frame = new FrameSample(
                detectedAt, detectedAt, duration, 1_000.0 / duration,
                1L, 2L, "minecraft:overworld", 0, 64, 0);
        SuspectAnalyzer.Suspect observation = new SuspectAnalyzer.Suspect(
                owner, owner, "1", samples, 100.0,
                samples, 100.0, 1.0, 1,
                Math.max(0, samples - 1), 0, 1);
        FreezeIncident incident = new FreezeIncident(
                detectedAt, duration, 120.0, frame, samples,
                new AttributionEvidence(state, samples, samples, 0, 0),
                List.of(observation), List.of(new SuspectAnalyzer.HotClass(hotClass, samples)),
                List.of(frame));
        return IncidentFingerprint.from(id, incident);
    }
}
