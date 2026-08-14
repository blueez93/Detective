package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionUiFormatter;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.HistoryCoverageViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.NearbyChangeViewModel;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Development-only neutral Case Evolution states; excluded from the public JAR. */
public final class CaseEvolutionValidationFixtures {
    private static final String CASE_ID = "case-8f3d09e0c1a2b3c4";
    private static final long FIRST_RECORDED = 1_786_530_000_000L;

    private CaseEvolutionValidationFixtures() {}

    public static CaseEvolutionViewModel updateBefore() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.UPDATED,
                        "example_machines", "Example Machines",
                        Optional.of("1.4.2"), Optional.of("1.5.0"),
                        -Duration.ofHours(6).plusMinutes(17).toMillis(), false, 0, 9)));
    }

    public static CaseEvolutionViewModel addedBefore() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.ADDED,
                        "example_storage", "Example Storage",
                        Optional.empty(), Optional.of("2.1.0"),
                        -Duration.ofHours(2).plusMinutes(5).toMillis(), false, 0, 9)));
    }

    public static CaseEvolutionViewModel removedBefore() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.REMOVED,
                        "example_worldgen", "Example Worldgen",
                        Optional.of("3.0.1"), Optional.empty(),
                        -Duration.ofHours(8).toMillis(), false, 0, 9)));
    }

    public static CaseEvolutionViewModel changeAfter() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.UPDATED,
                        "example_mod", "Example Mod",
                        Optional.of("1.0.0"), Optional.of("1.1.0"),
                        Duration.ofHours(1).plusMinutes(20).toMillis(), false, 4, 5)));
    }

    public static CaseEvolutionViewModel multiple() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(
                        change(NearbyChangeViewModel.Type.ADDED,
                                "example_storage", "Example Storage",
                                Optional.empty(), Optional.of("2.1.0"),
                                -Duration.ofMinutes(35).toMillis(), false, 0, 9),
                        change(NearbyChangeViewModel.Type.UPDATED,
                                "example_machines", "Example Machines",
                                Optional.of("1.4.2"), Optional.of("1.5.0"),
                                -Duration.ofHours(3).toMillis(), false, 0, 9),
                        change(NearbyChangeViewModel.Type.REMOVED,
                                "example_worldgen", "Example Worldgen",
                                Optional.of("3.0.1"), Optional.empty(),
                                Duration.ofHours(4).toMillis(), false, 6, 3)));
    }

    public static CaseEvolutionViewModel none() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE, List.of());
    }

    public static CaseEvolutionViewModel limitedBefore() {
        NearbyChangeViewModel change = change(NearbyChangeViewModel.Type.UPDATED,
                "example_machines", "Example Machines",
                Optional.of("1.4.2"), Optional.of("1.5.0"),
                -Duration.ofHours(6).toMillis(), false, -1, -1);
        return evolution(HistoryCoverageViewModel.Status.LIMITED_BEFORE,
                CaseEvolutionViewModel.HistoryAvailability.PARTIAL, List.of(change));
    }

    public static CaseEvolutionViewModel insufficient() {
        return new CaseEvolutionViewModel(
                CASE_ID,
                OptionalLong.empty(),
                new HistoryCoverageViewModel(HistoryCoverageViewModel.Status.INSUFFICIENT),
                CaseEvolutionViewModel.HistoryAvailability.UNAVAILABLE,
                List.of(),
                0);
    }

    public static CaseEvolutionViewModel sameLaunch() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.ADDED,
                        "example_mod", "Example Mod",
                        Optional.empty(), Optional.of("1.0.0"),
                        -Duration.ofMinutes(45).toMillis(), true, 0, 9)));
    }

    public static CaseEvolutionViewModel longModName() {
        return evolution(HistoryCoverageViewModel.Status.SUFFICIENT,
                CaseEvolutionViewModel.HistoryAvailability.COMPLETE,
                List.of(change(NearbyChangeViewModel.Type.UPDATED,
                        "example_machines_with_an_intentionally_long_validation_identifier_for_scale_two_"
                                + "and_narrow_windows_that_must_be_ellipsized_safely",
                        "Example Machines",
                        Optional.of("2026.08.14-development-preview-build-0000001-with-extra-fixture-text"),
                        Optional.of("2026.08.14-development-preview-build-0000002-with-extra-fixture-text"),
                        -Duration.ofHours(1).plusMinutes(9).toMillis(), false, 0, 9)));
    }

    private static CaseEvolutionViewModel evolution(
            HistoryCoverageViewModel.Status coverage,
            CaseEvolutionViewModel.HistoryAvailability availability,
            List<NearbyChangeViewModel> changes
    ) {
        return new CaseEvolutionViewModel(
                CASE_ID,
                OptionalLong.of(FIRST_RECORDED),
                new HistoryCoverageViewModel(coverage),
                availability,
                changes,
                changes.size());
    }

    private static NearbyChangeViewModel change(
            NearbyChangeViewModel.Type type,
            String modId,
            String displayName,
            Optional<String> previousVersion,
            Optional<String> newVersion,
            long offset,
            boolean sameLaunch,
            int before,
            int after
    ) {
        NearbyChangeViewModel.BeforeAfterViewModel comparison = before < 0 || after < 0
                ? NearbyChangeViewModel.BeforeAfterViewModel.unavailable()
                : NearbyChangeViewModel.BeforeAfterViewModel.available(before, after);
        return new NearbyChangeViewModel(
                type,
                modId,
                displayName + " (" + modId + ")",
                previousVersion,
                newVersion,
                FIRST_RECORDED + offset,
                offset,
                offset < 0L ? NearbyChangeViewModel.Direction.BEFORE
                        : offset > 0L ? NearbyChangeViewModel.Direction.AFTER
                        : NearbyChangeViewModel.Direction.AT,
                CaseEvolutionUiFormatter.offsetMagnitude(offset),
                sameLaunch,
                comparison);
    }
}
