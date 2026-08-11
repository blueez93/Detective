package fr.apocalypsebleu.moddetective.support.report;

import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;

import java.nio.file.Path;
import java.util.List;

final class SupportReportFixtures {
    private SupportReportFixtures() {}

    static SupportReportData data(EvidenceBadge evidence) {
        return data(evidence, normalMods(), "Create");
    }

    static SupportReportData data(
            EvidenceBadge evidence,
            List<SupportReportData.InstalledMod> mods,
            String suspectName
    ) {
        boolean attributed = evidence.isAttributedTier();
        int samples = 30;
        List<SuspectViewModel> suspects = attributed || evidence == EvidenceBadge.AMBIGUOUS_ATTRIBUTION
                ? List.of(
                        suspect("create", suspectName, 29, 30),
                        suspect("library", "Example Library", evidence == EvidenceBadge.AMBIGUOUS_ATTRIBUTION ? 28 : 3, 30))
                : List.of();
        String raw = switch (evidence) {
            case HIGH_EVIDENCE, MODERATE_EVIDENCE, LOW_EVIDENCE -> "ATTRIBUTED";
            case AMBIGUOUS_ATTRIBUTION -> "AMBIGUOUS_ATTRIBUTION";
            case INSUFFICIENT_EVIDENCE -> "INSUFFICIENT_EVIDENCE";
            case JVM_GC_SUSPECTED -> "JVM_GC_SUSPECTED";
            case NATIVE_OR_DRIVER_STALL_POSSIBLE -> "NATIVE_OR_DRIVER_STALL_POSSIBLE";
            case UNKNOWN -> "UNKNOWN";
        };
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                "freeze-test", Path.of("freeze-test.json"), 1_754_943_736_000L,
                621.2, 120.0, samples, evidence, raw,
                attributed ? suspectName : "", attributed,
                "2026-08-11 21:42:16", "Overworld", "-22, 60, -55");
        IncidentDetailViewModel detail = new IncidentDetailViewModel(
                summary,
                suspects,
                List.of(
                        new BlackBoxPoint(1L, 16.0, 62.5, 512L * 1024L * 1024L),
                        new BlackBoxPoint(2L, 621.2, 1.6, 520L * 1024L * 1024L)),
                2,
                false,
                "minecraft:overworld",
                -22,
                60,
                -55);
        ModpackChangesViewModel changes = new ModpackChangesViewModel(true, mods.size(), List.of(
                new ModpackChangesViewModel.Change(ModpackChangesViewModel.Type.UPDATED,
                        "jei", "Just Enough Items", "19.39.0.372", "19.44.0.399")));
        return new SupportReportData(
                "0.6.0-alpha.1",
                "1.21.1",
                "21.1.235",
                detail,
                mods,
                changes,
                DetectiveSettings.defaults(),
                new SupportReportData.Environment(
                        "Windows 11", "amd64", "21.0.8", 2_147_483_648L,
                        700_000_000L, 16, "unavailable"));
    }

    static List<SupportReportData.InstalledMod> normalMods() {
        return List.of(
                new SupportReportData.InstalledMod("detective", "Detective", "0.6.0-alpha.1", "detective.jar"),
                new SupportReportData.InstalledMod("jei", "Just Enough Items", "19.44.0.399", "jei.jar"));
    }

    private static SuspectViewModel suspect(String id, String name, int leaf, int samples) {
        double share = leaf * 100.0 / samples;
        return new SuspectViewModel(id, name, "1.0", Math.max(leaf, 1), share,
                leaf, share, 2.0, 2, Math.max(0, leaf - 1), 0, 1);
    }
}
