package fr.apocalypsebleu.moddetective.client.support;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.data.IncidentJsonAdapter;
import fr.apocalypsebleu.moddetective.client.ui.data.ModpackChangesAdapter;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.casefile.CaseHistoryService;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshot;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotDiff;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotService;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;
import fr.apocalypsebleu.moddetective.support.DetectiveSettingsStore;
import fr.apocalypsebleu.moddetective.support.IncidentHistoryRetention;
import fr.apocalypsebleu.moddetective.support.IncidentNotificationCooldown;
import fr.apocalypsebleu.moddetective.support.report.SupportReportData;
import fr.apocalypsebleu.moddetective.support.report.SupportReportExporter;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** Bounded local I/O service for settings, retention, notifications and support reports. */
public final class DetectiveSupportService {
    private static final int QUEUE_CAPACITY = 8;
    private static final AtomicBoolean INITIALIZATION_REQUESTED = new AtomicBoolean();
    private static final AtomicReference<DetectiveSettings> SETTINGS =
            new AtomicReference<>(DetectiveSettings.defaults());
    private static final IncidentNotificationCooldown NOTIFICATION_COOLDOWN =
            new IncidentNotificationCooldown();
    private static final DetectiveSettingsStore SETTINGS_STORE =
            new DetectiveSettingsStore(ModDetectivePaths.settings());
    private static final CaseHistoryService CASE_HISTORY = new CaseHistoryService(
            ModDetectivePaths.incidents(), ModDetectivePaths.caseIndex());
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            task -> {
                Thread thread = new Thread(task, "Detective-Support");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });

    private DetectiveSupportService() {}

    public static void initializeAsync() {
        if (!INITIALIZATION_REQUESTED.compareAndSet(false, true)) {
            return;
        }
        execute(() -> {
            try {
                ModDetectivePaths.ensureDirectories();
                DetectiveSettings settings = SETTINGS_STORE.load();
                SETTINGS.set(settings);
                if (!java.nio.file.Files.isRegularFile(SETTINGS_STORE.target())) {
                    SETTINGS_STORE.save(settings);
                }
                IncidentHistoryRetention.Result result = IncidentHistoryRetention.apply(
                        ModDetectivePaths.incidents(), settings, Instant.now());
                if (result.deleted() > 0) {
                    ModDetective.LOGGER.info("[Detective] Incident retention removed {} old record(s)",
                            result.deleted());
                }
                refreshCases();
            } catch (IOException | RuntimeException e) {
                ModDetective.LOGGER.warn("[Detective] Support settings or retention initialization failed; defaults remain active", e);
            }
        });
    }

    public static DetectiveSettings settings() {
        return SETTINGS.get();
    }

    public static CompletableFuture<DetectiveSettings> updateSettings(UnaryOperator<DetectiveSettings> update) {
        Objects.requireNonNull(update, "update");
        initializeAsync();
        return supply(() -> {
            DetectiveSettings next = Objects.requireNonNull(update.apply(SETTINGS.get()), "updated settings");
            SETTINGS_STORE.save(next);
            SETTINGS.set(next);
            try {
                IncidentHistoryRetention.apply(ModDetectivePaths.incidents(), next, Instant.now());
                refreshCases();
            } catch (IOException e) {
                ModDetective.LOGGER.warn("[Detective] Settings were saved, but incident retention failed", e);
            }
            DetectiveUiService.invalidateIndex();
            return next;
        });
    }

    public static CompletableFuture<IncidentHistoryRetention.Result> clearIncidentHistory() {
        initializeAsync();
        return supply(() -> {
            IncidentHistoryRetention.Result result = IncidentHistoryRetention.clear(ModDetectivePaths.incidents());
            refreshCases();
            DetectiveUiService.invalidateIndex();
            return result;
        });
    }

    public static CompletableFuture<Path> exportSupportReport(Path incidentSource) {
        initializeAsync();
        Path normalized = Objects.requireNonNull(incidentSource, "incidentSource").toAbsolutePath().normalize();
        Path incidentsRoot = ModDetectivePaths.incidents().toAbsolutePath().normalize();
        if (!normalized.startsWith(incidentsRoot)) {
            return CompletableFuture.failedFuture(
                    new IOException("Incident path is outside the Detective data directory"));
        }
        return supply(() -> {
            var detail = IncidentJsonAdapter.readDetail(normalized);
            ModSnapshotDiff diff = ModSnapshotService.latestDiff();
            ModSnapshot snapshot = diff == null ? null : diff.current();
            String minecraftVersion = snapshot == null
                    ? SharedConstants.getCurrentVersion().getName() : snapshot.minecraftVersion();
            String detectiveVersion = modVersion(ModDetective.MOD_ID);
            String neoForgeVersion = modVersion("neoforge");
            SupportReportData report = new SupportReportData(
                    detectiveVersion,
                    minecraftVersion,
                    neoForgeVersion,
                    detail,
                    SupportReportData.installedMods(snapshot),
                    ModpackChangesAdapter.from(diff),
                    SETTINGS.get(),
                    SupportReportData.Environment.capture());
            Path exported = SupportReportExporter.export(report, ModDetectivePaths.reports());
            ModDetective.LOGGER.info("[Detective] Local support report created: {}", exported.getFileName());
            return exported;
        });
    }

    /** Called only after the incident JSON has been persisted successfully. */
    public static void onIncidentRecorded(FreezeIncident incident, Path savedPath) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(savedPath, "savedPath");
        initializeAsync();
        execute(() -> {
            DetectiveSettings settings = SETTINGS.get();
            boolean show = NOTIFICATION_COOLDOWN.register(
                    savedPath.toAbsolutePath().normalize().toString(),
                    System.nanoTime(),
                    settings.incidentNotifications());
            if (show) {
                try {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.execute(() -> IncidentToastNotifier.show(minecraft, incident));
                } catch (RuntimeException e) {
                    ModDetective.LOGGER.debug("[Detective] Incident toast could not be scheduled", e);
                }
            }
            try {
                IncidentHistoryRetention.apply(ModDetectivePaths.incidents(), settings, Instant.now());
                refreshCases();
            } catch (IOException e) {
                ModDetective.LOGGER.warn("[Detective] Incident retention failed after recording an incident", e);
            }
            DetectiveUiService.invalidateIndex();
        });
    }

    public static void shutdown() {
        WORKER.shutdown();
        try {
            if (!WORKER.awaitTermination(2L, TimeUnit.SECONDS)) {
                WORKER.shutdownNow();
            }
        } catch (InterruptedException e) {
            WORKER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static String modVersion(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static void refreshCases() {
        try {
            CaseHistoryService.Result result = CASE_HISTORY.refresh();
            if (result.unreadableIncidents() > 0 || result.unreadablePersistedCases() > 0) {
                ModDetective.LOGGER.debug(
                        "[Detective] Case analysis skipped {} unreadable incident(s) and {} unreadable Case record(s)",
                        result.unreadableIncidents(), result.unreadablePersistedCases());
            }
        } catch (IOException | RuntimeException e) {
            ModDetective.LOGGER.warn("[Detective] Local Case analysis could not be refreshed", e);
        }
    }

    private static void execute(Runnable task) {
        try {
            WORKER.execute(task);
        } catch (RejectedExecutionException e) {
            ModDetective.LOGGER.warn("[Detective] Support worker queue is full or shutting down; task skipped");
        }
    }

    private static <T> CompletableFuture<T> supply(IoSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            WORKER.execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }
}
