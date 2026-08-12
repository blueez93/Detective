package fr.apocalypsebleu.moddetective.core.casefile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.apocalypsebleu.moddetective.storage.AtomicFiles;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Atomic local persistence for active backend Case records and their stable identities. */
public final class CaseFileStore {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path target;

    public CaseFileStore(Path target) {
        this.target = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
    }

    public LoadResult load() {
        if (!Files.isRegularFile(target)) {
            return LoadResult.EMPTY;
        }
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return LoadResult.UNSUPPORTED_OR_CORRUPT;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (intValue(root, "schemaVersion", -1) != SCHEMA_VERSION) {
                return LoadResult.UNSUPPORTED_OR_CORRUPT;
            }
            JsonArray rawCases = array(root, "cases");
            if (rawCases == null) {
                return new LoadResult(List.of(), 0, true);
            }
            List<CaseFile> cases = new ArrayList<>();
            int unreadable = 0;
            for (JsonElement rawCase : rawCases) {
                try {
                    if (!rawCase.isJsonObject()) {
                        throw new IllegalArgumentException("Case entry is not an object");
                    }
                    cases.add(readCase(rawCase.getAsJsonObject()));
                } catch (RuntimeException ignored) {
                    unreadable++;
                }
            }
            cases.sort(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                    .thenComparing(CaseFile::caseId));
            return new LoadResult(List.copyOf(cases), unreadable, true);
        } catch (IOException | RuntimeException ignored) {
            return LoadResult.UNSUPPORTED_OR_CORRUPT;
        }
    }

    public void save(List<CaseFile> cases) throws IOException {
        List<CaseFile> stable = Objects.requireNonNull(cases, "cases").stream()
                .sorted(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                        .thenComparing(CaseFile::caseId))
                .toList();
        AtomicFiles.writeUtf8(target, GSON.toJson(new PersistedCases(SCHEMA_VERSION, stable)));
    }

    public Path target() {
        return target;
    }

    private static CaseFile readCase(JsonObject raw) {
        List<String> incidentIds = strings(array(raw, "relatedIncidentIds"));
        return new CaseFile(
                stringValue(raw, "caseId", ""),
                incidentIds,
                longValue(raw, "firstSeenEpochMs", 0L),
                longValue(raw, "lastSeenEpochMs", 0L),
                intValue(raw, "occurrenceCount", incidentIds.size()),
                doubleValue(raw, "averageStallDurationMs", 0.0),
                doubleValue(raw, "longestStallDurationMs", 0.0),
                recurringEvidence(array(raw, "recurringEvidence")),
                recurringOwners(array(raw, "recurringOwners")),
                doubleValue(raw, "aggregateSimilarity", 0.0),
                doubleValue(raw, "aggregateEvidenceStrength", 0.0));
    }

    private static List<CaseFile.RecurringEvidence> recurringEvidence(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<CaseFile.RecurringEvidence> result = new ArrayList<>();
        for (JsonElement element : raw) {
            JsonObject value = element.getAsJsonObject();
            result.add(new CaseFile.RecurringEvidence(
                    CaseFile.EvidenceKind.valueOf(stringValue(value, "kind", "")),
                    stringValue(value, "technicalSignature", ""),
                    intValue(value, "supportingIncidents", 0),
                    doubleValue(value, "averageObservedShare", 0.0)));
        }
        return List.copyOf(result);
    }

    private static List<CaseFile.RecurringOwner> recurringOwners(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<CaseFile.RecurringOwner> result = new ArrayList<>();
        for (JsonElement element : raw) {
            JsonObject value = element.getAsJsonObject();
            result.add(new CaseFile.RecurringOwner(
                    stringValue(value, "ownerId", ""),
                    intValue(value, "supportingIncidents", 0),
                    doubleValue(value, "averageLeafShare", 0.0),
                    doubleValue(value, "averageStackPresenceShare", 0.0)));
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return List.copyOf(result);
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String stringValue(JsonObject object, String name, String fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String name, int fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(JsonObject object, String name, double fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record PersistedCases(int schemaVersion, List<CaseFile> cases) {}

    public record LoadResult(List<CaseFile> cases, int unreadableEntries, boolean writable) {
        private static final LoadResult EMPTY = new LoadResult(List.of(), 0, true);
        private static final LoadResult UNSUPPORTED_OR_CORRUPT = new LoadResult(List.of(), 1, false);

        public LoadResult {
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        }
    }
}
