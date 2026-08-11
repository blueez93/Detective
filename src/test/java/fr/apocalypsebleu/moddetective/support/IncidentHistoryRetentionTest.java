package fr.apocalypsebleu.moddetective.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentHistoryRetentionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsOnlyTheNewestIncidentsUpToTheConfiguredMaximum() throws IOException {
        Path incidents = temporaryDirectory.resolve("detective/incidents");
        write(incidents, "freeze-a.json", 1_000L);
        write(incidents, "freeze-b.json", 2_000L);
        write(incidents, "freeze-c.json", 3_000L);
        DetectiveSettings settings = DetectiveSettings.defaults()
                .withIncidentHistoryLimit(2)
                .withDataRetentionDays(365);

        IncidentHistoryRetention.Result result = IncidentHistoryRetention.apply(
                incidents, settings, Instant.ofEpochMilli(4_000L));

        assertEquals(1, result.deleted());
        assertFalse(Files.exists(incidents.resolve("freeze-a.json")));
        assertTrue(Files.exists(incidents.resolve("freeze-b.json")));
        assertTrue(Files.exists(incidents.resolve("freeze-c.json")));
    }

    @Test
    void removesIncidentsOlderThanTheConfiguredAgeEvenBelowTheCountLimit() throws IOException {
        Path incidents = temporaryDirectory.resolve("detective/incidents");
        long now = Instant.parse("2026-08-11T20:00:00Z").toEpochMilli();
        write(incidents, "freeze-old.json", now - java.time.Duration.ofDays(31).toMillis());
        write(incidents, "freeze-recent.json", now - java.time.Duration.ofDays(2).toMillis());

        IncidentHistoryRetention.Result result = IncidentHistoryRetention.apply(
                incidents, DetectiveSettings.defaults(), Instant.ofEpochMilli(now));

        assertEquals(1, result.deleted());
        assertFalse(Files.exists(incidents.resolve("freeze-old.json")));
        assertTrue(Files.exists(incidents.resolve("freeze-recent.json")));
    }

    @Test
    void clearDeletesOnlyDetectiveIncidentRecords() throws IOException {
        Path detective = temporaryDirectory.resolve("detective");
        Path incidents = detective.resolve("incidents");
        write(incidents, "freeze-one.json", 1L);
        Files.writeString(Files.createDirectories(incidents).resolve("notes.txt"), "keep");
        Files.writeString(Files.createDirectories(detective.resolve("snapshots"))
                .resolve("last-session.json"), "keep");

        IncidentHistoryRetention.Result result = IncidentHistoryRetention.clear(incidents);

        assertEquals(1, result.deleted());
        assertTrue(Files.exists(incidents.resolve("notes.txt")));
        assertTrue(Files.exists(detective.resolve("snapshots/last-session.json")));
    }

    private static void write(Path root, String name, long detectedAt) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(name), "{\"detectedAtEpochMs\":" + detectedAt + "}");
    }
}
