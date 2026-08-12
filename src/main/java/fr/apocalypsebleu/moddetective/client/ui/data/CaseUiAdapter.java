package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvidenceViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseFileViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseOwnerViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.RelatedIncidentViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import fr.apocalypsebleu.moddetective.core.casefile.CaseHistoryService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps prepared backend Cases and incident summaries to immutable screen data. */
public final class CaseUiAdapter {
    private CaseUiAdapter() {}

    public static CaseIndexViewModel from(
            CaseHistoryService.Result result,
            IncidentIndexViewModel incidents,
            Path incidentsRoot
    ) {
        Objects.requireNonNull(result, "result");
        return from(result.cases(), incidents, incidentsRoot, result.unreadablePersistedCases());
    }

    public static CaseIndexViewModel from(
            List<CaseFile> cases,
            IncidentIndexViewModel incidents,
            Path incidentsRoot,
            int unreadableCaseEntries
    ) {
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(incidents, "incidents");
        Path root = Objects.requireNonNull(incidentsRoot, "incidentsRoot").toAbsolutePath().normalize();
        Map<String, IncidentSummaryViewModel> incidentById = incidentMap(incidents, root);
        List<CaseFileViewModel> projected = cases.stream()
                .map(value -> project(value, incidentById))
                .toList();
        return new CaseIndexViewModel(projected, unreadableCaseEntries);
    }

    private static CaseFileViewModel project(
            CaseFile source,
            Map<String, IncidentSummaryViewModel> incidentById
    ) {
        List<CaseEvidenceViewModel> evidence = source.recurringEvidence().stream()
                .map(value -> new CaseEvidenceViewModel(
                        CaseEvidenceViewModel.Kind.valueOf(value.kind().name()),
                        value.technicalSignature(),
                        value.supportingIncidents(),
                        value.averageObservedShare() * 100.0))
                .sorted(Comparator.comparing(CaseEvidenceViewModel::kind)
                        .thenComparing(CaseEvidenceViewModel::technicalSignature))
                .toList();
        List<CaseOwnerViewModel> owners = source.recurringOwners().stream()
                .map(value -> new CaseOwnerViewModel(
                        value.ownerId(),
                        value.supportingIncidents(),
                        value.averageLeafShare() * 100.0,
                        value.averageStackPresenceShare() * 100.0))
                .sorted(Comparator.comparing(CaseOwnerViewModel::ownerId))
                .toList();

        List<RelatedIncidentViewModel> related = new ArrayList<>();
        for (String incidentId : source.relatedIncidentIds()) {
            IncidentSummaryViewModel incident = incidentById.get(incidentId);
            related.add(new RelatedIncidentViewModel(incidentId, incident));
        }
        related.sort(Comparator
                .comparing(RelatedIncidentViewModel::isAvailable).reversed()
                .thenComparing(Comparator.comparingLong((RelatedIncidentViewModel value) -> value.incident() == null
                        ? Long.MIN_VALUE : value.incident().detectedAtEpochMs()).reversed())
                .thenComparing(RelatedIncidentViewModel::incidentId));

        return new CaseFileViewModel(
                source.caseId(),
                UiFormatters.shortCaseId(source.caseId()),
                source.firstSeenEpochMs(),
                source.lastSeenEpochMs(),
                source.occurrenceCount(),
                source.averageStallDurationMs(),
                source.longestStallDurationMs(),
                source.aggregateSimilarity() * 100.0,
                source.aggregateEvidenceStrength() * 100.0,
                evidence,
                owners,
                related);
    }

    private static Map<String, IncidentSummaryViewModel> incidentMap(
            IncidentIndexViewModel incidents,
            Path root
    ) {
        Map<String, IncidentSummaryViewModel> result = new HashMap<>();
        for (IncidentSummaryViewModel incident : incidents.incidents()) {
            result.putIfAbsent(incident.id(), incident);
            Path source = incident.source().toAbsolutePath().normalize();
            if (source.startsWith(root)) {
                String relative = root.relativize(source).toString().replace('\\', '/');
                result.putIfAbsent(relative, incident);
            }
        }
        return result;
    }
}
