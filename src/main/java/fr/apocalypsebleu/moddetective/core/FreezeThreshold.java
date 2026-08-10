package fr.apocalypsebleu.moddetective.core;

import java.util.Collection;

public final class FreezeThreshold {
    public static final double ABSOLUTE_MINIMUM_MS = 120.0;
    public static final double BASELINE_MULTIPLIER = 6.0;

    private FreezeThreshold() {}

    public static double calculate(Collection<Double> baselineFramesMs) {
        double[] sorted = baselineFramesMs.stream()
                .filter(value -> value != null && Double.isFinite(value) && value > 0.0)
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();

        if (sorted.length == 0) {
            return ABSOLUTE_MINIMUM_MS;
        }

        int middle = sorted.length / 2;
        double median = (sorted.length & 1) == 0
                ? (sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
        return Math.max(ABSOLUTE_MINIMUM_MS, median * BASELINE_MULTIPLIER);
    }
}
