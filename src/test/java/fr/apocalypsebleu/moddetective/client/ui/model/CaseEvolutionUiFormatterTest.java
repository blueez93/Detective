package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaseEvolutionUiFormatterTest {
    @Test
    void formatsExactBeforeAndAfterMagnitudesWithoutDirectionSemantics() {
        long offset = Duration.ofHours(6).plusMinutes(17).toMillis();

        assertEquals("6h 17m", CaseEvolutionUiFormatter.offsetMagnitude(-offset));
        assertEquals("6h 17m", CaseEvolutionUiFormatter.offsetMagnitude(offset));
    }

    @Test
    void formatsSameTimeAsZeroMinutes() {
        assertEquals("0m", CaseEvolutionUiFormatter.offsetMagnitude(0L));
    }

    @Test
    void preservesSubMinuteAndSubSecondPrecision() {
        assertEquals("45s 250ms", CaseEvolutionUiFormatter.offsetMagnitude(45_250L));
    }

    @Test
    void formatsLongMinimumValueSafely() {
        assertEquals(CaseEvolutionUiFormatter.offsetMagnitude(Long.MAX_VALUE),
                CaseEvolutionUiFormatter.offsetMagnitude(Long.MIN_VALUE));
    }
}
