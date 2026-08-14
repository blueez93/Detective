package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.support.DetectiveSettings;

import java.util.List;
import java.util.Objects;

/** Bounded immutable query outcome retaining stable incident identity and existing summaries. */
public record IncidentQueryResult(
        List<IncidentSearchRecord> matchingIncidents,
        int totalIncidentCountConsidered,
        int matchingCount,
        IncidentQuery query,
        IncidentQuery.Sort sort,
        CaseFilterStatus caseFilterStatus
) {
    public IncidentQueryResult {
        matchingIncidents = List.copyOf(Objects.requireNonNull(
                matchingIncidents, "matchingIncidents"));
        query = Objects.requireNonNull(query, "query");
        sort = Objects.requireNonNull(sort, "sort");
        caseFilterStatus = Objects.requireNonNull(caseFilterStatus, "caseFilterStatus");
        if (totalIncidentCountConsidered < 0
                || totalIncidentCountConsidered > DetectiveSettings.MAXIMUM_HISTORY_LIMIT
                || matchingCount != matchingIncidents.size()
                || matchingCount > totalIncidentCountConsidered) {
            throw new IllegalArgumentException("Incident query counts are inconsistent");
        }
    }

    public enum CaseFilterStatus {
        NOT_REQUESTED,
        AVAILABLE,
        UNAVAILABLE
    }
}
