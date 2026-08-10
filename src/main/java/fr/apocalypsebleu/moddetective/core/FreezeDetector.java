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

public final class FreezeDetector implements AutoCloseable {
    private static final int BASELINE_WINDOW = 180;
    private static final int MINIMUM_BASELINE_SAMPLES = 30;
    private static final double MAX_BASELINE_FRAME_MS = FreezeThreshold.ABSOLUTE_MINIMUM_MS;
    private static final long INCIDENT_DEBOUNCE_MS = 2_000L;

    private final Deque<Double> recentFrames = new ArrayDeque<>();
    private final BlackBoxRecorder blackBox;
    private final RenderThreadWatchdog watchdog;
    private final SuspectAnalyzer analyzer;
    private final ThreadPoolExecutor incidentWorker;

    private long lastIncidentEndNanos = Long.MIN_VALUE;

    public FreezeDetector(BlackBoxRecorder blackBox, RenderThreadWatchdog watchdog, SuspectAnalyzer analyzer) {
        this.blackBox = blackBox;
        this.watchdog = watchdog;
        this.analyzer = analyzer;
        this.incidentWorker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8),
                task -> {
                    Thread thread = new Thread(task, "ModDetective-IncidentWorker");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
    }

    public void accept(FrameSample frame, long frameStartNanos, long frameEndNanos) {
        boolean enoughBaseline = recentFrames.size() >= MINIMUM_BASELINE_SAMPLES;
        boolean debounced = lastIncidentEndNanos == Long.MIN_VALUE
                || frameEndNanos - lastIncidentEndNanos >= TimeUnit.MILLISECONDS.toNanos(INCIDENT_DEBOUNCE_MS);

        if (enoughBaseline && debounced && frame.frameMs() >= FreezeThreshold.ABSOLUTE_MINIMUM_MS) {
            double threshold = FreezeThreshold.calculate(recentFrames);
            if (frame.frameMs() >= threshold) {
                lastIncidentEndNanos = frameEndNanos;
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
        lastIncidentEndNanos = Long.MIN_VALUE;
    }

    private void submitIncident(FrameSample frame, double threshold, List<StackSnapshot> stacks, List<FrameSample> history) {
        try {
            incidentWorker.execute(() -> processIncident(frame, threshold, stacks, history));
        } catch (RejectedExecutionException e) {
            ModDetective.LOGGER.warn("[Mod Detective] Incident processing queue is full or shutting down; report dropped", e);
        }
    }

    private void processIncident(FrameSample frame, double threshold, List<StackSnapshot> stacks, List<FrameSample> history) {
        try {
            SuspectAnalyzer.Analysis analysis = analyzer.analyze(stacks);
            FreezeIncident incident = new FreezeIncident(
                    frame.epochMs(),
                    frame.frameMs(),
                    threshold,
                    frame,
                    analysis.stackSamples(),
                    analysis.suspects(),
                    analysis.hotClasses(),
                    history);

            Path saved = IncidentStore.save(incident);
            logIncident(incident, saved);
        } catch (IOException | RuntimeException e) {
            ModDetective.LOGGER.error("[Mod Detective] Unable to analyze or save a freeze incident", e);
        }
    }

    private static void logIncident(FreezeIncident incident, Path saved) {
        ModDetective.LOGGER.warn("[Mod Detective] Freeze detected: {} ms (threshold {} ms). Saved to {}",
                round(incident.durationMs()), round(incident.thresholdMs()), saved);

        if (incident.suspects().isEmpty()) {
            ModDetective.LOGGER.warn("[Mod Detective] No non-vanilla mod could be attributed from {} watchdog samples.", incident.watchdogSamples());
            return;
        }

        for (int i = 0; i < incident.suspects().size(); i++) {
            SuspectAnalyzer.Suspect suspect = incident.suspects().get(i);
            ModDetective.LOGGER.warn("[Mod Detective] Suspect #{}: {} ({}) observed in {}% of freeze samples",
                    i + 1, suspect.modName(), suspect.modId(), round(suspect.sampleSharePercent()));
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

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
