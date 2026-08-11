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
            markDiscontinuity();
            return false;
        }
        if (stabilizationFramesRemaining > 0) {
            stabilizationFramesRemaining--;
            return false;
        }
        return true;
    }

    /**
     * Marks a render discontinuity even when Minecraft produced no inactive frame for the gate
     * to observe. This happens, for example, when an unfocused or minimized window renders no
     * frames at all and the first observable interval is therefore unusably long.
     */
    public void markDiscontinuity() {
        stabilizationFramesRemaining = RESUME_STABILIZATION_FRAMES;
    }
}
