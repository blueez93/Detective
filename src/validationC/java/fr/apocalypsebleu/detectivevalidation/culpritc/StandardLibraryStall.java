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

    public static void delegateToBReflectively(long durationMs) {
        invokeStaticBlock("fr.apocalypsebleu.detectivevalidation.culpritb.LibraryStall", "block", durationMs);
    }

    public static void delegateToAReflectively(long durationMs) {
        invokeStaticBlock("fr.apocalypsebleu.detectivevalidation.culprita.DirectStall", "block", durationMs);
    }

    private static void invokeStaticBlock(String className, String methodName, long durationMs) {
        try {
            Class.forName(className).getMethod(methodName, long.class).invoke(null, durationMs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Development validation bridge failed", e);
        }
    }
}
