package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentJsonAdapterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsACompleteIncidentToSummaryAndDetail() throws IOException {
        Path source = temporaryDirectory.resolve("freeze-complete.json");
        Files.writeString(source, """
                {
                  "detectedAtEpochMs": 1700000000000,
                  "durationMs": 612.5,
                  "thresholdMs": 120.0,
                  "watchdogSamples": 30,
                  "frame": {
                    "dimension": "minecraft:the_nether",
                    "playerX": 12,
                    "playerY": 70,
                    "playerZ": -9
                  },
                  "attributionEvidence": { "state": "ATTRIBUTED" },
                  "suspects": [{
                    "modId": "culprit",
                    "modName": "Culprit",
                    "version": "1.0",
                    "presenceSamples": 29,
                    "presenceSharePercent": 96.7,
                    "leafOwnershipCount": 29,
                    "leafOwnershipSharePercent": 96.7,
                    "averageFirstFrameDepth": 2.0,
                    "minimumFirstFrameDepth": 2,
                    "repeatedLeafOwnership": 28,
                    "callerOnlySamples": 0,
                    "stackDiversity": 1
                  }],
                  "blackBox": [
                    { "epochMs": 1, "frameMs": 16.0, "approximateFps": 62.5, "usedMemoryBytes": 100 },
                    { "epochMs": 2, "frameMs": 612.5, "approximateFps": 1.6, "usedMemoryBytes": 110 }
                  ]
                }
                """);

        var detail = IncidentJsonAdapter.readDetail(source);

        assertEquals(EvidenceBadge.HIGH_EVIDENCE, detail.summary().evidence());
        assertEquals("Culprit", detail.summary().primarySuspect());
        assertEquals("The Nether", detail.summary().dimension());
        assertEquals("12, 70, -9", detail.summary().coordinates());
        assertEquals(30, detail.summary().watchdogSamples());
        assertEquals(2, detail.originalBlackBoxSamples());
        assertEquals(2, detail.blackBox().size());
        assertEquals(29, detail.suspects().getFirst().leafOwnershipCount());
    }

    @Test
    void toleratesMissingOptionalFieldsWithoutInventingASuspect() throws IOException {
        Path source = temporaryDirectory.resolve("freeze-partial.json");
        Files.writeString(source, "{\"durationMs\":150.0,\"suspects\":[],\"blackBox\":[]}");

        var detail = IncidentJsonAdapter.readDetail(source);

        assertEquals(EvidenceBadge.UNKNOWN, detail.summary().evidence());
        assertTrue(detail.suspects().isEmpty());
        assertTrue(detail.blackBox().isEmpty());
        assertTrue(detail.blackBoxPartial());
        assertEquals("—", detail.summary().dimension());
        assertEquals("—", detail.summary().coordinates());
    }

    @Test
    void downsamplingKeepsThePeakInEveryBucket() {
        List<BlackBoxPoint> points = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            points.add(new BlackBoxPoint(index, index == 249 ? 1_200.0 : 16.0, 60.0, 1L));
        }

        List<BlackBoxPoint> downsampled = IncidentJsonAdapter.downsample(points, 100);

        assertEquals(100, downsampled.size());
        assertTrue(downsampled.stream().anyMatch(point -> point.frameMs() == 1_200.0));
    }
}
