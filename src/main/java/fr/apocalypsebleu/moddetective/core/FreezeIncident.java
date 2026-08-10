package fr.apocalypsebleu.moddetective.core;

import java.util.List;
import java.util.Objects;

public record FreezeIncident(
        long detectedAtEpochMs,
        double durationMs,
        double thresholdMs,
        FrameSample frame,
        int watchdogSamples,
        List<SuspectAnalyzer.Suspect> suspects,
        List<SuspectAnalyzer.HotClass> hotClasses,
        List<FrameSample> blackBox
) {
    public FreezeIncident {
        frame = Objects.requireNonNull(frame, "frame");
        suspects = List.copyOf(Objects.requireNonNull(suspects, "suspects"));
        hotClasses = List.copyOf(Objects.requireNonNull(hotClasses, "hotClasses"));
        blackBox = List.copyOf(Objects.requireNonNull(blackBox, "blackBox"));
    }
}
