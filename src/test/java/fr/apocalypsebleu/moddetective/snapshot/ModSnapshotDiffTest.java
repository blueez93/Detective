package fr.apocalypsebleu.moddetective.snapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSnapshotDiffTest {
    @Test
    void detectsAddedRemovedAndUpdatedMods() {
        ModSnapshot previous = snapshot(List.of(mod("alpha", "1.0"), mod("beta", "1.0")));
        ModSnapshot current = snapshot(List.of(mod("beta", "2.0"), mod("charlie", "1.0")));

        ModSnapshotDiff diff = ModSnapshotDiff.between(previous, current);

        assertEquals(List.of("charlie"), diff.added().stream().map(ModSnapshot.LoadedMod::id).toList());
        assertEquals(List.of("alpha"), diff.removed().stream().map(ModSnapshot.LoadedMod::id).toList());
        assertEquals(1, diff.updated().size());
        assertEquals("beta", diff.updated().getFirst().id());
        assertEquals("1.0", diff.updated().getFirst().oldVersion());
        assertEquals("2.0", diff.updated().getFirst().newVersion());
        assertEquals(3, diff.totalChanges());
    }

    @Test
    void firstLaunchHasNoSyntheticChanges() {
        ModSnapshotDiff diff = ModSnapshotDiff.between(null, snapshot(List.of(mod("alpha", "1.0"))));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
        assertTrue(diff.updated().isEmpty());
    }

    private static ModSnapshot snapshot(List<ModSnapshot.LoadedMod> mods) {
        return new ModSnapshot(1L, "1.21.1", "21", "fingerprint", mods);
    }

    private static ModSnapshot.LoadedMod mod(String id, String version) {
        return new ModSnapshot.LoadedMod(id, id, version, id + ".jar");
    }
}
