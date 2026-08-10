package fr.apocalypsebleu.moddetective.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class BlackBoxRecorder {
    private static final long RETENTION_NANOS = 30_000_000_000L;
    private final Deque<FrameSample> samples = new ArrayDeque<>();

    public synchronized void add(FrameSample sample) {
        samples.addLast(sample);
        long cutoff = sample.nanoTime() - RETENTION_NANOS;
        while (!samples.isEmpty() && samples.peekFirst().nanoTime() < cutoff) {
            samples.removeFirst();
        }
    }

    public synchronized List<FrameSample> snapshot() {
        return List.copyOf(new ArrayList<>(samples));
    }

    public synchronized int size() {
        return samples.size();
    }
}
