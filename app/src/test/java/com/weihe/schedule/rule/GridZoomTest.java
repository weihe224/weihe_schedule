package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The zoom range and the geometry it produces.
 *
 * <p>What is pinned here is the promise the feature makes: that the seven day
 * columns fit the width by default, that the scale cannot be driven past either
 * end, and that "show the whole day" never shrinks the grid past the point where
 * it can be read. All of it is arithmetic, so it is checked on the JVM rather
 * than by pinching a phone.</p>
 */
public class GridZoomTest {

    /** A phone the size of the one this was developed against: 411dp at 2.625. */
    private static final int PHONE_VIEWPORT_PX = 1080 - 126;

    // ------------------------------------------------------------------ range

    @Test
    public void theScaleIsHeldInsideItsRange() {
        assertEquals(GridZoom.MIN, GridZoom.clamp(0.1f), 0.0001f);
        assertEquals(GridZoom.MIN, GridZoom.clamp(-4f), 0.0001f);
        assertEquals(GridZoom.MAX, GridZoom.clamp(9f), 0.0001f);
        assertEquals(GridZoom.FIT, GridZoom.clamp(GridZoom.FIT), 0.0001f);
        assertEquals(1.5f, GridZoom.clamp(1.5f), 0.0001f);
    }

    @Test
    public void anUndecidableScaleReadsAsTheDefault() {
        // A detector that could not compute a factor must not look like a jump to
        // either end of the range.
        assertEquals(GridZoom.FIT, GridZoom.clamp(Float.NaN), 0.0001f);
    }

    @Test
    public void zoomingInIsPossibleAndZoomingOutStopsAtTheWholeWeek() {
        assertTrue("zooming in must be possible", GridZoom.MAX > GridZoom.FIT);
        // The floor is the fit-the-week scale. There is nothing further out to
        // see -- the seven columns already fill the width there -- so the range
        // does not go below it, and the week is never partly off the screen.
        assertEquals(GridZoom.FIT, GridZoom.MIN, 0.0001f);
        assertFalse(GridZoom.MIN < GridZoom.FIT);
        // And this is already more than reading a name needs.
        assertTrue(GridZoom.MAX <= 3f);
    }

    // ------------------------------------------------------------------ steps

    @Test
    public void aStepMovesByTheStepFactor() {
        assertEquals(1.25f, GridZoom.zoomIn(1f), 0.0001f);
        assertEquals(1.5625f, GridZoom.zoomIn(1.25f), 0.0001f);
        assertEquals(1f, GridZoom.zoomOut(1.25f), 0.0001f);
    }

    @Test
    public void steppingOutAtTheFloorStaysAtTheWholeWeek() {
        // Already showing the whole week: there is no smaller scale to reach.
        assertEquals(GridZoom.FIT, GridZoom.zoomOut(GridZoom.FIT), 0.0001f);
        assertEquals(GridZoom.FIT, GridZoom.zoomOut(0.5f), 0.0001f);
    }

    @Test
    public void steppingStopsAtTheEndsOfTheRange() {
        float top = GridZoom.FIT;
        for (int i = 0; i < 40; i++) {
            top = GridZoom.zoomIn(top);
        }
        assertEquals("stepping in must stop at the ceiling", GridZoom.MAX, top, 0.0001f);

        float bottom = GridZoom.FIT;
        for (int i = 0; i < 40; i++) {
            bottom = GridZoom.zoomOut(bottom);
        }
        assertEquals("stepping out must stop at the floor", GridZoom.MIN, bottom, 0.0001f);
    }

    @Test
    public void steppingBackAndForthReturnsToWhereItStarted() {
        // The buttons must not accumulate error: in then out is the same scale.
        float start = GridZoom.FIT;
        float there = GridZoom.zoomIn(GridZoom.zoomIn(start));
        float back = GridZoom.zoomOut(GridZoom.zoomOut(there));
        assertEquals(start, back, 0.0001f);
    }

    // -------------------------------------------------------------- fit width

    @Test
    public void sevenColumnsFitTheWidthTheyAreGiven() {
        int day = GridZoom.fitWidthDayPx(PHONE_VIEWPORT_PX, 16, 7);

        assertFalse("the whole week must fit without scrolling",
                GridZoom.needsHorizontalScroll(day, 7, 16, PHONE_VIEWPORT_PX));
        // And it must not leave a whole column's worth unused either.
        assertTrue("the week must actually use the width",
                day * 7 > PHONE_VIEWPORT_PX - 16 - 7);
    }

