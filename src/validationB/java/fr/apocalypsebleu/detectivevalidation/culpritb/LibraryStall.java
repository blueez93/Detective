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

    public static void delegateToAReflectively(long durationMs) {
        invokeStaticBlock("fr.apocalypsebleu.detectivevalidation.culprita.DirectStall", "block", durationMs);
    }

    public static void delegateToCThenA(long durationMs) {
        StandardLibraryStall.delegateToAReflectively(durationMs);
    }

    private static void invokeStaticBlock(String className, String methodName, long durationMs) {
        try {
            Class.forName(className).getMethod(methodName, long.class).invoke(null, durationMs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Development validation bridge failed", e);
        }
    }
}
