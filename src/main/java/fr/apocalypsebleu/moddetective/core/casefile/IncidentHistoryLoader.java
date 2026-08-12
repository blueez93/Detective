package fr.apocalypsebleu.moddetective.core.casefile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.DerivedIncidentEvidence;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Loads a bounded fingerprint history without rewriting legacy incident records. */
public final class IncidentHistoryLoader {
    public LoadResult load(Path incidentsRoot, int maximumIncidents) throws IOException {
        if (maximumIncidents < 1) {
            throw new IllegalArgumentException("maximumIncidents must be positive");
        }
        Path root = Objects.requireNonNull(incidentsRoot, "incidentsRoot")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return LoadResult.EMPTY;
        }

        List<Candidate> candidates = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path source : paths.filter(IncidentHistoryLoader::isIncidentFile).toList()) {
                Path normalized = source.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    continue;
                }
                String incidentId = root.relativize(normalized).toString().replace('\\', '/');
                candidates.add(new Candidate(normalized, incidentId, readTimestamp(normalized)));
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::detectedAtEpochMs).reversed()
                .thenComparing(Candidate::incidentId));
        int ignoredByBound = Math.max(0, candidates.size() - maximumIncidents);
        if (candidates.size() > maximumIncidents) {
            candidates = candidates.subList(0, maximumIncidents);
        }

        List<IncidentFingerprint> fingerprints = new ArrayList<>();
        int unreadable = 0;
        for (Candidate candidate : candidates) {
            try {
                fingerprints.add(readFingerprint(candidate.path(), candidate.incidentId()));
            } catch (IOException | RuntimeException ignored) {
                unreadable++;
            }
        }
        fingerprints.sort(Comparator.comparing(IncidentFingerprint::incidentId)
                .thenComparingLong(IncidentFingerprint::detectedAtEpochMs));
        return new LoadResult(List.copyOf(fingerprints), unreadable, ignoredByBound);
    }

    IncidentFingerprint readFingerprint(Path source, String incidentId) throws IOException {
        JsonObject root = readCaseFields(source);

        long detectedAt = longValue(root, "detectedAtEpochMs", 0L);
        double duration = doubleValue(root, "durationMs", 0.0);
        int samples = intValue(root, "watchdogSamples", 0);
        JsonObject rawAttribution = object(root, "attributionEvidence");
        AttributionEvidence.State state = state(stringValue(rawAttribution, "state", "UNKNOWN"));
        AttributionEvidence attribution = new AttributionEvidence(
                state,
                intValue(rawAttribution, "stackSamples", samples),
                intValue(rawAttribution, "strongestSuspectSamples", 0),
                intValue(rawAttribution, "gcMarkerFrames", 0),
                intValue(rawAttribution, "nativeOrDriverMarkerFrames", 0));
        List<SuspectAnalyzer.Suspect> suspects = suspects(array(root, "suspects"));
        List<SuspectAnalyzer.HotClass> hotClasses = hotClasses(array(root, "hotClasses"));
        DerivedIncidentEvidence enhanced = derivedEvidence(object(root, "derivedEvidence"));
        return IncidentFingerprint.fromPersistedEvidence(
                incidentId, detectedAt, duration, samples, attribution,
                suspects, hotClasses, enhanced);
    }

    private static JsonObject readCaseFields(Path source) throws IOException {
        try (Reader characterReader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(characterReader)) {
            JsonObject root = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("detectedAtEpochMs".equals(name)
                        || "durationMs".equals(name)
                        || "watchdogSamples".equals(name)
                        || "attributionEvidence".equals(name)
                        || "suspects".equals(name)
                        || "hotClasses".equals(name)
                        || "derivedEvidence".equals(name)) {
                    root.add(name, JsonParser.parseReader(reader));
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return root;
        } catch (RuntimeException e) {
            throw new IOException("Incident JSON is malformed: " + source, e);
        }
    }

    private static DerivedIncidentEvidence derivedEvidence(JsonObject raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (intValue(raw, "schemaVersion", -1) != DerivedIncidentEvidence.SCHEMA_VERSION) {
                return null;
            }
            return new DerivedIncidentEvidence(
                    DerivedIncidentEvidence.SCHEMA_VERSION,
                    stringValue(raw, "signatureFormat", ""),
                    intValue(raw, "representedSamples", -1),
                    signatures(array(raw, "classSignatures")),
                    signatures(array(raw, "frameSignatures")),
                    signatures(array(raw, "stackPathSignatures")),
                    owners(array(raw, "ownerObservations")));
        } catch (RuntimeException ignored) {
            // Enhanced evidence is optional. A corrupt optional block falls back to v1 fields.
            return null;
        }
    }

    private static List<DerivedIncidentEvidence.SignatureObservation> signatures(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<DerivedIncidentEvidence.SignatureObservation> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Signature observation must be an object");
            }
            JsonObject value = element.getAsJsonObject();
            result.add(new DerivedIncidentEvidence.SignatureObservation(
                    stringValue(value, "signature", ""),
                    intValue(value, "observations", -1)));
        }
        return result;
    }

    private static List<DerivedIncidentEvidence.OwnerObservation> owners(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<DerivedIncidentEvidence.OwnerObservation> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Owner observation must be an object");
            }
            JsonObject value = element.getAsJsonObject();
            result.add(new DerivedIncidentEvidence.OwnerObservation(
                    stringValue(value, "ownerId", ""),
                    intValue(value, "presenceSamples", -1),
                    intValue(value, "leafOwnershipSamples", -1),
                    intValue(value, "stackDiversity", -1)));
        }
        return result;
    }

    private static List<SuspectAnalyzer.HotClass> hotClasses(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<SuspectAnalyzer.HotClass> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            String className = stringValue(value, "className", "");
            int hits = intValue(value, "hits", 0);
            if (!className.isBlank() && hits > 0) {
                result.add(new SuspectAnalyzer.HotClass(className, hits));
            }
        }
        return List.copyOf(result);
    }

    private static List<SuspectAnalyzer.Suspect> suspects(JsonArray raw) {
        if (raw == null) {
            return List.of();
        }
        List<SuspectAnalyzer.Suspect> result = new ArrayList<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject value = element.getAsJsonObject();
            String modId = stringValue(value, "modId", "");
            if (modId.isBlank()) {
                continue;
            }
            int presence = intValue(value, "presenceSamples",
                    intValue(value, "samplesObserved", 0));
            int leaf = intValue(value, "leafOwnershipCount", 0);
            result.add(new SuspectAnalyzer.Suspect(
                    modId,
                    stringValue(value, "modName", modId),
                    stringValue(value, "version", "unknown"),
                    Math.max(0, presence),
                    doubleValue(value, "presenceSharePercent",
                            doubleValue(value, "sampleSharePercent", 0.0)),
                    Math.max(0, leaf),
                    doubleValue(value, "leafOwnershipSharePercent", 0.0),
                    doubleValue(value, "averageFirstFrameDepth", -1.0),
                    intValue(value, "minimumFirstFrameDepth", -1),
                    Math.max(0, intValue(value, "repeatedLeafOwnership", 0)),
                    Math.max(0, intValue(value, "callerOnlySamples", Math.max(0, presence - leaf))),
                    Math.max(0, intValue(value, "stackDiversity", 0))));
        }
        return List.copyOf(result);
    }

    private static long readTimestamp(Path source) {
        try (Reader characterReader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(characterReader)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("detectedAtEpochMs".equals(name)) {
                    return reader.nextLong();
                }
                reader.skipValue();
            }
        } catch (Exception ignored) {
            // Corrupt records sort as oldest and are counted if selected for parsing.
        }
        return 0L;
    }

    private static AttributionEvidence.State state(String raw) {
        try {
            return AttributionEvidence.State.valueOf(raw);
        } catch (RuntimeException ignored) {
            return AttributionEvidence.State.UNKNOWN;
        }
    }

    private static boolean isIncidentFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.startsWith("freeze-") && name.endsWith(".json");
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
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

    private record Candidate(Path path, String incidentId, long detectedAtEpochMs) {}

    public record LoadResult(
            List<IncidentFingerprint> fingerprints,
            int unreadableFiles,
            int ignoredByBound
    ) {
        private static final LoadResult EMPTY = new LoadResult(List.of(), 0, 0);

        public LoadResult {
            fingerprints = List.copyOf(Objects.requireNonNull(fingerprints, "fingerprints"));
        }
    }
}
