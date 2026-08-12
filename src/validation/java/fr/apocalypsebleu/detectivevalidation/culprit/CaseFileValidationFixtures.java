package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.moddetective.client.ui.data.CaseUiAdapter;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseFileViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;

import java.nio.file.Path;
import java.util.List;

/** Development-only deterministic Case Files states; excluded from the public main JAR. */
public final class CaseFileValidationFixtures {
    private static final Path INCIDENTS_ROOT = Path.of("validation-case-incidents").toAbsolutePath().normalize();
    private static final long BASE_TIME = 1_786_530_000_000L;

    private CaseFileValidationFixtures() {}

    public static CaseIndexViewModel emptyCaseFiles() {
        return CaseIndexViewModel.empty();
    }

    public static CaseIndexViewModel oneHighConsistencyCase() {
        List<IncidentSummaryViewModel> incidents = List.of(
                incident("freeze-machines-a.json", BASE_TIME, 420.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Machines"),
                incident("freeze-machines-b.json", BASE_TIME + 3_600_000L, 470.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Machines"),
                incident("freeze-machines-c.json", BASE_TIME + 7_200_000L, 510.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Machines"),
                incident("freeze-machines-d.json", BASE_TIME + 10_800_000L, 460.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Machines"));
        CaseFile caseFile = new CaseFile(
                "case-18d47a60d153a2bc",
                incidents.stream().map(IncidentSummaryViewModel::id).toList(),
                incidents.getFirst().detectedAtEpochMs(), incidents.getLast().detectedAtEpochMs(),
                incidents.size(), 465.0, 510.0,
                List.of(
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.CLASS,
                                "example.machines.TickCoordinator", 4, 0.86),
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.STACK_PATH,
                                "sha256:6b175d9f-example", 3, 0.78)),
                List.of(new CaseFile.RecurringOwner("example_machines", 4, 0.81, 0.94)),
                0.92, 0.89);
        return project(List.of(caseFile), incidents);
    }

    public static CaseIndexViewModel multipleCases() {
        CaseIndexViewModel machines = oneHighConsistencyCase();
        List<IncidentSummaryViewModel> worldgenIncidents = List.of(
                incident("freeze-worldgen-a.json", BASE_TIME - 86_400_000L, 760.0,
                        EvidenceBadge.AMBIGUOUS_ATTRIBUTION, ""),
                incident("freeze-worldgen-b.json", BASE_TIME - 82_800_000L, 890.0,
                        EvidenceBadge.LOW_EVIDENCE, "Example Worldgen"),
                incident("freeze-worldgen-c.json", BASE_TIME - 79_200_000L, 810.0,
                        EvidenceBadge.INSUFFICIENT_EVIDENCE, ""));
        CaseFile worldgen = new CaseFile(
                "case-92a0c44bd814ee31",
                worldgenIncidents.stream().map(IncidentSummaryViewModel::id).toList(),
                worldgenIncidents.getFirst().detectedAtEpochMs(),
                worldgenIncidents.getLast().detectedAtEpochMs(), 3, 820.0, 890.0,
                List.of(new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.FRAME,
                        "sha256:worldgen-example", 3, 0.72)),
                List.of(new CaseFile.RecurringOwner("example_worldgen", 3, 0.31, 0.77)),
                0.79, 0.74);

        CaseFileViewModel machineCase = machines.cases().getFirst();
        List<IncidentSummaryViewModel> allIncidents = new java.util.ArrayList<>(worldgenIncidents);
        machineCase.relatedIncidents().stream()
                .filter(value -> value.incident() != null)
                .map(value -> value.incident())
                .forEach(allIncidents::add);
        CaseFile machinesSource = sourceFrom(machineCase);
        return project(List.of(machinesSource, worldgen), allIncidents);
    }

    public static CaseFileViewModel caseDetailWithSeveralRelatedIncidents() {
        List<IncidentSummaryViewModel> incidents = List.of(
                incident("freeze-example-01.json", BASE_TIME, 621.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-02.json", BASE_TIME + 1_350_000L, 636.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Mod"),
                incident("freeze-example-03.json", BASE_TIME + 2_700_000L, 649.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-04.json", BASE_TIME + 4_050_000L, 663.2,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-05.json", BASE_TIME + 5_400_000L, 641.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Mod"),
                incident("freeze-example-06.json", BASE_TIME + 6_750_000L, 652.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-07.json", BASE_TIME + 8_100_000L, 638.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Mod"),
                incident("freeze-example-08.json", BASE_TIME + 9_450_000L, 650.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-09.json", BASE_TIME + 10_800_000L, 649.3,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"));
        CaseFile caseFile = new CaseFile(
                "case-8f3d09e0c1a2b3c4",
                incidents.stream().map(IncidentSummaryViewModel::id).toList(),
                incidents.getFirst().detectedAtEpochMs(), incidents.getLast().detectedAtEpochMs(),
                incidents.size(), 644.5, 663.2,
                List.of(
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.CLASS,
                                "sha256:example-class-signature-01", 9, 0.929),
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.FRAME,
                                "sha256:example-frame-signature-02", 9, 0.996),
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.STACK_PATH,
                                "sha256:example-stack-signature-03", 9, 1.000),
                        new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.CLASS,
                                "sha256:example-class-signature-04", 9, 0.936)),
                List.of(new CaseFile.RecurringOwner("example_mod", 9, 0.957, 0.957)),
                0.921, 0.914);
        return project(List.of(caseFile), incidents).cases().getFirst();
    }

    public static CaseFileViewModel mixedAttributionStates() {
        List<IncidentSummaryViewModel> incidents = List.of(
                incident("freeze-example-a.json", BASE_TIME + 20_000_000L, 330.0,
                        EvidenceBadge.HIGH_EVIDENCE, "Example Mod"),
                incident("freeze-example-b.json", BASE_TIME + 21_000_000L, 350.0,
                        EvidenceBadge.AMBIGUOUS_ATTRIBUTION, ""),
                incident("freeze-example-c.json", BASE_TIME + 22_000_000L, 390.0,
                        EvidenceBadge.INSUFFICIENT_EVIDENCE, ""),
                incident("freeze-example-d.json", BASE_TIME + 23_000_000L, 370.0,
                        EvidenceBadge.MODERATE_EVIDENCE, "Example Mod"));
        CaseFile caseFile = new CaseFile(
                "case-47b089e31d556a12",
                incidents.stream().map(IncidentSummaryViewModel::id).toList(),
                incidents.getFirst().detectedAtEpochMs(), incidents.getLast().detectedAtEpochMs(),
                incidents.size(), 360.0, 390.0,
                List.of(new CaseFile.RecurringEvidence(CaseFile.EvidenceKind.FRAME,
                        "sha256:mixed-example", 4, 0.81)),
                List.of(new CaseFile.RecurringOwner("example_mod", 3, 0.54, 0.84)),
                0.86, 0.82);
        return project(List.of(caseFile), incidents).cases().getFirst();
    }

    public static CaseIndexViewModel legacySparseHistory() {
        return CaseIndexViewModel.empty();
    }

    private static CaseIndexViewModel project(
            List<CaseFile> cases,
            List<IncidentSummaryViewModel> incidents
    ) {
        IncidentIndexViewModel incidentIndex = IncidentIndexViewModel.create(
                incidents, BASE_TIME + 20_000_000L, BASE_TIME - 1_000L, 0);
        return CaseUiAdapter.from(cases, incidentIndex, INCIDENTS_ROOT, 0);
    }

    private static CaseFile sourceFrom(CaseFileViewModel source) {
        return new CaseFile(
                source.caseId(),
                source.relatedIncidents().stream().map(value -> value.incidentId()).toList(),
                source.firstSeenEpochMs(), source.lastSeenEpochMs(), source.occurrenceCount(),
                source.averageStallDurationMs(), source.longestStallDurationMs(),
                source.recurringEvidence().stream().map(value -> new CaseFile.RecurringEvidence(
                        CaseFile.EvidenceKind.valueOf(value.kind().name()), value.technicalSignature(),
                        value.supportingIncidents(), value.averageObservedSharePercent() / 100.0)).toList(),
                source.recurringOwners().stream().map(value -> new CaseFile.RecurringOwner(
                        value.ownerId(), value.supportingIncidents(),
                        value.averageLeafSharePercent() / 100.0,
                        value.averageStackPresenceSharePercent() / 100.0)).toList(),
                source.consistencyPercent() / 100.0, source.evidenceStrengthPercent() / 100.0);
    }

    private static IncidentSummaryViewModel incident(
            String id,
            long detectedAt,
            double duration,
            EvidenceBadge evidence,
            String primarySuspect
    ) {
        boolean attributed = !primarySuspect.isBlank()
                && evidence != EvidenceBadge.AMBIGUOUS_ATTRIBUTION
                && evidence != EvidenceBadge.INSUFFICIENT_EVIDENCE;
        return new IncidentSummaryViewModel(
                id, INCIDENTS_ROOT.resolve(id), detectedAt, duration, 120.0, 18,
                evidence, attributed ? "ATTRIBUTED" : evidence.name(),
                attributed ? primarySuspect : "", attributed,
                UiFormatters.dateTime(detectedAt), "Overworld", "—");
    }
}
