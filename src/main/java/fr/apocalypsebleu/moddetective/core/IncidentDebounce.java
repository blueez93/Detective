package fr.apocalypsebleu.moddetective.core;

public final class IncidentDebounce {
    private final long intervalNanos;
    private long lastAcceptedNanos = Long.MIN_VALUE;

    public IncidentDebounce(long intervalNanos) {
        if (intervalNanos < 0L) {
            throw new IllegalArgumentException("intervalNanos must not be negative");
        }
        this.intervalNanos = intervalNanos;
    }

    public boolean tryAcquire(long nowNanos) {
        if (lastAcceptedNanos != Long.MIN_VALUE && nowNanos - lastAcceptedNanos < intervalNanos) {
            return false;
        }
        lastAcceptedNanos = nowNanos;
        return true;
    }

    public void reset() {
        lastAcceptedNanos = Long.MIN_VALUE;
    }
}
