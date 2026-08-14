package fr.apocalypsebleu.moddetective.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.apocalypsebleu.moddetective.storage.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Atomic, versioned persistence for the compact bounded modpack launch history. */
public final class ModpackLaunchHistoryStore {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path target;
    private final int maximumRecords;

    public ModpackLaunchHistoryStore(Path target) {
        this(target, ModpackLaunchHistory.DEFAULT_MAXIMUM_RECORDS);
    }

    public ModpackLaunchHistoryStore(Path target, int maximumRecords) {
        this.target = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        if (maximumRecords < 1
                || maximumRecords > ModpackLaunchHistory.ABSOLUTE_MAXIMUM_RECORDS) {
            throw new IllegalArgumentException("maximumRecords is outside its safety bounds");
        }
        this.maximumRecords = maximumRecords;
    }

    public LoadResult load() {
        if (!Files.isRegularFile(target)) {
            return LoadResult.missing();
        }
        try {
            JsonElement parsed = JsonParser.parseString(
                    Files.readString(target, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return LoadResult.corrupt();
            }
            JsonObject root = parsed.getAsJsonObject();
            int schemaVersion = intValue(root, "schemaVersion", -1);
            if (schemaVersion != SCHEMA_VERSION) {
                return schemaVersion > SCHEMA_VERSION
                        ? LoadResult.unsupportedSchema()
                        : LoadResult.corrupt();
            }
            JsonArray rawRecords = array(root, "records");
            if (rawRecords == null
                    || rawRecords.size() > ModpackLaunchHistory.ABSOLUTE_MAXIMUM_RECORDS) {
                return LoadResult.corrupt();
            }
            long omitted = optionalNonNegativeLong(root, "omittedEarlierRecords", 0L);
            if (omitted < 0L) {
                return LoadResult.corrupt();
            }
            boolean earlierUnavailable = optionalBoolean(
                    root, "earlierHistoryUnavailable", true);
            List<ModpackLaunchRecord> records = new ArrayList<>();
            Set<String> stableKeys = new HashSet<>();
            int unreadableRecords = 0;
            for (JsonElement rawRecord : rawRecords) {
                try {
                    if (!rawRecord.isJsonObject()) {
                        throw new IllegalArgumentException("Launch entry is not an object");
                    }
                    ModpackLaunchRecord record = readRecord(rawRecord.getAsJsonObject());
                    if (!stableKeys.add(record.stableKey())) {
                        throw new IllegalArgumentException("Duplicate launch record");
                    }
                    records.add(record);
                } catch (RuntimeException ignored) {
                    unreadableRecords++;
                }
            }
            ModpackLaunchHistory.BoundedResult bounded = new ModpackLaunchHistory(
                    records, omitted, earlierUnavailable).bounded(maximumRecords);
            LoadStatus status = unreadableRecords == 0
                    ? LoadStatus.LOADED : LoadStatus.CORRUPT;
            return new LoadResult(
                    bounded.history(),
                    status,
                    unreadableRecords,
                    bounded.evictedRecords(),
                    unreadableRecords == 0);
        } catch (IOException | RuntimeException ignored) {
            return LoadResult.corrupt();
        }
    }

    /**
     * Adds one observed launch at most once. Corrupt or unsupported source files are preserved and
     * the new launch remains available only in memory rather than overwriting their contents.
     */
    public RecordResult record(ModpackLaunchRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        LoadResult loaded = load();
        ModpackLaunchHistory.AppendResult appended = loaded.history().append(record, maximumRecords);
        boolean shouldNormalize = loaded.evictedByReadBound() > 0;
        if (!loaded.writable()) {
            return new RecordResult(
                    appended.history(), loaded.status(), loaded.unreadableRecords(),
                    false, appended.duplicate(), appended.evictedRecords(),
                    appended.duplicate());
        }
        boolean written = false;
        if (appended.changed() || shouldNormalize) {
            save(appended.history());
            written = true;
        }
        return new RecordResult(
                appended.history(), loaded.status(), loaded.unreadableRecords(),
                written, appended.duplicate(),
                appended.evictedRecords() + loaded.evictedByReadBound(),
                true);
    }

    public void save(ModpackLaunchHistory history) throws IOException {
        Objects.requireNonNull(history, "history");
        ModpackLaunchHistory bounded = history.bounded(maximumRecords).history();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("earlierHistoryUnavailable", bounded.earlierHistoryUnavailable());
        root.addProperty("omittedEarlierRecords", bounded.omittedEarlierRecords());
        JsonArray records = new JsonArray();
        for (ModpackLaunchRecord record : bounded.records()) {
            records.add(writeRecord(record));
        }
        root.add("records", records);
        AtomicFiles.writeUtf8(target, GSON.toJson(root));
    }

    public Path target() {
        return target;
    }

    public int maximumRecords() {
        return maximumRecords;
    }

    private static ModpackLaunchRecord readRecord(JsonObject raw) {
        JsonArray rawChanges = array(raw, "changes");
        if (rawChanges == null
                || rawChanges.size() > ModpackLaunchRecord.MAXIMUM_CHANGES_PER_LAUNCH) {
            throw new IllegalArgumentException("Launch record has no change list");
        }
        List<ModpackLaunchRecord.ModChange> changes = new ArrayList<>();
        for (JsonElement rawChange : rawChanges) {
            if (!rawChange.isJsonObject()) {
                throw new IllegalArgumentException("Mod change is not an object");
            }
            JsonObject change = rawChange.getAsJsonObject();
            changes.add(new ModpackLaunchRecord.ModChange(
                    ModpackLaunchRecord.ChangeType.valueOf(
                            requiredString(change, "type")),
                    requiredString(change, "modId"),
                    requiredString(change, "modDisplayName"),
                    optionalString(change, "previousVersion"),
                    optionalString(change, "newVersion")));
        }
        return new ModpackLaunchRecord(
                requiredLong(raw, "launchAtEpochMs"),
                optionalLong(raw, "previousLaunchAtEpochMs"),
                changes);
    }

    private static JsonObject writeRecord(ModpackLaunchRecord record) {
        JsonObject raw = new JsonObject();
        raw.addProperty("launchAtEpochMs", record.launchAtEpochMs());
        record.previousLaunchAtEpochMs().ifPresent(
                value -> raw.addProperty("previousLaunchAtEpochMs", value));
        JsonArray changes = new JsonArray();
        for (ModpackLaunchRecord.ModChange change : record.changes()) {
            JsonObject value = new JsonObject();
            value.addProperty("type", change.type().name());
            value.addProperty("modId", change.modId());
            value.addProperty("modDisplayName", change.modDisplayName());
            change.previousVersion().ifPresent(
                    version -> value.addProperty("previousVersion", version));
            change.newVersion().ifPresent(
                    version -> value.addProperty("newVersion", version));
            changes.add(value);
        }
        raw.add("changes", changes);
        return raw;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String requiredString(JsonObject root, String name) {
        try {
            JsonElement value = root.get(name);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Missing field: " + name);
            }
            return value.getAsString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid field: " + name, error);
        }
    }

    private static Optional<String> optionalString(JsonObject root, String name) {
        try {
            JsonElement value = root.get(name);
            if (value == null || value.isJsonNull()) {
                return Optional.empty();
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Optional field is not a string");
            }
            return Optional.of(value.getAsString());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid optional field: " + name, error);
        }
    }

    private static long requiredLong(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing field: " + name);
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("Field is not an exact long: " + name, invalid);
        }
    }

