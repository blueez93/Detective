package fr.apocalypsebleu.detectivevalidation.culpritb;

import fr.apocalypsebleu.detectivevalidation.culpritc.StandardLibraryStall;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class LibraryStall {
    private LibraryStall() {}

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
