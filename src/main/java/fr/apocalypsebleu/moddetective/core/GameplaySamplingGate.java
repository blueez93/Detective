package fr.apocalypsebleu.moddetective.core;

import java.util.concurrent.TimeUnit;

/**
 * Keeps initial world and dimension loading work out of gameplay incident detection.
 * Black Box capture remains active during this grace period; only incident detection is gated.
 */
public final class GameplaySamplingGate {
    static final long STABILIZATION_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private Object activeContext;
    private long detectAfterNanos;

    public boolean shouldDetect(Object gameplayContext, long nowNanos) {
        if (gameplayContext == null) {
            activeContext = null;
            detectAfterNanos = 0L;
            return false;
        }
        if (gameplayContext != activeContext) {
            activeContext = gameplayContext;
            detectAfterNanos = nowNanos + STABILIZATION_NANOS;
            return false;
        }
        return nowNanos - detectAfterNanos >= 0L;
    }
}
