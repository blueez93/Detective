package fr.apocalypsebleu.moddetective.client.ui.model;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record IncidentIndexViewModel(
        DetectiveSummaryViewModel summary,
        List<IncidentSummaryViewModel> incidents,
        int unreadableFiles
) {
    private static final long RECENT_WINDOW_MS = Duration.ofHours(24).toMillis();

    public IncidentIndexViewModel {
        summary = Objects.requireNonNull(summary, "summary");
        incidents = List.copyOf(Objects.requireNonNull(incidents, "incidents"));
    }

    public static IncidentIndexViewModel create(
            List<IncidentSummaryViewModel> source,
            long nowEpochMs,
            long sessionStartedEpochMs,
            int unreadableFiles
    ) {
        List<IncidentSummaryViewModel> sorted = source.stream()
                .sorted(Comparator.comparingLong(IncidentSummaryViewModel::detectedAtEpochMs).reversed()
                        .thenComparing(IncidentSummaryViewModel::id))
                .toList();
        int session = (int) sorted.stream()
                .filter(incident -> incident.detectedAtEpochMs() >= sessionStartedEpochMs)
                .count();
        long recentCutoff = nowEpochMs - RECENT_WINDOW_MS;
        int recent = (int) sorted.stream()
                .filter(incident -> incident.detectedAtEpochMs() >= recentCutoff)
                .count();
        int high = (int) sorted.stream()
                .filter(incident -> incident.evidence() == EvidenceBadge.HIGH_EVIDENCE)
                .count();
        int moderate = (int) sorted.stream()
                .filter(incident -> incident.evidence() == EvidenceBadge.MODERATE_EVIDENCE)
                .count();
        return new IncidentIndexViewModel(
                new DetectiveSummaryViewModel(sorted.size(), session, recent, high, moderate,
                        sorted.isEmpty() ? null : sorted.getFirst()),
                sorted,
                Math.max(0, unreadableFiles));
    }

    public static IncidentIndexViewModel empty(long nowEpochMs, long sessionStartedEpochMs) {
        return create(List.of(), nowEpochMs, sessionStartedEpochMs, 0);
    }
}
