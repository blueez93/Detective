package fr.apocalypsebleu.moddetective.support;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deletes only Detective incident records and keeps the most restrictive age/count policy. */
public final class IncidentHistoryRetention {
    private IncidentHistoryRetention() {}

    public static Result apply(
            Path incidentsRoot,
            DetectiveSettings settings,
            Instant now
    ) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(now, "now");
        Path root = normalizedRoot(incidentsRoot);
        if (!Files.isDirectory(root)) {
            return Result.EMPTY;
        }

        List<IncidentFile> incidents = listIncidents(root);
        incidents.sort(Comparator.comparingLong(IncidentFile::detectedAtEpochMs).reversed()
                .thenComparing(file -> file.path().toString()));
        long cutoff = now.minus(Duration.ofDays(settings.dataRetentionDays())).toEpochMilli();
        Set<Path> toDelete = new HashSet<>();
        for (int index = 0; index < incidents.size(); index++) {
            IncidentFile incident = incidents.get(index);
            if (incident.detectedAtEpochMs() < cutoff || index >= settings.incidentHistoryLimit()) {
                toDelete.add(incident.path());
            }
        }

        int deleted = 0;
        for (Path path : toDelete) {
            requireInside(root, path);
            if (Files.deleteIfExists(path)) {
                deleted++;
            }
        }
        deleteEmptyChildDirectories(root);
        return new Result(incidents.size(), deleted, incidents.size() - deleted);
    }

    public static Result clear(Path incidentsRoot) throws IOException {
        Path root = normalizedRoot(incidentsRoot);
        if (!Files.isDirectory(root)) {
            return Result.EMPTY;
        }
        List<IncidentFile> incidents = listIncidents(root);
        int deleted = 0;
        for (IncidentFile incident : incidents) {
            requireInside(root, incident.path());
            if (Files.deleteIfExists(incident.path())) {
                deleted++;
            }
        }
        deleteEmptyChildDirectories(root);
        return new Result(incidents.size(), deleted, incidents.size() - deleted);
    }

    private static List<IncidentFile> listIncidents(Path root) throws IOException {
        List<IncidentFile> result = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(IncidentHistoryRetention::isIncidentFile).toList()) {
                Path normalized = path.toAbsolutePath().normalize();
                requireInside(root, normalized);
                result.add(new IncidentFile(normalized, incidentTimestamp(normalized)));
            }
        }
        return result;
    }

    private static long incidentTimestamp(Path source) {
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
            // Unreadable legacy data still participates in count retention via file time.
        }
        try {
            FileTime time = Files.getLastModifiedTime(source);
            return time.toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static boolean isIncidentFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.startsWith("freeze-") && name.endsWith(".json");
    }

    private static void deleteEmptyChildDirectories(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
                if (!directory.equals(root)) {
                    requireInside(root, directory);
                    try (var children = Files.list(directory)) {
                        if (children.findAny().isEmpty()) {
                            Files.deleteIfExists(directory);
                        }
                    }
                }
            }
        }
    }

    private static Path normalizedRoot(Path root) throws IOException {
        Path normalized = Objects.requireNonNull(root, "incidentsRoot").toAbsolutePath().normalize();
        if (normalized.getParent() == null) {
            throw new IOException("Incident history root is too broad: " + normalized);
        }
        return normalized;
    }

    private static void requireInside(Path root, Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IOException("Incident path is outside the configured Detective history: " + candidate);
        }
    }

    private record IncidentFile(Path path, long detectedAtEpochMs) {}

    public record Result(int discovered, int deleted, int retained) {
        private static final Result EMPTY = new Result(0, 0, 0);
    }
}
