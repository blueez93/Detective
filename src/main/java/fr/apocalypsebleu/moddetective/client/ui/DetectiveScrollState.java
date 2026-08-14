package fr.apocalypsebleu.moddetective.client.ui;

/**
 * Small, deterministic scroll model shared by Detective's custom long-form screens.
 * Screen content and fixed headers/footers remain outside this class; callers provide
 * the exact scrollable viewport on every render.
 */
final class DetectiveScrollState {
    static final double WHEEL_STEP = 12.0;
    static final int MINIMUM_THUMB_HEIGHT = 18;

    private static final int HIT_PADDING = 2;

    private double offset;
    private int contentHeight;
    private int viewportTop;
    private int viewportHeight = 1;
    private int trackX;
    private int trackWidth = 4;
    private boolean dragging;
    private double dragGrabOffset;

    void updateLayout(
            int contentHeight,
            int viewportTop,
            int viewportHeight,
            int trackX,
            int trackWidth
    ) {
        this.contentHeight = Math.max(0, contentHeight);
        this.viewportTop = Math.max(0, viewportTop);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.trackX = Math.max(0, trackX);
        this.trackWidth = Math.max(1, trackWidth);
        offset = clamp(offset, 0.0, maximumOffset());
        if (!isScrollbarVisible()) {
            dragging = false;
        }
    }

    double offset() {
        return offset;
    }

    int roundedOffset() {
        return (int) Math.round(offset);
    }

    double maximumOffset() {
        return Math.max(0.0, contentHeight - viewportHeight);
    }

    boolean isScrollbarVisible() {
        return contentHeight > viewportHeight;
    }

    boolean isWithinViewport(double mouseY) {
        return mouseY >= viewportTop && mouseY < viewportTop + viewportHeight;
    }

    void scrollTo(double requestedOffset) {
        offset = clamp(requestedOffset, 0.0, maximumOffset());
    }

    void scrollToBottom() {
        offset = maximumOffset();
    }

    boolean scrollWheel(double wheelDelta) {
        if (!isScrollbarVisible() || !Double.isFinite(wheelDelta) || wheelDelta == 0.0) {
            return false;
        }
        scrollTo(offset - wheelDelta * WHEEL_STEP);
        return true;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isScrollbarVisible() || !isOverTrack(mouseX, mouseY)) {
            return false;
        }
        ScrollbarGeometry geometry = geometry();
        if (mouseY >= geometry.thumbTop()
                && mouseY < geometry.thumbTop() + geometry.thumbHeight()) {
            dragging = true;
            dragGrabOffset = mouseY - geometry.thumbTop();
        } else {
            double page = Math.max(WHEEL_STEP, viewportHeight * 0.85);
            scrollTo(offset + (mouseY < geometry.thumbTop() ? -page : page));
        }
        return true;
    }

    boolean mouseDragged(double mouseY, int button) {
        if (!dragging || button != 0 || !isScrollbarVisible()) {
            return false;
        }
        ScrollbarGeometry geometry = geometry();
        int travel = geometry.trackHeight() - geometry.thumbHeight();
        if (travel <= 0) {
            scrollTo(0.0);
            return true;
        }
        double requestedThumbTop = clamp(
                mouseY - dragGrabOffset,
                geometry.trackTop(),
                geometry.trackTop() + travel);
        scrollTo((requestedThumbTop - geometry.trackTop()) / travel * maximumOffset());
        return true;
    }

    boolean mouseReleased(int button) {
        if (button != 0 || !dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    void cancelDrag() {
        dragging = false;
    }

    boolean isDragging() {
        return dragging;
    }

    ScrollbarGeometry geometry() {
        int trackHeight = viewportHeight;
        if (!isScrollbarVisible()) {
            return new ScrollbarGeometry(
                    trackX, viewportTop, trackWidth, trackHeight, viewportTop, trackHeight, false);
        }
        int proportionalHeight = (int) Math.round(
                (double) viewportHeight * viewportHeight / contentHeight);
        int thumbHeight = Math.min(trackHeight,
                Math.max(MINIMUM_THUMB_HEIGHT, proportionalHeight));
        int travel = trackHeight - thumbHeight;
        int thumbTop = viewportTop;
        if (travel > 0 && maximumOffset() > 0.0) {
            thumbTop += (int) Math.round(offset / maximumOffset() * travel);
        }
        return new ScrollbarGeometry(
                trackX, viewportTop, trackWidth, trackHeight, thumbTop, thumbHeight, true);
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return mouseX >= trackX - HIT_PADDING
                && mouseX < trackX + trackWidth + HIT_PADDING
                && isWithinViewport(mouseY);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    record ScrollbarGeometry(
            int trackX,
            int trackTop,
            int trackWidth,
            int trackHeight,
            int thumbTop,
            int thumbHeight,
            boolean visible
    ) {}
}
