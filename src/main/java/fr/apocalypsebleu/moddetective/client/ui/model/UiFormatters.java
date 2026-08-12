package fr.apocalypsebleu.moddetective.client.ui.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class UiFormatters {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private UiFormatters() {}

    public static String duration(double milliseconds) {
        if (!Double.isFinite(milliseconds) || milliseconds < 0.0) {
            return "—";
        }
        if (milliseconds >= 1_000.0) {
            return String.format(Locale.ROOT, "%.2f s", milliseconds / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1f ms", milliseconds);
    }

    public static String percent(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    public static String shortCaseId(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return "UNKNOWN";
        }
        String value = caseId.trim();
        if (value.regionMatches(true, 0, "case-", 0, 5)) {
            value = value.substring(5);
        }
        if (value.length() > 8) {
            value = value.substring(0, 8);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static String memory(long bytes) {
        if (bytes < 0L) {
            return "—";
        }
        double mebibytes = bytes / (1024.0 * 1024.0);
        if (mebibytes >= 1024.0) {
            return String.format(Locale.ROOT, "%.2f GiB", mebibytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", mebibytes);
    }

    public static String dateTime(long epochMs) {
        if (epochMs <= 0L) {
            return "—";
        }
        try {
            return DATE_TIME.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException ignored) {
            return "—";
        }
    }

    /** Compact list formatting; detailed screens keep seconds via {@link #dateTime(long)}. */
    public static String compactDateTime(long epochMs) {
        if (epochMs <= 0L) {
            return "—";
        }
        try {
            return COMPACT_DATE_TIME.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
        } catch (RuntimeException ignored) {
            return "—";
        }
    }

    public static String dimension(String identifier) {
        if (identifier == null || identifier.isBlank() || "menu".equals(identifier)) {
            return "—";
        }
        int separator = identifier.indexOf(':');
        String path = separator >= 0 ? identifier.substring(separator + 1) : identifier;
        String[] words = path.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? identifier : result.toString();
    }

    public static String coordinates(Integer x, Integer y, Integer z) {
        return x == null || y == null || z == null ? "—" : x + ", " + y + ", " + z;
    }
}
