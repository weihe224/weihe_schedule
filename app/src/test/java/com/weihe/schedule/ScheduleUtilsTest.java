package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

/**
 * Tests for the week and period arithmetic.
 *
 * <p>This is the part of the app most likely to be quietly wrong: an off-by-one
 * in the semester calendar or in the odd/even rule produces a believable but
 * incorrect timetable. It is pure date maths, so it needs no Android runtime to
 * check.</p>
 *
 * <p>Assertions are written against dates derived from the semester start
 * rather than hard-coded weekdays, so they do not silently depend on which day
 * of the week a given date happens to be.</p>
 */
public class ScheduleUtilsTest {

    private static final LocalDate SEMESTER_START = LocalDate.of(2026, 3, 2);

    private static Course course(long id, int day, int fromPeriod, int toPeriod,
            int fromWeek, int toWeek, int parity) {
        Course course = new Course();
        course.id = id;
        course.dayOfWeek = day;
        course.startPeriod = fromPeriod;
        course.endPeriod = toPeriod;
        course.startWeek = fromWeek;
        course.endWeek = toWeek;
        course.parity = parity;
        return course;
    }

    @Test
    public void weekStartIsAlwaysAMonday() {
        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            assertEquals(1L,
                    ScheduleUtils.weekStart(SEMESTER_START, week).getDayOfWeek().getValue());
        }
    }

    @Test
    public void consecutiveWeeksAreSevenDaysApart() {
        assertEquals(7L, ChronoUnit.DAYS.between(
                ScheduleUtils.weekStart(SEMESTER_START, 1),
                ScheduleUtils.weekStart(SEMESTER_START, 2)));
    }

    @Test
    public void dateOfWalksThroughTheWeekFromMonday() {
        LocalDate monday = ScheduleUtils.weekStart(SEMESTER_START, 3);

        assertEquals(0L, ChronoUnit.DAYS.between(monday,
                ScheduleUtils.dateOf(SEMESTER_START, 3, 1)));
        assertEquals(6L, ChronoUnit.DAYS.between(monday,
                ScheduleUtils.dateOf(SEMESTER_START, 3, 7)));
    }

    @Test
    public void dateOfAgreesWithWeekStartForMonday() {
        assertEquals(ScheduleUtils.weekStart(SEMESTER_START, 5),
                ScheduleUtils.dateOf(SEMESTER_START, 5, 1));
    }

    @Test
    public void dateOfClampsOutOfRangeArguments() {
        assertEquals(ScheduleUtils.dateOf(SEMESTER_START, 1, 1),
                ScheduleUtils.dateOf(SEMESTER_START, -3, 0));
        assertEquals(
                ScheduleUtils.dateOf(SEMESTER_START, ScheduleUtils.SEMESTER_WEEKS, 7),
                ScheduleUtils.dateOf(SEMESTER_START, 99, 9));
    }

    @Test
    public void currentWeekIsOneOnTheSemesterStartItself() {
        assertEquals(1L, ScheduleUtils.currentWeek(SEMESTER_START, SEMESTER_START));
    }

    @Test
    public void currentWeekAdvancesEverySevenDaysFromTheFirstMonday() {
        LocalDate firstMonday = ScheduleUtils.weekStart(SEMESTER_START, 1);

        assertEquals(1L, ScheduleUtils.currentWeek(SEMESTER_START, firstMonday));
        assertEquals(2L, ScheduleUtils.currentWeek(SEMESTER_START, firstMonday.plusWeeks(1)));
        assertEquals(16L, ScheduleUtils.currentWeek(SEMESTER_START, firstMonday.plusWeeks(15)));
    }

    @Test
    public void currentWeekIsClampedToTheSemesterBounds() {
        LocalDate firstMonday = ScheduleUtils.weekStart(SEMESTER_START, 1);

        assertEquals(1L, ScheduleUtils.currentWeek(SEMESTER_START, firstMonday.minusWeeks(4)));
        assertEquals((long) ScheduleUtils.SEMESTER_WEEKS,
                ScheduleUtils.currentWeek(SEMESTER_START, firstMonday.plusWeeks(20)));
    }

    @Test
    public void occursInWeekHonoursTheWeekWindow() {
        Course course = course(1, 1, 1, 2, 3, 8, Course.PARITY_ALL);

        assertFalse(ScheduleUtils.occursInWeek(course, 2));
        assertTrue(ScheduleUtils.occursInWeek(course, 3));
        assertTrue(ScheduleUtils.occursInWeek(course, 8));
        assertFalse(ScheduleUtils.occursInWeek(course, 9));
    }

    @Test
    public void occursInWeekHonoursParity() {
        Course odd = course(1, 1, 1, 2, 1, 16, Course.PARITY_ODD);
        Course even = course(2, 1, 1, 2, 1, 16, Course.PARITY_EVEN);

        assertTrue(ScheduleUtils.occursInWeek(odd, 1));
        assertFalse(ScheduleUtils.occursInWeek(odd, 2));
        assertFalse(ScheduleUtils.occursInWeek(even, 1));
        assertTrue(ScheduleUtils.occursInWeek(even, 2));
    }

    @Test
    public void aWeeklyCourseOccursInEveryWeekOfItsWindow() {
        Course course = course(1, 1, 1, 2, 1, ScheduleUtils.SEMESTER_WEEKS,
                Course.PARITY_ALL);

        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            assertTrue(ScheduleUtils.occursInWeek(course, week));
        }
    }

    @Test
    public void periodTimeFormatsTheFirstPeriod() {
        assertEquals("08:15 - 08:55", ScheduleUtils.periodTime(1, 1));
    }

    @Test
    public void periodTimeClampsBelowTheFirstPeriod() {
        assertEquals(ScheduleUtils.periodTime(1, 1), ScheduleUtils.periodTime(0, 0));
        assertEquals(ScheduleUtils.periodTime(1, 1), ScheduleUtils.periodTime(-5, 0));
    }

    @Test
    public void periodTimeClampsAboveTheLastPeriod() {
        int last = ScheduleUtils.PERIODS_PER_DAY;

        assertEquals(ScheduleUtils.periodTime(last, last),
                ScheduleUtils.periodTime(last + 50, last + 100));
    }

    @Test
    public void periodTimeNeverInvertsTheRange() {
        assertEquals(ScheduleUtils.periodTime(3, 3), ScheduleUtils.periodTime(3, 1));
    }

    @Test
    public void periodLabelsCoverEveryPeriod() {
        String[] labels = ScheduleUtils.periodLabels();

        assertEquals(ScheduleUtils.PERIODS_PER_DAY, labels.length);
        assertEquals("1", labels[0]);
        assertEquals(String.valueOf(ScheduleUtils.PERIODS_PER_DAY),
                labels[labels.length - 1]);
    }

    @Test
    public void dayNameCoversTheWeekAndClampsOutOfRange() {
        assertEquals("\u5468\u4E00", ScheduleUtils.dayName(1));
        assertEquals("\u5468\u65E5", ScheduleUtils.dayName(7));
        assertEquals("\u5468\u4E00", ScheduleUtils.dayName(0));
        assertEquals("\u5468\u65E5", ScheduleUtils.dayName(9));
    }

    @Test
    public void weekRuleReadsAsTheUiExpects() {
        assertEquals("\u7B2C1-16\u5468 \u00B7 \u6BCF\u5468",
                ScheduleUtils.weekRule(
                        course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL)));
    }

    @Test
    public void parityNameDescribesTheWeekNumber() {
        assertEquals("\u5355\u5468", ScheduleUtils.parityName(1));
        assertEquals("\u53CC\u5468", ScheduleUtils.parityName(2));
    }
}
