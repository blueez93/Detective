package fr.apocalypsebleu.moddetective.client.ui.model;

public record DetectiveSummaryViewModel(
        int totalIncidents,
        int sessionIncidents,
        int recentIncidents,
        int highEvidenceIncidents,
        int moderateEvidenceIncidents,
        IncidentSummaryViewModel lastIncident
) {}
