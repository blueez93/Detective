package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextFitterTest {
    @Test
    void shortAttributionContextIsPreserved() {
        assertEquals("Primary suspect: Example Mod",
                UiTextFitter.ellipsize("Primary suspect: Example Mod", 80, String::length));
    }

    @Test
    void longAttributionValuesForBothCardsFitWithAClearEllipsis() {
        List<String> attributionValues = List.of(
                "Primary suspect: Example Machines With A Deliberately Long Display Name "
                        + "(example_machines_with_a_deliberately_long_mod_identifier)",
                "Primary suspect: Example Worldgen With A Deliberately Long Display Name "
                        + "(example_worldgen_with_a_deliberately_long_mod_identifier)");

        for (String attribution : attributionValues) {
            String fitted = UiTextFitter.ellipsize(attribution, 54, String::length);

            assertTrue(fitted.endsWith("…"));
            assertTrue(fitted.length() <= 54);
        }
    }

    @Test
    void extremelyNarrowWidthNeverProducesOverflow() {
        assertEquals("", UiTextFitter.ellipsize("Example Mod", 0, String::length));
        assertEquals("", UiTextFitter.ellipsize("Example Mod", 1, ignored -> 2));
    }
}
