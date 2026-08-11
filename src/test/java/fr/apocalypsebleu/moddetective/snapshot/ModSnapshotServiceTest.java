package fr.apocalypsebleu.moddetective.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModSnapshotServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsLegacyAndFutureSchemasWithoutCrashing() throws IOException {
        Path legacy = write("legacy.json", snapshotJson("", "alpha", "Alpha", "1.0"));
        Path future = write("future.json", snapshotJson("\"schemaVersion\":999,", "beta", "Beta", "2.0"));

        assertEquals(ModSnapshot.SCHEMA_VERSION, ModSnapshotService.readPrevious(legacy).schemaVersion());
        assertEquals("alpha", ModSnapshotService.readPrevious(legacy).mods().getFirst().id());
        assertEquals(ModSnapshot.SCHEMA_VERSION, ModSnapshotService.readPrevious(future).schemaVersion());
    }

    @Test
    void treatsEmptyTruncatedAndInvalidSnapshotsAsUnavailable() throws IOException {
        assertNull(ModSnapshotService.readPrevious(write("empty.json", "")));
        assertNull(ModSnapshotService.readPrevious(write("truncated.json", "{\"mods\":[")));
        assertNull(ModSnapshotService.readPrevious(write("null.json", "null")));
    }

    @Test
    void rejectsDuplicateOrIncompleteModMetadataAsOneCorruptSnapshot() throws IOException {
        Path duplicate = write("duplicate.json", """
                {
                  "capturedAtEpochMs": 1,
                  "minecraftVersion": "1.21.1",
                  "javaVersion": "21",
                  "fingerprint": "x",
                  "mods": [
                    {"id":"same","name":"One","version":"1","fileName":"one.jar"},
                    {"id":"same","name":"Two","version":"2","fileName":"two.jar"}
                  ]
                }
                """);
        Path missingName = write("missing-name.json", snapshotJson("", "alpha", null, "1.0"));
        Path emptyVersion = write("empty-version.json", snapshotJson("", "alpha", "Alpha", ""));

        assertNull(ModSnapshotService.readPrevious(duplicate));
        assertNull(ModSnapshotService.readPrevious(missingName));
        assertNull(ModSnapshotService.readPrevious(emptyVersion));
    }

    private Path write(String name, String content) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static String snapshotJson(String schema, String id, String name, String version) {
        String jsonName = name == null ? "null" : "\"" + name + "\"";
        return """
                {
                  %s
                  "capturedAtEpochMs": 1,
                  "minecraftVersion": "1.21.1",
                  "javaVersion": "21",
                  "fingerprint": "x",
                  "mods": [{"id":"%s","name":%s,"version":"%s","fileName":"mod.jar"}]
                }
                """.formatted(schema, id, jsonName, version);
    }
}
