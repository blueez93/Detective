package fr.apocalypsebleu.moddetective.core;

import java.util.Comparator;
import java.util.List;

/** Simple, coefficient-free ranking models used for validation comparisons. */
public final class SuspectRankingModels {
    private SuspectRankingModels() {}

    public static List<SuspectAnalyzer.Suspect> rank(
            List<SuspectAnalyzer.Suspect> suspects,
            Model model
    ) {
        return suspects.stream().sorted(comparator(model)).toList();
    }

    public static Comparator<SuspectAnalyzer.Suspect> comparator(Model model) {
        Comparator<SuspectAnalyzer.Suspect> modId = Comparator.comparing(SuspectAnalyzer.Suspect::modId);
        Comparator<SuspectAnalyzer.Suspect> presence = Comparator
                .comparingInt(SuspectAnalyzer.Suspect::presenceSamples).reversed();
        Comparator<SuspectAnalyzer.Suspect> leaf = Comparator
                .comparingInt(SuspectAnalyzer.Suspect::leafOwnershipCount).reversed();
        Comparator<SuspectAnalyzer.Suspect> repeatedLeaf = Comparator
                .comparingInt(SuspectAnalyzer.Suspect::repeatedLeafOwnership).reversed();
        Comparator<SuspectAnalyzer.Suspect> depth = Comparator
                .comparingDouble(SuspectAnalyzer.Suspect::averageFirstFrameDepth)
                .thenComparingInt(SuspectAnalyzer.Suspect::minimumFirstFrameDepth);

        return switch (model) {
            case PRESENCE -> presence.thenComparing(modId);
            case LEAF_OWNERSHIP -> leaf.thenComparing(repeatedLeaf).thenComparing(presence)
                    .thenComparing(depth).thenComparing(modId);
            case PRESENCE_THEN_LEAF -> presence.thenComparing(leaf).thenComparing(repeatedLeaf)
                    .thenComparing(depth).thenComparing(modId);
            case DEPTH -> depth.thenComparing(leaf).thenComparing(presence).thenComparing(modId);
        };
    }

    public static boolean hasPracticallyEquivalentTopEvidence(List<SuspectAnalyzer.Suspect> suspects) {
        List<SuspectAnalyzer.Suspect> ranked = rank(suspects, Model.LEAF_OWNERSHIP);
        if (ranked.size() < 2) {
            return false;
        }
        SuspectAnalyzer.Suspect first = ranked.get(0);
        SuspectAnalyzer.Suspect second = ranked.get(1);
        return Math.abs(first.presenceSamples() - second.presenceSamples()) <= 1
                && Math.abs(first.leafOwnershipCount() - second.leafOwnershipCount()) <= 1
                && Math.abs(first.averageFirstFrameDepth() - second.averageFirstFrameDepth()) <= 1.0;
    }

    public enum Model {
        PRESENCE,
        LEAF_OWNERSHIP,
        PRESENCE_THEN_LEAF,
        DEPTH
    }
}
