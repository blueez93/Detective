package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentDebounceTest {
    @Test
    void rejectsIncidentsInsideTheWindowAndAcceptsBoundary() {
        IncidentDebounce debounce = new IncidentDebounce(2_000L);

        assertTrue(debounce.tryAcquire(10_000L));
        assertFalse(debounce.tryAcquire(11_999L));
        assertTrue(debounce.tryAcquire(12_000L));
    }

    @Test
    void resetAllowsTheNextIncidentImmediately() {
        IncidentDebounce debounce = new IncidentDebounce(2_000L);
        assertTrue(debounce.tryAcquire(10_000L));

        debounce.reset();

        assertTrue(debounce.tryAcquire(10_001L));
    }
}
