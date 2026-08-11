package fr.apocalypsebleu.moddetective.support.report;

import com.google.gson.JsonParser;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportReportExporterTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T19:42:16Z"), ZoneOffset.ofHours(2));

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsVersionedManifestAndExpectedZipName() throws IOException {
        Path report = SupportReportExporter.export(
                SupportReportFixtures.data(EvidenceBadge.HIGH_EVIDENCE), temporaryDirectory, FIXED_CLOCK);

        assertEquals("detective-report-2026-08-11_21-42-16.zip", report.getFileName().toString());
        String manifest = entry(report, "detective-report/manifest.json");
        var json = JsonParser.parseString(manifest).getAsJsonObject();
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(1, json.get("reportFormat").getAsInt());
        assertEquals("0.5.0-alpha.1", json.get("detectiveVersion").getAsString());
        assertEquals(1, json.get("incidentCount").getAsInt());
    }

    @Test
    void highEvidenceReportIsReadableAndNeverClaimsCausality() throws IOException {
        Path report = export(EvidenceBadge.HIGH_EVIDENCE);
        String readme = entry(report, "detective-report/README.txt");
        String summary = entry(report, "detective-report/summary.txt");

        assertTrue(readme.contains("HIGH_EVIDENCE"));
        assertTrue(readme.contains("Create"));
        assertTrue(readme.contains("29 / 30 samples"));
        assertTrue(readme.contains("Never accuse"));
        assertTrue(summary.contains("This does not prove that Create is defective."));
        assertFalse(summary.toLowerCase(Locale.ROOT).contains("caused the freeze"));
    }

    @Test
    void exportsAmbiguousStateWithoutPrimarySuspect() throws IOException {
        Path report = export(EvidenceBadge.AMBIGUOUS_ATTRIBUTION);
        String summary = entry(report, "detective-report/summary.txt");

        assertTrue(summary.contains("AMBIGUOUS_ATTRIBUTION"));
        assertTrue(summary.contains("Primary suspect:\r\nNone assigned")
                || summary.contains("Primary suspect:\nNone assigned"));
    }

    @Test
    void exportsInsufficientEvidenceWithoutInventingASuspect() throws IOException {
        Path report = export(EvidenceBadge.INSUFFICIENT_EVIDENCE);
        String selected = entry(report, "detective-report/detective/selected-incident.json");

        assertTrue(selected.contains("INSUFFICIENT_EVIDENCE"));
        assertTrue(selected.contains("\"primarySuspect\": null"));
        assertTrue(selected.contains("\"suspects\": []"));
    }

    @Test
    void exportsSystemStallCautiouslyWithoutNamingAMod() throws IOException {
        Path report = export(EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE);
        String summary = entry(report, "detective-report/summary.txt");

        assertTrue(summary.contains("POSSIBLE_SYSTEM_STALL"));
        assertTrue(summary.contains("None assigned"));
        assertFalse(summary.contains("driver caused"));
    }

    @Test
    void exportsUnknownIncidentWithoutASuspect() throws IOException {
        Path report = export(EvidenceBadge.UNKNOWN);
        String selected = entry(report, "detective-report/detective/selected-incident.json");

        assertTrue(selected.contains("UNKNOWN"));
        assertTrue(selected.contains("\"primarySuspect\": null"));
    }

    @Test
    void handlesMissingSnapshotAndUnavailableChanges() throws IOException {
        SupportReportData base = SupportReportFixtures.data(EvidenceBadge.HIGH_EVIDENCE);
        SupportReportData withoutSnapshot = new SupportReportData(
                base.detectiveVersion(), base.minecraftVersion(), base.neoForgeVersion(),
                base.selectedIncident(), List.of(),
                new fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel(false, 0, List.of()),
                base.settings(), base.environment());
        Path report = SupportReportExporter.export(withoutSnapshot, temporaryDirectory, FIXED_CLOCK);

        assertTrue(entry(report, "detective-report/modpack/mods.json").contains("\"available\": false"));
        assertTrue(entry(report, "detective-report/modpack/changes.json").contains("\"available\": false"));
    }

    @Test
    void everyMachineReadableFileCarriesSchemaVersion() throws IOException {
        Path report = export(EvidenceBadge.HIGH_EVIDENCE);
        try (ZipFile zip = new ZipFile(report.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".json")) {
                    String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    assertEquals(1, JsonParser.parseString(json).getAsJsonObject()
                            .get("schemaVersion").getAsInt(), entry.getName());
                }
            }
        }
    }

    @Test
    void finalZipContainsNoSensitiveValuesOrMinecraftLog() throws IOException {
        String home = System.getProperty("user.home", "C:\\Users\\private");
        List<SupportReportData.InstalledMod> hostileMetadata = List.of(
                new SupportReportData.InstalledMod(
                        "accessToken=top-secret",
                        "username OS " + System.getProperty("user.name", "private"),
                        "session-id=123 192.168.1.44",
                        home + "\\mods\\550e8400-e29b-41d4-a716-446655440000.jar"));
        SupportReportData data = SupportReportFixtures.data(
                EvidenceBadge.HIGH_EVIDENCE, hostileMetadata,
                "hostname=private-host server-ip=10.0.0.2");
        Path report = SupportReportExporter.export(data, temporaryDirectory, FIXED_CLOCK);
        String all = allText(report).toLowerCase(Locale.ROOT);

        for (String forbidden : List.of(
                "accesstoken", "username", "user.home", "hostname", "192.168.1.44",
                "10.0.0.2", "session-id", "550e8400-e29b-41d4-a716-446655440000",
                home.toLowerCase(Locale.ROOT), "latest.log")) {
            assertFalse(all.contains(forbidden), () -> "Sensitive report content: " + forbidden);
        }
        assertFalse(names(report).stream().anyMatch(name -> name.endsWith("latest.log")));
    }

    @Test
    void reportUsesOnlyTheSmallAllowListedFileSet() throws IOException {
        Path report = export(EvidenceBadge.HIGH_EVIDENCE);

        assertEquals(List.of(
                "detective-report/README.txt",
                "detective-report/summary.txt",
                "detective-report/manifest.json",
                "detective-report/detective/version.json",
                "detective-report/detective/incidents.json",
                "detective-report/detective/selected-incident.json",
                "detective-report/detective/settings-summary.json",
                "detective-report/modpack/mods.json",
                "detective-report/modpack/changes.json",
                "detective-report/system/environment.json"), names(report));
        assertTrue(Files.size(report) < 500_000L);
    }

    private Path export(EvidenceBadge evidence) throws IOException {
        return SupportReportExporter.export(
                SupportReportFixtures.data(evidence), temporaryDirectory, FIXED_CLOCK);
    }

    private static String entry(Path report, String name) throws IOException {
        try (ZipFile zip = new ZipFile(report.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry(name);
            assertNotNull(entry, name);
            return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> names(Path report) throws IOException {
        try (ZipFile zip = new ZipFile(report.toFile(), StandardCharsets.UTF_8)) {
            return zip.stream().map(ZipEntry::getName).toList();
        }
    }

    private static String allText(Path report) throws IOException {
        StringBuilder result = new StringBuilder();
        try (ZipFile zip = new ZipFile(report.toFile(), StandardCharsets.UTF_8)) {
            for (ZipEntry entry : zip.stream().toList()) {
                result.append(entry.getName()).append('\n');
                result.append(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return result.toString();
    }
}
