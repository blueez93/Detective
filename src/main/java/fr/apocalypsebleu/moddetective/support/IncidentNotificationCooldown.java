package fr.apocalypsebleu.moddetective.support;

import java.util.LinkedHashMap;
import java.util.Objects;

/** UX-only spam protection. It never changes whether an incident is detected or stored. */
public final class IncidentNotificationCooldown {
    public static final long DEFAULT_COOLDOWN_NANOS = 8_000_000_000L;
    private static final int MAXIMUM_TRACKED_INCIDENTS = 256;

    private final long cooldownNanos;
    private final LinkedHashMap<String, Boolean> observedIncidentIds = new LinkedHashMap<>();
    private long lastShownNanos = Long.MIN_VALUE;

    public IncidentNotificationCooldown() {
        this(DEFAULT_COOLDOWN_NANOS);
    }

    public IncidentNotificationCooldown(long cooldownNanos) {
        if (cooldownNanos < 0L) {
            throw new IllegalArgumentException("cooldownNanos must not be negative");
        }
        this.cooldownNanos = cooldownNanos;
    }

    public synchronized boolean register(String incidentId, long nowNanos, boolean notificationsEnabled) {
        String normalizedId = Objects.requireNonNull(incidentId, "incidentId");
        if (observedIncidentIds.putIfAbsent(normalizedId, Boolean.TRUE) != null) {
            return false;
        }
        trimObservedIds();
        if (!notificationsEnabled) {
            return false;
        }
        if (lastShownNanos != Long.MIN_VALUE && nowNanos - lastShownNanos < cooldownNanos) {
            return false;
        }
        lastShownNanos = nowNanos;
        return true;
    }

    private void trimObservedIds() {
        while (observedIncidentIds.size() > MAXIMUM_TRACKED_INCIDENTS) {
            String oldest = observedIncidentIds.keySet().iterator().next();
            observedIncidentIds.remove(oldest);
        }
    }

    int trackedIncidentCount() {
        return observedIncidentIds.size();
    }
}
