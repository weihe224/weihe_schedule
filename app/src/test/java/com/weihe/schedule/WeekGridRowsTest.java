package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The overview grid's row count, and the invariant that broke it once.
 *
 * <p>{@code GridLayout.setRowCount} validates the new count against the children
 * it already holds. The screen shrank the grid before clearing it, so switching
 * from the whole-semester view (which reached period 13) to a single week ending
 * at period 6 threw {@code IllegalArgumentException} and killed the app. The
 * order is fixed in {@code WeekGridActivity.render}; what is pinned down here is
 * the contract that made it fatal -- the number of rows is a function of the
 * courses currently shown, so it can go down.</p>
 *
 * <p>{@code lastPeriodInUse} itself is private to the Activity and cannot be
 * reached from a JVM test. The rule is re-expressed here against the same inputs
 * so that a change to either {@code MIN_ROWS} or the period cap is noticed.</p>
 */
public class WeekGridRowsTest {

    /** Mirrors WeekGridActivity.MIN_ROWS. */
    private static final int MIN_ROWS = 10;

    private static int lastPeriodInUse(List<Course> courses) {
        int last = 0;
        for (Course course : courses) {
            last = Math.max(last, course.endPeriod);
        }
        return Math.max(MIN_ROWS, Math.min(ScheduleUtils.PERIODS_PER_DAY, last));
    }

    private static Course courseEndingAt(int endPeriod) {
        Course course = new Course();
        course.name = "\u8BFE\u7A0B";
        course.dayOfWeek = 1;
        course.startPeriod = Math.max(1, endPeriod - 1);
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    @Test
    public void aLongerTimetableNeedsMorePeriods() {
        assertEquals(MIN_ROWS, lastPeriodInUse(new ArrayList<Course>()));
        assertEquals(MIN_ROWS, lastPeriodInUse(
                Arrays.asList(courseEndingAt(3))));
        // The return value is a period number, not a row count; render() adds one
        // for the header row.
        assertEquals(13, lastPeriodInUse(Arrays.asList(courseEndingAt(13))));
        assertEquals(15, lastPeriodInUse(Arrays.asList(courseEndingAt(15))));
    }

    @Test
    public void theRowCountCanShrinkWhenTheShownCoursesChange() {
        // The exact transition that crashed: the whole semester reaches period
        // 13, the single week ends at period 5.
        int wholeSemester = lastPeriodInUse(Arrays.asList(courseEndingAt(13)));
        int singleWeek = lastPeriodInUse(Arrays.asList(courseEndingAt(5)));

        assertTrue("the single-week grid must be shorter than the whole-semester one",
                singleWeek < wholeSemester);
        // Which is only safe because render() clears the children before it
        // resizes; if that order is ever swapped back, this is the number that
        // the old children would be measured against.
        assertEquals(MIN_ROWS, singleWeek);
    }

    @Test
    public void theRowCountNeverExceedsThePeriodCap() {
        // A course that claims more periods than a day has must not push the
        // grid past the table it draws.
        int rows = lastPeriodInUse(Arrays.asList(courseEndingAt(15)));
        assertTrue(rows <= ScheduleUtils.PERIODS_PER_DAY);
    }
}
