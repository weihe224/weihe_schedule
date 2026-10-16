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

    // ------------------------------------------------- the course block's scale

    /**
     * What a course block is made of at {@link #FIT}.
     *
     * <p>These are the sizes the block had before zooming existed. Everything a
     * zoom multiplies is expressed as one of these times the scale, so the block
     * grows as one piece instead of the background growing while the words stay
     * the same size -- which is what made a magnified timetable look like a
     * small label floating in a large box.</p>
     */
    public static final float BASE_BLOCK_NAME_SP = 11f;
    public static final float BASE_BLOCK_META_SP = 9f;
    public static final float BASE_BLOCK_BADGE_SP = 10f;
    public static final int BASE_BLOCK_PADDING_DP = 5;
    public static final float BASE_BLOCK_RADIUS_DP = 4f;

    /**
     * The smallest sizes the block accepts.
     *
     * <p>Below these a name is no longer readable, so the text stops shrinking
     * even if the grid does. They do not bind at today's range -- the zoom floor
     * is {@link #FIT}, where the sizes are exactly the base ones -- and they are
     * here so that lowering the floor later cannot quietly turn the timetable
     * into an unreadable smear.</p>
     */
    public static final float MIN_BLOCK_NAME_SP = 8f;
    public static final float MIN_BLOCK_META_SP = 7f;
    public static final float MIN_BLOCK_BADGE_SP = 7f;
    /** The block's inner padding never collapses entirely, at any scale. */
    public static final int MIN_BLOCK_PADDING_DP = 2;

    /**
     * The course name's size in sp at {@code zoom}.
     *
     * <p>The largest of the block's three sizes, because the name is the block:
     * the week rule and the count are read after it, and never at the same
     * weight.</p>
     */
    public static float blockNameSp(float zoom) {
        return Math.max(MIN_BLOCK_NAME_SP, BASE_BLOCK_NAME_SP * clamp(zoom));
    }

    /** The week rule's size in sp at {@code zoom}: one step under the name. */
    public static float blockMetaSp(float zoom) {
        return Math.max(MIN_BLOCK_META_SP, BASE_BLOCK_META_SP * clamp(zoom));
    }

    /** The "+N" count's size in sp at {@code zoom}. */
    public static float blockBadgeSp(float zoom) {
        return Math.max(MIN_BLOCK_BADGE_SP, BASE_BLOCK_BADGE_SP * clamp(zoom));
    }

    /**
     * The block's inner padding in dp at {@code zoom}.
     *
     * <p>One value drives the card's whole internal rhythm -- its padding and the
     * gap between its lines -- so the text never crowds the edge at one scale and
     * floats in the middle of it at another.</p>
     */
    public static int blockPaddingDp(float zoom) {
        return Math.max(MIN_BLOCK_PADDING_DP,
                Math.round(BASE_BLOCK_PADDING_DP * clamp(zoom)));
    }

    /** The block's corner radius in dp at {@code zoom}. */
    public static float blockRadiusDp(float zoom) {
        return Math.max(2f, BASE_BLOCK_RADIUS_DP * clamp(zoom));
    }
}
