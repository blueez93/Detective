package fr.apocalypsebleu.detectivevalidation.culprit;

import net.minecraft.client.Minecraft;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class ControlledFreezeGenerator {
    private ControlledFreezeGenerator() {}

    public static GroundTruth stallRenderThread(long requestedDurationMs, String scenarioId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Controlled freezes must run on the Minecraft render thread");
        }
        if (minecraft.level == null || minecraft.player == null) {
            throw new IllegalStateException("Controlled freezes are only allowed while a world is loaded");
        }
        if (requestedDurationMs <= 0L) {
            throw new IllegalArgumentException("requestedDurationMs must be positive");
        }

        long startEpochMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        long requestedNanos = TimeUnit.MILLISECONDS.toNanos(requestedDurationMs);
        long deadline = startNanos + requestedNanos;
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) {
            LockSupport.parkNanos(remaining);
        }
        long endNanos = System.nanoTime();
        long endEpochMs = System.currentTimeMillis();

        return new GroundTruth(
                scenarioId,
                DetectiveTestCulprit.MOD_ID,
                requestedDurationMs,
                startNanos,
                endNanos,
                startEpochMs,
                endEpochMs);
    }

    public record GroundTruth(
            String scenarioId,
            String expectedModId,
            long requestedDurationMs,
            long startNanos,
            long endNanos,
            long startEpochMs,
            long endEpochMs
    ) {}
}
