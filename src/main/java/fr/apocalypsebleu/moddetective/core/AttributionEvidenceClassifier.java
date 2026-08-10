package fr.apocalypsebleu.moddetective.core;

import java.util.List;
import java.util.Objects;

public final class AttributionEvidenceClassifier {
    private static final int MINIMUM_STACK_SAMPLES = 3;
    private static final int MINIMUM_STRONGEST_SUSPECT_SAMPLES = 3;
    private static final double MINIMUM_STRONGEST_SUSPECT_SHARE_PERCENT = 40.0;

    private AttributionEvidenceClassifier() {}

    public static AttributionEvidence classify(
            List<StackSnapshot> snapshots,
            SuspectAnalyzer.Analysis analysis
    ) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(analysis, "analysis");

        int gcMarkers = 0;
        int nativeMarkers = 0;
        for (StackSnapshot snapshot : snapshots) {
            for (StackTraceElement frame : snapshot.stack()) {
                String className = frame.getClassName();
                String methodName = frame.getMethodName();
                if (("java.lang.System".equals(className) || "java.lang.Runtime".equals(className))
                        && "gc".equals(methodName)) {
                    gcMarkers++;
                }
                if (frame.isNativeMethod()
                        || className.startsWith("org.lwjgl.")
                        || className.startsWith("com.mojang.blaze3d.platform.")) {
                    nativeMarkers++;
                }
            }
        }

        int strongest = analysis.suspects().stream()
                .mapToInt(SuspectAnalyzer.Suspect::samplesObserved)
                .max()
                .orElse(0);
        AttributionEvidence.State state;
        double strongestShare = analysis.suspects().stream()
                .mapToDouble(SuspectAnalyzer.Suspect::sampleSharePercent)
                .max()
                .orElse(0.0);
        if (!analysis.suspects().isEmpty()
                && analysis.stackSamples() >= MINIMUM_STACK_SAMPLES
                && strongest >= MINIMUM_STRONGEST_SUSPECT_SAMPLES
                && strongestShare >= MINIMUM_STRONGEST_SUSPECT_SHARE_PERCENT) {
            state = AttributionEvidence.State.ATTRIBUTED;
        } else if (!analysis.suspects().isEmpty() || snapshots.isEmpty()) {
            state = AttributionEvidence.State.INSUFFICIENT_EVIDENCE;
        } else if (gcMarkers > 0) {
            state = AttributionEvidence.State.JVM_GC_SUSPECTED;
        } else if (nativeMarkers > 0) {
            state = AttributionEvidence.State.NATIVE_OR_DRIVER_STALL_POSSIBLE;
        } else {
            state = AttributionEvidence.State.UNKNOWN;
        }
        return new AttributionEvidence(state, analysis.stackSamples(), strongest, gcMarkers, nativeMarkers);
    }
}
