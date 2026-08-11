package fr.apocalypsebleu.moddetective.client.ui.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class DetectiveUiRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void oneCorruptIncidentDoesNotHideValidHistory() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        writeIncident(incidents.resolve("freeze-valid.json"), 10L);
        Files.writeString(incidents.resolve("freeze-empty.json"), "");
        Files.writeString(incidents.resolve("freeze-truncated.json"), "{\"durationMs\":");
        Files.writeString(incidents.resolve("notes.txt"), "ignored");

        var index = new DetectiveUiRepository(incidents).loadIndex(0L);

        assertEquals(1, index.incidents().size());
        assertEquals(2, index.unreadableFiles());
    }

    @Test
    void indexesTenTwentyFiveFiftyAndOneHundredIncidentsWithinAReleaseGuardrail() throws IOException {
        for (int size : new int[]{10, 25, 50, 100}) {
            Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents-" + size));
            for (int index = 0; index < size; index++) {
                writeIncident(incidents.resolve("freeze-%03d.json".formatted(index)), index + 1L);
            }

            var model = assertTimeout(Duration.ofSeconds(5),
                    () -> new DetectiveUiRepository(incidents).loadIndex(0L));

            assertEquals(size, model.incidents().size());
            assertEquals(size, model.incidents().getFirst().detectedAtEpochMs());
            assertEquals(0, model.unreadableFiles());
        }
    }

    private static void writeIncident(Path file, long timestamp) throws IOException {
        Files.writeString(file, """
                {"schemaVersion":1,"detectedAtEpochMs":%d,"durationMs":150.0,
                 "thresholdMs":120.0,"watchdogSamples":4,
                 "attributionEvidence":{"state":"INSUFFICIENT_EVIDENCE"},
                 "suspects":[],"blackBox":[]}
                """.formatted(timestamp));
    }
}
