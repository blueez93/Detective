package fr.apocalypsebleu.moddetective.core;

import fr.apocalypsebleu.moddetective.ModDetective;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RenderThreadWatchdog {
    private static final long SAMPLE_INTERVAL_MS = 20L;
    private static final long RETENTION_NANOS = 12_000_000_000L;

    private final Deque<StackSnapshot> samples = new ArrayDeque<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread renderThread;
    private volatile Thread samplerThread;

    public synchronized void start(Thread renderThread) {
        if (renderThread == null) {
            throw new IllegalArgumentException("renderThread must not be null");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        this.renderThread = renderThread;
        Thread sampler = new Thread(this::runLoop, "ModDetective-Watchdog");
        sampler.setDaemon(true);
        sampler.setPriority(Thread.NORM_PRIORITY - 1);
        sampler.setUncaughtExceptionHandler((thread, error) ->
                ModDetective.LOGGER.error("[Mod Detective] Watchdog thread stopped unexpectedly", error));
        this.samplerThread = sampler;
        sampler.start();
        ModDetective.LOGGER.info("[Mod Detective] Watchdog attached to render thread '{}'", renderThread.getName());
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
            } catch (SecurityException e) {
                running.compareAndSet(true, false);
                ModDetective.LOGGER.warn("[Mod Detective] Watchdog cannot sample the render thread", e);
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
        }
        if (wasRunning || sampler != null) {
            ModDetective.LOGGER.info("[Mod Detective] Watchdog stopped");
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
}
