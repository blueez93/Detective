package fr.apocalypsebleu.detectivevalidation.culprit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import fr.apocalypsebleu.moddetective.client.ClientPerformanceEvents;
import fr.apocalypsebleu.moddetective.core.EngineMetricsSnapshot;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ValidationHarness {
    private static final Gson GSON = new Gson();
    private static final long POSITIVE_TIMEOUT_MS = 8_000L;
    private static final long NEGATIVE_OBSERVATION_MS = 3_000L;
    private static final int MAX_SCHEDULED_ACTIONS = 24;
    private static final Object GROUND_TRUTH_LOCK = new Object();

    private static final ScheduledThreadPoolExecutor ACTIONS = new ScheduledThreadPoolExecutor(
            1, daemonThreadFactory("Detective-Validation-Actions"));
    private static final ThreadPoolExecutor VALIDATOR = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            daemonThreadFactory("Detective-Validation-Worker"));
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    static {
        ACTIONS.setRemoveOnCancelPolicy(true);
    }

    private ValidationHarness() {}

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        ACTIONS.scheduleAtFixedRate(ValidationHarness::logMetricsSafely, 5L, 5L, TimeUnit.SECONDS);
    }

    public static boolean scheduleOnRenderThread(Runnable action, long delayMs) {
        if (ACTIONS.isShutdown() || ACTIONS.getQueue().size() >= MAX_SCHEDULED_ACTIONS) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Action queue is full or shutting down");
            return false;
        }
        ACTIONS.schedule(() -> Minecraft.getInstance().execute(action), delayMs, TimeUnit.MILLISECONDS);
        return true;
    }

    public static Set<Path> captureExistingReports() {
        Path incidents = ModDetectivePaths.incidents();
        if (!Files.isDirectory(incidents)) {
            return Set.of();
        }
        try (var paths = Files.list(incidents)) {
            return Set.copyOf(paths.filter(ValidationHarness::isJsonReport).toList());
        } catch (IOException e) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Could not inventory existing incident reports", e);
            return Set.of();
        }
    }

    public static void validate(
            ControlledFreezeGenerator.GroundTruth truth,
            boolean incidentExpected,
            Set<Path> reportsBeforeStall
    ) {
        try {
            VALIDATOR.execute(() -> validateOnWorker(truth, incidentExpected, Set.copyOf(reportsBeforeStall)));
        } catch (RejectedExecutionException e) {
            DetectiveTestCulprit.LOGGER.error("[Detective Validation] Validation queue is full; result dropped", e);
        }
    }

    public static void logMetrics() {
        logMetricsSafely();
    }

    public static void shutdown() {
        ACTIONS.shutdownNow();
        VALIDATOR.shutdown();
        try {
            if (!VALIDATOR.awaitTermination(2L, TimeUnit.SECONDS)) {
                VALIDATOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            VALIDATOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static void validateOnWorker(
            ControlledFreezeGenerator.GroundTruth truth,
            boolean incidentExpected,
            Set<Path> reportsBeforeStall
    ) {
        appendGroundTruth(new GroundTruthLine(truth, incidentExpected));
        IncidentJson incident = awaitIncident(truth, reportsBeforeStall,
                incidentExpected ? POSITIVE_TIMEOUT_MS : NEGATIVE_OBSERVATION_MS);

        ValidationResultEvaluator.Detected detected = incident == null ? null : toDetected(incident);
        ValidationResultEvaluator.Result result = ValidationResultEvaluator.evaluate(
                new ValidationResultEvaluator.Expected(
                        truth.expectedModId(), truth.requestedDurationMs(), incidentExpected),
                detected);

        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] VALIDATION\nScenario: {}\nRequested: {} ms\nExpected: {}\nDetected #1: {}\nExpected rank: {}\nExpected share: {}%\nResult: {}\nDetails: {}",
                truth.scenarioId(),
                truth.requestedDurationMs(),
                truth.expectedModId(),
                result.detectedTopSuspect(),
                result.expectedCulpritRank() == 0 ? "absent" : result.expectedCulpritRank(),
                round(result.expectedCulpritSharePercent()),
                result.passed() ? "PASS" : "FAIL",
                result.reason());
        if (incident != null) {
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] INCIDENT duration={} ms threshold={} ms watchdogSamples={} blackBoxSamples={} dimension={} position=({}, {}, {})",
                    round(incident.durationMs()),
                    round(incident.thresholdMs()),
                    incident.watchdogSamples(),
                    incident.blackBox() == null ? 0 : incident.blackBox().size(),
                    incident.frame() == null ? "<missing>" : incident.frame().dimension(),
                    incident.frame() == null ? null : incident.frame().playerX(),
                    incident.frame() == null ? null : incident.frame().playerY(),
                    incident.frame() == null ? null : incident.frame().playerZ());
        }
        logMetricsSafely();
    }

    private static IncidentJson awaitIncident(
            ControlledFreezeGenerator.GroundTruth truth,
            Set<Path> reportsBeforeStall,
            long timeoutMs
    ) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        do {
            IncidentJson match = findMatchingIncident(truth, reportsBeforeStall);
            if (match != null) {
                return match;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.nanoTime() < deadline);
        return null;
    }

    private static IncidentJson findMatchingIncident(
            ControlledFreezeGenerator.GroundTruth truth,
            Set<Path> reportsBeforeStall
    ) {
        Path incidents = ModDetectivePaths.incidents();
        if (!Files.isDirectory(incidents)) {
            return null;
        }

        List<Path> candidates;
        try (var paths = Files.list(incidents)) {
            candidates = paths
                    .filter(ValidationHarness::isJsonReport)
                    .filter(path -> !reportsBeforeStall.contains(path))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            return null;
        }

        for (Path candidate : candidates) {
            try {
                IncidentJson incident = GSON.fromJson(Files.readString(candidate, StandardCharsets.UTF_8), IncidentJson.class);
                if (incident != null
                        && incident.detectedAtEpochMs() >= truth.endEpochMs() - 50L
                        && incident.detectedAtEpochMs() <= truth.endEpochMs() + 2_000L) {
                    return incident;
                }
            } catch (IOException | JsonParseException e) {
                DetectiveTestCulprit.LOGGER.debug("[Detective Validation] Incident report is not readable yet: {}", candidate, e);
            }
        }
        return null;
    }

    private static ValidationResultEvaluator.Detected toDetected(IncidentJson incident) {
        List<ValidationResultEvaluator.Suspect> suspects = new ArrayList<>();
        if (incident.suspects() != null) {
            for (SuspectJson suspect : incident.suspects()) {
                suspects.add(new ValidationResultEvaluator.Suspect(suspect.modId(), suspect.sampleSharePercent()));
            }
        }
        FrameJson frame = incident.frame();
        boolean hasWorldLocation = frame != null
                && frame.dimension() != null
                && !frame.dimension().isBlank()
                && !"menu".equals(frame.dimension())
                && frame.playerX() != null
                && frame.playerY() != null
                && frame.playerZ() != null;
        return new ValidationResultEvaluator.Detected(
                incident.durationMs(),
                incident.watchdogSamples(),
                incident.blackBox() == null ? 0 : incident.blackBox().size(),
                hasWorldLocation,
                suspects);
    }

    private static void appendGroundTruth(GroundTruthLine line) {
        Path file = FMLPaths.GAMEDIR.get()
                .resolve("detective-validation")
                .resolve("ground-truth.jsonl")
                .toAbsolutePath()
                .normalize();
        synchronized (GROUND_TRUTH_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(
                        file,
                        GSON.toJson(line) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                DetectiveTestCulprit.LOGGER.error("[Detective Validation] Could not persist ground truth", e);
            }
        }
    }

    private static void logMetricsSafely() {
        try {
            EngineMetricsSnapshot metrics = ClientPerformanceEvents.diagnostics();
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] OVERHEAD samples/s={} captureAvg={} us captureMax={} us retainedStacks={} blackBox={} workerQueue={}/{} dropped={} incidentsProcessed={} incidentAvg={} ms incidentMax={} ms",
                    round(metrics.watchdogSamplesPerSecond()),
                    round(metrics.averageWatchdogCaptureMicros()),
                    round(metrics.maximumWatchdogCaptureMicros()),
                    metrics.retainedWatchdogSamples(),
                    metrics.blackBoxSamples(),
                    metrics.incidentWorkerQueueSize(),
                    metrics.incidentWorkerQueueCapacity(),
                    metrics.droppedIncidents(),
                    metrics.processedIncidents(),
                    round(metrics.averageIncidentProcessingMs()),
                    round(metrics.maximumIncidentProcessingMs()));
        } catch (RuntimeException e) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Could not capture Detective overhead metrics", e);
        }
    }

    private static ThreadFactory daemonThreadFactory(String baseName) {
        return new ThreadFactory() {
            private int nextId;

            @Override
            public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, baseName + '-' + ++nextId);
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        };
    }

    private static boolean isJsonReport(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record GroundTruthLine(ControlledFreezeGenerator.GroundTruth groundTruth, boolean incidentExpected) {}

    private record IncidentJson(
            long detectedAtEpochMs,
            double durationMs,
            double thresholdMs,
            FrameJson frame,
            int watchdogSamples,
            List<SuspectJson> suspects,
            List<Object> blackBox
    ) {}

    private record FrameJson(String dimension, Integer playerX, Integer playerY, Integer playerZ) {}

    private record SuspectJson(String modId, double sampleSharePercent) {}
}
