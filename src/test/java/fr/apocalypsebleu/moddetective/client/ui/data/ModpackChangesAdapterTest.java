package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackChangesAdapterTest {
    @Test
    void mapsAddedRemovedAndUpdatedMods() {
        ModSnapshotDiff diff = ModSnapshotDiff.between(
                snapshot(List.of(mod("alpha", "1"), mod("beta", "1"))),
                snapshot(List.of(mod("beta", "2"), mod("charlie", "1"))));

        ModpackChangesViewModel model = ModpackChangesAdapter.from(diff);

        assertTrue(model.comparisonAvailable());
        assertEquals(2, model.currentModCount());
        assertEquals(List.of(
                        ModpackChangesViewModel.Type.ADDED,
                        ModpackChangesViewModel.Type.REMOVED,
                        ModpackChangesViewModel.Type.UPDATED),
                model.changes().stream().map(ModpackChangesViewModel.Change::type).toList());
    }

    @Test
    void reportsUnavailableComparisonOnFirstLaunch() {
        ModpackChangesViewModel model = ModpackChangesAdapter.from(
                ModSnapshotDiff.between(null, snapshot(List.of(mod("alpha", "1")))));

        assertFalse(model.comparisonAvailable());
        assertTrue(model.changes().isEmpty());
    }

    private static ModSnapshot snapshot(List<ModSnapshot.LoadedMod> mods) {
        return new ModSnapshot(1L, "1.21.1", "21", "fingerprint", mods);
    }

    private static ModSnapshot.LoadedMod mod(String id, String version) {
        return new ModSnapshot.LoadedMod(id, id, version, id + ".jar");
    }
}
