package fr.apocalypsebleu.moddetective.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SuspectAnalyzer {
    private static final List<String> IGNORED_PREFIXES = List.of(
            "java.", "jdk.", "sun.",
            "net.minecraft.", "com.mojang.", "org.lwjgl.",
            "net.neoforged.", "fr.apocalypsebleu.moddetective."
    );

    private final ClassOwnershipResolver resolver;

    public SuspectAnalyzer() {
        this(new ModSourceResolver()::resolve);
    }

    public SuspectAnalyzer(ClassOwnershipResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public Analysis analyze(List<StackSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return new Analysis(0, List.of(), List.of());
        }

        Map<ModSourceResolver.ResolvedMod, Integer> modHits = new HashMap<>();
        Map<String, Integer> hotClasses = new HashMap<>();

        for (StackSnapshot snapshot : snapshots) {
            Set<ModSourceResolver.ResolvedMod> modsSeenInThisSample = new HashSet<>();

            for (StackTraceElement frame : snapshot.stack()) {
                String className = frame.getClassName();
                if (isIgnored(className)) {
                    continue;
                }

                hotClasses.merge(className, 1, Integer::sum);
                resolver.resolve(className).ifPresent(modsSeenInThisSample::add);
            }

            modsSeenInThisSample.forEach(mod -> modHits.merge(mod, 1, Integer::sum));
        }

        List<Suspect> suspects = modHits.entrySet().stream()
                .map(entry -> new Suspect(
                        entry.getKey().id(),
                        entry.getKey().name(),
                        entry.getKey().version(),
                        entry.getValue(),
                        entry.getValue() * 100.0 / snapshots.size()))
                .sorted(Comparator.comparingInt(Suspect::samplesObserved).reversed()
                        .thenComparing(Suspect::modId))
                .limit(5)
                .toList();

        List<HotClass> classes = hotClasses.entrySet().stream()
                .map(entry -> new HotClass(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(HotClass::hits).reversed()
                        .thenComparing(HotClass::className))
                .limit(10)
                .toList();

        return new Analysis(snapshots.size(), suspects, classes);
    }

    private static boolean isIgnored(String className) {
        for (String prefix : IGNORED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public record Analysis(int stackSamples, List<Suspect> suspects, List<HotClass> hotClasses) {}
    public record Suspect(String modId, String modName, String version, int samplesObserved, double sampleSharePercent) {}
    public record HotClass(String className, int hits) {}

    @FunctionalInterface
    public interface ClassOwnershipResolver {
        Optional<ModSourceResolver.ResolvedMod> resolve(String className);
    }
}
