package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplingContinuityGateTest {
    @Test
    void recordsContinuousActiveFrames() {
        SamplingContinuityGate gate = new SamplingContinuityGate();

        assertTrue(gate.shouldRecord(true));
        assertTrue(gate.shouldRecord(true));
    }

    @Test
    void skipsSuspendedFramesAndThreeRestorationFrames() {
        SamplingContinuityGate gate = new SamplingContinuityGate();

        assertFalse(gate.shouldRecord(false));
        assertFalse(gate.shouldRecord(false));
        assertFalse(gate.shouldRecord(true));
        assertFalse(gate.shouldRecord(true));
        assertFalse(gate.shouldRecord(true));
        assertTrue(gate.shouldRecord(true));
    }

    @Test
    void losingFocusAgainRestartsTheFullStabilizationWindow() {
        SamplingContinuityGate gate = new SamplingContinuityGate();

        assertFalse(gate.shouldRecord(false));
        assertFalse(gate.shouldRecord(true));
        assertFalse(gate.shouldRecord(false));
        assertFalse(gate.shouldRecord(true));
        assertFalse(gate.shouldRecord(true));
        assertFalse(gate.shouldRecord(true));
        assertTrue(gate.shouldRecord(true));
    }
}
