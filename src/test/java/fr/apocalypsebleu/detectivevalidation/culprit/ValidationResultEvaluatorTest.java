package fr.apocalypsebleu.detectivevalidation.culprit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationResultEvaluatorTest {
    private static final String CULPRIT = "detective_testculprit";

    @Test
    void passesWhenExpectedCulpritIsFirstAndEvidenceIsComplete() {
        ValidationResultEvaluator.Result result = ValidationResultEvaluator.evaluate(
                expected(true),
                detected(List.of(
                        suspect(CULPRIT, 100.0),
                        suspect("another_mod", 20.0))));

        assertTrue(result.passed());
        assertEquals(1, result.expectedCulpritRank());
        assertEquals(CULPRIT, result.detectedTopSuspect());
        assertEquals(100.0, result.expectedCulpritSharePercent());
    }

    @Test
    void reportsTheTrueRankAmongSeveralSuspects() {
        ValidationResultEvaluator.Result result = ValidationResultEvaluator.evaluate(
                expected(true),
                detected(List.of(
                        suspect("alpha", 100.0),
                        suspect(CULPRIT, 80.0),
                        suspect("omega", 20.0))));

        assertFalse(result.passed());
        assertEquals(2, result.expectedCulpritRank());
        assertEquals("alpha", result.detectedTopSuspect());
    }

    @Test
    void failsCleanlyWhenThereAreNoSamplesOrSuspects() {
        ValidationResultEvaluator.Detected empty = new ValidationResultEvaluator.Detected(
                300.0, 0, 10, true, List.of());

        ValidationResultEvaluator.Result result = ValidationResultEvaluator.evaluate(expected(true), empty);

        assertFalse(result.passed());
        assertEquals(0, result.expectedCulpritRank());
        assertEquals("<none>", result.detectedTopSuspect());
    }

    @Test
    void negativeCasePassesOnlyWhenNoIncidentExists() {
        assertTrue(ValidationResultEvaluator.evaluate(expected(false), null).passed());
        assertFalse(ValidationResultEvaluator.evaluate(expected(false), detected(List.of())).passed());
    }

    private static ValidationResultEvaluator.Expected expected(boolean incidentExpected) {
        return new ValidationResultEvaluator.Expected(CULPRIT, 300L, incidentExpected);
    }

    private static ValidationResultEvaluator.Detected detected(List<ValidationResultEvaluator.Suspect> suspects) {
        return new ValidationResultEvaluator.Detected(320.0, 12, 90, true, suspects);
    }

    private static ValidationResultEvaluator.Suspect suspect(String id, double share) {
        return new ValidationResultEvaluator.Suspect(id, share);
    }
}
