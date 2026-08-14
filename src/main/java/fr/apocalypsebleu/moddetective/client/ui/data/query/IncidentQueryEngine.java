package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Deterministic O(n) filtering/search plus O(n log n) sorting of loaded incident summaries. */
public final class IncidentQueryEngine {
    public IncidentQueryResult query(
            List<IncidentSearchRecord> history,
            IncidentQuery query,
            CaseMembershipIndex caseMembership
    ) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(caseMembership, "caseMembership");
        if (history.size() > DetectiveSettings.MAXIMUM_HISTORY_LIMIT) {
            throw new IllegalArgumentException("Incident queries are bounded to the newest 500 records");
        }

        IncidentQueryResult.CaseFilterStatus caseStatus = caseStatus(query, caseMembership);
        if (caseStatus == IncidentQueryResult.CaseFilterStatus.UNAVAILABLE) {
            return new IncidentQueryResult(
                    List.of(), history.size(), 0, query, query.sort(), caseStatus);
        }

        String normalizedFreeText = query.freeText()
                .map(IncidentQueryEngine::normalizeSearchText).orElse("");
        List<IncidentSearchRecord> matches = new ArrayList<>();
        for (IncidentSearchRecord incident : history) {
            Objects.requireNonNull(incident, "history incident");
            if (matches(incident, query, normalizedFreeText, caseMembership)) {
                matches.add(incident);
            }
        }
        matches.sort(comparator(query.sort()));
        return new IncidentQueryResult(
                matches, history.size(), matches.size(), query, query.sort(), caseStatus);
    }

    private static boolean matches(
            IncidentSearchRecord incident,
            IncidentQuery query,
            String normalizedFreeText,
            CaseMembershipIndex cases
    ) {
        IncidentSummaryViewModel summary = incident.summary();
        if (query.freeText().isPresent()
                && !searchableValues(incident).stream()
                .anyMatch(value -> value.contains(normalizedFreeText))) {
            return false;
        }
        if (!query.evidenceStates().isEmpty()
                && !query.evidenceStates().contains(summary.evidence())) {
            return false;
        }
        if (query.minimumStallDurationMs().isPresent()
                && (incident.stallDurationMs().isEmpty()
                || incident.stallDurationMs().getAsDouble()
                < query.minimumStallDurationMs().getAsDouble())) {
            return false;
        }
        if (query.maximumStallDurationMs().isPresent()
                && (incident.stallDurationMs().isEmpty()
                || incident.stallDurationMs().getAsDouble()
                > query.maximumStallDurationMs().getAsDouble())) {
            return false;
        }
        if (query.ownerId().isPresent() && !incident.ownerIds().contains(query.ownerId().get())) {
            return false;
        }
        if (query.dimensionId().isPresent()
                && !incident.dimensionId().filter(query.dimensionId().get()::equals).isPresent()) {
            return false;
        }
        if (query.detectedAtOrAfterEpochMs().isPresent()
                && (incident.detectedAtEpochMs().isEmpty()
                || incident.detectedAtEpochMs().getAsLong()
                < query.detectedAtOrAfterEpochMs().getAsLong())) {
            return false;
        }
        if (query.detectedAtOrBeforeEpochMs().isPresent()
                && (incident.detectedAtEpochMs().isEmpty()
                || incident.detectedAtEpochMs().getAsLong()
                > query.detectedAtOrBeforeEpochMs().getAsLong())) {
            return false;
        }

        if (query.requiresCaseMembership()) {
            Set<String> membership = cases.caseIds(incident);
            if (query.hasRecurringCase().isPresent()
                    && query.hasRecurringCase().get() != !membership.isEmpty()) {
                return false;
            }
            if (query.caseId().isPresent() && !membership.contains(query.caseId().get())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> searchableValues(IncidentSearchRecord incident) {
        List<String> values = new ArrayList<>();
        values.add(normalizeSearchText(incident.summary().id()));
        values.add(normalizeSearchText(incident.summary().rawEvidenceState()));
        values.add(normalizeSearchText(incident.summary().evidence().name()));
        incident.ownerIds().stream().map(IncidentQueryEngine::normalizeSearchText).forEach(values::add);
        incident.modDisplayNames().stream()
                .map(IncidentQueryEngine::normalizeSearchText).forEach(values::add);
        return values;
    }

    private static String normalizeSearchText(String value) {
        return Objects.requireNonNullElse(value, "")
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    private static IncidentQueryResult.CaseFilterStatus caseStatus(
            IncidentQuery query,
            CaseMembershipIndex cases
    ) {
        if (!query.requiresCaseMembership()) {
            return IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED;
        }
        return cases.availability() == CaseMembershipIndex.Availability.AVAILABLE
                ? IncidentQueryResult.CaseFilterStatus.AVAILABLE
                : IncidentQueryResult.CaseFilterStatus.UNAVAILABLE;
    }

    private static Comparator<IncidentSearchRecord> comparator(IncidentQuery.Sort sort) {
        Comparator<IncidentSearchRecord> timestampAscending = Comparator
                .comparingLong((IncidentSearchRecord value) ->
                        value.summary().detectedAtEpochMs());
        Comparator<IncidentSearchRecord> timestampDescending = timestampAscending.reversed();
        Comparator<IncidentSearchRecord> durationAvailableFirst = Comparator
                .comparing((IncidentSearchRecord value) -> value.stallDurationMs().isEmpty());
        Comparator<IncidentSearchRecord> durationAscending = durationAvailableFirst
                .thenComparingDouble(value -> value.stallDurationMs().orElse(0.0));
        Comparator<IncidentSearchRecord> durationDescending = durationAvailableFirst
                .thenComparing(Comparator.comparingDouble(
                        (IncidentSearchRecord value) -> value.stallDurationMs().orElse(0.0)).reversed());
        Comparator<IncidentSearchRecord> primary = switch (sort) {
            case NEWEST_FIRST -> timestampDescending;
            case OLDEST_FIRST -> timestampAscending;
            case LONGEST_STALL_FIRST -> durationDescending.thenComparing(timestampDescending);
            case SHORTEST_STALL_FIRST -> durationAscending.thenComparing(timestampDescending);
        };
        return primary
                .thenComparing(value -> value.summary().id())
                .thenComparing(IncidentSearchRecord::incidentId);
    }
}
