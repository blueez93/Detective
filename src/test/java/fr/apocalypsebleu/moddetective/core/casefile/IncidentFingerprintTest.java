package fr.apocalypsebleu.moddetective.core.casefile;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IncidentFingerprintTest {
    @Test
    void derivesStableEvidenceWithoutDependingOnSuspectRankOrAttributionVerdict() {
        SuspectAnalyzer.Suspect alpha = suspect("alpha", 6, 5, 2);
        SuspectAnalyzer.Suspect beta = suspect("beta", 4, 1, 1);
        List<SuspectAnalyzer.HotClass> frames = List.of(
                new SuspectAnalyzer.HotClass("example.SharedWork", 6));
        FreezeIncident attributed = incident(
                AttributionEvidence.State.ATTRIBUTED, List.of(alpha, beta), frames, 6);
        FreezeIncident ambiguousAndReordered = incident(
                AttributionEvidence.State.AMBIGUOUS_ATTRIBUTION, List.of(beta, alpha), frames, 6);

        IncidentFingerprint first = IncidentFingerprint.from("freeze-a.json", attributed);
        IncidentFingerprint second = IncidentFingerprint.from("freeze-b.json", ambiguousAndReordered);

        assertEquals(first.classFingerprints(), second.classFingerprints());
        assertEquals(first.frameFingerprints(), second.frameFingerprints());
        assertEquals(first.leafOwners(), second.leafOwners());
        assertEquals(first.stackPresenceOwners(), second.stackPresenceOwners());
        assertEquals(first.stackDiversityByOwner(), second.stackDiversityByOwner());
        assertEquals(IncidentFingerprint.StallType.RENDER_THREAD_STALL, first.stallType());
        assertEquals(first.stallType(), second.stallType());
        assertEquals(IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK, first.evidenceSource());
    }

    @Test
    void refusesToTreatSparseOrOwnerlessObservationsAsClusterableEvidence() {
        IncidentFingerprint sparse = IncidentFingerprint.from(
                "freeze-sparse.json",
                incident(AttributionEvidence.State.INSUFFICIENT_EVIDENCE,
                        List.of(suspect("alpha", 2, 2, 1)),
                        List.of(new SuspectAnalyzer.HotClass("example.Work", 2)),
                        2));
        IncidentFingerprint ownerless = IncidentFingerprint.from(
                "freeze-ownerless.json",
                incident(AttributionEvidence.State.UNKNOWN, List.of(),
                        List.of(new SuspectAnalyzer.HotClass("example.UnknownWork", 6)), 6));

        assertFalse(sparse.hasSufficientTechnicalEvidence(3));
        assertFalse(ownerless.hasSufficientTechnicalEvidence(3));
    }

    private static FreezeIncident incident(
            AttributionEvidence.State state,
            List<SuspectAnalyzer.Suspect> suspects,
            List<SuspectAnalyzer.HotClass> hotClasses,
            int samples
    ) {
        FrameSample frame = new FrameSample(
                1L, 2L, 400.0, 2.5, 3L, 4L, "minecraft:overworld", 0, 64, 0);
        return new FreezeIncident(
                1L, 400.0, 120.0, frame, samples,
                new AttributionEvidence(state, samples, samples, 0, 0),
                suspects, hotClasses, List.of(frame));
    }

    private static SuspectAnalyzer.Suspect suspect(String id, int presence, int leaf, int diversity) {
        return new SuspectAnalyzer.Suspect(
                id, id, "1", presence, presence * 100.0 / 6.0,
                leaf, leaf * 100.0 / 6.0, 1.0, 1,
                Math.max(0, leaf - 1), presence - leaf, diversity);
    }
}
