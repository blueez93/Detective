package fr.apocalypsebleu.moddetective.core.casefile;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.DerivedIncidentEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.IncidentStore;
import fr.apocalypsebleu.moddetective.core.ModSourceResolver;
import fr.apocalypsebleu.moddetective.core.StackSnapshot;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseHistoryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyV07IncidentWithoutEnhancedEvidenceLoadsAndFingerprintsSafely() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        writeLegacy(incidents.resolve("freeze-legacy.json"),
                1_000L, 400.0, 6, "example.legacy.Work", "legacy-owner", "");

        IncidentHistoryLoader.LoadResult loaded = new IncidentHistoryLoader().load(incidents, 500);

        assertEquals(1, loaded.fingerprints().size());
        IncidentFingerprint fingerprint = loaded.fingerprints().getFirst();
        assertEquals(IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK, fingerprint.evidenceSource());
        assertFalse(fingerprint.classFingerprints().isEmpty());
        assertTrue(fingerprint.frameFingerprints().isEmpty());
        assertTrue(fingerprint.stackPathFingerprints().isEmpty());
        assertTrue(fingerprint.hasSufficientTechnicalEvidence(3));
        assertEquals(0, loaded.unreadableFiles());
    }

    @Test
    void identicalHistoryReloadKeepsCaseIdsMembershipAndPersistedBytesStable() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path caseIndex = temporaryDirectory.resolve("cases/index.json");
        writeLegacy(incidents.resolve("freeze-a.json"), 1L, 400.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-b.json"), 2L, 410.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-c.json"), 3L, 420.0, 6,
                "example.recurring.Work", "alpha", "");
        CaseHistoryService firstService = new CaseHistoryService(incidents, caseIndex);

        CaseHistoryService.Result first = firstService.refresh();
        String firstBytes = Files.readString(caseIndex);
        CaseHistoryService.Result second = new CaseHistoryService(incidents, caseIndex).refresh();
        String secondBytes = Files.readString(caseIndex);

        assertEquals(first.cases(), second.cases());
        assertEquals(firstBytes, secondBytes);
        assertEquals(1, first.cases().size());
        assertTrue(first.caseIndexWritten());
        assertEquals(3, first.loadedIncidents());
    }

    @Test
    void unrelatedIncidentDoesNotMutateAnExistingCase() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path caseIndex = temporaryDirectory.resolve("cases/index.json");
        writeLegacy(incidents.resolve("freeze-a.json"), 1L, 400.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-b.json"), 2L, 410.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-c.json"), 3L, 420.0, 6,
                "example.recurring.Work", "alpha", "");
        CaseHistoryService service = new CaseHistoryService(incidents, caseIndex);
        CaseFile before = service.refresh().cases().getFirst();

        writeLegacy(incidents.resolve("freeze-unrelated.json"), 4L, 900.0, 6,
                "example.unrelated.Work", "other", "");
        CaseFile after = service.refresh().cases().getFirst();

        assertEquals(before, after);
        assertEquals(4, new IncidentHistoryLoader().load(incidents, 500).fingerprints().size());
    }

    @Test
    void sparseLegacyHistoryCannotManufactureACase() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        for (int index = 0; index < 3; index++) {
            writeLegacy(incidents.resolve("freeze-sparse-" + index + ".json"),
                    index + 1L, 400.0, 2, "example.Sparse", "alpha", "");
        }

        CaseHistoryService.Result result = new CaseHistoryService(
                incidents, temporaryDirectory.resolve("cases/index.json")).refresh();

        assertTrue(result.cases().isEmpty());
        assertEquals(3, result.loadedIncidents());
    }

    @Test
    void mixedLegacyAndEnhancedIncidentsClusterThroughSharedClassEvidence() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        writeLegacy(incidents.resolve("freeze-legacy-a.json"), 1L, 400.0, 6,
                "example.shared.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-legacy-b.json"), 2L, 410.0, 6,
                "example.shared.Work", "alpha", "");
        writeEnhanced(incidents, 3L, 420.0, "example.shared.Work", "alpha");

        CaseHistoryService.Result result = new CaseHistoryService(
                incidents, temporaryDirectory.resolve("cases/index.json")).refresh();

        assertEquals(1, result.cases().size());
        assertEquals(3, result.cases().getFirst().occurrenceCount());
        IncidentHistoryLoader.LoadResult history = new IncidentHistoryLoader().load(incidents, 500);
        assertEquals(2, history.fingerprints().stream()
                .filter(value -> value.evidenceSource()
                        == IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK).count());
        assertEquals(1, history.fingerprints().stream()
                .filter(value -> value.evidenceSource()
                        == IncidentFingerprint.EvidenceSource.DERIVED_V1).count());
    }

    @Test
    void corruptOrMissingOptionalEnhancedEvidenceFallsBackWithoutFabrication() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        writeLegacy(incidents.resolve("freeze-missing.json"), 1L, 400.0, 6,
                "example.safe.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-corrupt.json"), 2L, 400.0, 6,
                "example.safe.Work", "alpha",
                ",\"derivedEvidence\":{\"schemaVersion\":1,\"signatureFormat\":\"bad\","
                        + "\"representedSamples\":6,\"classSignatures\":[{\"signature\":\"raw\","
                        + "\"observations\":6}]} ");

        IncidentHistoryLoader.LoadResult loaded = new IncidentHistoryLoader().load(incidents, 500);

        assertEquals(2, loaded.fingerprints().size());
        assertTrue(loaded.fingerprints().stream().allMatch(value ->
                value.evidenceSource() == IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK));
        assertTrue(loaded.fingerprints().stream().allMatch(value -> value.frameFingerprints().isEmpty()));
        assertEquals(0, loaded.unreadableFiles());
    }

    @Test
    void unsupportedCaseIndexIsNotOverwritten() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path caseIndex = temporaryDirectory.resolve("cases/index.json");
        Files.createDirectories(caseIndex.getParent());
        Files.writeString(caseIndex, "{\"schemaVersion\":999,\"future\":true}");
        writeLegacy(incidents.resolve("freeze-a.json"), 1L, 400.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-b.json"), 2L, 400.0, 6,
                "example.recurring.Work", "alpha", "");
        writeLegacy(incidents.resolve("freeze-c.json"), 3L, 400.0, 6,
                "example.recurring.Work", "alpha", "");

        CaseHistoryService.Result result = new CaseHistoryService(incidents, caseIndex).refresh();

        assertFalse(result.caseIndexWritten());
        assertEquals("{\"schemaVersion\":999,\"future\":true}", Files.readString(caseIndex));
        assertEquals(1, result.cases().size());
    }

    @Test
    void historyLoadingAppliesNewestBoundDeterministically() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        for (int index = 0; index < 6; index++) {
            writeLegacy(incidents.resolve("freeze-" + index + ".json"), index + 1L,
                    400.0, 6, "example.Work", "alpha", "");
        }

        IncidentHistoryLoader.LoadResult loaded = new IncidentHistoryLoader().load(incidents, 3);

        assertEquals(List.of("freeze-3.json", "freeze-4.json", "freeze-5.json"),
                loaded.fingerprints().stream().map(IncidentFingerprint::incidentId).toList());
        assertEquals(3, loaded.ignoredByBound());
    }

    private static Path writeEnhanced(
            Path incidents,
            long detectedAt,
            double duration,
            String className,
            String ownerId
    ) throws IOException {
        List<StackSnapshot> stacks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            stacks.add(new StackSnapshot(index, new StackTraceElement[]{
                    new StackTraceElement(className, "run", "Work.java", 10)
            }));
        }
        SuspectAnalyzer analyzer = new SuspectAnalyzer(ignored -> Optional.of(
                new ModSourceResolver.ResolvedMod(ownerId, ownerId, "1")));
        SuspectAnalyzer.Analysis analysis = analyzer.analyze(stacks);
        DerivedIncidentEvidence derived = DerivedIncidentEvidence.capture(stacks, analysis);
        FrameSample frame = new FrameSample(
                detectedAt, detectedAt, duration, 1_000.0 / duration,
                1L, 2L, "minecraft:overworld", 0, 64, 0);
        FreezeIncident incident = new FreezeIncident(
                detectedAt, duration, 120.0, frame, stacks.size(),
                new AttributionEvidence(AttributionEvidence.State.ATTRIBUTED,
                        stacks.size(), stacks.size(), 0, 0),
                analysis.suspects(), analysis.hotClasses(), List.of(frame), derived);
        return IncidentStore.save(incident, incidents);
    }

    private static void writeLegacy(
            Path target,
            long detectedAt,
            double duration,
            int samples,
            String className,
            String ownerId,
            String optionalTail
    ) throws IOException {
        Files.writeString(target, """
                {
                  "schemaVersion":1,
                  "detectedAtEpochMs":%d,
                  "durationMs":%s,
                  "watchdogSamples":%d,
                  "attributionEvidence":{"state":"ATTRIBUTED","stackSamples":%d},
                  "suspects":[{
                    "modId":"%s","modName":"%s","version":"1",
                    "presenceSamples":%d,"leafOwnershipCount":%d,"stackDiversity":1
                  }],
                  "hotClasses":[{"className":"%s","hits":%d}],
                  "blackBox":[]
                  %s
                }
                """.formatted(
                detectedAt, Double.toString(duration), samples, samples,
                ownerId, ownerId, samples, samples, className, samples, optionalTail));
    }
}
