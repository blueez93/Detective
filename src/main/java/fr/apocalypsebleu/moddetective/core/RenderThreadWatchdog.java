package fr.apocalypsebleu.moddetective.core;

import fr.apocalypsebleu.moddetective.ModDetective;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RenderThreadWatchdog {
    private static final long SAMPLE_INTERVAL_MS = 20L;
    private static final long RETENTION_NANOS = 12_000_000_000L;
    private static final int LATENCY_WINDOW_SAMPLES = 4_096;

    private final Deque<StackSnapshot> samples = new ArrayDeque<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean metricsEnabled;
    private final RollingLatencyStatistics latencyWindow;
    private final LongAdder capturedSamples = new LongAdder();
    private final LongAdder captureNanos = new LongAdder();
    private final AtomicLong maximumCaptureNanos = new AtomicLong();
    private volatile Thread renderThread;
    private volatile Thread samplerThread;
    private volatile long metricsStartedNanos;

    public RenderThreadWatchdog() {
        this(false);
    }

    public RenderThreadWatchdog(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        this.latencyWindow = metricsEnabled ? new RollingLatencyStatistics(LATENCY_WINDOW_SAMPLES) : null;
    }

    public synchronized void start(Thread renderThread) {
        if (renderThread == null) {
            throw new IllegalArgumentException("renderThread must not be null");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        this.renderThread = renderThread;
        Thread sampler = new Thread(this::runLoop, "Detective-Watchdog");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.NORM_PRIORITY - 1);
        sampler.setUncaughtExceptionHandler((thread, error) ->
                ModDetective.LOGGER.error("[Detective] Watchdog thread stopped unexpectedly", error));
        this.samplerThread = sampler;
        sampler.start();
        ModDetective.LOGGER.info("[Detective] Watchdog attached to render thread '{}'", renderThread.getName());
    }

    private void runLoop() {
        Thread currentSampler = Thread.currentThread();
        while (running.get() && samplerThread == currentSampler) {
            Thread thread = this.renderThread;
            if (thread == null || !thread.isAlive()) {
                running.compareAndSet(true, false);
                return;
            }

            try {
                long now = System.nanoTime();
                if (metricsEnabled && metricsStartedNanos == 0L) {
                    metricsStartedNanos = now;
                }
                StackTraceElement[] stack = thread.getStackTrace();
                if (stack.length > 0) {
                    synchronized (samples) {
                        samples.addLast(new StackSnapshot(now, stack));
                        long cutoff = now - RETENTION_NANOS;
                        while (!samples.isEmpty() && samples.peekFirst().nanoTime() < cutoff) {
                            samples.removeFirst();
                        }
                    }
                }
                if (metricsEnabled) {
                    long elapsed = System.nanoTime() - now;
                    capturedSamples.increment();
                    captureNanos.add(elapsed);
                    maximumCaptureNanos.accumulateAndGet(elapsed, Math::max);
                    latencyWindow.record(elapsed);
                }
            } catch (SecurityException e) {
                running.compareAndSet(true, false);
                ModDetective.LOGGER.warn("[Detective] Watchdog cannot sample the render thread", e);
                return;
            }

            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public synchronized void stop() {
        boolean wasRunning = running.getAndSet(false);
        Thread sampler = samplerThread;
        samplerThread = null;
        renderThread = null;
        if (sampler != null) {
            sampler.interrupt();
            if (sampler != Thread.currentThread()) {
                try {
                    sampler.join(1_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (wasRunning || sampler != null) {
            ModDetective.LOGGER.info("[Detective] Watchdog stopped");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<StackSnapshot> between(long startNanos, long endNanos) {
        synchronized (samples) {
            List<StackSnapshot> result = new ArrayList<>();
            for (StackSnapshot sample : samples) {
                if (sample.nanoTime() >= startNanos && sample.nanoTime() <= endNanos) {
                    result.add(sample);
                }
            }
            return List.copyOf(result);
        }
    }

    public Metrics metrics() {
        long count = capturedSamples.sum();
        long totalNanos = captureNanos.sum();
        long started = metricsStartedNanos;
        double elapsedSeconds = started == 0L ? 0.0 : Math.max(0L, System.nanoTime() - started) / 1_000_000_000.0;
        int retained;
        int retainedFrames = 0;
        synchronized (samples) {
            retained = samples.size();
            for (StackSnapshot sample : samples) {
                retainedFrames += sample.stack().length;
            }
        }
        RollingLatencyStatistics.Snapshot latency = metricsEnabled
                ? latencyWindow.snapshot()
                : new RollingLatencyStatistics.Snapshot(0, 0L, 0L, 0L);
        return new Metrics(
                count,
                elapsedSeconds == 0.0 ? 0.0 : count / elapsedSeconds,
                count == 0L ? 0.0 : totalNanos / (double) count / 1_000.0,
                latency.p50Nanos() / 1_000.0,
                latency.p95Nanos() / 1_000.0,
                latency.p99Nanos() / 1_000.0,
                maximumCaptureNanos.get() / 1_000.0,
                latency.samples(),
                retained,
                retainedFrames);
    }

    public record Metrics(
            long samples,
            double samplesPerSecond,
            double averageCaptureMicros,
            double p50CaptureMicros,
            double p95CaptureMicros,
            double p99CaptureMicros,
            double maximumCaptureMicros,
            int latencyWindowSamples,
            int retainedSamples,
            int retainedStackFrames
    ) {}
}
