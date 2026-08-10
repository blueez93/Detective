package fr.apocalypsebleu.moddetective.core;

/**
 * Prevents a paused or unfocused interval from being charged to the first active frame.
 */
public final class SamplingContinuityGate {
    private boolean suspended;

    public boolean shouldRecord(boolean samplingAllowed) {
        if (!samplingAllowed) {
            suspended = true;
            return false;
        }
        if (suspended) {
            suspended = false;
            return false;
        }
        return true;
    }
}
