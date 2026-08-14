package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparisonLoader;

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
        if (!Files.isDirectory(incidentsRoot)) {
            return IncidentIndexViewModel.empty(System.currentTimeMillis(), sessionStartedEpochMs);
        }

        List<IncidentSummaryViewModel> incidents = new ArrayList<>();
        int unreadable = 0;
        try (var paths = Files.walk(incidentsRoot)) {
            for (Path path : paths.filter(DetectiveUiRepository::isIncidentFile).toList()) {
                try {
                    incidents.add(IncidentJsonAdapter.readSummary(path));
                } catch (IOException | RuntimeException e) {
                    unreadable++;
                    ModDetective.LOGGER.debug("[Detective] Skipping unreadable incident file in the UI: {}", path, e);
                }
            }
        } catch (IOException e) {
            ModDetective.LOGGER.warn("[Detective] Unable to list incident files for the UI", e);
            unreadable++;
        }
        return IncidentIndexViewModel.create(
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
