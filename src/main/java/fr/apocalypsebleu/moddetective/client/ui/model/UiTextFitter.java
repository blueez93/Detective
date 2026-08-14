package fr.apocalypsebleu.moddetective.client.ui.model;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Width-safe single-line text fitting for compact Detective panels. */
public final class UiTextFitter {
    private static final String ELLIPSIS = "…";

    private UiTextFitter() {}

    public static String ellipsize(
            String value,
            int maximumWidth,
            ToIntFunction<String> width
    ) {
        String text = Objects.requireNonNullElse(value, "");
        ToIntFunction<String> widthFunction = Objects.requireNonNull(width, "width");
        if (maximumWidth <= 0 || text.isEmpty()) {
            return "";
        }
        if (widthFunction.applyAsInt(text) <= maximumWidth) {
            return text;
        }
        if (widthFunction.applyAsInt(ELLIPSIS) > maximumWidth) {
            return "";
        }

        int[] codePoints = text.codePoints().toArray();
        int low = 0;
        int high = codePoints.length;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = prefix(codePoints, middle).stripTrailing() + ELLIPSIS;
            if (widthFunction.applyAsInt(candidate) <= maximumWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return prefix(codePoints, low).stripTrailing() + ELLIPSIS;
    }

    private static String prefix(int[] codePoints, int length) {
        return new String(codePoints, 0, length);
    }
}
