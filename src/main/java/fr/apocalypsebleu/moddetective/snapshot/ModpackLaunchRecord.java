package fr.apocalypsebleu.moddetective.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.HashSet;
import java.util.Set;

/** Compact, privacy-conscious record of one observed modpack launch boundary. */
public record ModpackLaunchRecord(
        long launchAtEpochMs,
        OptionalLong previousLaunchAtEpochMs,
        List<ModChange> changes
) {
    public static final int MAXIMUM_CHANGES_PER_LAUNCH = 4_096;

    public ModpackLaunchRecord {
        Objects.requireNonNull(previousLaunchAtEpochMs, "previousLaunchAtEpochMs");
        if (Objects.requireNonNull(changes, "changes").size()
                > MAXIMUM_CHANGES_PER_LAUNCH) {
            throw new IllegalArgumentException("Launch change list exceeds its safety bound");
        }
        changes = Objects.requireNonNull(changes, "changes").stream()
                .peek(value -> Objects.requireNonNull(value, "change"))
                .sorted(ModChange.STABLE_ORDER)
                .toList();
        Set<String> changedModIds = new HashSet<>();
        for (ModChange change : changes) {
            if (!changedModIds.add(change.modId())) {
                throw new IllegalArgumentException("A launch cannot change one mod more than once");
            }
        }
    }

    public static ModpackLaunchRecord from(ModSnapshotDiff diff) {
        Objects.requireNonNull(diff, "diff");
        List<ModChange> changes = new ArrayList<>();
        for (ModSnapshot.LoadedMod mod : diff.added()) {
            changes.add(new ModChange(
                    ChangeType.ADDED,
                    mod.id(),
                    displayName(mod.name(), mod.id()),
                    Optional.empty(),
                    Optional.of(mod.version())));
        }
        for (ModSnapshotDiff.VersionChange change : diff.updated()) {
            changes.add(new ModChange(
                    ChangeType.UPDATED,
                    change.id(),
                    displayName(change.name(), change.id()),
                    Optional.of(change.oldVersion()),
                    Optional.of(change.newVersion())));
        }
        for (ModSnapshot.LoadedMod mod : diff.removed()) {
            changes.add(new ModChange(
                    ChangeType.REMOVED,
                    mod.id(),
                    displayName(mod.name(), mod.id()),
                    Optional.of(mod.version()),
                    Optional.empty()));
        }
        return new ModpackLaunchRecord(
                diff.current().capturedAtEpochMs(),
                diff.previous() == null
                        ? OptionalLong.empty()
                        : OptionalLong.of(diff.previous().capturedAtEpochMs()),
                changes);
    }

    /** Deterministic in-memory key used for ordering and duplicate suppression; it is not persisted. */
    public String stableKey() {
        StringBuilder source = new StringBuilder();
        append(source, Long.toString(launchAtEpochMs));
        append(source, previousLaunchAtEpochMs.isPresent()
                ? Long.toString(previousLaunchAtEpochMs.getAsLong()) : "");
        for (ModChange change : changes) {
            append(source, change.type().name());
            append(source, change.modId());
            append(source, change.modDisplayName());
            append(source, change.previousVersion().orElse(""));
            append(source, change.newVersion().orElse(""));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public enum ChangeType {
        ADDED,
        UPDATED,
        REMOVED
    }

    public record ModChange(
            ChangeType type,
            String modId,
            String modDisplayName,
            Optional<String> previousVersion,
            Optional<String> newVersion
    ) {
        private static final Comparator<ModChange> STABLE_ORDER = Comparator
                .comparing(ModChange::type)
                .thenComparing(ModChange::modId)
                .thenComparing(ModChange::modDisplayName)
                .thenComparing(value -> value.previousVersion().orElse(""))
                .thenComparing(value -> value.newVersion().orElse(""));

        public ModChange {
            type = Objects.requireNonNull(type, "type");
            modId = requireText(modId, "modId");
            modDisplayName = requireText(modDisplayName, "modDisplayName");
            previousVersion = normalizedOptional(previousVersion);
            newVersion = normalizedOptional(newVersion);
            if ((type == ChangeType.ADDED
                    && (previousVersion.isPresent() || newVersion.isEmpty()))
                    || (type == ChangeType.UPDATED
                    && (previousVersion.isEmpty() || newVersion.isEmpty()))
                    || (type == ChangeType.REMOVED
                    && (previousVersion.isEmpty() || newVersion.isPresent()))) {
                throw new IllegalArgumentException("Version fields do not match the change type");
            }
        }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('\n');
    }

    private static String displayName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static Optional<String> normalizedOptional(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return value.map(String::strip).filter(text -> !text.isEmpty());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
