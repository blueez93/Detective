package fr.apocalypsebleu.moddetective.core;

public record AttributionEvidence(
        State state,
        int stackSamples,
        int strongestSuspectSamples,
        int gcMarkerFrames,
        int nativeOrDriverMarkerFrames
) {
    public enum State {
        ATTRIBUTED,
        INSUFFICIENT_EVIDENCE,
        JVM_GC_SUSPECTED,
        NATIVE_OR_DRIVER_STALL_POSSIBLE,
        UNKNOWN
    }
}
