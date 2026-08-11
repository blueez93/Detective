package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
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

    public static CompletableFuture<IncidentDetailViewModel> loadDetail(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return REPOSITORY.loadDetail(source);
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
