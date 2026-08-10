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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public final class ValidationHarness {
    private static final Gson GSON = new Gson();
    private static final long POSITIVE_TIMEOUT_MS = 8_000L;
    private static final long NEGATIVE_OBSERVATION_MS = 3_000L;
    private static final int MAX_SCHEDULED_ACTIONS = 64;
    private static final Object GROUND_TRUTH_LOCK = new Object();
    private static final Object METRICS_LOCK = new Object();

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
    private static final AtomicReference<PhaseObservation> CURRENT_PHASE = new AtomicReference<>();
    private static final LongAdder POSITIVE_VALIDATIONS = new LongAdder();
    private static final LongAdder TOP_1_MATCHES = new LongAdder();
    private static final LongAdder TOP_3_MATCHES = new LongAdder();

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
        validate(truth, incidentExpected, 1, reportsBeforeStall);
    }

    public static void validate(
            ControlledFreezeGenerator.GroundTruth truth,
            boolean incidentExpected,
            int maximumAcceptedRank,
            Set<Path> reportsBeforeStall
    ) {
        try {
            VALIDATOR.execute(() -> validateOnWorker(
                    truth, incidentExpected, maximumAcceptedRank, Set.copyOf(reportsBeforeStall)));
        } catch (RejectedExecutionException e) {
            DetectiveTestCulprit.LOGGER.error("[Detective Validation] Validation queue is full; result dropped", e);
        }
    }

    public static void logMetrics() {
        logMetricsSafely();
    }

    public static void beginPhase(String phase, boolean falsePositiveEligible) {
        Objects.requireNonNull(phase, "phase");
        PhaseObservation next = new PhaseObservation(
                phase,
                falsePositiveEligible,
                System.currentTimeMillis(),
                System.nanoTime(),
                captureExistingReports().size());
        PhaseObservation previous = CURRENT_PHASE.getAndSet(next);
        if (previous != null) {
            finishPhase(previous);
        }
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] PHASE {} started (falsePositiveEligible={})",
                phase, falsePositiveEligible);
    }

    public static void shutdown() {
        PhaseObservation phase = CURRENT_PHASE.getAndSet(null);
        if (phase != null) {
            finishPhase(phase);
        }
        logAccuracy();
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
            int maximumAcceptedRank,
            Set<Path> reportsBeforeStall
    ) {
        appendGroundTruth(new GroundTruthLine(truth, incidentExpected));
        IncidentJson incident = awaitIncident(truth, reportsBeforeStall,
                incidentExpected ? POSITIVE_TIMEOUT_MS : NEGATIVE_OBSERVATION_MS);

        ValidationResultEvaluator.Detected detected = incident == null ? null : toDetected(incident);
        ValidationResultEvaluator.Result result = ValidationResultEvaluator.evaluate(
                new ValidationResultEvaluator.Expected(
                        truth.expectedModId(), truth.requestedDurationMs(), incidentExpected, maximumAcceptedRank),
                detected);

        if (incidentExpected) {
            POSITIVE_VALIDATIONS.increment();
            if (result.top1Match()) {
                TOP_1_MATCHES.increment();
            }
            if (result.top3Match()) {
                TOP_3_MATCHES.increment();
            }
        }

        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] VALIDATION\nScenario: {}\nPath: {}\nRequested: {} ms\nExpected: {}\nDetected #1: {}\nExpected rank: {}\nExpected share: {}%\nTop-1: {}\nTop-3: {}\nResult: {}\nDetails: {}",
                truth.scenarioId(),
                truth.path(),
                truth.requestedDurationMs(),
                truth.expectedModId(),
                result.detectedTopSuspect(),
                result.expectedCulpritRank() == 0 ? "absent" : result.expectedCulpritRank(),
                round(result.expectedCulpritSharePercent()),
                result.top1Match() ? "PASS" : "FAIL",
                result.top3Match() ? "PASS" : "FAIL",
                result.passed() ? "PASS" : "FAIL",
                result.reason());
        if (incident != null) {
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] INCIDENT duration={} ms threshold={} ms watchdogSamples={} evidence={} blackBoxSamples={} dimension={} position=({}, {}, {})",
                    round(incident.durationMs()),
                    round(incident.thresholdMs()),
                    incident.watchdogSamples(),
                    incident.attributionEvidence() == null ? "<missing>" : incident.attributionEvidence().state(),
                    incident.blackBox() == null ? 0 : incident.blackBox().size(),
                    incident.frame() == null ? "<missing>" : incident.frame().dimension(),
                    incident.frame() == null ? null : incident.frame().playerX(),
                    incident.frame() == null ? null : incident.frame().playerY(),
                    incident.frame() == null ? null : incident.frame().playerZ());
        }
        logMetricsSafely();
        logAccuracy();
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
            Runtime runtime = Runtime.getRuntime();
            long usedHeapBytes = runtime.totalMemory() - runtime.freeMemory();
            long estimatedRetainedBytes = estimateRetainedBytes(metrics);
            String phase = currentPhaseName();
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] OVERHEAD phase={} samples/s={} captureAvg={} us p50={} us p95={} us p99={} us captureMax={} us latencyWindow={} retainedStacks={} retainedFrames={} blackBox={} workerQueue={}/{} dropped={} incidentsProcessed={} incidentAvg={} ms incidentMax={} ms jvmHeapMiB={} detectiveRetainedEstimateKiB={}",
                    phase,
                    round(metrics.watchdogSamplesPerSecond()),
                    round(metrics.averageWatchdogCaptureMicros()),
                    round(metrics.p50WatchdogCaptureMicros()),
                    round(metrics.p95WatchdogCaptureMicros()),
                    round(metrics.p99WatchdogCaptureMicros()),
                    round(metrics.maximumWatchdogCaptureMicros()),
                    metrics.watchdogLatencyWindowSamples(),
                    metrics.retainedWatchdogSamples(),
                    metrics.retainedWatchdogFrames(),
                    metrics.blackBoxSamples(),
                    metrics.incidentWorkerQueueSize(),
                    metrics.incidentWorkerQueueCapacity(),
                    metrics.droppedIncidents(),
                    metrics.processedIncidents(),
                    round(metrics.averageIncidentProcessingMs()),
                    round(metrics.maximumIncidentProcessingMs()),
                    round(usedHeapBytes / 1024.0 / 1024.0),
                    round(estimatedRetainedBytes / 1024.0));
            appendJsonLine(metricsFile(), new MetricLine(
                    System.currentTimeMillis(),
                    System.nanoTime(),
                    phase,
                    usedHeapBytes,
                    estimatedRetainedBytes,
                    metrics));
        } catch (RuntimeException e) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Could not capture Detective overhead metrics", e);
        }
    }

    private static long estimateRetainedBytes(EngineMetricsSnapshot metrics) {
        // Conservative shallow-retention model; this is deliberately labelled as an estimate.
        long blackBox = metrics.blackBoxSamples() * 128L;
        long stacks = metrics.retainedWatchdogSamples() * 32L;
        long frames = metrics.retainedWatchdogFrames() * 160L;
        long queuedReferences = metrics.incidentWorkerQueueSize()
                * (64L + metrics.blackBoxSamples() * 8L + metrics.retainedWatchdogSamples() * 8L);
        return blackBox + stacks + frames + queuedReferences;
    }

    private static String currentPhaseName() {
        PhaseObservation phase = CURRENT_PHASE.get();
        return phase == null ? "startup" : phase.name();
    }

    private static void finishPhase(PhaseObservation phase) {
        int incidentDelta = Math.max(0, captureExistingReports().size() - phase.incidentsAtStart());
        PhaseResult result = new PhaseResult(
                phase.name(),
                phase.falsePositiveEligible(),
                phase.startedEpochMs(),
                System.currentTimeMillis(),
                Math.max(0L, System.nanoTime() - phase.startedNanos()),
                incidentDelta);
        appendJsonLine(phaseFile(), result);
        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] PHASE_RESULT phase={} durationMs={} incidents={} falsePositiveIncidents={}",
                result.name(),
                round(result.durationNanos() / 1_000_000.0),
                result.incidentsCreated(),
                result.falsePositiveEligible() ? result.incidentsCreated() : 0);
    }

    private static void logAccuracy() {
        long total = POSITIVE_VALIDATIONS.sum();
        double top1 = total == 0L ? 0.0 : TOP_1_MATCHES.sum() * 100.0 / total;
        double top3 = total == 0L ? 0.0 : TOP_3_MATCHES.sum() * 100.0 / total;
        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] ACCURACY validations={} top1={}/{} ({}%) top3={}/{} ({}%)",
                total, TOP_1_MATCHES.sum(), total, round(top1), TOP_3_MATCHES.sum(), total, round(top3));
    }

    private static Path metricsFile() {
        return validationDirectory().resolve("overhead-metrics.jsonl");
    }

    private static Path phaseFile() {
        return validationDirectory().resolve("phase-results.jsonl");
    }

    private static Path validationDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("detective-validation").toAbsolutePath().normalize();
    }

    private static void appendJsonLine(Path file, Object line) {
        synchronized (METRICS_LOCK) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(
                        file,
                        GSON.toJson(line) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Could not append {}", file, e);
            }
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
            AttributionEvidenceJson attributionEvidence,
            List<SuspectJson> suspects,
            List<Object> blackBox
    ) {}

    private record FrameJson(String dimension, Integer playerX, Integer playerY, Integer playerZ) {}

    private record SuspectJson(String modId, double sampleSharePercent) {}
    private record AttributionEvidenceJson(String state) {}
    private record MetricLine(
            long epochMs,
            long nanoTime,
            String phase,
            long jvmUsedHeapBytes,
            long detectiveRetainedEstimateBytes,
            EngineMetricsSnapshot engine
    ) {}
    private record PhaseObservation(
            String name,
            boolean falsePositiveEligible,
            long startedEpochMs,
            long startedNanos,
            int incidentsAtStart
    ) {}
    private record PhaseResult(
            String name,
            boolean falsePositiveEligible,
            long startedEpochMs,
            long endedEpochMs,
            long durationNanos,
            int incidentsCreated
    ) {}
}
