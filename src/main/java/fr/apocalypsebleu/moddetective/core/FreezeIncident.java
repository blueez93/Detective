package fr.apocalypsebleu.moddetective.core;

import java.util.List;
import java.util.Objects;

public record FreezeIncident(
        int schemaVersion,
        long detectedAtEpochMs,
        double durationMs,
        double thresholdMs,
        FrameSample frame,
        int watchdogSamples,
        AttributionEvidence attributionEvidence,
        List<SuspectAnalyzer.Suspect> suspects,
        List<SuspectAnalyzer.HotClass> hotClasses,
        List<FrameSample> blackBox
) {
    public static final int SCHEMA_VERSION = 1;

    public FreezeIncident {
        schemaVersion = SCHEMA_VERSION;
        frame = Objects.requireNonNull(frame, "frame");
        attributionEvidence = Objects.requireNonNull(attributionEvidence, "attributionEvidence");
        suspects = List.copyOf(Objects.requireNonNull(suspects, "suspects"));
        hotClasses = List.copyOf(Objects.requireNonNull(hotClasses, "hotClasses"));
        blackBox = List.copyOf(Objects.requireNonNull(blackBox, "blackBox"));
    }

    public FreezeIncident(
            long detectedAtEpochMs,
            double durationMs,
            double thresholdMs,
            FrameSample frame,
            int watchdogSamples,
            AttributionEvidence attributionEvidence,
            List<SuspectAnalyzer.Suspect> suspects,
            List<SuspectAnalyzer.HotClass> hotClasses,
            List<FrameSample> blackBox
    ) {
        this(SCHEMA_VERSION, detectedAtEpochMs, durationMs, thresholdMs, frame, watchdogSamples,
                attributionEvidence, suspects, hotClasses, blackBox);
    }
}
