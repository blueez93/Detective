package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.List;
import java.util.Objects;

public record ModpackChangesViewModel(
        boolean comparisonAvailable,
        int currentModCount,
        List<Change> changes
) {
    public ModpackChangesViewModel {
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
    }

    public int totalChanges() {
        return changes.size();
    }

    public enum Type {
        ADDED,
        REMOVED,
        UPDATED
    }

    public record Change(Type type, String modId, String modName, String oldVersion, String newVersion) {
        public Change {
            type = Objects.requireNonNull(type, "type");
            modId = Objects.requireNonNullElse(modId, "unknown");
            modName = Objects.requireNonNullElse(modName, modId);
            oldVersion = Objects.requireNonNullElse(oldVersion, "");
            newVersion = Objects.requireNonNullElse(newVersion, "");
        }
    }
}
