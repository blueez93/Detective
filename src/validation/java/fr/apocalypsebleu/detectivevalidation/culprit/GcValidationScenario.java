package fr.apocalypsebleu.detectivevalidation.culprit;

import com.google.gson.Gson;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Development-only memory pressure. It never runs on the render thread. */
final class GcValidationScenario {
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int PRESSURE_MIB = Math.max(64,
            Integer.getInteger("detective.validation.gcPressureMiB", 512));
    private static final int PASSES = Math.max(1,
            Integer.getInteger("detective.validation.gcPasses", 3));
    private static final int CHUNK_MIB = 16;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Detective-Validation-GC-Pressure");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static volatile long touchedChecksum;

    private GcValidationScenario() {}

    static boolean start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        WORKER.execute(GcValidationScenario::runPressure);
        return true;
    }

    static void shutdown() {
        WORKER.shutdownNow();
    }

    private static void runPressure() {
        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] GC pressure starting off render thread: {} MiB x {} pass(es); unifiedGcLogEnabled={}",
                PRESSURE_MIB, PASSES, Boolean.getBoolean("detective.validation.gcLogging"));
        try {
            for (int pass = 1; pass <= PASSES && !Thread.currentThread().isInterrupted(); pass++) {
                marker(pass, "BEFORE_ALLOCATION", 0L);
                byte[][] pressure = allocateAndTouch(PRESSURE_MIB);
                marker(pass, "AFTER_ALLOCATION", PRESSURE_MIB * 1024L * 1024L);
                pressure = null;
                marker(pass, "BEFORE_SYSTEM_GC", 0L);
                System.gc();
                marker(pass, "AFTER_SYSTEM_GC", 0L);
                Thread.sleep(1_000L);
            }
        } catch (OutOfMemoryError error) {
            marker(-1, "OUT_OF_MEMORY", 0L);
            DetectiveTestCulprit.LOGGER.error(
                    "[Detective Validation] GC pressure exceeded the validation JVM heap; run remains usable", error);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            RUNNING.set(false);
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] GC pressure finished");
        }
    }

    private static byte[][] allocateAndTouch(int mebibytes) {
        int chunks = Math.max(1, (int) Math.ceil(mebibytes / (double) CHUNK_MIB));
        byte[][] pressure = new byte[chunks][];
        long checksum = 0L;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int remainingMiB = mebibytes - chunk * CHUNK_MIB;
            int chunkMiB = Math.min(CHUNK_MIB, remainingMiB);
            pressure[chunk] = new byte[chunkMiB * 1024 * 1024];
            for (int offset = 0; offset < pressure[chunk].length; offset += 4_096) {
                pressure[chunk][offset] = (byte) (chunk + offset);
                checksum += pressure[chunk][offset];
            }
        }
        touchedChecksum = checksum;
        return pressure;
    }

    private static void marker(int pass, String stage, long requestedBytes) {
        Runtime runtime = Runtime.getRuntime();
        GcMarker marker = new GcMarker(
                System.currentTimeMillis(), System.nanoTime(), pass, stage, requestedBytes,
                runtime.totalMemory() - runtime.freeMemory(), touchedChecksum);
        Path file = FMLPaths.GAMEDIR.get().resolve("detective-validation")
                .resolve("gc-markers.jsonl").toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(marker) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Could not persist GC marker", e);
        }
    }

    private record GcMarker(
            long epochMs, long nanoTime, int pass, String stage,
            long requestedBytes, long usedHeapBytes, long checksum
    ) {}
}
