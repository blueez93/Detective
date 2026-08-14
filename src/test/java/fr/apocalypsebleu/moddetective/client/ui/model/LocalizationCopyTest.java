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
        assertEquals("EXPORT SUPPORT REPORT", english.get("detective.ui.export.title"));
        assertEquals("Nothing will be uploaded automatically.",
                english.get("detective.ui.export.local_only"));
        assertEquals("DETECTIVE SETTINGS", english.get("detective.ui.settings.title"));
        assertEquals("No reliable mod attribution",
                english.get("detective.notification.unattributed"));
        assertEquals("CASE FILES", english.get("detective.ui.cases.title"));
        assertEquals("Recurring patterns detected across your incident history.",
                english.get("detective.ui.cases.subtitle"));
        assertEquals("No recurring pattern established.",
                english.get("detective.ui.cases.empty"));
        assertEquals("Technical similarity: %s   Evidence strength: %s",
                english.get("detective.ui.cases.strength_summary"));
        assertEquals("Technical signature #%s",
                english.get("detective.ui.case.evidence.item"));
        assertEquals("A recurring pattern indicates repeated technical similarity between incidents. "
                        + "It does not prove that a mod is defective or solely responsible.",
                english.get("detective.ui.case.safety"));
        assertEquals("Search incidents…",
                english.get("detective.ui.incidents.search.hint"));
        assertEquals("Technical similarity compares captured evidence. It does not establish "
                        + "a shared cause or prove that a mod is defective or solely responsible.",
                english.get("detective.ui.comparison.caution"));
        assertEquals("Technical signature #%s",
                english.get("detective.ui.comparison.evidence.signature"));
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
        assertTrue(language("fr_fr").get("detective.ui.comparison.caution")
                .contains("ne prouve pas"));
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
