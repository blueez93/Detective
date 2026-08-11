package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentIndexViewModelTest {
    @Test
    void sortsNewestFirstAndBuildsSummaryCounts() {
        long now = 2_000_000_000L;
        IncidentSummaryViewModel old = incident("old", now - 90_000_000L, EvidenceBadge.HIGH_EVIDENCE);
        IncidentSummaryViewModel newest = incident("newest", now - 1_000L, EvidenceBadge.MODERATE_EVIDENCE);
        IncidentSummaryViewModel recent = incident("recent", now - 2_000L, EvidenceBadge.HIGH_EVIDENCE);

        IncidentIndexViewModel index = IncidentIndexViewModel.create(
                List.of(old, newest, recent), now, now - 1_500L, 2);

        assertEquals(List.of("newest", "recent", "old"),
                index.incidents().stream().map(IncidentSummaryViewModel::id).toList());
        assertEquals(3, index.summary().totalIncidents());
        assertEquals(1, index.summary().sessionIncidents());
        assertEquals(2, index.summary().recentIncidents());
        assertEquals(2, index.summary().highEvidenceIncidents());
        assertEquals(1, index.summary().moderateEvidenceIncidents());
        assertEquals(2, index.unreadableFiles());
    }

    private static IncidentSummaryViewModel incident(String id, long detectedAt, EvidenceBadge badge) {
        return new IncidentSummaryViewModel(
                id, Path.of(id + ".json"), detectedAt, 200.0, 120.0, 10,
                badge, "ATTRIBUTED", "Example", true, "date", "Overworld", "1, 2, 3");
    }
}
