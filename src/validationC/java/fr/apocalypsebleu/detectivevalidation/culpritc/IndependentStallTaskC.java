package fr.apocalypsebleu.detectivevalidation.culpritc;

public final class IndependentStallTaskC implements Runnable {
    private final long durationMs;
    private final Completion completion;

    public IndependentStallTaskC(long durationMs, Completion completion) {
        this.durationMs = durationMs;
        this.completion = completion;
    }

    @Override
    public void run() {
        long startEpochMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        StandardLibraryStall.block(durationMs);
        completion.complete(startNanos, System.nanoTime(), startEpochMs, System.currentTimeMillis());
    }

    @FunctionalInterface
    public interface Completion {
        void complete(long startNanos, long endNanos, long startEpochMs, long endEpochMs);
    }
}
