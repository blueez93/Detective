package fr.apocalypsebleu.moddetective.support;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.apocalypsebleu.moddetective.storage.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Tolerant, versioned settings storage. Unknown fields are intentionally ignored. */
public final class DetectiveSettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path target;

    public DetectiveSettingsStore(Path target) {
        this.target = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
    }

    public DetectiveSettings load() throws IOException {
        DetectiveSettings defaults = DetectiveSettings.defaults();
        if (!Files.isRegularFile(target)) {
            return defaults;
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8));
            root = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException malformed) {
            return defaults;
        }

        return new DetectiveSettings(
                DetectiveSettings.SCHEMA_VERSION,
                booleanValue(root, "incidentNotifications", defaults.incidentNotifications()),
                intValue(root, "incidentHistoryLimit", defaults.incidentHistoryLimit()),
                intValue(root, "dataRetentionDays", defaults.dataRetentionDays()),
                booleanValue(root, "showTechnicalEvidenceByDefault",
                        defaults.showTechnicalEvidenceByDefault()));
    }

    public void save(DetectiveSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", DetectiveSettings.SCHEMA_VERSION);
        root.addProperty("incidentNotifications", settings.incidentNotifications());
        root.addProperty("incidentHistoryLimit", settings.incidentHistoryLimit());
        root.addProperty("dataRetentionDays", settings.dataRetentionDays());
        root.addProperty("showTechnicalEvidenceByDefault", settings.showTechnicalEvidenceByDefault());
        AtomicFiles.writeUtf8(target, GSON.toJson(root));
    }

    public Path target() {
        return target;
    }

    private static boolean booleanValue(JsonObject root, String name, boolean fallback) {
        try {
            JsonElement value = root.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject root, String name, int fallback) {
        try {
            JsonElement value = root.get(name);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
