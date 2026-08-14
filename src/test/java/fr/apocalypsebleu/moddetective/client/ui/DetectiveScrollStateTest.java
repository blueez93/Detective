package fr.apocalypsebleu.moddetective.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectiveScrollStateTest {
    @Test
    void contentSmallerThanViewportHasNoScrollbar() {
        DetectiveScrollState state = configured(80, 100);

        assertFalse(state.isScrollbarVisible());
        assertEquals(0.0, state.maximumOffset());
        assertFalse(state.geometry().visible());
    }

    @Test
    void contentLargerThanViewportHasScrollbar() {
        DetectiveScrollState state = configured(101, 100);

        assertTrue(state.isScrollbarVisible());
        assertEquals(1.0, state.maximumOffset());
        assertTrue(state.geometry().visible());
    }

    @Test
    void contentExactlyFittingViewportHasNoScrollbar() {
        DetectiveScrollState state = configured(100, 100);

        assertFalse(state.isScrollbarVisible());
        assertEquals(0.0, state.maximumOffset());
        assertFalse(state.scrollWheel(-1.0));
    }

    @Test
    void offsetClampsAtZero() {
        DetectiveScrollState state = configured(500, 100);

        state.scrollTo(-200.0);

        assertEquals(0.0, state.offset());
    }

    @Test
    void offsetClampsAtMaximum() {
        DetectiveScrollState state = configured(500, 100);

        state.scrollTo(900.0);

        assertEquals(400.0, state.offset());
    }

    @Test
    void wheelUsesFineGrainedPredictablePixels() {
        DetectiveScrollState state = configured(500, 100);

        assertTrue(state.scrollWheel(-1.0));
        assertEquals(12.0, state.offset());
        state.scrollWheel(-1.0);
        assertEquals(24.0, state.offset());
        state.scrollWheel(0.5);
        assertEquals(18.0, state.offset());
    }

    @Test
    void fastWheelInputRemainsClampedAtBothEnds() {
        DetectiveScrollState state = configured(500, 100);

        assertTrue(state.scrollWheel(-1_000.0));
        assertEquals(state.maximumOffset(), state.offset());
        assertTrue(state.scrollWheel(1_000.0));
        assertEquals(0.0, state.offset());
    }

    @Test
    void thumbPositionTracksOffset() {
        DetectiveScrollState state = configured(500, 100);
        state.scrollTo(200.0);

        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertEquals(40, geometry.thumbTop() - geometry.trackTop());
    }

    @Test
    void thumbAtTopCorrespondsToZeroOffset() {
        DetectiveScrollState state = configured(500, 100);

        assertEquals(0.0, state.offset());
        assertEquals(state.geometry().trackTop(), state.geometry().thumbTop());
    }

    @Test
    void thumbAtBottomCorrespondsToMaximumOffset() {
        DetectiveScrollState state = configured(500, 100);
        state.scrollToBottom();
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertEquals(state.maximumOffset(), state.offset());
        assertEquals(geometry.trackTop() + geometry.trackHeight(),
                geometry.thumbTop() + geometry.thumbHeight());
    }

    @Test
    void draggingThumbToMiddleMapsNearMiddleContentPosition() {
        DetectiveScrollState state = configured(500, 100);
        DetectiveScrollState.ScrollbarGeometry initial = state.geometry();
        double grabY = initial.thumbTop() + initial.thumbHeight() / 2.0;

        assertTrue(state.mouseClicked(initial.trackX(), grabY, 0));
        assertTrue(state.mouseDragged(initial.trackTop() + initial.trackHeight() / 2.0, 0));
        assertTrue(state.mouseReleased(0));

        assertEquals(state.maximumOffset() / 2.0, state.offset(), 3.0);
        assertFalse(state.isDragging());
    }

    @Test
    void draggingThumbBeyondTrackClampsToTopAndBottom() {
        DetectiveScrollState state = configured(500, 100);
        DetectiveScrollState.ScrollbarGeometry initial = state.geometry();
        double grabY = initial.thumbTop() + initial.thumbHeight() / 2.0;

        assertTrue(state.mouseClicked(initial.trackX(), grabY, 0));
        assertTrue(state.mouseDragged(initial.trackTop() + initial.trackHeight() + 1_000.0, 0));
        assertEquals(state.maximumOffset(), state.offset());
        assertTrue(state.mouseDragged(initial.trackTop() - 1_000.0, 0));
        assertEquals(0.0, state.offset());
        assertTrue(state.mouseReleased(0));
    }

    @Test
    void trackClickMovesOneBoundedPageTowardPointer() {
        DetectiveScrollState state = configured(500, 100);
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertTrue(state.mouseClicked(geometry.trackX(),
                geometry.trackTop() + geometry.trackHeight() - 1, 0));

        assertEquals(85.0, state.offset());
        assertFalse(state.isDragging());
    }

    @Test
    void trackClicksAboveAndBelowMoveOnlyTowardThePointer() {
        DetectiveScrollState state = configured(500, 100);
        state.scrollTo(200.0);
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertTrue(state.mouseClicked(geometry.trackX(), geometry.trackTop() + 1, 0));
        assertEquals(115.0, state.offset());
        geometry = state.geometry();
        assertTrue(state.mouseClicked(
                geometry.trackX(), geometry.trackTop() + geometry.trackHeight() - 1, 0));
        assertEquals(200.0, state.offset());
    }

    @Test
    void clicksOutsideTrackOrWithAnotherButtonAreNotConsumed() {
        DetectiveScrollState state = configured(500, 100);
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertFalse(state.mouseClicked(geometry.trackX() - 3, geometry.trackTop() + 50, 0));
        assertFalse(state.mouseClicked(geometry.trackX(), geometry.trackTop() - 1, 0));
        assertFalse(state.mouseClicked(geometry.trackX(), geometry.trackTop() + 50, 1));
        assertEquals(0.0, state.offset());
        assertFalse(state.isDragging());
    }

    @Test
    void resizeRecomputePreservesALegalOffset() {
        DetectiveScrollState state = configured(1_000, 200);
        state.scrollTo(700.0);

        state.updateLayout(1_000, 30, 500, 620, 4);

        assertEquals(500.0, state.offset());
        assertEquals(500.0, state.maximumOffset());
    }

    @Test
    void repeatedResizeNeverLeavesStaleDragOrIllegalOffset() {
        DetectiveScrollState state = configured(1_000, 100);
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();
        assertTrue(state.mouseClicked(
                geometry.trackX(), geometry.thumbTop() + geometry.thumbHeight() / 2.0, 0));
        state.scrollToBottom();

        state.updateLayout(1_000, 30, 400, 620, 4);
        assertTrue(state.offset() <= state.maximumOffset());
        state.updateLayout(80, 30, 100, 620, 4);
        assertEquals(0.0, state.offset());
        assertFalse(state.isScrollbarVisible());
        assertFalse(state.isDragging());
        state.updateLayout(900, 30, 120, 620, 4);
        assertEquals(0.0, state.offset());
        assertTrue(state.isScrollbarVisible());
    }

    @Test
    void veryTallContentKeepsFiniteGeometry() {
        DetectiveScrollState state = configured(Integer.MAX_VALUE, 80);
        state.scrollToBottom();
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertEquals(DetectiveScrollState.MINIMUM_THUMB_HEIGHT, geometry.thumbHeight());
        assertEquals(geometry.trackTop() + geometry.trackHeight(),
                geometry.thumbTop() + geometry.thumbHeight());
    }

    @Test
    void tinyOverflowStillHasUsableMappedThumb() {
        DetectiveScrollState state = configured(101, 100);
        state.scrollToBottom();
        DetectiveScrollState.ScrollbarGeometry geometry = state.geometry();

        assertTrue(geometry.visible());
        assertEquals(99, geometry.thumbHeight());
        assertEquals(1.0, state.offset());
        assertEquals(geometry.trackTop() + geometry.trackHeight(),
                geometry.thumbTop() + geometry.thumbHeight());
    }

    @Test
    void fixedFooterIsExcludedByCallerProvidedViewport() {
        DetectiveScrollState state = new DetectiveScrollState();

        state.updateLayout(300, 42, 220, 620, 4);

        assertEquals(80.0, state.maximumOffset());
        assertTrue(state.isWithinViewport(261.9));
        assertFalse(state.isWithinViewport(262.0));
    }

    @Test
    void repeatedCalculationsAreDeterministic() {
        DetectiveScrollState first = configured(777, 213);
        DetectiveScrollState second = configured(777, 213);
        first.scrollTo(318.25);
        second.scrollTo(318.25);

        assertEquals(first.offset(), second.offset());
        assertEquals(first.maximumOffset(), second.maximumOffset());
        assertEquals(first.geometry(), second.geometry());
    }

    private static DetectiveScrollState configured(int contentHeight, int viewportHeight) {
        DetectiveScrollState state = new DetectiveScrollState();
        state.updateLayout(contentHeight, 30, viewportHeight, 620, 4);
        return state;
    }
}
