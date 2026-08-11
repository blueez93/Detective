package fr.apocalypsebleu.moddetective.support;

public record DetectiveSettings(
        int schemaVersion,
        boolean incidentNotifications,
        int incidentHistoryLimit,
        int dataRetentionDays,
        boolean showTechnicalEvidenceByDefault
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int MINIMUM_HISTORY_LIMIT = 1;
    public static final int MAXIMUM_HISTORY_LIMIT = 500;
    public static final int MINIMUM_RETENTION_DAYS = 1;
    public static final int MAXIMUM_RETENTION_DAYS = 365;

    public DetectiveSettings {
        schemaVersion = SCHEMA_VERSION;
        incidentHistoryLimit = clamp(incidentHistoryLimit, MINIMUM_HISTORY_LIMIT, MAXIMUM_HISTORY_LIMIT);
        dataRetentionDays = clamp(dataRetentionDays, MINIMUM_RETENTION_DAYS, MAXIMUM_RETENTION_DAYS);
    }

    public static DetectiveSettings defaults() {
        return new DetectiveSettings(
                SCHEMA_VERSION,
                true,
                DEFAULT_HISTORY_LIMIT,
                DEFAULT_RETENTION_DAYS,
                false);
    }

    public DetectiveSettings withIncidentNotifications(boolean enabled) {
        return new DetectiveSettings(schemaVersion, enabled, incidentHistoryLimit,
                dataRetentionDays, showTechnicalEvidenceByDefault);
    }

    public DetectiveSettings withIncidentHistoryLimit(int limit) {
        return new DetectiveSettings(schemaVersion, incidentNotifications, limit,
                dataRetentionDays, showTechnicalEvidenceByDefault);
    }

    public DetectiveSettings withDataRetentionDays(int days) {
        return new DetectiveSettings(schemaVersion, incidentNotifications, incidentHistoryLimit,
                days, showTechnicalEvidenceByDefault);
    }

    public DetectiveSettings withShowTechnicalEvidenceByDefault(boolean enabled) {
        return new DetectiveSettings(schemaVersion, incidentNotifications, incidentHistoryLimit,
                dataRetentionDays, enabled);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
