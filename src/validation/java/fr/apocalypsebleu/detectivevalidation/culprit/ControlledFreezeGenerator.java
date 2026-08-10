package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.detectivevalidation.culprita.DetectiveTestCulpritA;
import fr.apocalypsebleu.detectivevalidation.culprita.DirectStall;
import fr.apocalypsebleu.detectivevalidation.culpritb.DetectiveTestCulpritB;
import fr.apocalypsebleu.detectivevalidation.culpritb.LibraryStall;
import fr.apocalypsebleu.detectivevalidation.culpritc.DetectiveTestCulpritC;
import fr.apocalypsebleu.detectivevalidation.culpritc.StandardLibraryStall;
import net.minecraft.client.Minecraft;

public final class ControlledFreezeGenerator {
    private ControlledFreezeGenerator() {}

    public static GroundTruth stallRenderThread(long requestedDurationMs, String scenarioId) {
        return stallRenderThread(requestedDurationMs, scenarioId, Path.DIRECT_A);
    }

    public static GroundTruth stallRenderThread(long requestedDurationMs, String scenarioId, Path path) {
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
        path.block(requestedDurationMs);
        long endNanos = System.nanoTime();
        long endEpochMs = System.currentTimeMillis();

        return new GroundTruth(
                scenarioId,
                path.expectedModId(),
                path.name(),
                requestedDurationMs,
                startNanos,
                endNanos,
                startEpochMs,
                endEpochMs);
    }

    public static GroundTruth completedGroundTruth(
            String scenarioId,
            Path path,
            long requestedDurationMs,
            long startNanos,
            long endNanos,
            long startEpochMs,
            long endEpochMs
    ) {
        return new GroundTruth(
                scenarioId,
                path.expectedModId(),
                path.name(),
                requestedDurationMs,
                startNanos,
                endNanos,
                startEpochMs,
                endEpochMs);
    }

    public enum Path {
        DIRECT_A(DetectiveTestCulpritA.MOD_ID) {
            @Override
            void block(long durationMs) {
                DirectStall.block(durationMs);
            }
        },
        DIRECT_B(DetectiveTestCulpritB.MOD_ID) {
            @Override
            void block(long durationMs) {
                LibraryStall.block(durationMs);
            }
        },
        SCHEDULED_STANDARD_C(DetectiveTestCulpritC.MOD_ID) {
            @Override
            void block(long durationMs) {
                StandardLibraryStall.block(durationMs);
            }
        },
        INDIRECT_A_TO_B(DetectiveTestCulpritB.MOD_ID) {
            @Override
            void block(long durationMs) {
                callLibraryB(durationMs);
            }
        },
        NESTED_A_TO_B_TO_C(DetectiveTestCulpritC.MOD_ID) {
            @Override
            void block(long durationMs) {
                callLibraryBThenC(durationMs);
            }
        };

        private final String expectedModId;

        Path(String expectedModId) {
            this.expectedModId = expectedModId;
        }

        abstract void block(long durationMs);

        public String expectedModId() {
            return expectedModId;
        }
    }

    private static void callLibraryB(long durationMs) {
        LibraryStall.block(durationMs);
    }

    private static void callLibraryBThenC(long durationMs) {
        LibraryStall.delegateToC(durationMs);
    }

    public record GroundTruth(
            String scenarioId,
            String expectedModId,
            String path,
            long requestedDurationMs,
            long startNanos,
            long endNanos,
            long startEpochMs,
            long endEpochMs
    ) {}
}
