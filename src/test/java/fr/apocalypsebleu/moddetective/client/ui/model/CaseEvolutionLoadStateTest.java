package fr.apocalypsebleu.moddetective.client.ui.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseEvolutionLoadStateTest {
    @Test
    void staleAsyncResultIsIgnoredAfterAReplacementRequest() {
        CaseEvolutionLoadState state = new CaseEvolutionLoadState();
        long stale = state.begin("case-a");
        long current = state.begin("case-a");
        CaseEvolutionViewModel value = value("case-a");

        assertFalse(state.complete(stale, "case-a", value));
        assertNull(state.value());
        assertTrue(state.complete(current, "case-a", value));
        assertSame(value, state.value());
    }

    @Test
    void wrongCaseAndCancelledResponsesCannotReplaceContext() {
        CaseEvolutionLoadState state = new CaseEvolutionLoadState();
        long request = state.begin("case-a");

        assertFalse(state.complete(request, "case-a", value("case-b")));
        state.cancelPending();
        assertFalse(state.complete(request, "case-a", value("case-a")));
        assertNull(state.value());
    }

    private static CaseEvolutionViewModel value(String caseId) {
        return new CaseEvolutionViewModel(
                caseId,
                OptionalLong.empty(),
                new HistoryCoverageViewModel(HistoryCoverageViewModel.Status.UNKNOWN),
                CaseEvolutionViewModel.HistoryAvailability.UNAVAILABLE,
                List.of(),
                0);
    }
}
