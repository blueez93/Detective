package fr.apocalypsebleu.moddetective.core;

public record EngineMetricsSnapshot(
        long watchdogSamples,
        double watchdogSamplesPerSecond,
        double averageWatchdogCaptureMicros,
        double p50WatchdogCaptureMicros,
        double p95WatchdogCaptureMicros,
        double p99WatchdogCaptureMicros,
        double maximumWatchdogCaptureMicros,
        int watchdogLatencyWindowSamples,
        int retainedWatchdogSamples,
        int retainedWatchdogFrames,
        int blackBoxSamples,
        int incidentWorkerQueueSize,
        int maximumIncidentWorkerQueueSize,
        int incidentWorkerQueueCapacity,
        long droppedIncidents,
        long processedIncidents,
        double averageIncidentProcessingMs,
        double p95IncidentProcessingMs,
        double maximumIncidentProcessingMs
) {}
