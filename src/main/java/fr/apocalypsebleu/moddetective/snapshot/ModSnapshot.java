package fr.apocalypsebleu.moddetective.snapshot;

import java.util.List;
import java.util.Objects;

public record ModSnapshot(
        long capturedAtEpochMs,
        String minecraftVersion,
        String javaVersion,
        String fingerprint,
        List<LoadedMod> mods
) {
    public ModSnapshot {
        minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        javaVersion = Objects.requireNonNull(javaVersion, "javaVersion");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        mods = List.copyOf(Objects.requireNonNull(mods, "mods"));
    }

    public record LoadedMod(String id, String name, String version, String fileName) {
        public LoadedMod {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name");
            version = Objects.requireNonNull(version, "version");
            fileName = Objects.requireNonNull(fileName, "fileName");
        }
    }
}
