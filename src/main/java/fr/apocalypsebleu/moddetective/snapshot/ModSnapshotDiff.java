package fr.apocalypsebleu.moddetective.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ModSnapshotDiff(
        ModSnapshot previous,
        ModSnapshot current,
        List<ModSnapshot.LoadedMod> added,
        List<ModSnapshot.LoadedMod> removed,
        List<VersionChange> updated
) {
    public ModSnapshotDiff {
        current = Objects.requireNonNull(current, "current");
        added = List.copyOf(Objects.requireNonNull(added, "added"));
        removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        updated = List.copyOf(Objects.requireNonNull(updated, "updated"));
    }

    public int totalChanges() {
        return added.size() + removed.size() + updated.size();
    }

    public static ModSnapshotDiff between(ModSnapshot previous, ModSnapshot current) {
        Objects.requireNonNull(current, "current");
        if (previous == null) {
            return new ModSnapshotDiff(null, current, List.of(), List.of(), List.of());
        }

        Map<String, ModSnapshot.LoadedMod> before = index(previous.mods());
        Map<String, ModSnapshot.LoadedMod> after = index(current.mods());
        List<ModSnapshot.LoadedMod> added = new ArrayList<>();
        List<ModSnapshot.LoadedMod> removed = new ArrayList<>();
        List<VersionChange> updated = new ArrayList<>();

        after.forEach((id, mod) -> {
            ModSnapshot.LoadedMod old = before.get(id);
            if (old == null) {
                added.add(mod);
            } else if (!old.version().equals(mod.version())) {
                updated.add(new VersionChange(id, mod.name(), old.version(), mod.version()));
            }
        });

        before.forEach((id, mod) -> {
            if (!after.containsKey(id)) {
                removed.add(mod);
            }
        });

        added.sort(Comparator.comparing(ModSnapshot.LoadedMod::id));
        removed.sort(Comparator.comparing(ModSnapshot.LoadedMod::id));
        updated.sort(Comparator.comparing(VersionChange::id));
        return new ModSnapshotDiff(previous, current, added, removed, updated);
    }

    private static Map<String, ModSnapshot.LoadedMod> index(List<ModSnapshot.LoadedMod> mods) {
        Map<String, ModSnapshot.LoadedMod> result = new HashMap<>();
        for (ModSnapshot.LoadedMod mod : mods) {
            if (result.put(mod.id(), mod) != null) {
                throw new IllegalArgumentException("Duplicate mod id in snapshot: " + mod.id());
            }
        }
        return result;
    }

    public record VersionChange(String id, String name, String oldVersion, String newVersion) {
        public VersionChange {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name");
            oldVersion = Objects.requireNonNull(oldVersion, "oldVersion");
            newVersion = Objects.requireNonNull(newVersion, "newVersion");
        }
    }
}
