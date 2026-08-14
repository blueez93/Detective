package fr.apocalypsebleu.moddetective.client.ui.model;

/** Deterministic, locale-neutral duration tokens used inside localized Case Evolution copy. */
public final class CaseEvolutionUiFormatter {
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private CaseEvolutionUiFormatter() {}

    public static String offsetMagnitude(long offsetMs) {
        long remaining = offsetMs == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(offsetMs);
        if (remaining == 0L) {
            return "0m";
        }
        StringBuilder result = new StringBuilder();
        remaining = append(result, remaining, DAY, "d", false);
        remaining = append(result, remaining, HOUR, "h", !result.isEmpty());
        remaining = append(result, remaining, MINUTE, "m", !result.isEmpty());
        remaining = append(result, remaining, SECOND, "s", !result.isEmpty());
        if (remaining > 0L) {
            appendToken(result, remaining, "ms", !result.isEmpty());
        }
        return result.toString();
    }

    private static long append(
            StringBuilder result,
            long remaining,
            long unit,
            String suffix,
            boolean pad
    ) {
        long value = remaining / unit;
        if (value > 0L) {
            appendToken(result, value, suffix, pad);
        }
        return remaining % unit;
    }

    private static void appendToken(
            StringBuilder result,
            long value,
            String suffix,
            boolean pad
    ) {
        if (!result.isEmpty()) {
            result.append(' ');
        }
        if (pad && value < 10L) {
            result.append('0');
        }
        result.append(value).append(suffix);
    }
}
