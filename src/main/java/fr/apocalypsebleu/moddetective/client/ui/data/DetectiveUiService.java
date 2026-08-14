package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;
import fr.apocalypsebleu.moddetective.client.ui.data.query.CaseMembershipIndex;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQuery;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryEngine;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryResult;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchHistory;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.CaseEvolution;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.CaseEvolutionEngine;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.ModpackChangeHistory;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.RetainedHistoryCoverage;
import fr.apocalypsebleu.moddetective.core.casefile.CaseFile;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotService;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single background loader shared by all Detective screens. */
public final class DetectiveUiService {
    private static final Object LOCK = new Object();
    private static final DetectiveUiRepository REPOSITORY = new DetectiveUiRepository(ModDetectivePaths.incidents());
    private static final IncidentQueryEngine QUERY_ENGINE = new IncidentQueryEngine();
    private static final CaseEvolutionEngine CASE_EVOLUTION_ENGINE = new CaseEvolutionEngine();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Detective-UiData");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static CompletableFuture<IncidentIndexViewModel> cachedIndex;
    private static CompletableFuture<IncidentSearchHistory> cachedSearchHistory;
    private static CompletableFuture<CaseIndexViewModel> cachedCases;

    private DetectiveUiService() {}

    public static CompletableFuture<IncidentIndexViewModel> refreshIndex() {
        synchronized (LOCK) {
            cachedSearchHistory = CompletableFuture.supplyAsync(
                    () -> REPOSITORY.loadSearchHistory(ModDetective.SESSION_STARTED_AT_EPOCH_MS), WORKER);
            cachedIndex = cachedSearchHistory.thenApply(IncidentSearchHistory::incidentIndex);
            return cachedIndex;
        }
    }

    public static CompletableFuture<IncidentIndexViewModel> cachedIndex() {
        synchronized (LOCK) {
            return cachedIndex == null ? refreshIndex() : cachedIndex;
        }
    }

    public static void invalidateIndex() {
        synchronized (LOCK) {
            cachedIndex = null;
            cachedSearchHistory = null;
        }
    }

    /** Searches already loaded summaries on the UI-data worker; Case analysis remains separate. */
    public static CompletableFuture<IncidentQueryResult> queryIncidents(IncidentQuery query) {
        IncidentQuery requested = java.util.Objects.requireNonNull(query, "query");
        CompletableFuture<IncidentSearchHistory> history;
        synchronized (LOCK) {
            if (cachedSearchHistory == null) {
                refreshIndex();
            }
            history = cachedSearchHistory;
        }
        if (!requested.requiresCaseMembership()) {
            return history.thenApplyAsync(value -> QUERY_ENGINE.query(
                    value.records(), requested, CaseMembershipIndex.unavailable()), WORKER);
        }
        CompletableFuture<CaseMembershipIndex> memberships =
                DetectiveSupportService.preparedCaseHistory()
                        .handle((result, error) -> error == null && result != null
                                ? CaseMembershipIndex.available(result)
                                : CaseMembershipIndex.unavailable());
        return history.thenCombineAsync(memberships, (value, cases) -> QUERY_ENGINE.query(
                value.records(), requested, cases), WORKER);
    }

    public static CompletableFuture<CaseIndexViewModel> refreshCases() {
        synchronized (LOCK) {
            CompletableFuture<IncidentIndexViewModel> incidents = cachedIndex();
            cachedCases = DetectiveSupportService.preparedCaseHistory()
                    .thenCombineAsync(incidents, (cases, index) -> CaseUiAdapter.from(
                            cases, index, ModDetectivePaths.incidents()), WORKER);
            return cachedCases;
        }
    }

    public static CompletableFuture<CaseIndexViewModel> cachedCases() {
        synchronized (LOCK) {
            return cachedCases == null ? refreshCases() : cachedCases;
        }
    }

    /**
     * Correlates one prepared Case with retained incidents and the existing snapshot comparison.
     * Disk loading and analysis are both delegated to Detective's background workers.
     */
    public static CompletableFuture<CaseEvolution> caseEvolution(String caseId) {
        String requestedCaseId = java.util.Objects.requireNonNull(caseId, "caseId").strip();
        if (requestedCaseId.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("caseId must not be blank"));
        }
        CompletableFuture<IncidentSearchHistory> history;
        synchronized (LOCK) {
            if (cachedSearchHistory == null) {
                refreshIndex();
            }
            history = cachedSearchHistory;
        }
        return history.thenCombineAsync(
                DetectiveSupportService.preparedCaseHistory(),
                (incidents, cases) -> {
                    CaseFile selected = cases.cases().stream()
                            .filter(value -> value.caseId().equals(requestedCaseId))
                            .findFirst()
                            .orElseThrow(() -> new CompletionException(
                                    new IllegalArgumentException("Unknown Case: " + requestedCaseId)));
                    int unreadable = Math.max(
                            incidents.incidentIndex().unreadableFiles(),
                            cases.unreadableIncidents());
                    RetainedHistoryCoverage coverage = RetainedHistoryCoverage.bounded(
                            true,
                            cases.incidentsIgnoredByBound() > 0
                                    || incidents.records().size()
                                    >= DetectiveSupportService.settings().incidentHistoryLimit(),
                            unreadable);
                    return CASE_EVOLUTION_ENGINE.analyze(
                            selected,
                            incidents.records(),
                            coverage,
                            ModpackChangeHistory.from(ModSnapshotService.latestLaunchHistory()));
                },
                WORKER);
    }

    /** Keeps the bounded UI projection on the same background worker as Case analysis. */
    public static CompletableFuture<CaseEvolutionViewModel> caseEvolutionViewModel(String caseId) {
        return caseEvolution(caseId).thenApplyAsync(CaseEvolutionUiAdapter::from, WORKER);
    }

    public static void invalidateCases() {
        synchronized (LOCK) {
            cachedCases = null;
        }
    }

    public static CompletableFuture<IncidentDetailViewModel> loadDetail(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return REPOSITORY.loadDetail(source);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, WORKER);
    }

    /** Performs local disk loading and pairwise comparison on the existing UI-data worker. */
    public static CompletableFuture<IncidentComparison> compareIncidents(
            Path firstSource,
            Path secondSource
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return REPOSITORY.loadComparison(firstSource, secondSource);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, WORKER);
    }

    public static ModpackChangesViewModel modpackChanges() {
        return ModpackChangesAdapter.from(ModSnapshotService.latestDiff());
    }

    public static void shutdown() {
        WORKER.shutdownNow();
    }
}
