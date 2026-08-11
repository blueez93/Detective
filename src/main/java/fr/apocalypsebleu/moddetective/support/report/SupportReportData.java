package fr.apocalypsebleu.moddetective.support.report;

import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;

import java.util.List;
import java.util.Objects;

/** Explicit allow-list of everything a standard local support report may contain. */
public record SupportReportData(
        String detectiveVersion,
        String minecraftVersion,
        String neoForgeVersion,
        IncidentDetailViewModel selectedIncident,
        List<InstalledMod> installedMods,
        ModpackChangesViewModel modpackChanges,
        DetectiveSettings settings,
        Environment environment
) {
    public SupportReportData {
        detectiveVersion = ReportPrivacy.metadata(detectiveVersion);
        minecraftVersion = ReportPrivacy.metadata(minecraftVersion);
        neoForgeVersion = ReportPrivacy.metadata(neoForgeVersion);
        selectedIncident = Objects.requireNonNull(selectedIncident, "selectedIncident");
        installedMods = List.copyOf(Objects.requireNonNull(installedMods, "installedMods"));
        modpackChanges = Objects.requireNonNull(modpackChanges, "modpackChanges");
        settings = Objects.requireNonNull(settings, "settings");
        environment = Objects.requireNonNull(environment, "environment");
    }

    public static List<InstalledMod> installedMods(ModSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        return snapshot.mods().stream()
                .map(mod -> new InstalledMod(mod.id(), mod.name(), mod.version(), mod.fileName()))
                .toList();
    }

    public record InstalledMod(String modId, String name, String version, String sourceFile) {
        public InstalledMod {
            modId = ReportPrivacy.metadata(modId);
            name = ReportPrivacy.metadata(name);
            version = ReportPrivacy.metadata(version);
            sourceFile = ReportPrivacy.fileName(sourceFile);
        }
    }

    public record Environment(
            String os,
            String architecture,
            String javaVersion,
            long jvmMaximumMemoryBytes,
            long jvmUsedMemoryBytes,
            int logicalProcessors,
            String gpu
    ) {
        public Environment {
            os = ReportPrivacy.metadata(os);
            architecture = ReportPrivacy.metadata(architecture);
            javaVersion = ReportPrivacy.metadata(javaVersion);
            jvmMaximumMemoryBytes = Math.max(0L, jvmMaximumMemoryBytes);
            jvmUsedMemoryBytes = Math.max(0L, jvmUsedMemoryBytes);
            logicalProcessors = Math.max(0, logicalProcessors);
            gpu = gpu == null || gpu.isBlank() ? "unavailable" : ReportPrivacy.metadata(gpu);
        }

        public static Environment capture() {
            Runtime runtime = Runtime.getRuntime();
            return new Environment(
                    System.getProperty("os.name", "unknown"),
                    System.getProperty("os.arch", "unknown"),
                    System.getProperty("java.version", "unknown"),
                    runtime.maxMemory(),
                    runtime.totalMemory() - runtime.freeMemory(),
                    runtime.availableProcessors(),
                    "unavailable");
        }
    }
}
