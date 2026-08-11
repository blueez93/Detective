package fr.apocalypsebleu.moddetective.support.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates the small, local-only, allow-listed Detective support ZIP. */
public final class SupportReportExporter {
    public static final int SCHEMA_VERSION = 1;
    public static final int REPORT_FORMAT = 1;
    public static final String ROOT = "detective-report/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter SUMMARY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SupportReportExporter() {}

    public static Path export(SupportReportData data, Path reportsDirectory) throws IOException {
        return export(data, reportsDirectory, Clock.systemDefaultZone());
    }

    public static Path export(SupportReportData data, Path reportsDirectory, Clock clock) throws IOException {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(clock, "clock");
        Path directory = Objects.requireNonNull(reportsDirectory, "reportsDirectory")
                .toAbsolutePath().normalize();
        Files.createDirectories(directory);

        Instant generatedAt = clock.instant();
        ZoneId zone = clock.getZone();
        String baseName = "detective-report-" + FILE_TIME.withZone(zone).format(generatedAt);
        Path target = uniqueTarget(directory, baseName);
        Path temporary = Files.createTempFile(directory, ".detective-report-", ".tmp");
        try {
            writeZip(temporary, entries(data, generatedAt, zone));
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Map<String, byte[]> entries(SupportReportData data, Instant generatedAt, ZoneId zone) {
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        addText(entries, "README.txt", readme(data));
        addText(entries, "summary.txt", summary(data, zone));
        addJson(entries, "manifest.json", manifest(data, generatedAt));
        addJson(entries, "detective/version.json", version(data));
        addJson(entries, "detective/incidents.json", incidentIndex(data.selectedIncident()));
        addJson(entries, "detective/selected-incident.json", selectedIncident(data.selectedIncident()));
        addJson(entries, "detective/settings-summary.json", settings(data));
        addJson(entries, "modpack/mods.json", mods(data));
        addJson(entries, "modpack/changes.json", changes(data.modpackChanges()));
        addJson(entries, "system/environment.json", environment(data));
        return Collections.unmodifiableMap(entries);
    }

    private static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(ROOT + entry.getKey());
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private static JsonObject manifest(SupportReportData data, Instant generatedAt) {
        JsonObject root = schema();
        root.addProperty("reportFormat", REPORT_FORMAT);
        root.addProperty("detectiveVersion", data.detectiveVersion());
        root.addProperty("generatedAt", generatedAt.toString());
        root.addProperty("minecraftVersion", data.minecraftVersion());
        root.addProperty("neoForgeVersion", data.neoForgeVersion());
        root.addProperty("incidentCount", 1);
        return root;
    }

    private static JsonObject version(SupportReportData data) {
        JsonObject root = schema();
        root.addProperty("modId", "detective");
        root.addProperty("detectiveVersion", data.detectiveVersion());
        root.addProperty("minecraftVersion", data.minecraftVersion());
        root.addProperty("neoForgeVersion", data.neoForgeVersion());
        return root;
    }

    private static JsonObject incidentIndex(IncidentDetailViewModel detail) {
        JsonObject root = schema();
        JsonArray incidents = new JsonArray();
        incidents.add(incidentSummary(detail));
        root.add("incidents", incidents);
        return root;
    }

    private static JsonObject incidentSummary(IncidentDetailViewModel detail) {
        JsonObject incident = new JsonObject();
        incident.addProperty("detectedAtEpochMs", detail.summary().detectedAtEpochMs());
        incident.addProperty("durationMs", finite(detail.summary().durationMs()));
        incident.addProperty("thresholdMs", finite(detail.summary().thresholdMs()));
        incident.addProperty("classification", displayState(detail));
        incident.addProperty("rawAttributionState", ReportPrivacy.metadata(detail.summary().rawEvidenceState()));
        SuspectViewModel primary = primary(detail);
        if (primary == null) {
            incident.add("primarySuspect", JsonNull.INSTANCE);
        } else {
            JsonObject suspect = new JsonObject();
            suspect.addProperty("modId", ReportPrivacy.metadata(primary.modId()));
            suspect.addProperty("name", ReportPrivacy.metadata(primary.modName()));
            suspect.addProperty("version", ReportPrivacy.metadata(primary.version()));
            incident.add("primarySuspect", suspect);
        }
        return incident;
    }

    private static JsonObject selectedIncident(IncidentDetailViewModel detail) {
        JsonObject root = schema();
        JsonObject incident = incidentSummary(detail);
        incident.addProperty("watchdogSamples", Math.max(0, detail.summary().watchdogSamples()));

        JsonObject location = new JsonObject();
        location.addProperty("dimension", ReportPrivacy.metadata(detail.dimensionId()));
        JsonObject position = new JsonObject();
        addNullable(position, "x", detail.playerX());
        addNullable(position, "y", detail.playerY());
        addNullable(position, "z", detail.playerZ());
        location.add("position", position);
        incident.add("location", location);

        JsonArray suspects = new JsonArray();
        for (int index = 0; index < detail.suspects().size(); index++) {
            SuspectViewModel suspect = detail.suspects().get(index);
            JsonObject value = new JsonObject();
            value.addProperty("rank", index + 1);
            value.addProperty("modId", ReportPrivacy.metadata(suspect.modId()));
            value.addProperty("name", ReportPrivacy.metadata(suspect.modName()));
            value.addProperty("version", ReportPrivacy.metadata(suspect.version()));
            value.addProperty("presenceSamples", Math.max(0, suspect.presenceSamples()));
            value.addProperty("presenceSharePercent", finite(suspect.presenceSharePercent()));
            value.addProperty("leafOwnershipCount", Math.max(0, suspect.leafOwnershipCount()));
            value.addProperty("leafOwnershipSharePercent", finite(suspect.leafOwnershipSharePercent()));
            addFiniteOrNull(value, "averageFirstFrameDepth", suspect.averageFirstFrameDepth());
            value.addProperty("minimumFirstFrameDepth", suspect.minimumFirstFrameDepth());
            value.addProperty("repeatedLeafOwnership", Math.max(0, suspect.repeatedLeafOwnership()));
            value.addProperty("callerOnlySamples", Math.max(0, suspect.callerOnlySamples()));
            value.addProperty("stackDiversity", Math.max(0, suspect.stackDiversity()));
            suspects.add(value);
        }
        incident.add("suspects", suspects);

        JsonObject blackBox = new JsonObject();
        blackBox.addProperty("partial", detail.blackBoxPartial());
        blackBox.addProperty("originalSampleCount", Math.max(0, detail.originalBlackBoxSamples()));
        JsonArray points = new JsonArray();
        for (BlackBoxPoint point : detail.blackBox()) {
            JsonObject value = new JsonObject();
            value.addProperty("epochMs", point.epochMs());
            value.addProperty("frameMs", finite(point.frameMs()));
            value.addProperty("approximateFps", finite(point.approximateFps()));
            value.addProperty("usedMemoryBytes", Math.max(0L, point.usedMemoryBytes()));
            points.add(value);
        }
        blackBox.add("points", points);
        incident.add("blackBox", blackBox);
        root.add("incident", incident);
        return root;
    }

    private static JsonObject settings(SupportReportData data) {
        JsonObject root = schema();
        root.addProperty("incidentNotifications", data.settings().incidentNotifications());
        root.addProperty("incidentHistoryLimit", data.settings().incidentHistoryLimit());
        root.addProperty("dataRetentionDays", data.settings().dataRetentionDays());
        root.addProperty("showTechnicalEvidenceByDefault",
                data.settings().showTechnicalEvidenceByDefault());
        return root;
    }

    private static JsonObject mods(SupportReportData data) {
        JsonObject root = schema();
        root.addProperty("available", !data.installedMods().isEmpty());
        root.addProperty("count", data.installedMods().size());
        JsonArray mods = new JsonArray();
        for (SupportReportData.InstalledMod mod : data.installedMods()) {
            JsonObject value = new JsonObject();
            value.addProperty("modId", mod.modId());
            value.addProperty("name", mod.name());
            value.addProperty("version", mod.version());
            value.addProperty("loader", "neoforge");
            value.addProperty("sourceFile", mod.sourceFile());
            mods.add(value);
        }
        root.add("mods", mods);
        return root;
    }

    private static JsonObject changes(ModpackChangesViewModel changes) {
        JsonObject root = schema();
        root.addProperty("available", changes.comparisonAvailable());
        root.addProperty("currentModCount", changes.currentModCount());
        JsonArray added = new JsonArray();
        JsonArray removed = new JsonArray();
        JsonArray updated = new JsonArray();
        for (ModpackChangesViewModel.Change change : changes.changes()) {
            JsonObject value = new JsonObject();
            value.addProperty("modId", ReportPrivacy.metadata(change.modId()));
            value.addProperty("name", ReportPrivacy.metadata(change.modName()));
            if (change.type() == ModpackChangesViewModel.Type.UPDATED) {
                value.addProperty("oldVersion", ReportPrivacy.metadata(change.oldVersion()));
                value.addProperty("newVersion", ReportPrivacy.metadata(change.newVersion()));
                updated.add(value);
            } else if (change.type() == ModpackChangesViewModel.Type.ADDED) {
                value.addProperty("version", ReportPrivacy.metadata(change.newVersion()));
                added.add(value);
            } else {
                value.addProperty("version", ReportPrivacy.metadata(change.oldVersion()));
                removed.add(value);
            }
        }
        root.add("added", added);
        root.add("updated", updated);
        root.add("removed", removed);
        return root;
    }

    private static JsonObject environment(SupportReportData data) {
        SupportReportData.Environment environment = data.environment();
        JsonObject root = schema();
        root.addProperty("os", environment.os());
        root.addProperty("architecture", environment.architecture());
        root.addProperty("javaVersion", environment.javaVersion());
        root.addProperty("minecraftVersion", data.minecraftVersion());
        root.addProperty("neoForgeVersion", data.neoForgeVersion());
        root.addProperty("detectiveVersion", data.detectiveVersion());
        root.addProperty("jvmMaximumMemoryBytes", environment.jvmMaximumMemoryBytes());
        root.addProperty("jvmUsedMemoryBytesApproximate", environment.jvmUsedMemoryBytes());
        root.addProperty("logicalProcessors", environment.logicalProcessors());
        root.addProperty("gpu", environment.gpu());
        return root;
    }

    static String readme(SupportReportData data) {
        IncidentDetailViewModel detail = data.selectedIncident();
        SuspectViewModel primary = primary(detail);
        String leaf = primary == null ? "Not available"
                : primary.leafOwnershipCount() + " / " + detail.summary().watchdogSamples() + " samples";
        String presence = primary == null ? "Not available"
                : primary.presenceSamples() + " / " + detail.summary().watchdogSamples() + " samples";
        return """
                DETECTIVE SUPPORT REPORT

                Generated by Detective %s
                Minecraft %s
                NeoForge %s

                This report contains diagnostic information recorded locally
                by Detective.

                Detective identifies probable suspects from captured execution
                evidence. A suspect is not proof that a mod is defective.

                Detective does not upload this report automatically.

                PRIMARY INCIDENT

                Freeze duration:
                %s

                Classification:
                %s

                Primary suspect:
                %s

                Leaf ownership:
                %s

                Stack presence:
                %s

                Detect. Measure. Explain. Never accuse.
                """.formatted(
                data.detectiveVersion(), data.minecraftVersion(), data.neoForgeVersion(),
                milliseconds(detail.summary().durationMs()), displayState(detail), primarySuspect(detail),
                leaf, presence);
    }

    static String summary(SupportReportData data, ZoneId zone) {
        IncidentDetailViewModel detail = data.selectedIncident();
        SuspectViewModel primary = primary(detail);
        String position = detail.playerX() == null || detail.playerY() == null || detail.playerZ() == null
                ? "Unavailable"
                : "X %d / Y %d / Z %d".formatted(detail.playerX(), detail.playerY(), detail.playerZ());
        String leaf = primary == null ? "Not available"
                : primary.leafOwnershipCount() + " / " + detail.summary().watchdogSamples();
        String presence = primary == null ? "Not available"
                : primary.presenceSamples() + " / " + detail.summary().watchdogSamples();
        String suspect = primarySuspect(detail);
        return """
                DETECTIVE DIAGNOSTIC SUMMARY

                Minecraft:
                %s

                NeoForge:
                %s

                Detective:
                %s

                Installed mods:
                %d

                ------------------------------

                INCIDENT

                Date:
                %s

                Duration:
                %s

                Threshold:
                %s

                Dimension:
                %s

                Position:
                %s

                Samples:
                %d

                ------------------------------

                ATTRIBUTION

                State:
                %s

                Primary suspect:
                %s

                Leaf ownership:
                %s

                Stack presence:
                %s

                ------------------------------

                IMPORTANT

                This does not prove that %s is defective.

                The incident may depend on configuration, another mod,
                the game state, or interaction between several components.
                """.formatted(
                data.minecraftVersion(), data.neoForgeVersion(), data.detectiveVersion(),
                data.installedMods().size(),
                SUMMARY_TIME.withZone(zone).format(Instant.ofEpochMilli(detail.summary().detectedAtEpochMs())),
                milliseconds(detail.summary().durationMs()), milliseconds(detail.summary().thresholdMs()),
                ReportPrivacy.metadata(detail.dimensionId()), position,
                detail.summary().watchdogSamples(), displayState(detail), suspect, leaf, presence,
                primary == null ? "any mod" : ReportPrivacy.metadata(primary.modName()));
    }

    private static String displayState(IncidentDetailViewModel detail) {
        EvidenceBadge evidence = detail.summary().evidence();
        return evidence.isSystemStall() ? "POSSIBLE_SYSTEM_STALL" : evidence.name();
    }

    private static SuspectViewModel primary(IncidentDetailViewModel detail) {
        return detail.summary().hasPrimarySuspect() && !detail.suspects().isEmpty()
                ? detail.suspects().getFirst() : null;
    }

    private static String primarySuspect(IncidentDetailViewModel detail) {
        SuspectViewModel primary = primary(detail);
        return primary == null ? "None assigned" : ReportPrivacy.metadata(primary.modName());
    }

    private static String milliseconds(double value) {
        return Double.isFinite(value) && value >= 0.0
                ? String.format(Locale.ROOT, "%.1f ms", value)
                : "Unavailable";
    }

    private static JsonObject schema() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        return root;
    }

    private static void addNullable(JsonObject object, String name, Integer value) {
        if (value == null) {
            object.add(name, JsonNull.INSTANCE);
        } else {
            object.addProperty(name, value);
        }
    }

    private static void addFiniteOrNull(JsonObject object, String name, double value) {
        if (Double.isFinite(value)) {
            object.addProperty(name, value);
        } else {
            object.add(name, JsonNull.INSTANCE);
        }
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static void addText(Map<String, byte[]> entries, String name, String content) {
        entries.put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void addJson(Map<String, byte[]> entries, String name, JsonObject content) {
        addText(entries, name, GSON.toJson(content));
    }

    private static Path uniqueTarget(Path directory, String baseName) {
        Path candidate = directory.resolve(baseName + ".zip");
        for (int suffix = 2; Files.exists(candidate); suffix++) {
            candidate = directory.resolve(baseName + "-" + suffix + ".zip");
        }
        return candidate;
    }
}
