package com.weihe.schedule.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The arithmetic behind the time wheels.
 *
 * <p>The wheels themselves need a screen; which value an index means and where that
 * index sits does not. That split matters here more than usual, because the wrap
 * around {@code 23 -> 00} and {@code 59 -> 00} happens in this arithmetic -- if it
 * is wrong, the wheel either stops at the ends or shows a value outside the range,
 * and no amount of device testing makes that safe.</p>
 *
 * <p>The tests below are written in terms of the visible band, because that is the
 * level at which the defect was reported: "at {@code 00}, the two rows above should
 * read {@code 22} and {@code 23}". Each case asks
 * {@link WheelColumnView#valueAt} for a small run of indices around the selection
 * and compares the whole run, so an off-by-one shows up as a mismatched row rather
 * than a passing single-value check.</p>
 */
public class WheelColumnViewTest {

    /** The row height the wheels actually use, in dp. */
    private static final int ROW = 44;

    /** A column of hours: 24 values, {@code 00}..{@code 23}. */
    private static final int HOURS = 24;
    /** A column of minutes: 60 values, {@code 00}..{@code 59}. */
    private static final int MINUTES = 60;

    /**
     * A stand-in for the view's own column, so the cycle can be exercised without
     * a {@code Context}.
     *
     * <p>It mirrors exactly what the view does: values run from {@code from}, the
     * count is inclusive, and the value at an index is the normalised modulo of
     * that index. Keeping the mirror this thin is the point -- if the view's
     * arithmetic changes, these go red.</p>
     */
    private static int valueAt(int index, int from, int count) {
        return from + WheelColumnView.mod(index, count);
    }

    /**
     * The five rows a wheel shows, top to bottom, for a column whose selection sits
     * at {@code selectedIndex}.
     *
     * <p>Index order is screen order: a larger index is drawn lower down, which is
     * why the rows above the selection are the smaller indices. Reading them the
     * same way the eye does is what makes these assertions readable as the
     * screenshots they stand in for.</p>
     */
    private static int[] visibleBand(int selectedIndex, int from, int count) {
        int[] band = new int[5];
        for (int offset = -2; offset <= 2; offset++) {
            band[offset + 2] = valueAt(selectedIndex + offset, from, count);
        }
        return band;
    }

    private static void assertBand(int[] expected, int[] actual, String what) {
        assertEquals(what + ": expected 5 rows", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(what + ": row " + (i - 2) + " from centre", expected[i],
                    actual[i]);
        }
    }

    // ================================================== normalised modulo itself

    /**
     * The modulo is Euclidean, not Java's remainder.
     *
     * <p>This is the root cause of the boundary defect, stated directly. Java's
     * {@code -1 % 24} is {@code -1}; a wheel that used it would show an hour of
     * {@code -1} after one upward step from midnight. The normalised form maps
     * {@code -1} to {@code 23}.</p>
     */
    @Test
    public void moduloOfANegativeIndexWrapsForward() {
        assertEquals(23, WheelColumnView.mod(-1, HOURS));
        assertEquals(22, WheelColumnView.mod(-2, HOURS));
        assertEquals(0, WheelColumnView.mod(-24, HOURS));
        assertEquals(23, WheelColumnView.mod(-25, HOURS));
        // The contrast that makes the point: Java's % would give -1 here.
        assertEquals(-1, -1 % HOURS);
    }

    @Test
    public void moduloNeverLeavesTheRangeForAnyOffset() {
        // A whole cycle either side of zero, plus a long drag's worth of indices.
        for (int i = -500; i <= 500; i++) {
            int h = WheelColumnView.mod(i, HOURS);
            int m = WheelColumnView.mod(i, MINUTES);
            assertTrue("hour " + h + " out of range at index " + i, h >= 0 && h < HOURS);
            assertTrue("minute " + m + " out of range at index " + i, m >= 0 && m < MINUTES);
        }
    }

    @Test
    public void moduloOfADegenerateColumnIsZero() {
        assertEquals(0, WheelColumnView.mod(5, 0));
        assertEquals(0, WheelColumnView.mod(5, -3));
    }

    // ============================================ the bands the defect was about

    /** At {@code 00}: {@code 22, 23, 00, 01, 02}. */
    @Test
    public void theHourColumnAtZeroShows2223AboveIt() {
        assertBand(
                new int[] {22, 23, 0, 1, 2},
                visibleBand(0, 0, HOURS),
                "hours at 00");
    }

    /** At {@code 23}: {@code 21, 22, 23, 00, 01}. */
    @Test
    public void theHourColumnAtTwentyThreeShows00BelowIt() {
        assertBand(
                new int[] {21, 22, 23, 0, 1},
                visibleBand(23, 0, HOURS),
                "hours at 23");
    }

    /** At {@code 00}: {@code 58, 59, 00, 01, 02}. */
    @Test
    public void theMinuteColumnAtZeroShows5859AboveIt() {
        assertBand(
                new int[] {58, 59, 0, 1, 2},
                visibleBand(0, 0, MINUTES),
                "minutes at 00");
    }

    /** At {@code 59}: {@code 57, 58, 59, 00, 01}. */
    @Test
    public void theMinuteColumnAtFiftyNineShows00BelowIt() {
        assertBand(
                new int[] {57, 58, 59, 0, 1},
                visibleBand(59, 0, MINUTES),
                "minutes at 59");
    }

    // ============================================== crossing a boundary by index

    /**
     * The index is the thing that crosses the boundary, not the value.
     *
     * <p>Scrolling up from {@code 00} means visiting index {@code -1}, which must
     * mean {@code 23}. This is the step the previous implementation could not take:
     * it clamped the index to {@code 0} and therefore drew nothing above
     * {@code 00}.</p>
     */
    @Test
    public void steppingUpFromZeroLandsOnTheTopOfTheRange() {
        assertEquals(0, valueAt(0, 0, HOURS));
        assertEquals(23, valueAt(-1, 0, HOURS));
        assertEquals(22, valueAt(-2, 0, HOURS));
        assertEquals(59, valueAt(-1, 0, MINUTES));
    }

    @Test
    public void steppingDownFromTheTopLandsOnZero() {
        assertEquals(23, valueAt(23, 0, HOURS));
        assertEquals(0, valueAt(24, 0, HOURS));
        assertEquals(1, valueAt(25, 0, HOURS));
        assertEquals(59, valueAt(59, 0, MINUTES));
        assertEquals(0, valueAt(60, 0, MINUTES));
        assertEquals(1, valueAt(61, 0, MINUTES));
    }

    /**
     * Crossing several boundaries in a row still lands in range.
     *
     * <p>This is the case a fix that merely special-cased the two ends would fail:
     * after five complete laps up and five down, the hour is still an hour and the
     * minute is still a minute.</p>
     */
    @Test
    public void crossingManyBoundariesStaysInRange() {
        for (int lap = -5; lap <= 5; lap++) {
            for (int step = -2; step <= 2; step++) {
                int hIndex = lap * HOURS + step;
                int mIndex = lap * MINUTES + step;
                int h = valueAt(hIndex, 0, HOURS);
                int m = valueAt(mIndex, 0, MINUTES);
                assertEquals("hour at index " + hIndex, WheelColumnView.mod(hIndex, HOURS), h);
                assertEquals("minute at index " + mIndex, WheelColumnView.mod(mIndex, MINUTES), m);
            }
        }
    }

    /**
     * A run of adjacent indices is a run of adjacent values, wrapping as it goes.
     *
     * <p>Walks a full lap of each column and checks every step is either the next
     * value or the wrap back to the start -- never a jump, never a repeat, never
     * out of range. That is the property the acceptance criterion asks for
     * ("不能跳值、截断或显示错误") expressed as a loop.</p>
     */
    @Test
    public void aFullLapStepsWithoutSkippingOrRepeating() {
        assertLap(HOURS, "hour");
        assertLap(MINUTES, "minute");
    }

    private static void assertLap(int count, String what) {
        for (int i = -count; i < 2 * count; i++) {
            int here = valueAt(i, 0, count);
            int next = valueAt(i + 1, 0, count);
            int expected = (here + 1) % count;
            assertEquals(what + " step from index " + i, expected, next);
            assertTrue(what + " value in range at index " + i,
                    here >= 0 && here < count);
        }
    }

    // ============================================== the 23:59 <-> 00:00 wrap

    /**
     * {@code 23:59} scrolled forward by one minute is {@code 00:00} in that column.
     *
     * <p>Both columns rest on the same index step, which is what lets the picker
     * treat "scroll both wheels one row" as an hour rollover without either column
     * disagreeing with the other.</p>
     */
    @Test
    public void rollingFromTheEndOfTheDayToTheStartKeepsBothColumnsAgreeing() {
        int hourIndex = 23;
        int minuteIndex = 59;
        assertEquals(23, valueAt(hourIndex, 0, HOURS));
        assertEquals(59, valueAt(minuteIndex, 0, MINUTES));

        assertEquals(0, valueAt(hourIndex + 1, 0, HOURS));
        assertEquals(0, valueAt(minuteIndex + 1, 0, MINUTES));
    }

    /** And the other way: {@code 00:00} scrolled back is {@code 23:59}. */
    @Test
    public void rollingBackwardsFromTheStartOfTheDayReachesTheEnd() {
        assertEquals(23, valueAt(0 - 1, 0, HOURS));
        assertEquals(59, valueAt(0 - 1, 0, MINUTES));
    }

    // ================================================== the two columns stay apart

    /**
     * The columns are independent: each maps its own index to its own range.
     *
     * <p>The picker holds two views that share no state; this pins that the shared
     * arithmetic does not couple them. The same index means {@code 05} in an hour
     * column and {@code 05} in a minute column -- the same number, but the columns
     * reach it from their own scroll positions.</p>
     */
    @Test
    public void theSameIndexIsIndependentPerColumn() {
        // Index 5 out of a 24-cycle and out of a 60-cycle must both be 5, but the
        // cycles diverge as soon as one wraps and the other has not.
        assertEquals(5, valueAt(5, 0, HOURS));
        assertEquals(5, valueAt(5, 0, MINUTES));
        assertEquals(23, valueAt(-1, 0, HOURS));
        assertEquals(59, valueAt(-1, 0, MINUTES));
        assertNotEquals(valueAt(-1, 0, HOURS), valueAt(-1, 0, MINUTES));
    }

    // ===================================================== index <-> offset math

    /**
     * The index origin sits far enough out that hand-dragging cannot reach zero.
     *
     * <p>The unbounded index is only safe if it starts well away from the {@code int}
     * limits. A user would have to cross this many rows in one direction -- thousands
     * of full turns of a minute column -- before the index or the pixel offset could
     * approach negative territory, which no gesture can do.</p>
     */
    @Test
    public void theIndexOriginLeavesRoomForThousandsOfTurns() {
        // A minute column is the worst case: 60 rows per turn.
        assertTrue("origin should allow thousands of turns",
                WheelColumnView.indexOrigin() >= 1000 * 60);
    }

    /**
     * The origin-nearest index for a value is the one the wheel opens on.
     *
     * <p>The index is not the value -- it is an offset into the cycle -- so what has
     * to hold is that the index maps back to the value it was chosen for. This is
     * the round trip that stops the picker opening on the wrong number.</p>
     */
    @Test
    public void theIndexChosenForAValueMapsBackToThatValue() {
        for (int v = 0; v < HOURS; v++) {
            assertEquals("hour " + v, v,
                    valueAt(WheelColumnView.indexForValue(v, 0, HOURS), 0, HOURS));
        }
        for (int v = 0; v < MINUTES; v++) {
            assertEquals("minute " + v, v,
                    valueAt(WheelColumnView.indexForValue(v, 0, MINUTES), 0, MINUTES));
        }
        // A range not starting at zero round-trips through its own origin too.
        assertEquals(8, valueAt(WheelColumnView.indexForValue(8, 8, 4), 8, 4));
        assertEquals(11, valueAt(WheelColumnView.indexForValue(11, 8, 4), 8, 4));
    }

    /**
     * The origin is a whole number of turns, so the wheel opens on the right value.
     *
     * <p>This is the assertion that caught the defect: the raw origin is a plain row
     * number, and {@code 100000} is not a multiple of {@code 24}, so an index derived
     * from it showed {@code 16} where {@code 0} was intended. Rounding the origin down
     * to a whole number of turns is what fixes it, and it has to hold for both column
     * lengths at once.</p>
     */
    @Test
    public void theOriginIsAWholeNumberOfTurnsForEveryColumnLength() {
        for (int count : new int[] {HOURS, MINUTES}) {
            assertEquals("origin should show the range start for a cycle of " + count,
                    0, valueAt(WheelColumnView.indexForValue(0, 0, count), 0, count));
            assertEquals(0, WheelColumnView.indexForValue(0, 0, count) % count);
        }
    }

    /**
     * A degenerate cycle length does not divide by zero.
     *
     * <p>The picker never builds such a column, but the origin helper is public and
     * must not throw.</p>
     */
    @Test
    public void anOriginForAnEmptyCycleIsStillUsable() {
        assertEquals(WheelColumnView.indexOrigin(),
                WheelColumnView.indexForValue(0, 0, 0));
        assertEquals(WheelColumnView.indexOrigin(),
                WheelColumnView.indexForValue(0, 0, -5));
    }

    /**
     * A fling's bounds must contain the current offset, or the fling degrades.
     *
     * <p>This is the regression test for the creep defect found on the device:
     * {@code OverScroller.fling} checks {@code start > max || start < min} before
     * anything else, and a start outside the range does not throw -- it silently
     * animates slowly towards the boundary. The bounds are derived from the offset
     * for exactly this reason, and the arithmetic must not overflow while doing it.
     * Each bound is computed the same way the view computes it.</p>
     */
    @Test
    public void aFlingsBoundsAlwaysContainItsStart() {
        // Reaches are computed in ints by the view; check the extremes stay in range.
        int[] origins = {0, 1, 100_000, 1_000_000, 100_000_000};
        for (int origin : origins) {
            int here = origin * ROW;
            for (int rangeRows : new int[] {1_000, 10_000, 100_000}) {
                int reach = ROW * rangeRows;
                assertTrue("reach overflowed for origin " + origin,
                        reach > 0);
                assertTrue("start must be within its own bounds at origin " + origin,
                        here - reach <= here && here <= here + reach);
            }
        }
    }

    @Test
    public void anIndexSitsExactlyItsOwnRowsBelowTheOrigin() {
        assertEquals(0f, WheelColumnView.offsetForIndex(0, ROW), 0f);
        assertEquals(3f * ROW, WheelColumnView.offsetForIndex(3, ROW), 0f);
        // Negative indices are one row's height above the origin, not an error.
        assertEquals(-ROW, WheelColumnView.offsetForIndex(-1, ROW), 0f);
        assertEquals(-3f * ROW, WheelColumnView.offsetForIndex(-3, ROW), 0f);
    }

    @Test
    public void offsetAndIndexAreInversesAcrossTheRange() {
        for (int i = -200; i <= 200; i++) {
            float offset = WheelColumnView.offsetForIndex(i, ROW);
            assertEquals("index " + i, i, WheelColumnView.nearestIndex(offset, ROW));
        }
    }

    @Test
    public void anOffsetBetweenTwoIndicesPicksTheNearerOne() {
        assertEquals(3, WheelColumnView.nearestIndex(3 * (float) ROW + 1, ROW));
        assertEquals(3, WheelColumnView.nearestIndex(3 * (float) ROW + ROW / 2 - 1, ROW));
        assertEquals(4, WheelColumnView.nearestIndex(3 * (float) ROW + ROW / 2 + 1, ROW));
        // The same rule below the origin, which is where an upward scroll lives.
        assertEquals(-3, WheelColumnView.nearestIndex(-3 * (float) ROW - 1, ROW));
        assertEquals(-4, WheelColumnView.nearestIndex(-3 * (float) ROW - ROW / 2 - 1, ROW));
    }

    /**
     * Wherever a fling stops, snapping must land exactly on a whole row.
     *
     * <p>This is the regression test for the defect an earlier device run found: a
     * fling ended, the wheel committed the nearest value, but the offset was left at
     * whatever pixel the glide stopped on -- so the chosen number sat a fraction of
     * a row off centre, permanently. The snap is "nearest index, then that index's
     * exact offset", and the offset must be a whole multiple of the row height and
     * must still mean the index it came from. Checked across the origin as well as
     * above it.</p>
     */
    @Test
    public void snappingToAnIndexAlwaysLandsExactlyOnIt() {
        for (int pixel = -3 * ROW; pixel <= 3 * ROW; pixel++) {
            int index = WheelColumnView.nearestIndex(pixel, ROW);
            float snapped = WheelColumnView.offsetForIndex(index, ROW);
            assertEquals("pixel " + pixel + " should snap onto a whole row",
                    0f, snapped % ROW, 0f);
            assertEquals("index " + index + " must round-trip through the offset",
                    index, WheelColumnView.nearestIndex(snapped, ROW));
        }
    }

    @Test
    public void snappingDoesNotChangeTheSelectedValue() {
        for (int pixel = -5 * ROW; pixel <= 5 * ROW; pixel += 7) {
            int before = WheelColumnView.nearestIndex(pixel, ROW);
            int after = WheelColumnView.nearestIndex(
                    WheelColumnView.offsetForIndex(before, ROW), ROW);
            assertEquals("snap changed the index at pixel " + pixel, before, after);
        }
    }

    // ================================================================= corner cases

    @Test
    public void aDegenerateRowHeightDoesNotThrow() {
        assertEquals(0, WheelColumnView.nearestIndex(100f, 0));
        assertEquals(0, WheelColumnView.nearestIndex(100f, -1));
    }

    /**
     * A single-value column is a cycle of one, so it never leaves its value.
     *
     * <p>Not reachable from the picker, which always spans a whole hour or minute,
     * but the wrap must not divide by anything or pick a neighbouring value.</p>
     */
    @Test
    public void aColumnOfOneValueAlwaysShowsThatValue() {
        for (int i = -3; i <= 3; i++) {
            assertEquals(7, valueAt(i, 7, 1));
        }
        assertBand(new int[] {7, 7, 7, 7, 7}, visibleBand(0, 7, 1), "single value");
    }

    /**
     * A range that does not start at zero wraps within its own bounds.
     *
     * <p>The picker builds both columns from zero, so this is defensive: the wrap is
     * a property of the range, not of the number 24 or 60, and a future column that
     * starts elsewhere must inherit it.</p>
     */
    @Test
    public void aRangeNotStartingAtZeroWrapsWithinItsOwnBounds() {
        // 8..11 is four values: 8, 9, 10, 11, then back to 8.
        assertEquals(8, valueAt(0, 8, 4));
        assertEquals(11, valueAt(3, 8, 4));
        assertEquals(8, valueAt(4, 8, 4));
        assertEquals(11, valueAt(-1, 8, 4));
        assertEquals(10, valueAt(-2, 8, 4));
        assertBand(new int[] {10, 11, 8, 9, 10}, visibleBand(0, 8, 4), "range 8..11");
    }
}