    @Test
    public void theLeftoverPixelsAreDroppedRatherThanPushedPastTheEdge() {
        // Integer division: rounding up would put the last column over the edge
        // and bring back the sideways scroll this exists to remove. The seven
        // columns are narrower than the space, never wider.
        int day = GridZoom.fitWidthDayPx(1000, 0, 7);
        assertEquals(142, day);
        assertTrue(day * 7 <= 1000);
        // And with an inset, the inset counts too.
        int padded = GridZoom.fitWidthDayPx(1000, 16, 7);
        assertTrue(16 + padded * 7 <= 1000);
    }

    @Test
    public void aViewportTooNarrowForAnythingStillYieldsAUsableWidth() {
        assertEquals(1, GridZoom.fitWidthDayPx(0, 16, 7));
        assertEquals(1, GridZoom.fitWidthDayPx(10, 16, 7));
        assertEquals(1, GridZoom.fitWidthDayPx(-100, 16, 7));
        assertEquals(1, GridZoom.fitWidthDayPx(800, 16, 0));
    }

    @Test
    public void everyScreenWidthFitsSevenColumns() {
        // The promise has to hold on a small phone and a tablet alike.
        int[] viewports = {720, 828, 900, 1080, 1440, 1600, 2560};
        for (int viewport : viewports) {
            int day = GridZoom.fitWidthDayPx(viewport, 16, 7);
            assertFalse("a " + viewport + "px viewport must still show the week",
                    GridZoom.needsHorizontalScroll(day, 7, 16, viewport));
        }
    }

    // ------------------------------------------------------------- scaling up

    @Test
    public void zoomingInWidensTheColumnsAndTheRows() {
        int base = 130;
        assertEquals(base, GridZoom.dayWidthPx(base, GridZoom.FIT));
        assertTrue(GridZoom.dayWidthPx(base, 2f) > base);
        assertEquals(260, GridZoom.dayWidthPx(base, 2f));
        assertEquals(260, GridZoom.rowHeightPx(base, 2f));
    }

    @Test
    public void zoomingInEnoughBringsBackTheSidewaysScrollOnPurpose() {
        int base = GridZoom.fitWidthDayPx(PHONE_VIEWPORT_PX, 16, 7);
        int zoomed = GridZoom.dayWidthPx(base, 1.5f);
        assertTrue("magnifying has to give up showing the whole week",
                GridZoom.needsHorizontalScroll(zoomed, 7, 16, PHONE_VIEWPORT_PX));
    }

    @Test
    public void aSizeNeverCollapsesToNothing() {
        assertEquals(1, GridZoom.dayWidthPx(1, GridZoom.MIN));
        assertEquals(1, GridZoom.rowHeightPx(1, GridZoom.MIN));
        assertEquals(1, GridZoom.dayWidthPx(0, GridZoom.MAX));
        assertEquals(1, GridZoom.rowHeightPx(0, GridZoom.MAX));
    }

    // ----------------------------------------------------------- whole period

    @Test
    public void theFloorStillShowsTheWholeWeekOnEveryScreen() {
        // The point of making the fit-the-week scale the floor: whatever the
        // device, zooming all the way out lands on the whole week rather than on
        // a shrunken grid that still needs dragging sideways.
        int[] viewports = {720, 828, 900, 1080, 1440, 1600, 2560};
        for (int viewport : viewports) {
            int base = GridZoom.fitWidthDayPx(viewport, 16, 7);
            int atFloor = GridZoom.dayWidthPx(base, GridZoom.MIN);
            assertFalse("a " + viewport + "px screen must show the week at the floor",
                    GridZoom.needsHorizontalScroll(atFloor, 7, 16, viewport));
            assertEquals("the floor is the fit scale, so the width is unchanged",
                    base, atFloor);
        }
    }

    @Test
    public void degenerateInputsFallBackToTheDefault() {
        assertEquals(GridZoom.FIT, GridZoom.clamp(Float.NaN), 0.0001f);
        assertEquals(GridZoom.FIT, GridZoom.clamp(Float.NEGATIVE_INFINITY), 0.0001f);
    }

    // ---------------------------------------------------------------- scroll

    @Test
    public void theDefaultNeedsNoSidewaysScrollingButASmallScreenStillFits() {
        // The headline promise, at both ends of the phone range.
        int small = GridZoom.fitWidthDayPx(720, 16, 7);
        assertFalse(GridZoom.needsHorizontalScroll(small, 7, 16, 720));
        int large = GridZoom.fitWidthDayPx(1440, 16, 7);
        assertFalse(GridZoom.needsHorizontalScroll(large, 7, 16, 1440));
    }

    @Test
    public void aGridWithNoDaysNeverNeedsScrolling() {
        assertFalse(GridZoom.needsHorizontalScroll(200, 0, 16, 100));
    }
}
