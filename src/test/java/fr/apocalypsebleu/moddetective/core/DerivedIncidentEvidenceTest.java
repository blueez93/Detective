package fr.apocalypsebleu.moddetective.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedIncidentEvidenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void newIncidentPersistsCompactDerivedEvidenceAndRoundTripsIdentically() throws IOException {
        List<StackSnapshot> stacks = List.of(
                stack(1L, "example.alpha.Work/0x000001", "run"),
                stack(2L, "example.alpha.Work/0x000002", "run"),
                stack(3L, "example.alpha.Work/0x000003", "run"),
                stack(4L, "example.alpha.Work/0x000004", "run"));
        SuspectAnalyzer analyzer = new SuspectAnalyzer(className -> Optional.of(
                new ModSourceResolver.ResolvedMod("alpha", "Alpha", "1")));
        SuspectAnalyzer.Analysis analysis = analyzer.analyze(stacks);
        DerivedIncidentEvidence derived = DerivedIncidentEvidence.capture(stacks, analysis);
        FrameSample frame = new FrameSample(
                1_000L, 2_000L, 400.0, 2.5, 3L, 4L,
                "minecraft:overworld", 0, 64, 0);
        FreezeIncident incident = new FreezeIncident(
                1_000L, 400.0, 120.0, frame, stacks.size(),
                new AttributionEvidence(AttributionEvidence.State.ATTRIBUTED,
                        stacks.size(), stacks.size(), 0, 0),
                analysis.suspects(), analysis.hotClasses(), List.of(frame), derived);

        Path saved = IncidentStore.save(incident, temporaryDirectory.resolve("incidents"));
        FreezeIncident loaded = IncidentStore.read(saved);
        JsonObject root = JsonParser.parseString(Files.readString(saved)).getAsJsonObject();
        String persistedDerived = root.getAsJsonObject("derivedEvidence").toString();

        assertEquals(2, root.get("schemaVersion").getAsInt());
        assertEquals(derived, loaded.derivedEvidence());
        assertEquals(DerivedIncidentEvidence.SIGNATURE_FORMAT,
                root.getAsJsonObject("derivedEvidence").get("signatureFormat").getAsString());
        assertTrue(derived.usable());
        assertFalse(derived.classSignatures().isEmpty());
        assertFalse(derived.frameSignatures().isEmpty());
        assertFalse(derived.stackPathSignatures().isEmpty());
        assertEquals(List.of("alpha"),
                derived.ownerObservations().stream()
                        .map(DerivedIncidentEvidence.OwnerObservation::ownerId).toList());
        assertFalse(persistedDerived.contains("example.alpha.Work"));
        assertFalse(persistedDerived.contains("Work.java"));
        assertFalse(persistedDerived.contains("/0x"));
        assertTrue(derived.classSignatures().stream()
                .allMatch(value -> value.signature().matches("[0-9a-f]{32}")));
        assertNotNull(loaded.derivedEvidence());
    }

    @Test
    void hiddenClassRuntimeSuffixesDoNotChangeTheDerivedSignature() {
        SuspectAnalyzer.Analysis emptyAnalysis = new SuspectAnalyzer.Analysis(1, List.of(), List.of());

        DerivedIncidentEvidence first = DerivedIncidentEvidence.capture(
                List.of(stack(1L, "example.Work/0x111", "run")), emptyAnalysis);
        DerivedIncidentEvidence second = DerivedIncidentEvidence.capture(
                List.of(stack(2L, "example.Work/0x999", "run")), emptyAnalysis);

        assertEquals(first.classSignatures(), second.classSignatures());
        assertEquals(first.frameSignatures(), second.frameSignatures());
        assertEquals(first.stackPathSignatures(), second.stackPathSignatures());
    }

    @Test
    void fullSchemaV1IncidentLoadsAsCurrentWithoutRewritingTheLegacyFile() throws IOException {
        FrameSample frame = new FrameSample(
                1_000L, 2_000L, 400.0, 2.5, 3L, 4L,
                "minecraft:overworld", 0, 64, 0);
        FreezeIncident currentShapeWithoutDerived = new FreezeIncident(
                1_000L, 400.0, 120.0, frame, 6,
                new AttributionEvidence(AttributionEvidence.State.ATTRIBUTED, 6, 6, 0, 0),
                List.of(new SuspectAnalyzer.Suspect(
                        "legacy-owner", "Legacy Owner", "1", 6, 100.0,
                        6, 100.0, 0.0, 0, 6, 0, 1)),
                List.of(new SuspectAnalyzer.HotClass("example.legacy.Work", 6)),
                List.of(frame));
        String legacyJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(currentShapeWithoutDerived)
                .replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
                .replace(",\n  \"derivedEvidence\": null", "");
        Path source = temporaryDirectory.resolve("freeze-schema-v1.json");
        Files.writeString(source, legacyJson);
        byte[] original = Files.readAllBytes(source);

        FreezeIncident loaded = IncidentStore.read(source);

        assertEquals(FreezeIncident.SCHEMA_VERSION, loaded.schemaVersion());
        assertNull(loaded.derivedEvidence());
        assertEquals("legacy-owner", loaded.suspects().getFirst().modId());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    private static StackSnapshot stack(long nanoTime, String className, String methodName) {
        return new StackSnapshot(nanoTime, new StackTraceElement[]{
                new StackTraceElement(className, methodName, "Work.java", 42)
        });
    }
}
