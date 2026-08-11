package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiFormattersTest {
    @Test
    void formatsDurationsAndPercentagesWithoutSuggestingCulpability() {
        assertEquals("150.0 ms", UiFormatters.duration(150.0));
        assertEquals("1.20 s", UiFormatters.duration(1_200.0));
        assertEquals("62.5%", UiFormatters.percent(62.5));
        assertEquals("512.0 MiB", UiFormatters.memory(512L * 1024L * 1024L));
        assertEquals("1.50 GiB", UiFormatters.memory(1536L * 1024L * 1024L));
    }

    @Test
    void shortensDimensionAndHandlesMissingCoordinates() {
        assertEquals("The Nether", UiFormatters.dimension("minecraft:the_nether"));
        assertEquals("—", UiFormatters.dimension("menu"));
        assertEquals("1, 64, -2", UiFormatters.coordinates(1, 64, -2));
        assertEquals("—", UiFormatters.coordinates(null, 64, -2));
    }
}
