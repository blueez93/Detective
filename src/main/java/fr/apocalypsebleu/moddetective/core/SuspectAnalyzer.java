package fr.apocalypsebleu.moddetective.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SuspectAnalyzer {
    /**
     * Runtime validation showed that presence-only ties identify callers, not the code owning the
     * active mod frame. Leaf ownership resolved every captured direct, indirect, nested and
     * permuted scenario without coefficients or ground-truth-specific rules.
     */
    public static final SuspectRankingModels.Model PRODUCTION_RANKING_MODEL = SuspectRankingModels.Model.LEAF_OWNERSHIP;
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

        Map<ModSourceResolver.ResolvedMod, MutableEvidence> evidenceByMod = new HashMap<>();
        Map<String, Integer> hotClasses = new HashMap<>();
        ModSourceResolver.ResolvedMod previousLeafOwner = null;

        for (StackSnapshot snapshot : snapshots) {
            Map<ModSourceResolver.ResolvedMod, SampleEvidence> sampleEvidence = new LinkedHashMap<>();

            // Thread.getStackTrace() returns the active/top frame first. Depth zero is therefore
            // closest to the current execution point; this convention is guarded by unit tests.
            StackTraceElement[] stack = snapshot.stack();
            for (int depth = 0; depth < stack.length; depth++) {
                StackTraceElement frame = stack[depth];
                String className = frame.getClassName();
                if (isIgnored(className)) {
                    continue;
                }

                hotClasses.merge(className, 1, Integer::sum);
                Optional<ModSourceResolver.ResolvedMod> owner = resolver.resolve(className);
                if (owner.isEmpty()) {
                    continue;
                }

                int frameDepth = depth;
                SampleEvidence sample = sampleEvidence.computeIfAbsent(
                        owner.get(), ignored -> new SampleEvidence(frameDepth));
                sample.ownedFrames().add(className + '#' + frame.getMethodName());
            }

            ModSourceResolver.ResolvedMod leafOwner = sampleEvidence.isEmpty()
                    ? null
                    : sampleEvidence.keySet().iterator().next();
            for (Map.Entry<ModSourceResolver.ResolvedMod, SampleEvidence> entry : sampleEvidence.entrySet()) {
                MutableEvidence evidence = evidenceByMod.computeIfAbsent(entry.getKey(), ignored -> new MutableEvidence());
                SampleEvidence sample = entry.getValue();
                evidence.presenceSamples++;
                evidence.firstDepthTotal += sample.firstDepth();
                evidence.minimumFirstDepth = Math.min(evidence.minimumFirstDepth, sample.firstDepth());
                evidence.stackSignatures.add(String.join(" > ", sample.ownedFrames()));
                if (entry.getKey().equals(leafOwner)) {
                    evidence.leafOwnershipCount++;
                    if (entry.getKey().equals(previousLeafOwner)) {
                        evidence.repeatedLeafOwnership++;
                    }
                }
            }
            previousLeafOwner = leafOwner;
        }

        List<Suspect> suspects = evidenceByMod.entrySet().stream()
                .map(entry -> entry.getValue().toSuspect(entry.getKey(), snapshots.size()))
                .sorted(SuspectRankingModels.comparator(PRODUCTION_RANKING_MODEL))
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

    public record Analysis(int stackSamples, List<Suspect> suspects, List<HotClass> hotClasses) {
        public Analysis {
            suspects = List.copyOf(Objects.requireNonNull(suspects, "suspects"));
            hotClasses = List.copyOf(Objects.requireNonNull(hotClasses, "hotClasses"));
        }
    }

    public record Suspect(
            String modId,
            String modName,
            String version,
            int presenceSamples,
            double presenceSharePercent,
            int leafOwnershipCount,
            double leafOwnershipSharePercent,
            double averageFirstFrameDepth,
            int minimumFirstFrameDepth,
            int repeatedLeafOwnership,
            int callerOnlySamples,
            int stackDiversity
    ) {
        public Suspect {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(modName, "modName");
            Objects.requireNonNull(version, "version");
        }

        public Suspect(
                String modId,
                String modName,
                String version,
                int presenceSamples,
                double presenceSharePercent
        ) {
            this(modId, modName, version, presenceSamples, presenceSharePercent,
                    0, 0.0, Double.POSITIVE_INFINITY, -1, 0, presenceSamples, 0);
        }

        /** Compatibility accessor for code reading v0.3 terminology. */
        public int samplesObserved() {
            return presenceSamples;
        }

        /** Compatibility accessor for code reading v0.3 terminology. */
        public double sampleSharePercent() {
            return presenceSharePercent;
        }
    }

    public record HotClass(String className, int hits) {}

    @FunctionalInterface
    public interface ClassOwnershipResolver {
        Optional<ModSourceResolver.ResolvedMod> resolve(String className);
    }

    private static final class MutableEvidence {
        private int presenceSamples;
        private int leafOwnershipCount;
        private long firstDepthTotal;
        private int minimumFirstDepth = Integer.MAX_VALUE;
        private int repeatedLeafOwnership;
        private final Set<String> stackSignatures = new HashSet<>();

        private Suspect toSuspect(ModSourceResolver.ResolvedMod mod, int stackSamples) {
            return new Suspect(
                    mod.id(),
                    mod.name(),
                    mod.version(),
                    presenceSamples,
                    presenceSamples * 100.0 / stackSamples,
                    leafOwnershipCount,
                    leafOwnershipCount * 100.0 / stackSamples,
                    presenceSamples == 0 ? Double.POSITIVE_INFINITY : firstDepthTotal / (double) presenceSamples,
                    minimumFirstDepth == Integer.MAX_VALUE ? -1 : minimumFirstDepth,
                    repeatedLeafOwnership,
                    presenceSamples - leafOwnershipCount,
                    stackSignatures.size());
        }
    }

    private record SampleEvidence(int firstDepth, List<String> ownedFrames) {
        private SampleEvidence(int firstDepth) {
            this(firstDepth, new ArrayList<>());
        }
    }
}
