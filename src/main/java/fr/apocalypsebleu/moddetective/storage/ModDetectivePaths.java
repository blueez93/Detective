package fr.apocalypsebleu.moddetective.storage;

import fr.apocalypsebleu.moddetective.ModDetective;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModDetectivePaths {
    private ModDetectivePaths() {}

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("detective").toAbsolutePath().normalize();
    }

    public static Path snapshots() {
        return root().resolve("snapshots");
    }

    public static Path incidents() {
        return root().resolve("incidents");
    }

    public static synchronized void ensureDirectories() {
        try {
            Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            DataDirectoryMigration.Result migration = DataDirectoryMigration.migrate(
                    gameDirectory.resolve("moddetective"), root());
            if (migration.changed()) {
                ModDetective.LOGGER.info("[Detective] Migrated legacy data directory: {} file(s) moved, {} conflict(s) retained",
                        migration.movedFiles(), migration.skippedFiles());
            }
            Files.createDirectories(snapshots());
            Files.createDirectories(incidents());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Detective data directories", e);
        }
    }
}