    private static OptionalLong optionalLong(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) {
            return OptionalLong.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Optional field is not a number: " + name);
        }
        try {
            return OptionalLong.of(value.getAsBigDecimal().longValueExact());
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    "Optional field is not an exact long: " + name, invalid);
        }
    }

    private static int intValue(JsonObject root, String name, int fallback) {
        try {
            JsonElement value = root.get(name);
            return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()
                    ? fallback : value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long optionalNonNegativeLong(JsonObject root, String name, long fallback) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Optional field is not a number: " + name);
        }
        long result;
        try {
            result = value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    "Optional field is not an exact long: " + name, invalid);
        }
        if (result < 0L) {
            throw new IllegalArgumentException("Optional field is negative: " + name);
        }
        return result;
    }

    private static boolean optionalBoolean(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Optional field is not a boolean: " + name);
        }
        return value.getAsBoolean();
    }

    public enum LoadStatus {
        MISSING,
        LOADED,
        CORRUPT,
        UNSUPPORTED_SCHEMA
    }

    public record LoadResult(
            ModpackLaunchHistory history,
            LoadStatus status,
            int unreadableRecords,
            int evictedByReadBound,
            boolean writable
    ) {
        public LoadResult {
            history = Objects.requireNonNull(history, "history");
            status = Objects.requireNonNull(status, "status");
            if (unreadableRecords < 0 || evictedByReadBound < 0) {
                throw new IllegalArgumentException("Load counters must be non-negative");
            }
        }

        private static LoadResult missing() {
            return new LoadResult(
                    ModpackLaunchHistory.empty(), LoadStatus.MISSING, 0, 0, true);
        }

        private static LoadResult corrupt() {
            return new LoadResult(
                    ModpackLaunchHistory.empty(), LoadStatus.CORRUPT, 1, 0, false);
        }

        private static LoadResult unsupportedSchema() {
            return new LoadResult(
                    ModpackLaunchHistory.empty(), LoadStatus.UNSUPPORTED_SCHEMA, 0, 0, false);
        }
    }

    public record RecordResult(
            ModpackLaunchHistory history,
            LoadStatus sourceStatus,
            int unreadableRecords,
            boolean written,
            boolean duplicate,
            int evictedRecords,
            boolean currentLaunchPersisted
    ) {
        public RecordResult {
            history = Objects.requireNonNull(history, "history");
            sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus");
            if (unreadableRecords < 0 || evictedRecords < 0) {
                throw new IllegalArgumentException("Record counters must be non-negative");
            }
        }

        public int unavailableHistoryFiles() {
            return sourceStatus == LoadStatus.CORRUPT
                    || sourceStatus == LoadStatus.UNSUPPORTED_SCHEMA ? 1 : 0;
        }
    }
}
