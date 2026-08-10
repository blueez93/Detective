package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreezeThresholdTest {
    @Test
    void usesAbsoluteMinimumForNormalFrameTimes() {
        assertEquals(120.0, FreezeThreshold.calculate(List.of(15.0, 16.0, 17.0)));
    }

    @Test
    void usesSixTimesTheMedianForAHighBaseline() {
        assertEquals(150.0, FreezeThreshold.calculate(List.of(20.0, 30.0)));
    }

    @Test
    void ignoresInvalidBaselineValues() {
        assertEquals(120.0, FreezeThreshold.calculate(List.of(Double.NaN, -1.0, 20.0)));
    }
}
