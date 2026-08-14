package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;

/** Client-thread request token state that rejects stale Case Evolution responses. */
public final class CaseEvolutionLoadState {
    private long generation;
    private String requestedCaseId = "";
    private Status status = Status.IDLE;
    private CaseEvolutionViewModel value;

    public static CaseEvolutionLoadState preloaded(CaseEvolutionViewModel value) {
        CaseEvolutionLoadState state = new CaseEvolutionLoadState();
        state.requestedCaseId = Objects.requireNonNull(value, "value").caseId();
        state.value = value;
        state.status = Status.LOADED;
        return state;
    }

    public long begin(String caseId) {
        requestedCaseId = requireCaseId(caseId);
        value = null;
        status = Status.LOADING;
        return ++generation;
    }

    public boolean complete(long requestGeneration, String caseId, CaseEvolutionViewModel result) {
        Objects.requireNonNull(result, "result");
        if (!isCurrent(requestGeneration, caseId)
                || !requestedCaseId.equals(result.caseId())) {
            return false;
        }
        value = result;
        status = Status.LOADED;
        return true;
    }

    public boolean fail(long requestGeneration, String caseId) {
        if (!isCurrent(requestGeneration, caseId)) {
            return false;
        }
        value = null;
        status = Status.FAILED;
        return true;
    }

    public void cancelPending() {
        if (status == Status.LOADING) {
            generation++;
            requestedCaseId = "";
            status = Status.IDLE;
        }
    }

    public Status status() {
        return status;
    }

    public CaseEvolutionViewModel value() {
        return value;
    }

    private boolean isCurrent(long requestGeneration, String caseId) {
        return status == Status.LOADING
                && generation == requestGeneration
                && requestedCaseId.equals(requireCaseId(caseId));
    }

    private static String requireCaseId(String caseId) {
        String normalized = Objects.requireNonNull(caseId, "caseId").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        return normalized;
    }

    public enum Status {
        IDLE,
        LOADING,
        LOADED,
        FAILED
    }
}
