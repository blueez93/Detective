package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceBadgeTest {
    @Test
    void mapsAttributedEvidenceToReadableTiersWithoutChangingEngineState() {
        assertEquals(EvidenceBadge.HIGH_EVIDENCE, EvidenceBadge.from("ATTRIBUTED", suspect(12, 85.0)));
        assertEquals(EvidenceBadge.MODERATE_EVIDENCE, EvidenceBadge.from("ATTRIBUTED", suspect(6, 60.0)));
        assertEquals(EvidenceBadge.LOW_EVIDENCE, EvidenceBadge.from("ATTRIBUTED", suspect(3, 45.0)));
    }

    @Test
    void preservesEverySpecialAttributionState() {
        assertEquals(EvidenceBadge.AMBIGUOUS_ATTRIBUTION,
                EvidenceBadge.from("AMBIGUOUS_ATTRIBUTION", null));
        assertEquals(EvidenceBadge.INSUFFICIENT_EVIDENCE,
                EvidenceBadge.from("INSUFFICIENT_EVIDENCE", null));
        assertEquals(EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE,
                EvidenceBadge.from("NATIVE_OR_DRIVER_STALL_POSSIBLE", null));
        assertEquals(EvidenceBadge.UNKNOWN, EvidenceBadge.from("future_state", null));
    }

    @Test
    void mapsSpecialStatesToCautiousUserFacingCopy() {
        assertEquals("detective.ui.evidence.ambiguous",
                EvidenceBadge.AMBIGUOUS_ATTRIBUTION.translationKey());
        assertEquals("detective.ui.incidents.card.ambiguous",
                EvidenceBadge.AMBIGUOUS_ATTRIBUTION.listSummaryKey());
        assertEquals("detective.ui.evidence.system", EvidenceBadge.JVM_GC_SUSPECTED.translationKey());
        assertEquals("detective.ui.evidence.system",
                EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE.translationKey());
        assertEquals("detective.ui.incidents.card.system",
                EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE.listSummaryKey());
    }

    private static SuspectViewModel suspect(int leafSamples, double leafShare) {
        return new SuspectViewModel(
                "example", "Example", "1", leafSamples, leafShare,
                leafSamples, leafShare, 2.0, 2, leafSamples - 1, 0, 1);
    }
}
