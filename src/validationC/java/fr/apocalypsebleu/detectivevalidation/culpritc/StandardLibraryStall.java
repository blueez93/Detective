package fr.apocalypsebleu.detectivevalidation.culpritc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class StandardLibraryStall {
    private StandardLibraryStall() {}

    public static void block(long durationMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) {
            LockSupport.parkNanos(remaining);
        }
    }
}
