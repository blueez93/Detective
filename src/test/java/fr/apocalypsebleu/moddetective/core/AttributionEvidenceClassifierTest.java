package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributionEvidenceClassifierTest {
    @Test
    void reportsInsufficientEvidenceWithoutSamples() {
        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                List.of(), new SuspectAnalyzer.Analysis(0, List.of(), List.of()));

        assertEquals(AttributionEvidence.State.INSUFFICIENT_EVIDENCE, evidence.state());
    }

    @Test
    void reportsInsufficientEvidenceForOnlyOneAttributedSample() {
        SuspectAnalyzer.Suspect suspect = new SuspectAnalyzer.Suspect("alpha", "Alpha", "1", 1, 100.0);
        List<StackSnapshot> stacks = List.of(stack("example.alpha.Work", "run", false));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(1, List.of(suspect), List.of()));

        assertEquals(AttributionEvidence.State.INSUFFICIENT_EVIDENCE, evidence.state());
    }

    @Test
    void rejectsSparseLowShareObservationSeenDuringDimensionChange() {
        SuspectAnalyzer.Suspect suspect = new SuspectAnalyzer.Suspect("sodium", "Sodium", "1", 2, 28.6);
        List<StackSnapshot> stacks = List.of(
                stack("example.Work1", "run", false),
                stack("example.Work2", "run", false),
                stack("example.Work3", "run", false));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(7, List.of(suspect), List.of()));

        assertEquals(AttributionEvidence.State.INSUFFICIENT_EVIDENCE, evidence.state());
        assertEquals(2, evidence.strongestSuspectSamples());
    }

    @Test
    void acceptsRepeatedHighShareAttribution() {
        SuspectAnalyzer.Suspect suspect = new SuspectAnalyzer.Suspect("culprit", "Culprit", "1", 7, 87.5);
        List<StackSnapshot> stacks = List.of(
                stack("example.Work1", "run", false),
                stack("example.Work2", "run", false),
                stack("example.Work3", "run", false));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(8, List.of(suspect), List.of()));

        assertEquals(AttributionEvidence.State.ATTRIBUTED, evidence.state());
    }

    @Test
    void reportsGcOnlyWhenAnExplicitGcMarkerExistsWithoutAModSuspect() {
        List<StackSnapshot> stacks = List.of(stack("java.lang.System", "gc", true));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(1, List.of(), List.of()));

        assertEquals(AttributionEvidence.State.JVM_GC_SUSPECTED, evidence.state());
        assertEquals(1, evidence.gcMarkerFrames());
    }

    @Test
    void reportsNativeOrDriverPossibilityFromLwjglFrames() {
        List<StackSnapshot> stacks = List.of(stack("org.lwjgl.glfw.GLFW", "nglfwSwapBuffers", true));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(1, List.of(), List.of()));

        assertEquals(AttributionEvidence.State.NATIVE_OR_DRIVER_STALL_POSSIBLE, evidence.state());
    }

    @Test
    void doesNotInventAModForUnattributableJavaStacks() {
        List<StackSnapshot> stacks = List.of(stack("java.util.concurrent.locks.LockSupport", "park", false));

        AttributionEvidence evidence = AttributionEvidenceClassifier.classify(
                stacks, new SuspectAnalyzer.Analysis(1, List.of(), List.of()));

        assertEquals(AttributionEvidence.State.UNKNOWN, evidence.state());
        assertEquals(0, evidence.strongestSuspectSamples());
    }

    private static StackSnapshot stack(String className, String methodName, boolean nativeMethod) {
        return new StackSnapshot(1L, new StackTraceElement[]{
                new StackTraceElement(className, methodName, null, nativeMethod ? -2 : 1)
        });
    }
}
