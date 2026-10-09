package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The overview grid's row count and column height, and the invariants that broke
 * them once.
 *
 * <p>{@code GridLayout.setRowCount} validates the new count against the children
 * it already holds. The screen shrank the grid before clearing it, so switching
 * from the whole-semester view (which reached period 13) to a single week ending
 * at period 6 threw {@code IllegalArgumentException} and killed the app. The
 * order is fixed in {@code WeekGridActivity.render}; what is pinned down here is
 * the contract that made it fatal -- the number of rows is a function of the
 * courses currently shown, so it can go down.</p>
 *
 * <p>Both rules are called on the Activity itself rather than copied. They used
 * to be private, and this file carried a hand-written copy of
 * {@code lastPeriodInUse} with {@code MIN_ROWS} typed out again -- which meant
 * changing the Activity left the test passing against a stale mirror.</p>
 *
 * <p>They are static methods that touch no Android API, so a plain JVM test can
 * call them; the class they live on is only loaded, never instantiated.</p>
 */
public class WeekGridRowsTest {

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
        // The floor is WeekGridActivity.MIN_ROWS; reaching it through the real
        // method is the point, so it is not repeated here.
        int floor = WeekGridActivity.lastPeriodInUse(new ArrayList<Course>());
        assertEquals(floor, WeekGridActivity.lastPeriodInUse(
                Arrays.asList(courseEndingAt(3))));
        assertEquals(10, floor);
        assertEquals(13, WeekGridActivity.lastPeriodInUse(
                Arrays.asList(courseEndingAt(13))));
        assertEquals(15, WeekGridActivity.lastPeriodInUse(
                Arrays.asList(courseEndingAt(15))));
    }

    @Test
    public void theRowCountCanShrinkWhenTheShownCoursesChange() {
        // The exact transition that crashed: the whole semester reaches period
        // 13, the single week ends at period 5.
        int wholeSemester = WeekGridActivity.lastPeriodInUse(
                Arrays.asList(courseEndingAt(13)));
        int singleWeek = WeekGridActivity.lastPeriodInUse(Arrays.asList(courseEndingAt(5)));

        assertTrue("the single-week grid must be shorter than the whole-semester one",
                singleWeek < wholeSemester);
        // Which is only safe because render() clears the children before it
        // resizes; if that order is ever swapped back, this is the number that
        // the old children would be measured against.
        assertEquals(10, singleWeek);
    }

    @Test
    public void theRowCountNeverExceedsThePeriodCap() {
        // A course that claims more periods than a day has must not push the
        // grid past the table it draws.
        int rows = WeekGridActivity.lastPeriodInUse(Arrays.asList(courseEndingAt(15)));
        assertTrue(rows <= ScheduleUtils.PERIODS_PER_DAY);
        assertTrue(WeekGridActivity.lastPeriodInUse(
                Arrays.asList(courseEndingAt(99))) <= ScheduleUtils.PERIODS_PER_DAY);
    }

    /**
     * The day column has to be tall enough for the last period it draws.
     *
     * <p>Every day is one frame spanning all the rows, with the period cells
     * positioned inside it by {@code rowTop}. That frame used to be sized with
     * {@code rowTop(rows)} -- the top edge of the last row -- so it was exactly one
     * row shorter than the rows it held: the frame's own bounds cut the last
     * period off, and because the grid's content was then shorter than the period
     * column's, scrolling could not bring that row fully into view either.</p>
     *
     * <p>Checked against the courses rather than against a number, so the
     * assertion is about the timetable the user can see: a day that runs to
     * period 13 needs a column at least 13 rows tall.</p>
     */
    @Test
    public void aDayColumnIsTallEnoughForTheLastPeriodItDraws() {
        for (int endPeriod = 1; endPeriod <= ScheduleUtils.PERIODS_PER_DAY; endPeriod++) {
            List<Course> courses = Arrays.asList(courseEndingAt(endPeriod));
            int rows = WeekGridActivity.lastPeriodInUse(courses);
            int heightRows = WeekGridActivity.dayColumnRows(rows);

            assertTrue("a column of " + heightRows + " rows cannot draw a course"
                            + " ending at period " + endPeriod,
                    heightRows >= endPeriod);
            // The column and the period label column draw the same rows, which is
            // what keeps the two in step when the grid scrolls.
            assertEquals(rows, heightRows);
        }
    }

    @Test
    public void theDayColumnHeightIsBoundedLikeTheRowCount() {
        assertEquals(1, WeekGridActivity.dayColumnRows(0));
        assertEquals(1, WeekGridActivity.dayColumnRows(-4));
        assertEquals(ScheduleUtils.PERIODS_PER_DAY,
                WeekGridActivity.dayColumnRows(ScheduleUtils.PERIODS_PER_DAY));
        assertEquals(ScheduleUtils.PERIODS_PER_DAY,
                WeekGridActivity.dayColumnRows(99));
    }
}
