package fr.apocalypsebleu.moddetective.client.ui.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/** Maps persisted incident bundles to stable UI data without exposing engine records to screens. */
public final class IncidentJsonAdapter {
    private static final int MAX_GRAPH_POINTS = 240;
    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "detectedAtEpochMs", "durationMs", "thresholdMs", "watchdogSamples",
            "frame", "attributionEvidence", "suspects", "derivedEvidence");

    private IncidentJsonAdapter() {}

    public static IncidentSummaryViewModel readSummary(Path source) throws IOException {
        return parse(source, false).summary();
    }

    /** Reads only summary/search metadata; raw stacks and Black Box samples are not indexed. */
    public static IncidentSearchRecord readSearchRecord(Path source, String incidentId)
            throws IOException {
        ParsedIncident parsed = parse(source, false);
        JsonObject root = parsed.root();
        JsonObject frame = object(root, "frame");
        Set<String> ownerIds = new TreeSet<>();
        Set<String> displayNames = new TreeSet<>();
        JsonArray rawSuspects = array(root, "suspects");
        if (rawSuspects != null) {
            for (JsonElement element : rawSuspects) {
                if (element.isJsonObject()) {
                    JsonObject suspect = element.getAsJsonObject();
                    strictText(suspect, "modId").ifPresent(ownerIds::add);
                    strictText(suspect, "modName").ifPresent(displayNames::add);
                }
            }
        }
        JsonArray derivedOwners = array(object(root, "derivedEvidence"), "ownerObservations");
        if (derivedOwners != null) {
            for (JsonElement element : derivedOwners) {
                if (element.isJsonObject()) {
                    strictText(element.getAsJsonObject(), "ownerId").ifPresent(ownerIds::add);
                }
            }
        }

        OptionalLong detectedAt = strictLong(root, "detectedAtEpochMs");
        if (detectedAt.isEmpty()) {
            detectedAt = strictLong(frame, "epochMs");
        }
        OptionalDouble duration = strictDouble(root, "durationMs");
        if (duration.isEmpty()) {
            duration = strictDouble(frame, "frameMs");
        }
        duration = duration.isPresent() && duration.getAsDouble() >= 0.0
                ? duration : OptionalDouble.empty();
        return new IncidentSearchRecord(
                incidentId,
                parsed.summary(),
                ownerIds,
                displayNames,
                strictText(frame, "dimension"),
                detectedAt,
                duration);
    }

    public static IncidentDetailViewModel readDetail(Path source) throws IOException {
        ParsedIncident parsed = parse(source, true);
        JsonArray rawBlackBox = array(parsed.root(), "blackBox");
        List<BlackBoxPoint> points = readBlackBox(rawBlackBox);
        int originalSamples = rawBlackBox == null ? 0 : rawBlackBox.size();
        boolean partial = rawBlackBox == null || originalSamples < 2 || points.size() < Math.min(originalSamples, MAX_GRAPH_POINTS);
        return new IncidentDetailViewModel(
                parsed.summary(),
                parsed.suspects(),
                downsample(points, MAX_GRAPH_POINTS),
                originalSamples,
                partial,
                parsed.dimensionId(),
                parsed.playerX(),
                parsed.playerY(),
                parsed.playerZ());
    }

    private static ParsedIncident parse(Path source, boolean includeBlackBox) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        JsonObject root = readRoot(normalized, includeBlackBox);

        JsonObject frame = object(root, "frame");
        long detectedAt = longValue(root, "detectedAtEpochMs",
                longValue(frame, "epochMs", 0L));
        double duration = doubleValue(root, "durationMs",
                doubleValue(frame, "frameMs", 0.0));
        double threshold = doubleValue(root, "thresholdMs", 0.0);
        int watchdogSamples = intValue(root, "watchdogSamples", 0);
        String rawState = stringValue(object(root, "attributionEvidence"), "state", "UNKNOWN");
        List<SuspectViewModel> suspects = readSuspects(array(root, "suspects"));
        SuspectViewModel top = suspects.isEmpty() ? null : suspects.getFirst();
        EvidenceBadge badge = EvidenceBadge.from(rawState, top);
        boolean hasPrimary = "ATTRIBUTED".equalsIgnoreCase(rawState) && top != null;
        String rawDimension = stringValue(frame, "dimension", "");
        Integer x = nullableInt(frame, "playerX");
        Integer y = nullableInt(frame, "playerY");
        Integer z = nullableInt(frame, "playerZ");

        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                normalized.getFileName().toString(),
                normalized,
                detectedAt,
                duration,
                threshold,
                watchdogSamples,
                badge,
                rawState,
                hasPrimary ? top.modName() : "",
                hasPrimary,
                UiFormatters.dateTime(detectedAt),
                UiFormatters.dimension(rawDimension),
                UiFormatters.coordinates(x, y, z));
        return new ParsedIncident(root, summary, suspects, rawDimension, x, y, z);
    }

    private static JsonObject readRoot(Path source, boolean includeBlackBox) throws IOException {
        try (Reader characterReader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(characterReader)) {
            reader.beginObject();
            JsonObject root = new JsonObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!includeBlackBox && !SUMMARY_FIELDS.contains(name)) {
                    reader.skipValue();
                } else {
                    root.add(name, JsonParser.parseReader(reader));
                }
            }
            reader.endObject();
            return root;
        } catch (RuntimeException e) {
            throw new IOException("Incident JSON is malformed: " + source, e);
        }
    }

    private static List<SuspectViewModel> readSuspects(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<SuspectViewModel> suspects = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject suspect = element.getAsJsonObject();
            suspects.add(new SuspectViewModel(
                    stringValue(suspect, "modId", "unknown"),
                    stringValue(suspect, "modName", stringValue(suspect, "modId", "Unknown mod")),
                    stringValue(suspect, "version", "unknown"),
                    intValue(suspect, "presenceSamples", intValue(suspect, "samplesObserved", 0)),
                    doubleValue(suspect, "presenceSharePercent", doubleValue(suspect, "sampleSharePercent", 0.0)),
                    intValue(suspect, "leafOwnershipCount", 0),
                    doubleValue(suspect, "leafOwnershipSharePercent", 0.0),
                    doubleValue(suspect, "averageFirstFrameDepth", -1.0),
                    intValue(suspect, "minimumFirstFrameDepth", -1),
                    intValue(suspect, "repeatedLeafOwnership", 0),
                    intValue(suspect, "callerOnlySamples", 0),
                    intValue(suspect, "stackDiversity", 0)));
        }
        return List.copyOf(suspects);
    }

    private static List<BlackBoxPoint> readBlackBox(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<BlackBoxPoint> points = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject point = element.getAsJsonObject();
            double frameMs = doubleValue(point, "frameMs", Double.NaN);
            if (!Double.isFinite(frameMs) || frameMs < 0.0) {
                continue;
            }
            points.add(new BlackBoxPoint(
                    longValue(point, "epochMs", 0L),
                    frameMs,
                    doubleValue(point, "approximateFps", 0.0),
                    longValue(point, "usedMemoryBytes", 0L)));
        }
        return points;
    }

    static List<BlackBoxPoint> downsample(List<BlackBoxPoint> source, int maximumPoints) {
        if (maximumPoints <= 0) {
            throw new IllegalArgumentException("maximumPoints must be positive");
        }
        if (source.size() <= maximumPoints) {
            return List.copyOf(source);
        }
        List<BlackBoxPoint> result = new ArrayList<>(maximumPoints);
        for (int bucket = 0; bucket < maximumPoints; bucket++) {
            int start = bucket * source.size() / maximumPoints;
            int end = Math.max(start + 1, (bucket + 1) * source.size() / maximumPoints);
            BlackBoxPoint peak = source.get(start);
            for (int index = start + 1; index < end; index++) {
                BlackBoxPoint candidate = source.get(index);
                if (candidate.frameMs() > peak.frameMs()) {
                    peak = candidate;
                }
            }
            result.add(peak);
        }
        return List.copyOf(result);
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null) {
            return null;
        }
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String name) {
        if (parent == null) {
            return null;
        }
        JsonElement value = parent.get(name);
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

    private static long longValue(JsonObject object, String name, long fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
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

    private static Integer nullableInt(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull() ? null : value.getAsInt();
        } catch (RuntimeException ignored) {
            return null;
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

    private static Optional<String> strictText(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            if (value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String result = value.getAsString().strip();
            return result.isEmpty() ? Optional.empty() : Optional.of(result);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static OptionalLong strictLong(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    ? OptionalLong.empty() : OptionalLong.of(value.getAsLong());
        } catch (RuntimeException ignored) {
            return OptionalLong.empty();
        }
    }

    private static OptionalDouble strictDouble(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            if (value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return OptionalDouble.empty();
            }
            double result = value.getAsDouble();
            return Double.isFinite(result) ? OptionalDouble.of(result) : OptionalDouble.empty();
        } catch (RuntimeException ignored) {
            return OptionalDouble.empty();
        }
    }

    private record ParsedIncident(
            JsonObject root,
            IncidentSummaryViewModel summary,
            List<SuspectViewModel> suspects,
            String dimensionId,
            Integer playerX,
            Integer playerY,
            Integer playerZ
    ) {}
}
