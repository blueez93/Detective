package fr.apocalypsebleu.moddetective.client.support;

import net.minecraft.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Best-effort platform folder opening. A desktop integration failure must never affect Minecraft. */
public final class LocalFolderOpener {
    private LocalFolderOpener() {}

    public static Result open(Path folder) {
        return open(folder, file -> Util.getPlatform().openFile(file));
    }

    static Result open(Path folder, Consumer<File> opener) {
        Objects.requireNonNull(opener, "opener");
        if (folder == null) {
            return Result.MISSING;
        }
        Path normalized = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return Result.MISSING;
        }
        try {
            opener.accept(normalized.toFile());
            return Result.OPENED;
        } catch (RuntimeException | LinkageError ignored) {
            return Result.FAILED;
        }
    }

    public enum Result {
        OPENED,
        MISSING,
        FAILED
    }
}
