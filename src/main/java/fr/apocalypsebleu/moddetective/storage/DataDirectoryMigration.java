package fr.apocalypsebleu.moddetective.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class DataDirectoryMigration {
    private DataDirectoryMigration() {}

    public static Result migrate(Path legacyRoot, Path currentRoot) throws IOException {
        Path legacy = legacyRoot.toAbsolutePath().normalize();
        Path current = currentRoot.toAbsolutePath().normalize();
        if (legacy.equals(current) || !Files.isDirectory(legacy)) {
            return Result.NONE;
        }

        if (Files.notExists(current)) {
            try {
                Files.move(legacy, current, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(legacy, current);
            }
            return new Result(true, countRegularFiles(current), 0);
        }

        int moved = 0;
        int skipped = 0;
        try (var paths = Files.walk(legacy)) {
            for (Path source : paths.sorted(Comparator.comparingInt(Path::getNameCount)).toList()) {
                Path relative = legacy.relativize(source);
                if (relative.toString().isEmpty()) {
                    continue;
                }

                Path target = current.resolve(relative).normalize();
                if (!target.startsWith(current)) {
                    throw new IOException("Legacy data path escapes the Detective directory: " + source);
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.notExists(target)) {
                    Files.createDirectories(target.getParent());
                    Files.move(source, target);
                    moved++;
                } else {
                    skipped++;
                }
            }
        }
        return new Result(moved > 0 || skipped > 0, moved, skipped);
    }

    private static int countRegularFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return Math.toIntExact(paths.filter(Files::isRegularFile).count());
        }
    }

    public record Result(boolean changed, int movedFiles, int skippedFiles) {
        private static final Result NONE = new Result(false, 0, 0);
    }
}
