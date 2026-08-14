package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import fr.apocalypsebleu.moddetective.core.casefile.CaseHistoryService;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Explicit active-Case membership keyed only by persisted incident IDs. */
public final class CaseMembershipIndex {
    private static final CaseMembershipIndex UNAVAILABLE =
            new CaseMembershipIndex(Availability.UNAVAILABLE, Map.of());

    private final Availability availability;
    private final Map<String, Set<String>> caseIdsByIncidentId;

    private CaseMembershipIndex(
            Availability availability,
            Map<String, Set<String>> caseIdsByIncidentId
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        TreeMap<String, Set<String>> immutable = new TreeMap<>();
        caseIdsByIncidentId.forEach((incidentId, caseIds) -> immutable.put(
                requireText(incidentId, "incidentId"),
                Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(caseIds, "caseIds")))));
        this.caseIdsByIncidentId = Collections.unmodifiableMap(immutable);
    }

    public static CaseMembershipIndex available(List<CaseFile> cases) {
        Objects.requireNonNull(cases, "cases");
        Map<String, Set<String>> memberships = new TreeMap<>();
        for (CaseFile caseFile : cases) {
            Objects.requireNonNull(caseFile, "caseFile");
            for (String incidentId : caseFile.relatedIncidentIds()) {
                memberships.computeIfAbsent(requireText(incidentId, "incidentId"),
                        ignored -> new TreeSet<>()).add(
                                caseFile.caseId().strip().toLowerCase(Locale.ROOT));
            }
        }
        return new CaseMembershipIndex(Availability.AVAILABLE, memberships);
    }

    public static CaseMembershipIndex available(CaseHistoryService.Result result) {
        return available(Objects.requireNonNull(result, "result").cases());
    }

    public static CaseMembershipIndex unavailable() {
        return UNAVAILABLE;
    }

    public Availability availability() {
        return availability;
    }

    public Set<String> caseIds(IncidentSearchRecord incident) {
        Objects.requireNonNull(incident, "incident");
        if (availability == Availability.UNAVAILABLE) {
            return Set.of();
        }
        TreeSet<String> result = new TreeSet<>();
        for (String alias : incident.identityAliases()) {
            result.addAll(caseIdsByIncidentId.getOrDefault(alias, Set.of()));
        }
        return Collections.unmodifiableSet(result);
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
