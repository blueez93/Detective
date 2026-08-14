package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.moddetective.client.ui.data.IncidentComparisonUiAdapter;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQuery;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryResult;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentComparisonViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentInvestigationState;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.casefile.IncidentFingerprint;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/** Neutral, local-only data for deterministic v0.9 investigation screenshots. */
final class IncidentInvestigationValidationFixtures {
    private IncidentInvestigationValidationFixtures() {}

    static IncidentInvestigationState searchResults() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setSearchText("example");
        List<IncidentSearchRecord> matches = incidents();
        apply(state, matches, 8, IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED);
        return state;
    }

    static IncidentInvestigationState emptySearchResults() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setSearchText("no matching fictional mod");
        apply(state, List.of(), 8, IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED);
        return state;
    }

    static IncidentInvestigationState activeFilters() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        state.setEvidenceFilter(IncidentInvestigationState.EvidenceFilter.HIGH);
        state.setDurationFilter(IncidentInvestigationState.DurationFilter.AT_LEAST_500_MS);
        state.setCaseFilter(IncidentInvestigationState.CaseFilter.IN_CASE);
        state.setSort(IncidentQuery.Sort.LONGEST_STALL_FIRST);
        apply(state, incidents().subList(0, 2), 8, IncidentQueryResult.CaseFilterStatus.AVAILABLE);
        return state;
    }

    static IncidentInvestigationState comparisonSelection() {
        IncidentInvestigationState state = new IncidentInvestigationState();
        List<IncidentSearchRecord> incidents = incidents();
        apply(state, incidents, incidents.size(), IncidentQueryResult.CaseFilterStatus.NOT_REQUESTED);
        state.toggleSelection(incidents.get(0));
        state.toggleSelection(incidents.get(1));
        return state;
    }

    static IncidentComparisonViewModel highComparison() {
        return comparisonView(0.94, false, false, true);
    }

    static IncidentComparisonViewModel lowComparison() {
        return comparisonView(0.12, false, false, false);
    }

    static IncidentComparisonViewModel legacyComparison() {
        return comparisonView(0.78, true, false, false);
    }

    static IncidentComparisonViewModel insufficientComparison() {
        return comparisonView(0.0, false, true, false);
    }

    private static IncidentComparisonViewModel comparisonView(
            double score,
            boolean legacy,
            boolean insufficient,
            boolean longAttribution
    ) {
        IncidentSearchRecord firstRecord = incidents().get(0);
        IncidentSearchRecord secondRecord = incidents().get(1);
        IncidentComparison.EvidenceAvailability unavailable = insufficient
                ? IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE
                : IncidentComparison.EvidenceAvailability.NOT_CAPTURED;
        IncidentComparison.EvidenceSetComparison classes = insufficient
                ? evidence(unavailable, List.of(), List.of(), List.of())
                : evidence(IncidentComparison.EvidenceAvailability.AVAILABLE,
                        List.of(hash('a'), hash('b')), List.of(hash('c')), List.of(hash('d')));
        IncidentComparison.EvidenceSetComparison frames = legacy || insufficient
                ? evidence(unavailable, List.of(), List.of(), List.of())
                : evidence(IncidentComparison.EvidenceAvailability.AVAILABLE,
                        List.of(hash('e')), List.of(), List.of(hash('f')));
        IncidentComparison.EvidenceSetComparison paths = legacy || insufficient
                ? evidence(unavailable, List.of(), List.of(), List.of())
                : evidence(IncidentComparison.EvidenceAvailability.AVAILABLE,
                        List.of(hash('g')), List.of(hash('h')), List.of());
        IncidentComparison.EvidenceSetComparison owners = insufficient
                ? evidence(unavailable, List.of(), List.of(), List.of())
                : evidence(IncidentComparison.EvidenceAvailability.AVAILABLE,
                        List.of("example_mod"), List.of("example_machines"),
                        List.of("example_worldgen"));
        IncidentComparison.EvidenceSetComparison unavailableOwners =
                evidence(unavailable, List.of(), List.of(), List.of());
        IncidentComparison.TechnicalSimilarity similarity = insufficient
                ? new IncidentComparison.TechnicalSimilarity(
                        IncidentComparison.EvidenceAvailability.INSUFFICIENT_EVIDENCE,
                        OptionalDouble.empty(), 0.0, List.of())
                : new IncidentComparison.TechnicalSimilarity(
                        IncidentComparison.EvidenceAvailability.AVAILABLE,
                        OptionalDouble.of(score), 1.0, List.of());
        IncidentComparison comparison = new IncidentComparison(
                descriptor("incident-a", legacy), descriptor("incident-b", false),
                new IncidentComparison.LongDifference(
                        OptionalLong.of(1_723_632_800_000L),
                        OptionalLong.of(1_723_719_200_000L), OptionalLong.of(86_400_000L)),
                new IncidentComparison.DoubleDifference(
                        OptionalDouble.of(840.0), OptionalDouble.of(690.0), OptionalDouble.of(-150.0)),
                new IncidentComparison.IntDifference(
                        OptionalInt.of(24), OptionalInt.of(22), OptionalInt.of(-2)),
                similarity, classes, frames, paths, owners,
                unavailableOwners, unavailableOwners, unavailableOwners,
                new IncidentComparison.ContextComparison(
                        new IncidentComparison.LongDifference(
                                OptionalLong.of(1_073_741_824L), OptionalLong.of(1_288_490_188L),
                                OptionalLong.of(214_748_364L)),
                        new IncidentComparison.LongDifference(
                                OptionalLong.of(4_294_967_296L), OptionalLong.of(4_294_967_296L),
                                OptionalLong.of(0L)),
                        new IncidentComparison.TextDifference(
                                Optional.of("minecraft:overworld"),
                                Optional.of("minecraft:overworld"), Optional.of(true)),
                        new IncidentComparison.PositionDifference(
                                Optional.empty(), Optional.empty(), Optional.empty())));
        IncidentSummaryViewModel firstSummary = longAttribution
                ? withPrimary(firstRecord.summary(),
                        "Example Machines With A Deliberately Long Display Name "
                                + "(example_machines_with_a_deliberately_long_mod_identifier)")
                : firstRecord.summary();
        IncidentSummaryViewModel secondSummary = longAttribution
                ? withPrimary(secondRecord.summary(),
                        "Example Worldgen With A Deliberately Long Display Name "
                                + "(example_worldgen_with_a_deliberately_long_mod_identifier)")
                : secondRecord.summary();
        return IncidentComparisonUiAdapter.from(
                comparison, firstSummary, secondSummary, Map.of(
                        "example_mod", "Example Mod",
                        "example_machines", "Example Machines",
                        "example_worldgen", "Example Worldgen"));
    }

    private static IncidentSummaryViewModel withPrimary(
            IncidentSummaryViewModel source,
            String primarySuspect
    ) {
        return new IncidentSummaryViewModel(
                source.id(), source.source(), source.detectedAtEpochMs(), source.durationMs(),
                source.thresholdMs(), source.watchdogSamples(), source.evidence(),
                source.rawEvidenceState(), primarySuspect, true, source.occurredAt(),
                source.dimension(), source.coordinates());
    }

    private static IncidentComparison.IncidentDescriptor descriptor(String id, boolean legacy) {
        return new IncidentComparison.IncidentDescriptor(
                id, AttributionEvidence.State.ATTRIBUTED,
                IncidentFingerprint.StallType.RENDER_THREAD_STALL,
                legacy ? IncidentFingerprint.EvidenceSource.LEGACY_FALLBACK
                        : IncidentFingerprint.EvidenceSource.DERIVED_V1);
    }

    private static IncidentComparison.EvidenceSetComparison evidence(
            IncidentComparison.EvidenceAvailability availability,
            List<String> shared,
            List<String> first,
            List<String> second
    ) {
        return new IncidentComparison.EvidenceSetComparison(availability, shared, first, second);
    }

    private static void apply(
            IncidentInvestigationState state,
            List<IncidentSearchRecord> matches,
            int total,
            IncidentQueryResult.CaseFilterStatus caseStatus
    ) {
        IncidentQuery query = state.query();
        state.applyResult(new IncidentQueryResult(
                matches, total, matches.size(), query, query.sort(), caseStatus));
    }

    private static List<IncidentSearchRecord> incidents() {
        return List.of(
                incident("incident-a", "Example Mod", "example_mod", 840.0, 1_723_632_800_000L),
                incident("incident-b", "Example Machines", "example_machines", 690.0,
                        1_723_719_200_000L),
                incident("incident-c", "Example Worldgen", "example_worldgen", 520.0,
                        1_723_805_600_000L));
    }

    private static IncidentSearchRecord incident(
            String id,
            String modName,
            String modId,
            double duration,
            long detectedAt
    ) {
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                id, Path.of("validation-" + id + ".json"), detectedAt,
                duration, 120.0, 24, EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED",
                modName, true, "2026-08-14 12:00:00", "Overworld", "128, 64, -342");
        return new IncidentSearchRecord(
                id, summary, Set.of(modId), Set.of(modName),
                Optional.of("minecraft:overworld"), OptionalLong.of(detectedAt),
                OptionalDouble.of(duration));
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
