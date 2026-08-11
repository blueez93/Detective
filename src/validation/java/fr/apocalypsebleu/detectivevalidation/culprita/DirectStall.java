package fr.apocalypsebleu.detectivevalidation.culprita;

import fr.apocalypsebleu.detectivevalidation.culpritc.StandardLibraryStall;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class DirectStall {
    private DirectStall() {}

    public static void block(long durationMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMs);
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0L) {
            LockSupport.parkNanos(remaining);
        }
    }

    public static void delegateToC(long durationMs) {
        StandardLibraryStall.block(durationMs);
    }
}
