package fr.apocalypsebleu.detectivevalidation.culpritb;

public final class IndependentStallTaskB implements Runnable {
    private final long durationMs;
    private final Completion completion;

    public IndependentStallTaskB(long durationMs, Completion completion) {
        this.durationMs = durationMs;
        this.completion = completion;
    }

    @Override
    public void run() {
        long startEpochMs = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        LibraryStall.block(durationMs);
        completion.complete(startNanos, System.nanoTime(), startEpochMs, System.currentTimeMillis());
    }

    @FunctionalInterface
    public interface Completion {
        void complete(long startNanos, long endNanos, long startEpochMs, long endEpochMs);
    }
}
