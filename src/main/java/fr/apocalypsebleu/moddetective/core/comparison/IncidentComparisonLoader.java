package fr.apocalypsebleu.moddetective.core.comparison;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentHistoryLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Loads exactly two local incident records for pairwise comparison.
 *
 * <p>Fingerprint parsing uses the same tolerant legacy/enhanced reader as Case Files. Optional
 * malformed derived evidence therefore falls back to recorded legacy hot-class/owner evidence
 * without rewriting either source file. This loader is synchronous by design and is dispatched
 * through Detective's existing UI-data worker by {@code DetectiveUiService}.</p>
 */
public final class IncidentComparisonLoader {
    private final Path incidentsRoot;
    private final IncidentHistoryLoader fingerprintLoader;
    private final IncidentComparisonEngine engine;

    public IncidentComparisonLoader(Path incidentsRoot) {
        this(incidentsRoot, new IncidentHistoryLoader(), new IncidentComparisonEngine());
    }

    public IncidentComparisonLoader(
            Path incidentsRoot,
            IncidentHistoryLoader fingerprintLoader,
            IncidentComparisonEngine engine
    ) {
        this.incidentsRoot = Objects.requireNonNull(incidentsRoot, "incidentsRoot")
                .toAbsolutePath().normalize();
        this.fingerprintLoader = Objects.requireNonNull(fingerprintLoader, "fingerprintLoader");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public IncidentComparison compare(Path firstSource, Path secondSource) throws IOException {
        Path first = requireLocalIncident(firstSource);
        Path second = requireLocalIncident(secondSource);
        return engine.compare(load(first), load(second));
    }

    private IncidentComparisonEngine.Source load(Path source) throws IOException {
        String incidentId = incidentsRoot.relativize(source).toString().replace('\\', '/');
        IncidentFingerprint fingerprint = fingerprintLoader.readFingerprint(source, incidentId);
        Metadata metadata = readMetadata(source);
        return IncidentComparisonEngine.source(
                fingerprint,
                metadata.attributionState(),
                metadata.detectedAtEpochMs(),
                metadata.stallDurationMs(),
                metadata.capturedSampleCount(),
                metadata.context());
    }

    private Path requireLocalIncident(Path source) throws IOException {
        Path normalized = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (!normalized.startsWith(incidentsRoot) || !Files.isRegularFile(normalized)) {
            throw new IOException("Incident path is outside the Detective incident history or is missing");
        }
        String name = normalized.getFileName().toString();
        if (!name.startsWith("freeze-") || !name.endsWith(".json")) {
            throw new IOException("Incident path does not identify a Detective incident file");
        }
        return normalized;
    }

    private static Metadata readMetadata(Path source) throws IOException {
        JsonObject root = readComparisonFields(source);
        JsonObject attribution = object(root, "attributionEvidence");
        JsonObject frame = object(root, "frame");

        OptionalLong detectedAt = optionalLong(root, "detectedAtEpochMs");
        if (detectedAt.isEmpty()) {
            detectedAt = optionalLong(frame, "epochMs");
        }
        OptionalDouble duration = optionalDouble(root, "durationMs");
        if (duration.isEmpty()) {
            duration = optionalDouble(frame, "frameMs");
        }
        OptionalInt samples = optionalInt(root, "watchdogSamples");
        AttributionEvidence.State state = state(stringValue(attribution, "state"));
        OptionalLong usedMemory = positive(optionalLong(frame, "usedMemoryBytes"));
        OptionalLong maximumMemory = positive(optionalLong(frame, "maxMemoryBytes"));
        Optional<String> dimension = optionalText(frame, "dimension");
        Optional<IncidentComparison.Position> position = position(frame);

        return new Metadata(
                detectedAt,
                nonNegative(duration),
                nonNegative(samples),
                state,
                new IncidentComparisonEngine.Context(
                        usedMemory, maximumMemory, dimension, position));
    }

    private static JsonObject readComparisonFields(Path source) throws IOException {
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
                        || "frame".equals(name)) {
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

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static OptionalLong optionalLong(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    ? OptionalLong.empty()
                    : OptionalLong.of(value.getAsLong());
        } catch (RuntimeException ignored) {
            return OptionalLong.empty();
        }
    }

    private static OptionalInt optionalInt(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    ? OptionalInt.empty()
                    : OptionalInt.of(value.getAsInt());
        } catch (RuntimeException ignored) {
            return OptionalInt.empty();
        }
    }

    private static OptionalDouble optionalDouble(JsonObject object, String name) {
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

    private static Optional<String> optionalText(JsonObject object, String name) {
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

    private static String stringValue(JsonObject object, String name) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value == null || value.isJsonNull()
                    || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                    ? "" : value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static AttributionEvidence.State state(String value) {
        try {
            return AttributionEvidence.State.valueOf(value);
        } catch (RuntimeException ignored) {
            return AttributionEvidence.State.UNKNOWN;
        }
    }

    private static Optional<IncidentComparison.Position> position(JsonObject frame) {
        OptionalInt x = optionalInt(frame, "playerX");
        OptionalInt y = optionalInt(frame, "playerY");
        OptionalInt z = optionalInt(frame, "playerZ");
        return x.isPresent() && y.isPresent() && z.isPresent()
                ? Optional.of(new IncidentComparison.Position(
                        x.getAsInt(), y.getAsInt(), z.getAsInt()))
                : Optional.empty();
    }

    private static OptionalLong positive(OptionalLong value) {
        return value.isPresent() && value.getAsLong() > 0L ? value : OptionalLong.empty();
    }

    private static OptionalDouble nonNegative(OptionalDouble value) {
        return value.isPresent() && value.getAsDouble() >= 0.0 ? value : OptionalDouble.empty();
    }

    private static OptionalInt nonNegative(OptionalInt value) {
        return value.isPresent() && value.getAsInt() >= 0 ? value : OptionalInt.empty();
    }

    private record Metadata(
            OptionalLong detectedAtEpochMs,
            OptionalDouble stallDurationMs,
            OptionalInt capturedSampleCount,
            AttributionEvidence.State attributionState,
            IncidentComparisonEngine.Context context
    ) {}
}
