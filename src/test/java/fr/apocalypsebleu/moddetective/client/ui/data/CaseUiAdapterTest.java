package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseUiAdapterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emptyBackendStateProducesNormalEmptyUiState() {
        CaseIndexViewModel result = CaseUiAdapter.from(
                List.of(), IncidentIndexViewModel.empty(10L, 0L), temporaryDirectory, 0);

        assertTrue(result.isEmpty());
        assertEquals(0, result.unreadableCaseEntries());
    }

    @Test
    void casesAreStableNewestFirstAndRelatedIncidentsAreNewestFirst() {
        IncidentSummaryViewModel older = incident("freeze-old.json", 100L, EvidenceBadge.INSUFFICIENT_EVIDENCE);
        IncidentSummaryViewModel newest = incident("freeze-new.json", 300L, EvidenceBadge.HIGH_EVIDENCE);
        IncidentIndexViewModel incidents = IncidentIndexViewModel.create(
                List.of(older, newest), 500L, 0L, 0);
        CaseFile olderCase = caseFile("case-aaaaaaaa11111111", 10L, 200L,
                List.of("freeze-other-a.json", "freeze-other-b.json", "freeze-other-c.json"),
                List.of(), List.of());
        CaseFile newestCase = caseFile("case-bbbbbbbb22222222", 100L, 300L,
                List.of("freeze-old.json", "freeze-missing.json", "freeze-new.json"),
                List.of(), List.of());

        CaseIndexViewModel result = CaseUiAdapter.from(
                List.of(olderCase, newestCase), incidents, temporaryDirectory, 1);

        assertEquals(List.of("case-bbbbbbbb22222222", "case-aaaaaaaa11111111"),
                result.cases().stream().map(value -> value.caseId()).toList());
        assertEquals(List.of("freeze-new.json", "freeze-old.json", "freeze-missing.json"),
                result.cases().getFirst().relatedIncidents().stream()
                        .map(value -> value.incidentId()).toList());
        assertEquals(1, result.unreadableCaseEntries());
    }

    @Test
    void preservesDurationsCaseIdentityAndAggregatePresentation() {
        CaseFile source = caseFile("case-0123456789abcdef", 100L, 300L,
                List.of("freeze-a.json", "freeze-b.json", "freeze-c.json"),
                List.of(new CaseFile.RecurringEvidence(
                        CaseFile.EvidenceKind.FRAME, "sha256:example", 3, 0.75)),
                List.of(new CaseFile.RecurringOwner("example_machines", 3, 0.6, 0.8)));

        var result = CaseUiAdapter.from(List.of(source), IncidentIndexViewModel.empty(0L, 0L),
                temporaryDirectory, 0).cases().getFirst();

        assertEquals("case-0123456789abcdef", result.caseId());
        assertEquals("01234567", result.shortCaseId());
        assertEquals("450.0 ms", UiFormatters.duration(result.averageStallDurationMs()));
        assertEquals("900.0 ms", UiFormatters.duration(result.longestStallDurationMs()));
        assertEquals("84.0%", UiFormatters.percent(result.consistencyPercent()));
        assertEquals("76.0%", UiFormatters.percent(result.evidenceStrengthPercent()));
        assertEquals(75.0, result.recurringEvidence().getFirst().averageObservedSharePercent());
        assertEquals(80.0, result.recurringOwners().getFirst().averageStackPresenceSharePercent());
    }

    @Test
    void missingOptionalEvidenceAndRetainedIncidentFilesFailSoftly() {
        CaseFile legacyCompatible = caseFile("case-legacy", 100L, 300L,
                List.of("freeze-legacy-a.json", "freeze-legacy-b.json", "freeze-legacy-c.json"),
                List.of(), List.of());

        var projected = assertDoesNotThrow(() -> CaseUiAdapter.from(
                List.of(legacyCompatible), IncidentIndexViewModel.empty(0L, 0L),
                temporaryDirectory, 0)).cases().getFirst();

        assertTrue(projected.recurringEvidence().isEmpty());
        assertTrue(projected.recurringOwners().isEmpty());
        assertTrue(projected.relatedIncidents().stream().noneMatch(value -> value.isAvailable()));
    }

    @Test
    void identicalPreparedHistoryProducesIdenticalUiState() {
        CaseFile source = caseFile("case-stable", 100L, 300L,
                List.of("freeze-a.json", "freeze-b.json", "freeze-c.json"), List.of(), List.of());
        IncidentIndexViewModel incidents = IncidentIndexViewModel.create(List.of(
                incident("freeze-a.json", 100L, EvidenceBadge.UNKNOWN),
                incident("freeze-b.json", 200L, EvidenceBadge.AMBIGUOUS_ATTRIBUTION),
                incident("freeze-c.json", 300L, EvidenceBadge.HIGH_EVIDENCE)), 400L, 0L, 0);

        CaseIndexViewModel first = CaseUiAdapter.from(List.of(source), incidents, temporaryDirectory, 0);
        CaseIndexViewModel second = CaseUiAdapter.from(List.of(source), incidents, temporaryDirectory, 0);

        assertEquals(first, second);
    }

    @Test
    void shortCaseIdFormattingIsSafeForSparseValues() {
        assertEquals("89ABCDEF", UiFormatters.shortCaseId("case-89abcdef01234567"));
        assertEquals("LEGACY", UiFormatters.shortCaseId("legacy"));
        assertEquals("UNKNOWN", UiFormatters.shortCaseId(null));
        assertEquals("—", UiFormatters.compactDateTime(0L));
    }

    private IncidentSummaryViewModel incident(String id, long detectedAt, EvidenceBadge badge) {
        return new IncidentSummaryViewModel(
                id, temporaryDirectory.resolve(id), detectedAt, 200.0, 120.0, 10,
                badge, badge == EvidenceBadge.HIGH_EVIDENCE ? "ATTRIBUTED" : "UNKNOWN",
                badge == EvidenceBadge.HIGH_EVIDENCE ? "Example Machines" : "",
                badge == EvidenceBadge.HIGH_EVIDENCE, "date", "Overworld", "—");
    }

    private static CaseFile caseFile(
            String id,
            long firstSeen,
            long lastSeen,
            List<String> related,
            List<CaseFile.RecurringEvidence> evidence,
            List<CaseFile.RecurringOwner> owners
    ) {
        return new CaseFile(
                id, related, firstSeen, lastSeen, related.size(), 450.0, 900.0,
                evidence, owners, 0.84, 0.76);
    }
}
