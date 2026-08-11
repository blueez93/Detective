package fr.apocalypsebleu.detectivevalidation.culprit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Development-only repeated world leave/reconnect validation. */
final class LifecycleCyclePlan {
    private static final int TARGET_CYCLES = 20;
    private static final long CONNECTED_SETTLE_NANOS = TimeUnit.SECONDS.toNanos(2L);
    private static final long DISCONNECTED_SETTLE_NANOS = TimeUnit.SECONDS.toNanos(3L);
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static Stage stage = Stage.WAITING_FOR_WORLD;
    private static long stageDeadlineNanos;
    private static int completedCycles;
    private static int initialWatchdogs;
    private static Set<Path> initialReports = Set.of();

    private LifecycleCyclePlan() {}

    static boolean start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        completedCycles = 0;
        initialWatchdogs = watchdogThreadCount();
        initialReports = ValidationHarness.captureExistingReports();
        stage = Stage.WAITING_FOR_WORLD;
        stageDeadlineNanos = 0L;
        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] LIFECYCLE20 start watchdogThreads={}", initialWatchdogs);
        return true;
    }

    static void tick() {
        if (!RUNNING.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        boolean worldLoaded = minecraft.level != null && minecraft.player != null;

        switch (stage) {
            case WAITING_FOR_WORLD -> {
                if (worldLoaded) {
                    stage = Stage.SETTLING_IN_WORLD;
                    stageDeadlineNanos = now + CONNECTED_SETTLE_NANOS;
                    DetectiveTestCulprit.LOGGER.info(
                            "[Detective Validation] LIFECYCLE20 entered cycle={}/{}",
                            completedCycles + 1, TARGET_CYCLES);
                }
            }
            case SETTLING_IN_WORLD -> {
                if (!worldLoaded) {
                    stage = Stage.WAITING_FOR_WORLD;
                } else if (now - stageDeadlineNanos >= 0L) {
                    minecraft.disconnect(new TitleScreen());
                    stage = Stage.SETTLING_OUT_OF_WORLD;
                    stageDeadlineNanos = now + DISCONNECTED_SETTLE_NANOS;
                }
            }
            case SETTLING_OUT_OF_WORLD -> {
                // A level becoming null does not mean the previous Netty connection has fully
                // closed. Reconnecting during that short window can race late registry-backed
                // packets from the old session and invalidate the lifecycle test itself.
                if (worldLoaded || minecraft.getConnection() != null || now - stageDeadlineNanos < 0L) {
                    return;
                }
                completedCycles++;
                if (completedCycles >= TARGET_CYCLES) {
                    finish();
                } else {
                    ValidationCommands.reconnectToConfiguredServer();
                    stage = Stage.WAITING_FOR_WORLD;
                }
            }
        }
    }

    private static void finish() {
        int finalWatchdogs = watchdogThreadCount();
        long newIncidents = ValidationHarness.captureExistingReports().stream()
                .filter(path -> !initialReports.contains(path))
                .count();
        boolean passed = initialWatchdogs == 1 && finalWatchdogs == 1 && newIncidents == 0L;
        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] LIFECYCLE20 result={} cycles={} watchdogStart={} watchdogEnd={} newIncidents={}",
                passed ? "PASS" : "FAIL", completedCycles, initialWatchdogs, finalWatchdogs, newIncidents);
        RUNNING.set(false);
    }

    private static int watchdogThreadCount() {
        return Math.toIntExact(Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> "Detective-Watchdog".equals(thread.getName()))
                .count());
    }

    private enum Stage {
        WAITING_FOR_WORLD,
        SETTLING_IN_WORLD,
        SETTLING_OUT_OF_WORLD
    }
}
