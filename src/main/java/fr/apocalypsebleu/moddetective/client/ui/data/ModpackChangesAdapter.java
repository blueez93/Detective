package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;

import java.util.ArrayList;
import java.util.List;

public final class ModpackChangesAdapter {
    private ModpackChangesAdapter() {}

    public static ModpackChangesViewModel from(ModSnapshotDiff diff) {
        if (diff == null) {
            return new ModpackChangesViewModel(false, 0, List.of());
        }
        int currentCount = diff.current().mods().size();
        if (diff.previous() == null) {
            return new ModpackChangesViewModel(false, currentCount, List.of());
        }

        List<ModpackChangesViewModel.Change> changes = new ArrayList<>();
        diff.added().forEach(mod -> changes.add(new ModpackChangesViewModel.Change(
                ModpackChangesViewModel.Type.ADDED, mod.id(), mod.name(), "", mod.version())));
        diff.removed().forEach(mod -> changes.add(new ModpackChangesViewModel.Change(
                ModpackChangesViewModel.Type.REMOVED, mod.id(), mod.name(), mod.version(), "")));
        diff.updated().forEach(change -> changes.add(new ModpackChangesViewModel.Change(
                ModpackChangesViewModel.Type.UPDATED, change.id(), change.name(),
                change.oldVersion(), change.newVersion())));
        return new ModpackChangesViewModel(true, currentCount, changes);
    }
}
