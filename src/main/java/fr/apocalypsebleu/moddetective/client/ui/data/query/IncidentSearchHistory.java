package fr.apocalypsebleu.moddetective.client.ui.data.query;

import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, bounded set of local incident summaries and their searchable metadata. */
public record IncidentSearchHistory(
        IncidentIndexViewModel incidentIndex,
        List<IncidentSearchRecord> records
) {
    public IncidentSearchHistory {
        incidentIndex = Objects.requireNonNull(incidentIndex, "incidentIndex");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        if (records.size() > DetectiveSettings.MAXIMUM_HISTORY_LIMIT
                || incidentIndex.incidents().size() != records.size()) {
            throw new IllegalArgumentException("Search history must match the bounded incident index");
        }
    }

    public static IncidentSearchHistory create(
            List<IncidentSearchRecord> source,
            long nowEpochMs,
            long sessionStartedEpochMs,
            int unreadableFiles
    ) {
        List<IncidentSearchRecord> bounded = Objects.requireNonNull(source, "source").stream()
                .sorted(Comparator
                        .comparingLong((IncidentSearchRecord value) ->
                                value.summary().detectedAtEpochMs()).reversed()
                        .thenComparing(value -> value.summary().id())
                        .thenComparing(IncidentSearchRecord::incidentId))
                .limit(DetectiveSettings.MAXIMUM_HISTORY_LIMIT)
                .toList();
        IncidentIndexViewModel index = IncidentIndexViewModel.create(
                bounded.stream().map(IncidentSearchRecord::summary).toList(),
                nowEpochMs, sessionStartedEpochMs, unreadableFiles);
        return new IncidentSearchHistory(index, bounded);
    }
}
