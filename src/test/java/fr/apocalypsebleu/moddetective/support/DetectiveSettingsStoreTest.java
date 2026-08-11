package fr.apocalypsebleu.moddetective.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectiveSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsEveryV05SettingWithSchemaVersion() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        DetectiveSettingsStore store = new DetectiveSettingsStore(target);
        DetectiveSettings expected = new DetectiveSettings(1, false, 100, 90, true);

        store.save(expected);
        DetectiveSettings loaded = store.load();

        assertEquals(expected, loaded);
        assertTrue(Files.readString(target).contains("\"schemaVersion\": 1"));
    }

    @Test
    void recoversIndividualFieldsFromPartiallyCorruptSettings() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        Files.writeString(target, """
                {
                  "schemaVersion": 999,
                  "incidentNotifications": false,
                  "incidentHistoryLimit": "broken",
                  "dataRetentionDays": 7,
                  "showTechnicalEvidenceByDefault": {"bad":true},
                  "futureField": "ignored"
                }
                """);

        DetectiveSettings loaded = new DetectiveSettingsStore(target).load();

        assertFalse(loaded.incidentNotifications());
        assertEquals(DetectiveSettings.DEFAULT_HISTORY_LIMIT, loaded.incidentHistoryLimit());
        assertEquals(7, loaded.dataRetentionDays());
        assertFalse(loaded.showTechnicalEvidenceByDefault());
        assertEquals(DetectiveSettings.SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void fallsBackToDefaultsForMalformedJson() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        Files.writeString(target, "{not-json");

        assertEquals(DetectiveSettings.defaults(), new DetectiveSettingsStore(target).load());
    }

    @Test
    void emptyAndTruncatedFilesFallBackToSafeDefaults() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        DetectiveSettingsStore store = new DetectiveSettingsStore(target);

        Files.writeString(target, "");
        assertEquals(DetectiveSettings.defaults(), store.load());
        Files.writeString(target, "{\"incidentNotifications\":false");
        assertEquals(DetectiveSettings.defaults(), store.load());
    }

    @Test
    void clampsNegativeAndHugeNumericValues() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        Files.writeString(target, """
                {
                  "incidentHistoryLimit": -500,
                  "dataRetentionDays": 2147483647
                }
                """);

        DetectiveSettings loaded = new DetectiveSettingsStore(target).load();

        assertEquals(DetectiveSettings.MINIMUM_HISTORY_LIMIT, loaded.incidentHistoryLimit());
        assertEquals(DetectiveSettings.MAXIMUM_RETENTION_DAYS, loaded.dataRetentionDays());
    }

    @Test
    void normalizesOldAndFutureSchemasAndIgnoresUnknownFields() throws IOException {
        Path target = temporaryDirectory.resolve("settings.json");
        DetectiveSettingsStore store = new DetectiveSettingsStore(target);
        Files.writeString(target, """
                {"schemaVersion":0,"incidentNotifications":false,"futureField":"ignored"}
                """);
        assertFalse(store.load().incidentNotifications());
        assertEquals(DetectiveSettings.SCHEMA_VERSION, store.load().schemaVersion());

        Files.writeString(target, """
                {"schemaVersion":999,"incidentHistoryLimit":100,"dataRetentionDays":90}
                """);
        assertEquals(100, store.load().incidentHistoryLimit());
        assertEquals(90, store.load().dataRetentionDays());
        assertEquals(DetectiveSettings.SCHEMA_VERSION, store.load().schemaVersion());
    }
}
