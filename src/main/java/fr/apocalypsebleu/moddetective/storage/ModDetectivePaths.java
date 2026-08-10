package fr.apocalypsebleu.moddetective.storage;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModDetectivePaths {
    private ModDetectivePaths() {}

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("moddetective").toAbsolutePath().normalize();
    }

    public static Path snapshots() {
        return root().resolve("snapshots");
    }

    public static Path incidents() {
        return root().resolve("incidents");
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(snapshots());
            Files.createDirectories(incidents());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Mod Detective data directories", e);
        }
    }
}
