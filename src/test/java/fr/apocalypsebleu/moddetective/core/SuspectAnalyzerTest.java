package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static ModSourceResolver.ResolvedMod mod(String id) {
        return new ModSourceResolver.ResolvedMod(id, id, "1.0");
    }

    private static StackSnapshot stack(long nanoTime, String... classes) {
        StackTraceElement[] frames = java.util.Arrays.stream(classes)
                .map(className -> new StackTraceElement(className, "run", "Work.java", 1))
                .toArray(StackTraceElement[]::new);
        return new StackSnapshot(nanoTime, frames);
    }
}
