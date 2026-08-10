package fr.apocalypsebleu.moddetective.client;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.core.BlackBoxRecorder;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeDetector;
import fr.apocalypsebleu.moddetective.core.EngineMetricsSnapshot;
import fr.apocalypsebleu.moddetective.core.RenderThreadWatchdog;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@EventBusSubscriber(modid = ModDetective.MOD_ID, value = Dist.CLIENT)
public final class ClientPerformanceEvents {
    private static final double MAX_USEFUL_FRAME_MS = 10_000.0;
    private static final long FAILURE_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final boolean DEVELOPMENT_METRICS = Boolean.getBoolean("detective.validation.enabled");

    private static final BlackBoxRecorder BLACK_BOX = new BlackBoxRecorder();
    private static final RenderThreadWatchdog WATCHDOG = new RenderThreadWatchdog(DEVELOPMENT_METRICS);
    private static final FreezeDetector FREEZE_DETECTOR = new FreezeDetector(
            BLACK_BOX, WATCHDOG, new SuspectAnalyzer(), DEVELOPMENT_METRICS);

    private static boolean watchdogStartAttempted;
    private static boolean gameplayActive;
    private static long previousFrameNanos;
    private static long lastFailureLogNanos = Long.MIN_VALUE;

    private ClientPerformanceEvents() {}

    @SubscribeEvent
    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        long nowNanos = System.nanoTime();

        try {
            recordFrame(nowNanos);
        } catch (RuntimeException e) {
            if (lastFailureLogNanos == Long.MIN_VALUE || nowNanos - lastFailureLogNanos >= FAILURE_LOG_INTERVAL_NANOS) {
                lastFailureLogNanos = nowNanos;
                ModDetective.LOGGER.error("[Detective] Performance sampling failed; the game will continue", e);
            }
        }
    }

    private static void recordFrame(long nowNanos) {
        if (!watchdogStartAttempted) {
            watchdogStartAttempted = true;
            WATCHDOG.start(Thread.currentThread());
        }

        if (previousFrameNanos == 0L) {
            previousFrameNanos = nowNanos;
            return;
        }

        long frameStartNanos = previousFrameNanos;
        previousFrameNanos = nowNanos;
        double frameMs = (nowNanos - frameStartNanos) / 1_000_000.0;

        Minecraft minecraft = Minecraft.getInstance();
        // Pauses, lost focus, breakpoints and very long suspensions are not useful gameplay incidents.
        if (minecraft.isPaused() || !minecraft.isWindowActive() || frameMs <= 0.0 || frameMs > MAX_USEFUL_FRAME_MS) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        String dimension = "menu";
        int x = 0;
        int y = 0;
        int z = 0;

        if (minecraft.level != null) {
            dimension = minecraft.level.dimension().location().toString();
        }
        if (minecraft.player != null) {
            var pos = minecraft.player.blockPosition();
            x = pos.getX();
            y = pos.getY();
            z = pos.getZ();
        }

        FrameSample frame = new FrameSample(
                System.currentTimeMillis(),
                nowNanos,
                frameMs,
                1000.0 / frameMs,
                usedMemory,
                maxMemory,
                dimension,
                x,
                y,
                z);

        BLACK_BOX.add(frame);
        boolean gameplayNow = minecraft.level != null && minecraft.player != null;
        if (gameplayNow) {
            gameplayActive = true;
            FREEZE_DETECTOR.accept(frame, frameStartNanos, nowNanos);
        } else if (gameplayActive) {
            gameplayActive = false;
            FREEZE_DETECTOR.resetBaseline();
        }
    }

    public static EngineMetricsSnapshot diagnostics() {
        RenderThreadWatchdog.Metrics watchdog = WATCHDOG.metrics();
        FreezeDetector.Metrics detector = FREEZE_DETECTOR.metrics();
        return new EngineMetricsSnapshot(
                watchdog.samples(),
                watchdog.samplesPerSecond(),
                watchdog.averageCaptureMicros(),
                watchdog.maximumCaptureMicros(),
                watchdog.retainedSamples(),
                BLACK_BOX.size(),
                detector.queueSize(),
                detector.queueCapacity(),
                detector.droppedIncidents(),
                detector.processedIncidents(),
                detector.averageProcessingMs(),
                detector.maximumProcessingMs());
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        WATCHDOG.stop();
        FREEZE_DETECTOR.close();
    }
}
