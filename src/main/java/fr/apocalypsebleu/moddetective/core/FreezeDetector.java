package fr.apocalypsebleu.moddetective.core;

import fr.apocalypsebleu.moddetective.ModDetective;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

public final class FreezeDetector implements AutoCloseable {
    private static final int BASELINE_WINDOW = 180;
    private static final int MINIMUM_BASELINE_SAMPLES = 30;
    private static final double MAX_BASELINE_FRAME_MS = FreezeThreshold.ABSOLUTE_MINIMUM_MS;
    private static final long INCIDENT_DEBOUNCE_MS = 2_000L;
    private static final int INCIDENT_QUEUE_CAPACITY = 8;

    private final Deque<Double> recentFrames = new ArrayDeque<>();
    private final BlackBoxRecorder blackBox;
    private final RenderThreadWatchdog watchdog;
    private final SuspectAnalyzer analyzer;
    private final boolean metricsEnabled;
    private final ThreadPoolExecutor incidentWorker;
    private final IncidentDebounce debounce = new IncidentDebounce(TimeUnit.MILLISECONDS.toNanos(INCIDENT_DEBOUNCE_MS));
    private final LongAdder droppedIncidents = new LongAdder();
    private final LongAdder processedIncidents = new LongAdder();
    private final LongAdder incidentProcessingNanos = new LongAdder();
    private final AtomicLong maximumIncidentProcessingNanos = new AtomicLong();
    private final BiConsumer<FreezeIncident, Path> incidentRecordedListener;

    public FreezeDetector(BlackBoxRecorder blackBox, RenderThreadWatchdog watchdog, SuspectAnalyzer analyzer) {
        this(blackBox, watchdog, analyzer, false);
    }

    public FreezeDetector(
            BlackBoxRecorder blackBox,
            RenderThreadWatchdog watchdog,
            SuspectAnalyzer analyzer,
            boolean metricsEnabled
    ) {
        this(blackBox, watchdog, analyzer, metricsEnabled, (incident, path) -> {});
    }

