package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplaySamplingGateTest {
    @Test
    void suppressesInitialWorldLoadingForFiveSeconds() {
        GameplaySamplingGate gate = new GameplaySamplingGate();
        Object world = new Object();

        assertFalse(gate.shouldDetect(world, 1_000L));
        assertFalse(gate.shouldDetect(world, 1_000L + GameplaySamplingGate.STABILIZATION_NANOS - 1L));
        assertTrue(gate.shouldDetect(world, 1_000L + GameplaySamplingGate.STABILIZATION_NANOS));
    }

    @Test
    void worldOrDimensionReplacementRestartsStabilization() {
        GameplaySamplingGate gate = new GameplaySamplingGate();
        Object overworld = new Object();
        Object nether = new Object();

        gate.shouldDetect(overworld, 0L);
        assertTrue(gate.shouldDetect(overworld, GameplaySamplingGate.STABILIZATION_NANOS));
        assertFalse(gate.shouldDetect(nether, GameplaySamplingGate.STABILIZATION_NANOS + 1L));
        assertTrue(gate.shouldDetect(nether, 2L * GameplaySamplingGate.STABILIZATION_NANOS + 1L));
    }

    @Test
    void leavingAndRejoiningSameObjectRestartsStabilization() {
        GameplaySamplingGate gate = new GameplaySamplingGate();
        Object world = new Object();

        gate.shouldDetect(world, 0L);
        assertTrue(gate.shouldDetect(world, GameplaySamplingGate.STABILIZATION_NANOS));
        assertFalse(gate.shouldDetect(null, GameplaySamplingGate.STABILIZATION_NANOS + 1L));
        assertFalse(gate.shouldDetect(world, GameplaySamplingGate.STABILIZATION_NANOS + 2L));
    }
}
