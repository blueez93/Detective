package fr.apocalypsebleu.detectivevalidation.culprit;

import java.util.List;
import java.util.Objects;

public final class ValidationResultEvaluator {
    private ValidationResultEvaluator() {}

    public static Result evaluate(Expected expected, Detected detected) {
        Objects.requireNonNull(expected, "expected");
        if (!expected.incidentExpected()) {
            return detected == null
                    ? new Result(true, 0, "<none>", 0.0, false, false, "No incident created, as expected")
                    : new Result(false, rankOf(expected.modId(), detected.suspects()), detected.topSuspect(),
                    shareOf(expected.modId(), detected.suspects()), false, false, "Unexpected incident created");
        }
        if (detected == null) {
            return new Result(false, 0, "<none>", 0.0, false, false, "No incident report was created");
        }

        int rank = rankOf(expected.modId(), detected.suspects());
        double share = shareOf(expected.modId(), detected.suspects());
        boolean durationCoherent = detected.durationMs() >= expected.requestedDurationMs() * 0.85
                && detected.durationMs() <= expected.requestedDurationMs() + 500.0;
        boolean complete = detected.watchdogSamples() > 0
                && detected.blackBoxSamples() > 0
                && detected.hasWorldLocation();
        boolean top1 = rank == 1;
        boolean top3 = rank > 0 && rank <= 3;
        boolean passed = rank > 0 && rank <= expected.maximumAcceptedRank() && durationCoherent && complete;

        String reason;
        if (rank == 0) {
            reason = "Expected culprit is absent from suspects";
        } else if (rank > expected.maximumAcceptedRank()) {
            reason = "Expected culprit is ranked #" + rank + " (required top " + expected.maximumAcceptedRank() + ")";
        } else if (!durationCoherent) {
            reason = "Incident duration is inconsistent with the requested stall";
        } else if (!complete) {
            reason = "Incident is missing watchdog, Black Box, dimension, or position data";
        } else {
            reason = "Expected culprit is within required rank with complete incident evidence";
        }
        return new Result(passed, rank, detected.topSuspect(), share, top1, top3, reason);
    }

    private static int rankOf(String expectedModId, List<Suspect> suspects) {
        for (int index = 0; index < suspects.size(); index++) {
            if (expectedModId.equals(suspects.get(index).modId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static double shareOf(String expectedModId, List<Suspect> suspects) {
        return suspects.stream()
                .filter(suspect -> expectedModId.equals(suspect.modId()))
                .mapToDouble(Suspect::sampleSharePercent)
                .findFirst()
                .orElse(0.0);
    }

    public record Expected(String modId, long requestedDurationMs, boolean incidentExpected, int maximumAcceptedRank) {
        public Expected {
            Objects.requireNonNull(modId, "modId");
            if (maximumAcceptedRank < 1) {
                throw new IllegalArgumentException("maximumAcceptedRank must be positive");
            }
        }

        public Expected(String modId, long requestedDurationMs, boolean incidentExpected) {
            this(modId, requestedDurationMs, incidentExpected, 1);
        }
    }

    public record Detected(
            double durationMs,
            int watchdogSamples,
            int blackBoxSamples,
            boolean hasWorldLocation,
            List<Suspect> suspects
    ) {
        public Detected {
            suspects = List.copyOf(Objects.requireNonNull(suspects, "suspects"));
        }

        public String topSuspect() {
            return suspects.isEmpty() ? "<none>" : suspects.getFirst().modId();
        }
    }

    public record Suspect(String modId, double sampleSharePercent) {}

    public record Result(
            boolean passed,
            int expectedCulpritRank,
            String detectedTopSuspect,
            double expectedCulpritSharePercent,
            boolean top1Match,
            boolean top3Match,
            String reason
    ) {}
}
