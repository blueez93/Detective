package fr.apocalypsebleu.moddetective.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataDirectoryMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void movesTheWholeLegacyDirectoryWhenCurrentDoesNotExist() throws IOException {
        Path legacy = temporaryDirectory.resolve("moddetective");
        Path current = temporaryDirectory.resolve("detective");
        Files.createDirectories(legacy.resolve("snapshots"));
        Files.writeString(legacy.resolve("snapshots/last-session.json"), "legacy");

        DataDirectoryMigration.Result result = DataDirectoryMigration.migrate(legacy, current);

        assertTrue(result.changed());
        assertEquals(1, result.movedFiles());
        assertFalse(Files.exists(legacy));
        assertEquals("legacy", Files.readString(current.resolve("snapshots/last-session.json")));
    }

    @Test
    void mergesWithoutOverwritingConflictingFiles() throws IOException {
        Path legacy = temporaryDirectory.resolve("moddetective");
        Path current = temporaryDirectory.resolve("detective");
        Files.createDirectories(legacy.resolve("incidents"));
        Files.createDirectories(current.resolve("incidents"));
        Files.writeString(legacy.resolve("incidents/conflict.json"), "legacy");
        Files.writeString(current.resolve("incidents/conflict.json"), "current");
        Files.writeString(legacy.resolve("incidents/movable.json"), "move-me");

        DataDirectoryMigration.Result result = DataDirectoryMigration.migrate(legacy, current);

        assertTrue(result.changed());
        assertEquals(1, result.movedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals("current", Files.readString(current.resolve("incidents/conflict.json")));
        assertEquals("legacy", Files.readString(legacy.resolve("incidents/conflict.json")));
        assertEquals("move-me", Files.readString(current.resolve("incidents/movable.json")));
    }
}
