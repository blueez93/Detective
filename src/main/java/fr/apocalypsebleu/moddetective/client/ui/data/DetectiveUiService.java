package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;
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
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Detective-UiData");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static CompletableFuture<IncidentIndexViewModel> cachedIndex;
    private static CompletableFuture<CaseIndexViewModel> cachedCases;

    private DetectiveUiService() {}

    public static CompletableFuture<IncidentIndexViewModel> refreshIndex() {
        synchronized (LOCK) {
            cachedIndex = CompletableFuture.supplyAsync(
                    () -> REPOSITORY.loadIndex(ModDetective.SESSION_STARTED_AT_EPOCH_MS), WORKER);
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
        }
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
