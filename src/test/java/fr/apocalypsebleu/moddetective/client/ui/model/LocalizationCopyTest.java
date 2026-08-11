package fr.apocalypsebleu.moddetective.client.ui.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationCopyTest {
    @Test
    void englishContainsCanonicalProductCopy() throws IOException {
        Map<String, String> english = language("en_us");

        assertEquals("Modpack diagnostics, without the guesswork.",
                english.get("detective.ui.home.subtitle"));
        assertEquals("Detective finds the evidence. You make the call.",
                english.get("detective.ui.tagline"));
        assertEquals("Why this suspect?", english.get("detective.ui.incident.why.title"));
        assertEquals("AMBIGUOUS ATTRIBUTION", english.get("detective.ui.evidence.ambiguous"));
        assertEquals("POSSIBLE SYSTEM STALL", english.get("detective.ui.evidence.system"));
        assertEquals("Partial Black Box data",
                english.get("detective.ui.incident.black_box.partial.title"));
    }

    @Test
    void englishAndFrenchExposeTheSameKeys() throws IOException {
        assertEquals(language("en_us").keySet(), language("fr_fr").keySet());
    }

    @Test
    void localizedUiCopyAvoidsAccusatoryTerms() throws IOException {
        String english = String.join("\n", language("en_us").values()).toLowerCase();
        for (String forbidden : List.of("culprit", "guilty", "caused by", "definitely caused", "broken mod")) {
            assertFalse(english.contains(forbidden), () -> "Forbidden English UI copy: " + forbidden);
        }

        String french = String.join("\n", language("fr_fr").values()).toLowerCase();
        for (String forbidden : List.of("coupable", "mod responsable", "cause certaine")) {
            assertFalse(french.contains(forbidden), () -> "Forbidden French UI copy: " + forbidden);
        }
        assertTrue(french.contains("suspect principal"));
    }

    private static Map<String, String> language(String code) throws IOException {
        String resource = "/assets/detective/lang/" + code + ".json";
        try (var stream = LocalizationCopyTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing language resource: " + resource);
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            }
        }
    }
}
