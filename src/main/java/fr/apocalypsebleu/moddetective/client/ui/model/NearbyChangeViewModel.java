package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable, non-causal presentation of one nearby recorded modpack change. */
public record NearbyChangeViewModel(
        Type type,
        String modId,
        String displayLabel,
        Optional<String> previousVersion,
        Optional<String> newVersion,
        long recordedAtEpochMs,
        long offsetFromFirstRecordedMs,
        Direction direction,
        String offsetMagnitude,
        boolean sameRecordedLaunch,
        BeforeAfterViewModel beforeAfter
) {
    public NearbyChangeViewModel {
        type = Objects.requireNonNull(type, "type");
        modId = requireText(modId, "modId");
        displayLabel = requireText(displayLabel, "displayLabel");
        previousVersion = normalized(previousVersion);
        newVersion = normalized(newVersion);
        direction = Objects.requireNonNull(direction, "direction");
        offsetMagnitude = requireText(offsetMagnitude, "offsetMagnitude");
        beforeAfter = Objects.requireNonNull(beforeAfter, "beforeAfter");
    }

    public enum Type {
        ADDED,
        UPDATED,
        REMOVED
    }

    public enum Direction {
        BEFORE,
        AT,
        AFTER
    }

    public record BeforeAfterViewModel(OptionalInt before, OptionalInt after) {
        public BeforeAfterViewModel {
            before = Objects.requireNonNull(before, "before");
            after = Objects.requireNonNull(after, "after");
            if (before.isPresent() != after.isPresent()
                    || before.orElse(0) < 0 || after.orElse(0) < 0) {
                throw new IllegalArgumentException("Before/after counts must be jointly available and non-negative");
            }
        }

        public static BeforeAfterViewModel available(int before, int after) {
            return new BeforeAfterViewModel(OptionalInt.of(before), OptionalInt.of(after));
        }

        public static BeforeAfterViewModel unavailable() {
            return new BeforeAfterViewModel(OptionalInt.empty(), OptionalInt.empty());
        }

        public boolean available() {
            return before.isPresent();
        }
    }

    private static Optional<String> normalized(Optional<String> value) {
        return Objects.requireNonNull(value, "version")
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
