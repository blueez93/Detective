package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparisonLoader;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchHistory;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DetectiveUiRepository {
    private final Path incidentsRoot;
    private final IncidentComparisonLoader comparisonLoader;

    public DetectiveUiRepository(Path incidentsRoot) {
        this.incidentsRoot = incidentsRoot.toAbsolutePath().normalize();
        this.comparisonLoader = new IncidentComparisonLoader(this.incidentsRoot);
    }

    public IncidentIndexViewModel loadIndex(long sessionStartedEpochMs) {
        return loadSearchHistory(sessionStartedEpochMs).incidentIndex();
    }

    /** Loads the same bounded history used by the current index plus lightweight search metadata. */
    public IncidentSearchHistory loadSearchHistory(long sessionStartedEpochMs) {
        if (!Files.isDirectory(incidentsRoot)) {
            return IncidentSearchHistory.create(
                    List.of(), System.currentTimeMillis(), sessionStartedEpochMs, 0);
        }

        List<IncidentSearchRecord> incidents = new ArrayList<>();
        int unreadable = 0;
        try (var paths = Files.walk(incidentsRoot)) {
            for (Path path : paths.filter(DetectiveUiRepository::isIncidentFile).toList()) {
                try {
                    Path normalized = path.toAbsolutePath().normalize();
                    String incidentId = incidentsRoot.relativize(normalized)
                            .toString().replace('\\', '/');
                    incidents.add(IncidentJsonAdapter.readSearchRecord(normalized, incidentId));
                } catch (IOException | RuntimeException e) {
                    unreadable++;
                    ModDetective.LOGGER.debug("[Detective] Skipping unreadable incident file in the UI: {}", path, e);
                }
            }
        } catch (IOException e) {
            ModDetective.LOGGER.warn("[Detective] Unable to list incident files for the UI", e);
            unreadable++;
        }
        return IncidentSearchHistory.create(
                incidents, System.currentTimeMillis(), sessionStartedEpochMs, unreadable);
    }

    public IncidentDetailViewModel loadDetail(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (!normalized.startsWith(incidentsRoot)) {
            throw new IOException("Incident path is outside the Detective data directory");
        }
        return IncidentJsonAdapter.readDetail(normalized);
    }

    /** Loads and compares only the two requested local incident records. */
    public IncidentComparison loadComparison(Path firstSource, Path secondSource) throws IOException {
        return comparisonLoader.compare(firstSource, secondSource);
    }

    private static boolean isIncidentFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString();
        return name.startsWith("freeze-") && name.endsWith(".json");
    }
}
