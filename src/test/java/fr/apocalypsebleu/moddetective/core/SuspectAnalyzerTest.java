package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuspectAnalyzerTest {
    @Test
    void ranksBySamplesAndCountsAModOnlyOncePerStackSample() {
        Map<String, ModSourceResolver.ResolvedMod> owners = Map.of(
                "example.alpha.Work", mod("alpha"),
                "example.beta.Work", mod("beta"),
                "example.charlie.Work", mod("charlie"));
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.ofNullable(owners.get(className)));

        List<StackSnapshot> snapshots = List.of(
                stack(1L, "example.alpha.Work", "example.alpha.Work", "example.beta.Work"),
                stack(2L, "example.alpha.Work", "example.charlie.Work"),
                stack(3L, "example.beta.Work"));

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(snapshots);

        assertEquals(3, analysis.stackSamples());
        assertEquals(List.of("alpha", "beta", "charlie"),
                analysis.suspects().stream().map(SuspectAnalyzer.Suspect::modId).toList());
        assertEquals(2, analysis.suspects().get(0).samplesObserved());
        assertEquals(2, analysis.suspects().get(1).samplesObserved());
        assertEquals(1, analysis.suspects().get(2).samplesObserved());
        assertEquals(2.0 / 3.0 * 100.0, analysis.suspects().get(0).sampleSharePercent(), 0.0001);
    }

    @Test
    void breaksEqualScoresDeterministicallyByModId() {
        Map<String, ModSourceResolver.ResolvedMod> owners = Map.of(
                "example.zeta.Work", mod("zeta"),
                "example.alpha.Work", mod("alpha"));
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.ofNullable(owners.get(className)));

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(List.of(
                stack(1L, "example.zeta.Work"),
                stack(2L, "example.alpha.Work")));

        assertEquals(List.of("alpha", "zeta"),
                analysis.suspects().stream().map(SuspectAnalyzer.Suspect::modId).toList());
    }

    @Test
    void treatsIndexZeroAsTheActiveLeafDirectionAndCountsRepeatedOwnership() {
        Map<String, ModSourceResolver.ResolvedMod> owners = Map.of(
                "example.alpha.Caller", mod("alpha"),
                "example.beta.Leaf", mod("beta"));
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.ofNullable(owners.get(className)));

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(List.of(
                stack(1L, "java.util.concurrent.locks.LockSupport", "example.beta.Leaf", "example.alpha.Caller"),
                stack(2L, "java.util.concurrent.locks.LockSupport", "example.beta.Leaf", "example.alpha.Caller")));

        SuspectAnalyzer.Suspect beta = byId(analysis, "beta");
        SuspectAnalyzer.Suspect alpha = byId(analysis, "alpha");
        assertEquals(2, beta.leafOwnershipCount());
        assertEquals(1, beta.minimumFirstFrameDepth());
        assertEquals(1, beta.repeatedLeafOwnership());
        assertEquals(0, alpha.leafOwnershipCount());
        assertEquals(2, alpha.callerOnlySamples());
        assertEquals(2.0, alpha.averageFirstFrameDepth());
    }

    @Test
    void recordsDistinctOwnedStackShapesWithoutInflatingPresence() {
        Map<String, ModSourceResolver.ResolvedMod> owners = Map.of(
                "example.alpha.One", mod("alpha"),
                "example.alpha.Two", mod("alpha"));
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.ofNullable(owners.get(className)));

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(List.of(
                stack(1L, "example.alpha.One"),
                stack(2L, "example.alpha.One"),
                stack(3L, "example.alpha.Two")));

        SuspectAnalyzer.Suspect alpha = byId(analysis, "alpha");
        assertEquals(3, alpha.presenceSamples());
        assertEquals(2, alpha.stackDiversity());
    }

    @Test
    void returnsEmptyAnalysisWithoutStackSamples() {
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.of(mod("unexpected")));

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(List.of());

        assertEquals(0, analysis.stackSamples());
        assertTrue(analysis.suspects().isEmpty());
        assertTrue(analysis.hotClasses().isEmpty());
    }

    @Test
    void preservesSharedOwnershipAndDoesNotInventAnOwnerForUnknownClasses() {
        ModSourceResolver.ResolvedMod shared = new ModSourceResolver.ResolvedMod(
                "alpha+beta", "Alpha / Beta (shared mod file)", "1.0");
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> switch (className) {
            case "example.shared.Work" -> Optional.of(shared);
            default -> Optional.empty();
        });

        SuspectAnalyzer.Analysis analysis = analyzer.analyze(List.of(
                stack(1L, "example.unknown.Work", "example.shared.Work")));

        assertEquals(1, analysis.suspects().size());
        assertEquals("alpha+beta", analysis.suspects().getFirst().modId());
        assertEquals("Alpha / Beta (shared mod file)", analysis.suspects().getFirst().modName());
    }

    private static ModSourceResolver.ResolvedMod mod(String id) {
        return new ModSourceResolver.ResolvedMod(id, id, "1.0");
    }

    private static SuspectAnalyzer.Suspect byId(SuspectAnalyzer.Analysis analysis, String id) {
        return analysis.suspects().stream().filter(suspect -> id.equals(suspect.modId())).findFirst().orElseThrow();
    }

    private static StackSnapshot stack(long nanoTime, String... classes) {
        StackTraceElement[] frames = java.util.Arrays.stream(classes)
                .map(className -> new StackTraceElement(className, "run", "Work.java", 1))
                .toArray(StackTraceElement[]::new);
        return new StackSnapshot(nanoTime, frames);
    }
}
