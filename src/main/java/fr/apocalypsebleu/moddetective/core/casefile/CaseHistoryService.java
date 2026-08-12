package fr.apocalypsebleu.moddetective.core.casefile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Loads local incident history, computes Cases, reconciles identities, and persists the result. */
public final class CaseHistoryService {
    private final Path incidentsRoot;
    private final IncidentHistoryLoader historyLoader;
    private final CaseClusterer clusterer;
    private final CaseFileStore caseStore;
    private final int maximumIncidents;

    public CaseHistoryService(Path incidentsRoot, Path caseIndex) {
        this(incidentsRoot, new IncidentHistoryLoader(), new CaseClusterer(),
                new CaseFileStore(caseIndex),
                CaseClusterer.DEFAULT_CONFIGURATION.maximumIncidents());
    }

    public CaseHistoryService(
            Path incidentsRoot,
            IncidentHistoryLoader historyLoader,
            CaseClusterer clusterer,
            CaseFileStore caseStore,
            int maximumIncidents
    ) {
        this.incidentsRoot = Objects.requireNonNull(incidentsRoot, "incidentsRoot")
                .toAbsolutePath().normalize();
        this.historyLoader = Objects.requireNonNull(historyLoader, "historyLoader");
        this.clusterer = Objects.requireNonNull(clusterer, "clusterer");
        this.caseStore = Objects.requireNonNull(caseStore, "caseStore");
        if (maximumIncidents < 1) {
            throw new IllegalArgumentException("maximumIncidents must be positive");
        }
        this.maximumIncidents = maximumIncidents;
    }

    public Result refresh() throws IOException {
        IncidentHistoryLoader.LoadResult history = historyLoader.load(incidentsRoot, maximumIncidents);
        List<CaseFile> computed = clusterer.cluster(history.fingerprints());
        CaseFileStore.LoadResult persisted = caseStore.load();
        List<CaseFile> resolved = CaseIdentityResolver.resolve(computed, persisted.cases());
        boolean written = false;
        if (persisted.writable()) {
            caseStore.save(resolved);
            written = true;
        }
        return new Result(
                resolved,
                history.fingerprints().size(),
                history.unreadableFiles(),
                history.ignoredByBound(),
                persisted.unreadableEntries(),
                written);
    }

    public record Result(
            List<CaseFile> cases,
            int loadedIncidents,
            int unreadableIncidents,
            int incidentsIgnoredByBound,
            int unreadablePersistedCases,
            boolean caseIndexWritten
    ) {
        public Result {
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        }
    }
}
