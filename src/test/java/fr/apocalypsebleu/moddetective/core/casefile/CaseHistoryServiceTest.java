package fr.apocalypsebleu.moddetective.core.casefile;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.DerivedIncidentEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.IncidentStore;
import fr.apocalypsebleu.moddetective.core.ModSourceResolver;
import fr.apocalypsebleu.moddetective.core.StackSnapshot;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;
import fr.apocalypsebleu.moddetective.support.IncidentHistoryRetention;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void legacyMixedAndMalformedEvidenceSurvivesRepeatedRestartsWithoutIncidentRewrite() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("compatibility/incidents"));
        Path caseIndex = temporaryDirectory.resolve("compatibility/cases/index.json");
        Path legacy = incidents.resolve("freeze-legacy.json");
        Path malformed = incidents.resolve("freeze-malformed-derived.json");
        writeLegacy(legacy, 1_000L, 400.0, 6,
                "example.compat.Work", "compat-owner", "");
        writeLegacy(malformed, 2_000L, 410.0, 6,
                "example.compat.Work", "compat-owner",
                ",\"derivedEvidence\":{\"schemaVersion\":1,\"signatureFormat\":\"malformed\","
                        + "\"representedSamples\":6,\"classSignatures\":[{\"signature\":\"raw text\","
                        + "\"observations\":6}]}");
        Path enhanced = writeEnhanced(
                incidents, 3_000L, 420.0, "example.compat.Work", "compat-owner");
        Map<Path, byte[]> originalBytes = new HashMap<>();
        for (Path incident : List.of(legacy, malformed, enhanced)) {
            originalBytes.put(incident, Files.readAllBytes(incident));
        }

        String stableId = null;
        for (int restart = 0; restart < 5; restart++) {
            CaseHistoryService.Result result = new CaseHistoryService(incidents, caseIndex).refresh();
            assertEquals(1, result.cases().size());
            assertEquals(3, result.cases().getFirst().occurrenceCount());
            if (stableId == null) {
                stableId = result.cases().getFirst().caseId();
            } else {
                assertEquals(stableId, result.cases().getFirst().caseId());
            }
            for (Map.Entry<Path, byte[]> original : originalBytes.entrySet()) {
                assertArrayEquals(original.getValue(), Files.readAllBytes(original.getKey()));
            }
        }

        IncidentHistoryLoader.LoadResult history = new IncidentHistoryLoader().load(incidents, 500);
        assertEquals(2, history.fingerprints().stream()
                .filter(value -> value.evidenceSource()
                        == IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK).count());
        assertEquals(1, history.fingerprints().stream()
                .filter(value -> value.evidenceSource()
                        == IncidentFingerprint.EvidenceSource.DERIVED_V1).count());
    }

    @Test
    void corruptThenMissingCaseIndexRecoversDeterministicallyWithoutTouchingIncidents() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("index-recovery/incidents"));
        Path caseIndex = temporaryDirectory.resolve("index-recovery/cases/index.json");
        List<Path> incidentFiles = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Path incident = incidents.resolve("freeze-" + index + ".json");
            writeLegacy(incident, index + 1L, 400.0 + index, 6,
                    "example.index.Work", "index-owner", "");
            incidentFiles.add(incident);
        }
        Map<Path, byte[]> originalBytes = new HashMap<>();
        for (Path incident : incidentFiles) {
            originalBytes.put(incident, Files.readAllBytes(incident));
        }
        Files.createDirectories(caseIndex.getParent());
        byte[] corruptIndex = "{\"schemaVersion\":1,\"cases\":[".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(caseIndex, corruptIndex);

        CaseHistoryService.Result corrupt = new CaseHistoryService(incidents, caseIndex).refresh();

        assertEquals(1, corrupt.cases().size());
        assertEquals(1, corrupt.unreadablePersistedCases());
        assertFalse(corrupt.caseIndexWritten());
        assertArrayEquals(corruptIndex, Files.readAllBytes(caseIndex));
        Files.delete(caseIndex);

        CaseHistoryService.Result recovered = new CaseHistoryService(incidents, caseIndex).refresh();
        CaseHistoryService.Result restarted = new CaseHistoryService(incidents, caseIndex).refresh();

        assertTrue(recovered.caseIndexWritten());
        assertEquals(recovered.cases(), restarted.cases());
        assertEquals(corrupt.cases().getFirst().caseId(), recovered.cases().getFirst().caseId());
        for (Map.Entry<Path, byte[]> original : originalBytes.entrySet()) {
            assertArrayEquals(original.getValue(), Files.readAllBytes(original.getKey()));
        }
    }

    @Test
    void retentionUpdatesMembershipThenRemovesAndLaterReformsTheCaseWithoutStaleIndex() throws IOException {
        Path incidents = Files.createDirectories(temporaryDirectory.resolve("lifecycle/incidents"));
        Path caseIndex = temporaryDirectory.resolve("lifecycle/cases/index.json");
        long base = Instant.parse("2026-08-01T12:00:00Z").toEpochMilli();
        for (int index = 0; index < 5; index++) {
            writeLegacy(incidents.resolve("freeze-original-" + index + ".json"),
                    base + index, 400.0 + index, 6,
                    "example.lifecycle.Work", "lifecycle-owner", "");
        }
        CaseHistoryService service = new CaseHistoryService(incidents, caseIndex);
        CaseFile original = service.refresh().cases().getFirst();
        assertEquals(5, original.occurrenceCount());

        Instant now = Instant.ofEpochMilli(base + 10_000L);
        DetectiveSettings settings = DetectiveSettings.defaults().withDataRetentionDays(365);
        IncidentHistoryRetention.apply(incidents, settings.withIncidentHistoryLimit(4), now);
        CaseFile fourMembers = new CaseHistoryService(incidents, caseIndex).refresh().cases().getFirst();
        assertEquals(original.caseId(), fourMembers.caseId());
        assertEquals(4, fourMembers.occurrenceCount());
        assertEquals(List.of(fourMembers), new CaseFileStore(caseIndex).load().cases());

        IncidentHistoryRetention.apply(incidents, settings.withIncidentHistoryLimit(3), now);
        CaseFile threeMembers = new CaseHistoryService(incidents, caseIndex).refresh().cases().getFirst();
        assertEquals(original.caseId(), threeMembers.caseId());
        assertEquals(3, threeMembers.occurrenceCount());

        IncidentHistoryRetention.apply(incidents, settings.withIncidentHistoryLimit(2), now);
        CaseHistoryService.Result disappeared = new CaseHistoryService(incidents, caseIndex).refresh();
        assertTrue(disappeared.cases().isEmpty());
        assertTrue(new CaseFileStore(caseIndex).load().cases().isEmpty());
        assertTrue(new CaseHistoryService(incidents, caseIndex).refresh().cases().isEmpty());

        IncidentHistoryRetention.clear(incidents);
        for (int index = 0; index < 3; index++) {
            writeLegacy(incidents.resolve("freeze-reformed-" + index + ".json"),
                    base + 20_000L + index, 450.0 + index, 6,
                    "example.lifecycle.Work", "lifecycle-owner", "");
        }
        CaseFile reformed = new CaseHistoryService(incidents, caseIndex).refresh().cases().getFirst();
        CaseFile restarted = new CaseHistoryService(incidents, caseIndex).refresh().cases().getFirst();

        assertNotEquals(original.caseId(), reformed.caseId());
        assertEquals(3, reformed.occurrenceCount());
        assertEquals(reformed, restarted);
        assertEquals(List.of(reformed), new CaseFileStore(caseIndex).load().cases());
    }

    @Test
    void benchmarksBoundedHistoryLoadingClusteringReconciliationAndPersistence() throws IOException {
        for (int size : List.of(50, 250, 500)) {
            Path root = temporaryDirectory.resolve("benchmark-" + size);
            Path incidents = Files.createDirectories(root.resolve("incidents"));
            Path caseIndex = root.resolve("cases/index.json");
            for (int index = 0; index < size; index++) {
                writeLegacy(incidents.resolve("freeze-%04d.json".formatted(index)),
                        index + 1L, 400.0 + index % 20, 6,
                        "example.benchmark.Work", "benchmark-owner", "");
            }
            IncidentHistoryLoader loader = new IncidentHistoryLoader();
            CaseClusterer clusterer = new CaseClusterer();
            CaseFileStore store = new CaseFileStore(caseIndex);
            runMeasuredPipeline(loader, clusterer, store, incidents, size, null);

            long[][] samples = new long[5][];
            for (int iteration = 0; iteration < samples.length; iteration++) {
                samples[iteration] = runMeasuredPipeline(
                        loader, clusterer, store, incidents, size, new long[5]);
            }
            double loadMs = nanosToMillis(median(samples, 0));
            double clusterMs = nanosToMillis(median(samples, 1));
            double reconcilePersistMs = nanosToMillis(median(samples, 2));
            double processingMs = nanosToMillis(median(samples, 3));
            double totalMs = nanosToMillis(median(samples, 4));
            System.out.printf(Locale.ROOT,
                    "CASE_HISTORY_BENCHMARK size=%d load=%.3fms cluster=%.3fms "
                            + "reconcilePersist=%.3fms processing=%.3fms total=%.3fms%n",
                    size, loadMs, clusterMs, reconcilePersistMs, processingMs, totalMs);
        }
    }

    private static long[] runMeasuredPipeline(
            IncidentHistoryLoader loader,
            CaseClusterer clusterer,
            CaseFileStore store,
            Path incidents,
            int expectedSize,
            long[] timings
    ) throws IOException {
        long totalStart = System.nanoTime();
        long phaseStart = totalStart;
        IncidentHistoryLoader.LoadResult history = loader.load(incidents, 500);
        long load = System.nanoTime() - phaseStart;
        phaseStart = System.nanoTime();
        List<CaseFile> computed = clusterer.cluster(history.fingerprints());
        long cluster = System.nanoTime() - phaseStart;
        phaseStart = System.nanoTime();
        List<CaseFile> resolved = CaseIdentityResolver.resolve(computed, store.load().cases());
        store.save(resolved);
        long reconcilePersist = System.nanoTime() - phaseStart;
        long total = System.nanoTime() - totalStart;
        assertEquals(expectedSize, history.fingerprints().size());
        assertEquals(1, resolved.size());
        assertEquals(expectedSize, resolved.getFirst().occurrenceCount());
        if (timings == null) {
            return null;
        }
        timings[0] = load;
        timings[1] = cluster;
        timings[2] = reconcilePersist;
        timings[3] = cluster + reconcilePersist;
        timings[4] = total;
        return timings;
    }

    private static long median(long[][] samples, int phase) {
        long[] values = new long[samples.length];
        for (int index = 0; index < samples.length; index++) {
            values[index] = samples[index][phase];
        }
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
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