    public FreezeDetector(
            BlackBoxRecorder blackBox,
            RenderThreadWatchdog watchdog,
            SuspectAnalyzer analyzer,
            boolean metricsEnabled,
            BiConsumer<FreezeIncident, Path> incidentRecordedListener
    ) {
        this.blackBox = blackBox;
        this.watchdog = watchdog;
        this.analyzer = analyzer;
        this.metricsEnabled = metricsEnabled;
        this.incidentRecordedListener = java.util.Objects.requireNonNull(
                incidentRecordedListener, "incidentRecordedListener");
        this.incidentWorker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(INCIDENT_QUEUE_CAPACITY),
                task -> {
                    Thread thread = new Thread(task, "Detective-IncidentWorker");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
    }

    public void accept(FrameSample frame, long frameStartNanos, long frameEndNanos) {
        boolean enoughBaseline = recentFrames.size() >= MINIMUM_BASELINE_SAMPLES;
        if (enoughBaseline && frame.frameMs() >= FreezeThreshold.ABSOLUTE_MINIMUM_MS) {
            double threshold = FreezeThreshold.calculate(recentFrames);
            if (frame.frameMs() >= threshold && debounce.tryAcquire(frameEndNanos)) {
                submitIncident(frame, threshold,
                        watchdog.between(frameStartNanos, frameEndNanos),
                        blackBox.snapshot());
            }
        }

        // Keep pathological frames out of the baseline so a freeze cannot normalize itself.
        if (Double.isFinite(frame.frameMs()) && frame.frameMs() > 0.0 && frame.frameMs() < MAX_BASELINE_FRAME_MS) {
            recentFrames.addLast(frame.frameMs());
            while (recentFrames.size() > BASELINE_WINDOW) {
                recentFrames.removeFirst();
            }
        }
    }

    public void resetBaseline() {
        recentFrames.clear();
        debounce.reset();
    }

    private void submitIncident(FrameSample frame, double threshold, List<StackSnapshot> stacks, List<FrameSample> history) {
        try {
            incidentWorker.execute(() -> processIncident(frame, threshold, stacks, history));
        } catch (RejectedExecutionException e) {
            droppedIncidents.increment();
            ModDetective.LOGGER.warn("[Detective] Incident processing queue is full or shutting down; report dropped");
        }
    }

    private void processIncident(FrameSample frame, double threshold, List<StackSnapshot> stacks, List<FrameSample> history) {
        long startedNanos = metricsEnabled ? System.nanoTime() : 0L;
        try {
            SuspectAnalyzer.Analysis analysis = analyzer.analyze(stacks);
            AttributionEvidence evidence = AttributionEvidenceClassifier.classify(stacks, analysis);
            FreezeIncident incident = new FreezeIncident(
                    frame.epochMs(),
                    frame.frameMs(),
                    threshold,
                    frame,
                    analysis.stackSamples(),
                    evidence,
                    analysis.suspects(),
                    analysis.hotClasses(),
                    history);

            Path saved = IncidentStore.save(incident);
            logIncident(incident, saved);
            try {
                incidentRecordedListener.accept(incident, saved);
            } catch (RuntimeException listenerFailure) {
                ModDetective.LOGGER.warn("[Detective] Incident was saved, but post-save support handling failed", listenerFailure);
            }
        } catch (IOException | RuntimeException e) {
            ModDetective.LOGGER.error("[Detective] Unable to analyze or save a freeze incident", e);
        } finally {
            if (metricsEnabled) {
                long elapsed = System.nanoTime() - startedNanos;
                processedIncidents.increment();
                incidentProcessingNanos.add(elapsed);
                maximumIncidentProcessingNanos.accumulateAndGet(elapsed, Math::max);
            }
        }
    }

    private static void logIncident(FreezeIncident incident, Path saved) {
        ModDetective.LOGGER.warn("[Detective] Freeze detected: {} ms (threshold {} ms). Saved to {}",
                round(incident.durationMs()), round(incident.thresholdMs()), saved);

        if (incident.attributionEvidence().state() != AttributionEvidence.State.ATTRIBUTED) {
            ModDetective.LOGGER.warn("[Detective] No probable mod attribution from {} watchdog samples (evidence state: {}).",
                    incident.watchdogSamples(), incident.attributionEvidence().state());
            for (SuspectAnalyzer.Suspect observation : incident.suspects()) {
                ModDetective.LOGGER.warn("[Detective] Observation: {} ({}) presence={}/{}% leaf={}/{}% averageDepth={}; confidence does not permit attribution",
                        observation.modName(), observation.modId(), observation.presenceSamples(),
                        round(observation.presenceSharePercent()), observation.leafOwnershipCount(),
                        round(observation.leafOwnershipSharePercent()), round(observation.averageFirstFrameDepth()));
            }
            return;
        }

        for (int i = 0; i < incident.suspects().size(); i++) {
            SuspectAnalyzer.Suspect suspect = incident.suspects().get(i);
            ModDetective.LOGGER.warn("[Detective] Suspect #{}: {} ({}) observed in {}% of freeze samples",
                    i + 1, suspect.modName(), suspect.modId(), round(suspect.presenceSharePercent()));
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public Metrics metrics() {
        long processed = processedIncidents.sum();
        long totalNanos = incidentProcessingNanos.sum();
        return new Metrics(
                incidentWorker.getQueue().size(),
                INCIDENT_QUEUE_CAPACITY,
                droppedIncidents.sum(),
                processed,
                processed == 0L ? 0.0 : totalNanos / (double) processed / 1_000_000.0,
                maximumIncidentProcessingNanos.get() / 1_000_000.0);
    }

    public record Metrics(
            int queueSize,
            int queueCapacity,
            long droppedIncidents,
            long processedIncidents,
            double averageProcessingMs,
            double maximumProcessingMs
    ) {}

    @Override
    public void close() {
        incidentWorker.shutdown();
        try {
            if (!incidentWorker.awaitTermination(2, TimeUnit.SECONDS)) {
                incidentWorker.shutdownNow();
            }
        } catch (InterruptedException e) {
            incidentWorker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
