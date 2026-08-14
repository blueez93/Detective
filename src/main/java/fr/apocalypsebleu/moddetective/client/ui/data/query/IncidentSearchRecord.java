package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/** Lightweight searchable metadata referencing, rather than duplicating, an incident summary. */
public record IncidentSearchRecord(
        String incidentId,
        IncidentSummaryViewModel summary,
        Set<String> ownerIds,
        Set<String> modDisplayNames,
        Optional<String> dimensionId,
        OptionalLong detectedAtEpochMs,
        OptionalDouble stallDurationMs
) {
    public IncidentSearchRecord {
        incidentId = requireText(incidentId, "incidentId");
        summary = Objects.requireNonNull(summary, "summary");
        ownerIds = immutableNormalized(ownerIds, "ownerIds", true);
        modDisplayNames = immutableNormalized(modDisplayNames, "modDisplayNames", false);
        dimensionId = normalizedOptional(dimensionId, true);
        Objects.requireNonNull(detectedAtEpochMs, "detectedAtEpochMs");
        Objects.requireNonNull(stallDurationMs, "stallDurationMs");
        stallDurationMs.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("stallDurationMs must be finite and non-negative");
            }
        });
    }

    public Set<String> identityAliases() {
        TreeSet<String> aliases = new TreeSet<>();
        aliases.add(incidentId);
        aliases.add(summary.id());
        return Collections.unmodifiableSet(aliases);
    }

    private static Set<String> immutableNormalized(
            Set<String> source,
            String name,
            boolean identifier
    ) {
        Objects.requireNonNull(source, name);
        TreeSet<String> result = new TreeSet<>();
        for (String value : source) {
            String normalized = requireText(value, name + " value");
            result.add(identifier ? normalized.toLowerCase(Locale.ROOT) : normalized);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Optional<String> normalizedOptional(Optional<String> source, boolean identifier) {
        Objects.requireNonNull(source, "source");
        return source.map(value -> requireText(value, "optional value"))
                .map(value -> identifier ? value.toLowerCase(Locale.ROOT) : value);
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
