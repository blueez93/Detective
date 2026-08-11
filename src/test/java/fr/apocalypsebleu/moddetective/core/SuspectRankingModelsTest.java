package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspectRankingModelsTest {
    @Test
    void leafEvidenceNaturallyPromotesIndirectCalleeBOverCallerA() {
        List<SuspectAnalyzer.Suspect> ranked = SuspectRankingModels.rank(List.of(
                suspect("a", 20, 0, 5.0),
                suspect("b", 20, 20, 2.0)), SuspectRankingModels.Model.LEAF_OWNERSHIP);

        assertEquals(List.of("b", "a"), ids(ranked));
    }

    @Test
    void leafEvidenceOrdersNestedCalleeCThenBThenA() {
        List<SuspectAnalyzer.Suspect> ranked = SuspectRankingModels.rank(List.of(
                suspect("a", 20, 0, 6.0),
                suspect("b", 20, 0, 4.0),
                suspect("c", 20, 20, 2.0)), SuspectRankingModels.Model.LEAF_OWNERSHIP);

        assertEquals(List.of("c", "b", "a"), ids(ranked));
    }

    @Test
    void permutationsDoNotDependOnAlphabeticalModIdsWhenLeafEvidenceExists() {
        List<SuspectAnalyzer.Suspect> ranked = SuspectRankingModels.rank(List.of(
                suspect("z-caller", 12, 0, 5.0),
                suspect("a-leaf", 12, 12, 2.0)), SuspectRankingModels.Model.LEAF_OWNERSHIP);

        assertEquals("a-leaf", ranked.getFirst().modId());
        List<SuspectAnalyzer.Suspect> reversedIds = SuspectRankingModels.rank(List.of(
                suspect("a-caller", 12, 0, 5.0),
                suspect("z-leaf", 12, 12, 2.0)), SuspectRankingModels.Model.LEAF_OWNERSHIP);
        assertEquals("z-leaf", reversedIds.getFirst().modId());
    }

    @Test
    void marksPracticallyEquivalentEvidenceAmbiguous() {
        assertTrue(SuspectRankingModels.hasPracticallyEquivalentTopEvidence(List.of(
                suspect("a", 49, 20, 2.0),
                suspect("b", 48, 19, 2.5))));
        assertFalse(SuspectRankingModels.hasPracticallyEquivalentTopEvidence(List.of(
                suspect("a", 49, 40, 2.0),
                suspect("b", 48, 5, 2.5))));
    }

    @Test
    void usesModIdOnlyAsTheFinalDeterministicFallback() {
        List<SuspectAnalyzer.Suspect> ranked = SuspectRankingModels.rank(List.of(
                suspect("zeta", 10, 5, 2.0),
                suspect("alpha", 10, 5, 2.0)), SuspectRankingModels.Model.LEAF_OWNERSHIP);

        assertEquals(List.of("alpha", "zeta"), ids(ranked));
    }

    private static SuspectAnalyzer.Suspect suspect(String id, int presence, int leaf, double depth) {
        return new SuspectAnalyzer.Suspect(
                id, id, "1", presence, presence, leaf, leaf, depth, (int) depth,
                Math.max(0, leaf - 1), presence - leaf, 1);
    }

    private static List<String> ids(List<SuspectAnalyzer.Suspect> suspects) {
        return suspects.stream().map(SuspectAnalyzer.Suspect::modId).toList();
    }
}
