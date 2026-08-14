package fr.apocalypsebleu.moddetective.client.ui.model;

import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQuery;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryResult;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * UI-only investigation state. Query execution remains in {@code DetectiveUiService}; this class
 * only builds immutable backend queries and enforces the exactly-two comparison selection limit.
 */
public final class IncidentInvestigationState {
    public static final int MAXIMUM_COMPARISON_SELECTION = 2;

    private String searchText = "";
    private EvidenceFilter evidenceFilter = EvidenceFilter.ANY;
    private DurationFilter durationFilter = DurationFilter.ANY;
    private CaseFilter caseFilter = CaseFilter.ANY;
    private IncidentQuery.Sort sort = IncidentQuery.Sort.NEWEST_FIRST;
    private IncidentQueryResult result;
    private final Map<String, IncidentSearchRecord> selected = new LinkedHashMap<>();

    public IncidentQuery query() {
        IncidentQuery.Builder builder = IncidentQuery.builder()
                .freeText(searchText)
                .evidenceStates(evidenceFilter.evidenceStates())
                .sort(sort);
        durationFilter.minimumDurationMs().ifPresent(builder::minimumStallDurationMs);
        caseFilter.hasRecurringCase().ifPresent(builder::hasRecurringCase);
        return builder.build();
    }

    public String searchText() {
        return searchText;
    }

    public void setSearchText(String value) {
        String normalized = Objects.requireNonNullElse(value, "");
        if (!searchText.equals(normalized)) {
            searchText = normalized;
            clearSelection();
        }
    }

    public EvidenceFilter evidenceFilter() {
        return evidenceFilter;
    }

    public void setEvidenceFilter(EvidenceFilter value) {
        EvidenceFilter requested = Objects.requireNonNull(value, "value");
        if (evidenceFilter != requested) {
            evidenceFilter = requested;
            clearSelection();
        }
    }

    public DurationFilter durationFilter() {
        return durationFilter;
    }

    public void setDurationFilter(DurationFilter value) {
        DurationFilter requested = Objects.requireNonNull(value, "value");
        if (durationFilter != requested) {
            durationFilter = requested;
            clearSelection();
        }
    }

    public CaseFilter caseFilter() {
        return caseFilter;
    }

    public void setCaseFilter(CaseFilter value) {
        CaseFilter requested = Objects.requireNonNull(value, "value");
        if (caseFilter != requested) {
            caseFilter = requested;
            clearSelection();
        }
    }

    public IncidentQuery.Sort sort() {
        return sort;
    }

    public void setSort(IncidentQuery.Sort value) {
        IncidentQuery.Sort requested = Objects.requireNonNull(value, "value");
        if (sort != requested) {
            sort = requested;
            clearSelection();
        }
    }

    /** Counts filtering constraints only; free text and presentation order are shown separately. */
    public int activeFilterCount() {
        int count = 0;
        if (evidenceFilter != EvidenceFilter.ANY) {
            count++;
        }
        if (durationFilter != DurationFilter.ANY) {
            count++;
        }
        if (caseFilter != CaseFilter.ANY) {
            count++;
        }
        return count;
    }

    public void clearFilters() {
        boolean changed = evidenceFilter != EvidenceFilter.ANY
                || durationFilter != DurationFilter.ANY
                || caseFilter != CaseFilter.ANY;
        evidenceFilter = EvidenceFilter.ANY;
        durationFilter = DurationFilter.ANY;
        caseFilter = CaseFilter.ANY;
        if (changed) {
            clearSelection();
        }
    }

    public void clearAllCriteria() {
        searchText = "";
        evidenceFilter = EvidenceFilter.ANY;
        durationFilter = DurationFilter.ANY;
        caseFilter = CaseFilter.ANY;
        sort = IncidentQuery.Sort.NEWEST_FIRST;
        clearSelection();
    }

    public void applyResult(IncidentQueryResult value) {
        result = Objects.requireNonNull(value, "value");
    }

    public Optional<IncidentQueryResult> result() {
        return Optional.ofNullable(result);
    }

    public List<IncidentSearchRecord> matchingIncidents() {
        return result == null ? List.of() : result.matchingIncidents();
    }

    public SelectionChange toggleSelection(IncidentSearchRecord incident) {
        IncidentSearchRecord requested = Objects.requireNonNull(incident, "incident");
        if (selected.remove(requested.incidentId()) != null) {
            return SelectionChange.DESELECTED;
        }
        if (selected.size() >= MAXIMUM_COMPARISON_SELECTION) {
            return SelectionChange.LIMIT_REACHED;
        }
        selected.put(requested.incidentId(), requested);
        return SelectionChange.SELECTED;
    }

    public boolean isSelected(String incidentId) {
        return selected.containsKey(incidentId);
    }

    public int selectionCount() {
        return selected.size();
    }

    public boolean canCompare() {
        return selected.size() == MAXIMUM_COMPARISON_SELECTION;
    }

    public List<IncidentSearchRecord> selectedIncidents() {
        return Collections.unmodifiableList(new ArrayList<>(selected.values()));
    }

    public void clearSelection() {
        selected.clear();
    }

    public enum SelectionChange {
        SELECTED,
        DESELECTED,
        LIMIT_REACHED
    }

    public enum EvidenceFilter {
        ANY(Set.of()),
        HIGH(EnumSet.of(EvidenceBadge.HIGH_EVIDENCE)),
        MODERATE(EnumSet.of(EvidenceBadge.MODERATE_EVIDENCE)),
        LOW(EnumSet.of(EvidenceBadge.LOW_EVIDENCE)),
        AMBIGUOUS(EnumSet.of(EvidenceBadge.AMBIGUOUS_ATTRIBUTION)),
        INSUFFICIENT(EnumSet.of(EvidenceBadge.INSUFFICIENT_EVIDENCE)),
        SYSTEM(EnumSet.of(
                EvidenceBadge.JVM_GC_SUSPECTED,
                EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE)),
        UNKNOWN(EnumSet.of(EvidenceBadge.UNKNOWN));

        private final Set<EvidenceBadge> evidenceStates;

        EvidenceFilter(Set<EvidenceBadge> evidenceStates) {
            this.evidenceStates = evidenceStates.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(evidenceStates));
        }

        public Set<EvidenceBadge> evidenceStates() {
            return evidenceStates;
        }

        public EvidenceFilter next() {
            EvidenceFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum DurationFilter {
        ANY(null),
        AT_LEAST_250_MS(250.0),
        AT_LEAST_500_MS(500.0),
        AT_LEAST_1_SECOND(1_000.0);

        private final Double minimumDurationMs;

        DurationFilter(Double minimumDurationMs) {
            this.minimumDurationMs = minimumDurationMs;
        }

        public Optional<Double> minimumDurationMs() {
            return Optional.ofNullable(minimumDurationMs);
        }

        public DurationFilter next() {
            DurationFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum CaseFilter {
        ANY(null),
        IN_CASE(Boolean.TRUE),
        NOT_IN_CASE(Boolean.FALSE);

        private final Boolean hasRecurringCase;

        CaseFilter(Boolean hasRecurringCase) {
            this.hasRecurringCase = hasRecurringCase;
        }

        public Optional<Boolean> hasRecurringCase() {
            return Optional.ofNullable(hasRecurringCase);
        }

        public CaseFilter next() {
            CaseFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
