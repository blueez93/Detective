package fr.apocalypsebleu.moddetective.core.casefile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CaseIdentityResolverTest {
    @Test
    void mergedGroupKeepsTheIdentityWithTheLargestMembershipOverlap() {
        CaseFile previousLarge = caseFile("case-large", List.of("a", "b", "c", "d"), 1L);
        CaseFile previousSmall = caseFile("case-small", List.of("e", "f", "g"), 2L);
        CaseFile merged = caseFile("case-provisional", List.of("a", "b", "c", "d", "e", "f", "g"), 1L);

        List<CaseFile> resolved = CaseIdentityResolver.resolve(
                List.of(merged), List.of(previousSmall, previousLarge));

        assertEquals("case-large", resolved.getFirst().caseId());
    }

    @Test
    void splitKeepsThePriorIdentityOnlyOnTheLargestOverlapChild() {
        CaseFile previous = caseFile("case-original", List.of("a", "b", "c", "d", "e", "f"), 1L);
        CaseFile largeChild = caseFile("case-new-large", List.of("a", "b", "c", "d"), 1L);
        CaseFile smallChild = caseFile("case-new-small", List.of("e", "f", "x"), 5L);

        List<CaseFile> resolved = CaseIdentityResolver.resolve(
                List.of(smallChild, largeChild), List.of(previous));

        assertEquals("case-original", resolved.getFirst().caseId());
        assertNotEquals("case-original", resolved.getLast().caseId());
    }

    private static CaseFile caseFile(String id, List<String> incidents, long firstSeen) {
        return new CaseFile(
                id, incidents, firstSeen, firstSeen + incidents.size(), incidents.size(),
                400.0, 400.0, List.of(), List.of(), 0.9, 0.9);
    }
}
