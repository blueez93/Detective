package fr.apocalypsebleu.moddetective.core;

/**
 * Prevents a paused or unfocused interval from being charged to the first active frame.
 */
public final class SamplingContinuityGate {
    /**
     * GLFW restoration can occupy more than the first active render frame. The v0.3.1 soak
     * observed the native restore stall on the second active frame, so require three complete
     * active frames before resuming measurements.
     */
    private static final int RESUME_STABILIZATION_FRAMES = 3;

    private int stabilizationFramesRemaining;

    public boolean shouldRecord(boolean samplingAllowed) {
        if (!samplingAllowed) {
            stabilizationFramesRemaining = RESUME_STABILIZATION_FRAMES;
            return false;
        }
        if (stabilizationFramesRemaining > 0) {
            stabilizationFramesRemaining--;
            return false;
        }
        return true;
    }
}
