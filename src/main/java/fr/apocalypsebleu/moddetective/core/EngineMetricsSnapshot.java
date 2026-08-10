package fr.apocalypsebleu.moddetective.core;

public record EngineMetricsSnapshot(
        long watchdogSamples,
        double watchdogSamplesPerSecond,
        double averageWatchdogCaptureMicros,
        double maximumWatchdogCaptureMicros,
        int retainedWatchdogSamples,
        int blackBoxSamples,
        int incidentWorkerQueueSize,
        int incidentWorkerQueueCapacity,
        long droppedIncidents,
        long processedIncidents,
        double averageIncidentProcessingMs,
        double maximumIncidentProcessingMs
) {}
