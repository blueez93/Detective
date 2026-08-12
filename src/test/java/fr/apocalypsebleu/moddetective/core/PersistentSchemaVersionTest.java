package fr.apocalypsebleu.moddetective.core;

import com.google.gson.Gson;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentSchemaVersionTest {
    private static final Gson GSON = new Gson();

    @Test
    void newIncidentsCarryAnExplicitSchemaVersion() {
        FrameSample frame = new FrameSample(1L, 2L, 150.0, 6.6, 3L, 4L,
                "minecraft:overworld", 1, 2, 3);
        FreezeIncident incident = new FreezeIncident(
                1L, 150.0, 120.0, frame, 0,
                new AttributionEvidence(AttributionEvidence.State.INSUFFICIENT_EVIDENCE, 0, 0, 0, 0),
                List.of(), List.of(), List.of(frame));

        assertEquals(FreezeIncident.SCHEMA_VERSION, incident.schemaVersion());
        assertEquals(2, FreezeIncident.SCHEMA_VERSION);
        assertTrue(GSON.toJson(incident).contains("\"schemaVersion\":2"));
    }

    @Test
    void newSnapshotsCarryAnExplicitSchemaVersion() {
        ModSnapshot snapshot = new ModSnapshot(
                1L, "1.21.1", "21", "fingerprint",
                List.of(new ModSnapshot.LoadedMod("detective", "Detective", "0.6", "detective.jar")));

        assertEquals(ModSnapshot.SCHEMA_VERSION, snapshot.schemaVersion());
        assertTrue(GSON.toJson(snapshot).contains("\"schemaVersion\":1"));
    }
}
