package fr.apocalypsebleu.moddetective.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.apocalypsebleu.moddetective.storage.AtomicFiles;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class IncidentStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private IncidentStore() {}

    public static Path save(FreezeIncident incident) throws IOException {
        ModDetectivePaths.ensureDirectories();
        return save(incident, ModDetectivePaths.incidents());
    }

    public static Path save(FreezeIncident incident, Path incidentsDirectory) throws IOException {
        String fileName = "freeze-" + FILE_TIME.format(Instant.ofEpochMilli(incident.detectedAtEpochMs())) + ".json";
        Path target = incidentsDirectory.toAbsolutePath().normalize().resolve(fileName);
        AtomicFiles.writeUtf8(target, GSON.toJson(incident));
        return target;
    }

    public static FreezeIncident read(Path source) throws IOException {
        try {
            FreezeIncident incident = GSON.fromJson(
                    Files.readString(source, StandardCharsets.UTF_8), FreezeIncident.class);
            if (incident == null) {
                throw new IOException("Incident JSON is empty: " + source);
            }
            return incident;
        } catch (RuntimeException e) {
            throw new IOException("Incident JSON is malformed: " + source, e);
        }
    }
}
