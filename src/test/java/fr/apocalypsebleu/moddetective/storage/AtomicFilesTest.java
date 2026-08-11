package fr.apocalypsebleu.moddetective.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesPersistentUtf8ContentWithoutLeavingTemporaryFiles() throws IOException {
        Path target = temporaryDirectory.resolve("detective/settings.json");

        AtomicFiles.writeUtf8(target, "first");
        AtomicFiles.writeUtf8(target, "deuxième");

        assertEquals("deuxième", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void staleCrashTemporaryDoesNotReplaceTheLastCommittedFile() throws IOException {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("detective"));
        Path target = directory.resolve("settings.json");
        AtomicFiles.writeUtf8(target, "committed");
        Files.writeString(directory.resolve(".detective-stale.tmp"), "partial");

        assertEquals("committed", Files.readString(target));
    }
}
