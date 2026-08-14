package fr.apocalypsebleu.moddetective.core.comparison;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison.EvidenceAvailability;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentComparisonLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void malformedOptionalEnhancedEvidenceFallsBackSafelyAndDoesNotRewriteHistory() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path first = incidents.resolve("freeze-first.json");
        Path second = incidents.resolve("freeze-second.json");
        writeLegacy(first, 1_000L, """
                ,"derivedEvidence":{
                  "schemaVersion":1,
                  "signatureFormat":"unsupported",
                  "representedSamples":6,
                  "classSignatures":[{"signature":"not-a-hash","observations":6}]
                }
                """);
        writeLegacy(second, 2_000L, "");
        byte[] firstBefore = Files.readAllBytes(first);
        byte[] secondBefore = Files.readAllBytes(second);

        IncidentComparison result = new IncidentComparisonLoader(incidents).compare(first, second);

        assertEquals(IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK,
                result.firstIncident().evidenceSource());
        assertEquals(IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK,
                result.secondIncident().evidenceSource());
        assertEquals(1.0, result.technicalSimilarity().score().orElseThrow(), 0.000_000_1);
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.frameSignatures().availability());
        assertEquals(EvidenceAvailability.NOT_CAPTURED, result.stackPathSignatures().availability());
        assertArrayEquals(firstBefore, Files.readAllBytes(first));
        assertArrayEquals(secondBefore, Files.readAllBytes(second));
    }

    @Test
    void missingOptionalFieldsRemainExplicitlyUnavailableAcrossRepeatedLoads() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path first = incidents.resolve("freeze-sparse-a.json");
        Path second = incidents.resolve("freeze-sparse-b.json");
        Files.writeString(first, "{\"schemaVersion\":1}");
        Files.writeString(second, """
                {
                  "schemaVersion":1,
                  "derivedEvidence":"malformed optional value",
                  "frame":{"dimension":17,"usedMemoryBytes":"invalid"}
                }
                """);
        IncidentComparisonLoader loader = new IncidentComparisonLoader(incidents);

        IncidentComparison firstLoad = loader.compare(first, second);
        IncidentComparison secondLoad = loader.compare(first, second);

        assertEquals(firstLoad, secondLoad);
        assertEquals(EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                firstLoad.technicalSimilarity().availability());
        assertTrue(firstLoad.technicalSimilarity().score().isEmpty());
        assertTrue(firstLoad.detectedAtEpochMs().delta().isEmpty());
        assertTrue(firstLoad.stallDurationMs().delta().isEmpty());
        assertTrue(firstLoad.capturedSampleCount().delta().isEmpty());
        assertTrue(firstLoad.context().usedMemoryBytes().delta().isEmpty());
        assertTrue(firstLoad.context().dimension().equal().isEmpty());
        assertTrue(firstLoad.context().dimension().secondValue().isEmpty());
        assertEquals(AttributionEvidence.State.UNKNOWN,
                firstLoad.firstIncident().attributionState());
        assertEquals(AttributionEvidence.State.UNKNOWN,
                firstLoad.secondIncident().attributionState());
    }

    @Test
    void refusesIncidentPathsOutsideTheConfiguredLocalHistory() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path local = incidents.resolve("freeze-local.json");
        Path outside = temporaryDirectory.resolve("freeze-outside.json");
        writeLegacy(local, 1_000L, "");
        writeLegacy(outside, 2_000L, "");

        IncidentComparisonLoader loader = new IncidentComparisonLoader(incidents);

        assertThrows(IOException.class, () -> loader.compare(local, outside));
    }

    private static void writeLegacy(Path target, long detectedAt, String optionalTail)
            throws IOException {
        Files.writeString(target, """
                {
                  "schemaVersion":1,
                  "detectedAtEpochMs":%d,
                  "durationMs":400.0,
                  "watchdogSamples":6,
                  "frame":{
                    "epochMs":%d,
                    "frameMs":400.0,
                    "usedMemoryBytes":256,
                    "maxMemoryBytes":1024,
                    "dimension":"minecraft:overworld",
                    "playerX":0,"playerY":64,"playerZ":0
                  },
                  "attributionEvidence":{"state":"ATTRIBUTED","stackSamples":6},
                  "suspects":[{
                    "modId":"alpha","modName":"Alpha","version":"1",
                    "presenceSamples":6,"leafOwnershipCount":6,"stackDiversity":1
                  }],
                  "hotClasses":[{"className":"example.shared.Work","hits":6}],
                  "blackBox":[]
                  %s
                }
                """.formatted(detectedAt, detectedAt, optionalTail));
    }
}
