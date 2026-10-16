package com.weihe.schedule.rule;

/**
 * The zoom range of the timetable grid, and the sizes it produces.
 *
 * <p>Plain arithmetic on purpose. The grid's column width and row height are
 * derived from the viewport and a zoom factor, and both of those are numbers a
 * JVM test can supply -- so the rules that decide whether a week fits on a
 * screen, and how far a pinch is allowed to go, are checked here rather than
 * discovered by pinching a phone.</p>
 *
 * <h2>Zoom is a multiple, not a size</h2>
 *
 * <p>{@link #FIT} means "the seven day columns fill the width available", which
 * is a different number of pixels on every device and after every rotation. A
 * stored pixel width would be wrong on the next phone; a multiple of a baseline
 * that is recomputed from the viewport is right on all of them. That is also
 * what makes "fit the screen" a real recalculation rather than a reset to a
 * constant.</p>
 *
 * <h2>Why the range is bounded</h2>
 *
 * <p>The floor is the fit-the-week scale itself, so zooming out can only ever
 * take the user back to the whole week. There is deliberately no state in which
 * part of the week is off the screen and has to be dragged into view: the seven
 * columns already fill the width at the floor, so a smaller scale would show no
 * more of the timetable while costing the rows the height they need to be read.
 * Above {@link #MAX} a single lesson fills the screen, which is more
 * magnification than reading a name needs.</p>
 */
public final class GridZoom {

    /** The default: seven days across the available width. */
    public static final float FIT = 1f;

    /**
     * The smallest scale the grid accepts.
     *
     * <p>Equal to {@link #FIT}. This is the whole week, and it is the floor
     * because there is nothing further out to see -- see the note above.</p>
     */
    public static final float MIN = FIT;

    /**
     * The largest scale the grid accepts.
     *
     * <p>2.4 times fit-the-week. At that size one day column takes a bit over a
     * third of the width, which is enough to read a long course name without
     * wrapping, and further magnification would only show fewer days for no more
     * legibility.</p>
     */
    public static final float MAX = 2.4f;

    /**
     * How much one press of the zoom buttons changes the scale.
     *
     * <p>A quarter, so the buttons and a pinch land on the same kind of step and
     * the range takes a handful of presses to cross rather than twenty.</p>
     */
    public static final float STEP = 1.25f;

    private GridZoom() {
    }

    /**
     * Brings any scale into the supported range.
     *
     * <p>A {@code NaN} reads as {@link #FIT} rather than as either end: it can
     * only come from a scale factor the detector could not compute, and treating
     * that as "fully zoomed out" would look like a crash.</p>
     */
    public static float clamp(float zoom) {
        if (Float.isNaN(zoom)) {
            return FIT;
        }
        return Math.max(MIN, Math.min(MAX, zoom));
    }

    /** One step in, bounded. */
    public static float zoomIn(float zoom) {
        return clamp(clamp(zoom) * STEP);
    }

    /** One step out, bounded. */
    public static float zoomOut(float zoom) {
        return clamp(clamp(zoom) / STEP);
    }

    /**
     * The width of one day column that fits {@code dayCount} of them across
     * {@code viewportWidthPx}.
     *
     * <p>Integer division, and the remainder is simply not used: rounding up
     * would push the last column past the edge and bring back the sideways
     * scroll this exists to remove. The few leftover pixels read as the grid's
     * own margin.</p>
     *
     * @param gridPadPx the grid's left inset, which is not part of any column
     */
    public static int fitWidthDayPx(int viewportWidthPx, int gridPadPx, int dayCount) {
        if (dayCount <= 0) {
            return 1;
        }
        int usable = viewportWidthPx - gridPadPx;
        if (usable <= 0) {
            return 1;
        }
        return Math.max(1, usable / dayCount);
    }

    /** The day column width at {@code zoom}; never below one pixel. */
    public static int dayWidthPx(int baseDayWidthPx, float zoom) {
        return Math.max(1, Math.round(baseDayWidthPx * clamp(zoom)));
    }

    /** The period row height at {@code zoom}; never below one pixel. */
    public static int rowHeightPx(int baseRowHeightPx, float zoom) {
        return Math.max(1, Math.round(baseRowHeightPx * clamp(zoom)));
    }

    /**
     * Whether the grid at this scale needs sideways scrolling.
     *
     * <p>Used to say so in the readout, and to check the promise that the default
     * shows the whole week.</p>
     */
    public static boolean needsHorizontalScroll(int dayWidthPx, int dayCount,
            int gridPadPx, int viewportWidthPx) {
        if (dayCount <= 0) {
            return false;
        }
        return gridPadPx + dayWidthPx * dayCount > viewportWidthPx;
    }
}
