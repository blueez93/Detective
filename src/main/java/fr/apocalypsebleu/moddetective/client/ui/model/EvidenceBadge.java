package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Locale;

/** User-facing evidence state. It never represents certainty of causality. */
public enum EvidenceBadge {
    HIGH_EVIDENCE("detective.ui.evidence.high", "detective.ui.evidence.high.description",
            "detective.ui.evidence.strength.high", 0xFF55AA55),
    MODERATE_EVIDENCE("detective.ui.evidence.moderate", "detective.ui.evidence.moderate.description",
            "detective.ui.evidence.strength.moderate", 0xFFE0A83E),
    LOW_EVIDENCE("detective.ui.evidence.low", "detective.ui.evidence.low.description",
            "detective.ui.evidence.strength.low", 0xFFB77A3D),
    AMBIGUOUS_ATTRIBUTION("detective.ui.evidence.ambiguous", "detective.ui.evidence.ambiguous.description",
            "detective.ui.evidence.ambiguous", 0xFFD38BDD),
    INSUFFICIENT_EVIDENCE("detective.ui.evidence.insufficient", "detective.ui.evidence.insufficient.description",
            "detective.ui.evidence.insufficient", 0xFFAAAAAA),
    JVM_GC_SUSPECTED("detective.ui.evidence.system", "detective.ui.evidence.system.description",
            "detective.ui.evidence.system", 0xFF6FA8DC),
    NATIVE_OR_DRIVER_STALL_POSSIBLE("detective.ui.evidence.system", "detective.ui.evidence.system.description",
            "detective.ui.evidence.system", 0xFF6FA8DC),
    UNKNOWN("detective.ui.evidence.unknown", "detective.ui.evidence.unknown.description",
            "detective.ui.evidence.unknown", 0xFF888888);

    private final String translationKey;
    private final String descriptionKey;
    private final String strengthKey;
    private final int color;

    EvidenceBadge(String translationKey, String descriptionKey, String strengthKey, int color) {
        this.translationKey = translationKey;
        this.descriptionKey = descriptionKey;
        this.strengthKey = strengthKey;
        this.color = color;
    }

    public String translationKey() {
        return translationKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public String strengthKey() {
        return strengthKey;
    }

    public int color() {
        return color;
    }

    public boolean isAttributedTier() {
        return this == HIGH_EVIDENCE || this == MODERATE_EVIDENCE || this == LOW_EVIDENCE;
    }

    public boolean isSystemStall() {
        return this == JVM_GC_SUSPECTED || this == NATIVE_OR_DRIVER_STALL_POSSIBLE;
    }

    public String listSummaryKey() {
        return switch (this) {
            case AMBIGUOUS_ATTRIBUTION -> "detective.ui.incidents.card.ambiguous";
            case INSUFFICIENT_EVIDENCE -> "detective.ui.incidents.card.insufficient";
            case JVM_GC_SUSPECTED, NATIVE_OR_DRIVER_STALL_POSSIBLE -> "detective.ui.incidents.card.system";
            case UNKNOWN -> "detective.ui.incidents.card.unknown";
            default -> descriptionKey;
        };
    }

    public static EvidenceBadge from(String state, SuspectViewModel topSuspect) {
        String normalized = state == null ? "UNKNOWN" : state.trim().toUpperCase(Locale.ROOT);
        if (!"ATTRIBUTED".equals(normalized)) {
            try {
                return EvidenceBadge.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return UNKNOWN;
            }
        }
        if (topSuspect == null) {
            return UNKNOWN;
        }

        int samples = topSuspect.leafOwnershipCount() > 0
                ? topSuspect.leafOwnershipCount()
                : topSuspect.presenceSamples();
        double share = topSuspect.leafOwnershipCount() > 0
                ? topSuspect.leafOwnershipSharePercent()
                : topSuspect.presenceSharePercent();
        if (samples >= 10 && share >= 75.0) {
            return HIGH_EVIDENCE;
        }
        if (samples >= 5 && share >= 50.0) {
            return MODERATE_EVIDENCE;
        }
        return LOW_EVIDENCE;
    }
}
