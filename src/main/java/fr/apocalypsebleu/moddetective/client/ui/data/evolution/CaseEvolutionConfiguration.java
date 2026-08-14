package fr.apocalypsebleu.moddetective.client.ui.data.evolution;

import java.time.Duration;
import java.util.Objects;

/**
 * Central, testable temporal windows for non-causal Case/change correlation.
 * The defaults retain exact offsets within seven days, with descriptive two-hour and 24-hour
 * bands, and compare frequency only across equal 24-hour windows around a change.
 */
public record CaseEvolutionConfiguration(
        Duration veryNearWindow,
        Duration withinDayWindow,
        Duration broaderNearbyWindow,
        Duration comparableFrequencyWindow
) {
    public static final CaseEvolutionConfiguration DEFAULT = new CaseEvolutionConfiguration(
            Duration.ofHours(2),
            Duration.ofHours(24),
            Duration.ofDays(7),
            Duration.ofHours(24));

    public CaseEvolutionConfiguration {
        veryNearWindow = positive(veryNearWindow, "veryNearWindow");
        withinDayWindow = positive(withinDayWindow, "withinDayWindow");
        broaderNearbyWindow = positive(broaderNearbyWindow, "broaderNearbyWindow");
        comparableFrequencyWindow = positive(
                comparableFrequencyWindow, "comparableFrequencyWindow");
        if (veryNearWindow.compareTo(withinDayWindow) > 0
                || withinDayWindow.compareTo(broaderNearbyWindow) > 0) {
            throw new IllegalArgumentException("Temporal proximity windows must be ordered");
        }
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
