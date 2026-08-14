package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

/** Immutable, UI-agnostic criteria for searching an already loaded incident history. */
public record IncidentQuery(
        Optional<String> freeText,
        Set<EvidenceBadge> evidenceStates,
        OptionalDouble minimumStallDurationMs,
        OptionalDouble maximumStallDurationMs,
        Optional<String> ownerId,
        Optional<String> dimensionId,
        OptionalLong detectedAtOrAfterEpochMs,
        OptionalLong detectedAtOrBeforeEpochMs,
        Optional<Boolean> hasRecurringCase,
        Optional<String> caseId,
        Sort sort
) {
    public IncidentQuery {
        freeText = normalizedOptional(freeText, false);
        Objects.requireNonNull(evidenceStates, "evidenceStates");
        evidenceStates = evidenceStates.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(evidenceStates));
        Objects.requireNonNull(minimumStallDurationMs, "minimumStallDurationMs");
        Objects.requireNonNull(maximumStallDurationMs, "maximumStallDurationMs");
        minimumStallDurationMs.ifPresent(value -> requireDuration(value, "minimumStallDurationMs"));
        maximumStallDurationMs.ifPresent(value -> requireDuration(value, "maximumStallDurationMs"));
        if (minimumStallDurationMs.isPresent() && maximumStallDurationMs.isPresent()
                && minimumStallDurationMs.getAsDouble() > maximumStallDurationMs.getAsDouble()) {
            throw new IllegalArgumentException("minimumStallDurationMs must not exceed maximumStallDurationMs");
        }
        ownerId = normalizedOptional(ownerId, true);
        dimensionId = normalizedOptional(dimensionId, true);
        Objects.requireNonNull(detectedAtOrAfterEpochMs, "detectedAtOrAfterEpochMs");
        Objects.requireNonNull(detectedAtOrBeforeEpochMs, "detectedAtOrBeforeEpochMs");
        if (detectedAtOrAfterEpochMs.isPresent() && detectedAtOrBeforeEpochMs.isPresent()
                && detectedAtOrAfterEpochMs.getAsLong() > detectedAtOrBeforeEpochMs.getAsLong()) {
            throw new IllegalArgumentException("date range start must not exceed its end");
        }
        Objects.requireNonNull(hasRecurringCase, "hasRecurringCase");
        caseId = normalizedOptional(caseId, true);
        if (caseId.isPresent() && hasRecurringCase.filter(value -> !value).isPresent()) {
            throw new IllegalArgumentException("A specific Case cannot be combined with hasRecurringCase=false");
        }
        sort = Objects.requireNonNull(sort, "sort");
    }

    public static IncidentQuery empty() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean requiresCaseMembership() {
        return hasRecurringCase.isPresent() || caseId.isPresent();
    }

    public enum Sort {
        NEWEST_FIRST,
        OLDEST_FIRST,
        LONGEST_STALL_FIRST,
        SHORTEST_STALL_FIRST
    }

    public static final class Builder {
        private String freeText;
        private Set<EvidenceBadge> evidenceStates = Set.of();
        private OptionalDouble minimumStallDurationMs = OptionalDouble.empty();
        private OptionalDouble maximumStallDurationMs = OptionalDouble.empty();
        private String ownerId;
        private String dimensionId;
        private OptionalLong detectedAtOrAfterEpochMs = OptionalLong.empty();
        private OptionalLong detectedAtOrBeforeEpochMs = OptionalLong.empty();
        private Optional<Boolean> hasRecurringCase = Optional.empty();
        private String caseId;
        private Sort sort = Sort.NEWEST_FIRST;

        public Builder freeText(String value) {
            freeText = value;
            return this;
        }

        public Builder evidenceStates(Set<EvidenceBadge> values) {
            evidenceStates = Objects.requireNonNull(values, "values");
            return this;
        }

        public Builder minimumStallDurationMs(double value) {
            minimumStallDurationMs = OptionalDouble.of(value);
            return this;
        }

        public Builder maximumStallDurationMs(double value) {
            maximumStallDurationMs = OptionalDouble.of(value);
            return this;
        }

        public Builder ownerId(String value) {
            ownerId = value;
            return this;
        }

        public Builder dimensionId(String value) {
            dimensionId = value;
            return this;
        }

        public Builder detectedAtOrAfterEpochMs(long value) {
            detectedAtOrAfterEpochMs = OptionalLong.of(value);
            return this;
        }

        public Builder detectedAtOrBeforeEpochMs(long value) {
            detectedAtOrBeforeEpochMs = OptionalLong.of(value);
            return this;
        }

        public Builder hasRecurringCase(boolean value) {
            hasRecurringCase = Optional.of(value);
            return this;
        }

        public Builder caseId(String value) {
            caseId = value;
            return this;
        }

        public Builder sort(Sort value) {
            sort = Objects.requireNonNull(value, "value");
            return this;
        }

        public IncidentQuery build() {
            return new IncidentQuery(
                    Optional.ofNullable(freeText), evidenceStates,
                    minimumStallDurationMs, maximumStallDurationMs,
                    Optional.ofNullable(ownerId), Optional.ofNullable(dimensionId),
                    detectedAtOrAfterEpochMs, detectedAtOrBeforeEpochMs,
                    hasRecurringCase, Optional.ofNullable(caseId), sort);
        }
    }

    private static Optional<String> normalizedOptional(Optional<String> source, boolean identifier) {
        Objects.requireNonNull(source, "source");
        return source.map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> identifier ? value.toLowerCase(Locale.ROOT) : value);
    }

    private static void requireDuration(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
