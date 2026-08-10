package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackBoxRecorderTest {
    @Test
    void retainsApproximatelyThirtySecondsUsingMonotonicTime() {
        BlackBoxRecorder recorder = new BlackBoxRecorder();
        recorder.add(frame(0L, 1_000_000_000L));
        recorder.add(frame(1_000L, 2_000_000_000L));
        recorder.add(frame(31_001L, 32_001_000_000L));

        assertEquals(1, recorder.snapshot().size());
        assertEquals(31_001L, recorder.snapshot().getFirst().epochMs());
    }

    private static FrameSample frame(long epochMs, long nanoTime) {
        return new FrameSample(epochMs, nanoTime, 16.0, 62.5, 1L, 2L, "test", 0, 0, 0);
    }
}
