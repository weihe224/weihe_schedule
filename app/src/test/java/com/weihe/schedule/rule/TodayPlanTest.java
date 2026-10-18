package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * What the home-screen widget shows, and when.
 *
 * <p>The widget is the one surface of this app that is read without opening it, so
 * the rules behind it are pinned here rather than in a launcher: the teaching week,
 * the odd/even parity, which bell schedule a venue rings to, and the order the day
 * is read in. Every one of those has been got wrong somewhere else in this app
 * before.</p>
 *
 * <p>All of it is plain JVM: {@link TodayPlan} takes the moment as an argument, so a
 * test can stand at 07:00, 08:20 or 23:00 without waiting for a clock.</p>
 */
public class TodayPlanTest {

    /** A Monday. Week 1 runs 2026-08-31 .. 2026-09-06. */
    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate MONDAY = TERM_START;

    /** The venue keyword the test tables match, and the venue the courses use. */
    private static final String A_BUILDING = "a\u697C";
    private static final String A_VENUE = "A\u697C101";

    // ============================================ what is happening now

    @Test
    public void beforeTheFirstClassTheDayIsUpcoming() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(7, 0));

        assertEquals(TodayPlan.State.UPCOMING, plan.state());
        assertNull("nothing is running at seven in the morning", plan.current());
        assertNotNull(plan.next());
        assertEquals(LocalDateTime.of(MONDAY, LocalTime.of(8, 15)),
                plan.next().startAt());
        assertEquals(plan.next(), plan.headline());
    }

    @Test
    public void aClassInProgressIsTheHeadlineAndTheCountdownTargetsItsEnd() {
        TodayPlan plan = plan(Arrays.asList(course(1L, 1, 1, 2), course(2L, 1, 3, 4)),
                MONDAY.atTime(8, 20));

        assertEquals(TodayPlan.State.IN_CLASS, plan.state());
        assertNotNull(plan.current());
        assertEquals(1L, plan.current().courseId());
        assertEquals("the countdown during a class runs to its end",
                plan.current().endAt(), plan.countdownAt());
        assertNotNull("the following class is still the next one", plan.next());
        assertEquals(2L, plan.next().courseId());
        assertEquals("while a class runs, the headline is the class, not the next one",
                1L, plan.headline().courseId());
    }

    @Test
    public void afterTheLastClassTheDaySaysSoRatherThanStayingOnTheLastOne() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(23, 0));

        assertEquals(TodayPlan.State.NO_CLASS_TODAY, plan.state());
        assertNull(plan.headline());
        assertNull(plan.countdownAt());
        assertTrue(plan.following().isEmpty());
    }

    @Test
    public void exactlyAtTheStartOfAClassItCountsAsRunning() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(8, 15));

        assertEquals(TodayPlan.State.IN_CLASS, plan.state());
        assertEquals(1L, plan.headline().courseId());
    }

    // ============================================ the empty states

    @Test
    public void noCoursesAtAllIsItsOwnStateRatherThanAnEmptyDay() {
        TodayPlan plan = TodayPlan.of(Collections.<Course>emptyList(), semester(),
                schedules(), null, MONDAY.atTime(9, 0), 3);

        assertEquals(TodayPlan.State.NO_TIMETABLE, plan.state());
        assertNull(plan.headline());
        assertTrue(plan.following().isEmpty());
    }

    @Test
    public void noSemesterIsAlsoNoTimetable() {
        TodayPlan plan = TodayPlan.of(courses(course(1L, 1, 1, 2)), null,
                schedules(), null, MONDAY.atTime(9, 0), 3);

        assertEquals(TodayPlan.State.NO_TIMETABLE, plan.state());
    }

    @Test
    public void aDateOutsideTheTermSaysWhichSideOfItItIs() {
        TodayPlan before = plan(courses(course(1L, 1, 1, 2)),
                TERM_START.minusWeeks(2).atTime(9, 0));
        TodayPlan after = plan(courses(course(1L, 1, 1, 2)),
                TERM_START.plusWeeks(ScheduleUtils.SEMESTER_WEEKS).atTime(9, 0));

        assertEquals(TodayPlan.State.OUTSIDE_TERM, before.state());
        assertTrue("before the term the week is not a teaching week", before.week() <= 0);
        assertEquals(TodayPlan.State.OUTSIDE_TERM, after.state());
        assertTrue(after.week() > ScheduleUtils.SEMESTER_WEEKS);
    }

    // ============================================ which courses are today's

    @Test
    public void theOddEvenParityDecidesWhetherACourseRunsThisWeek() {
        Course oddOnly = course(1L, 1, 1, 2);
        oddOnly.parity = Course.PARITY_ODD;

        TodayPlan weekOne = plan(courses(oddOnly), MONDAY.atTime(7, 0));
        TodayPlan weekTwo = plan(courses(oddOnly), MONDAY.plusWeeks(1).atTime(7, 0));

        assertEquals("week 1 is an odd week", TodayPlan.State.UPCOMING, weekOne.state());
        assertEquals("week 2 is an even week, so the course does not run",
                TodayPlan.State.NO_CLASS_TODAY, weekTwo.state());
    }

    @Test
    public void aCourseThatHasNotReachedItsFirstWeekIsNotShown() {
        Course later = course(1L, 1, 1, 2);
        later.startWeek = 3;

        TodayPlan plan = plan(courses(later), MONDAY.atTime(7, 0));

        assertEquals(TodayPlan.State.NO_CLASS_TODAY, plan.state());
    }

    @Test
    public void aCourseOnAnotherWeekdayIsNotShown() {
        TodayPlan plan = plan(courses(course(1L, 2, 1, 2)), MONDAY.atTime(7, 0));

        assertEquals(TodayPlan.State.NO_CLASS_TODAY, plan.state());
    }

    @Test
    public void theWeekAndParityComeFromTheSemesterStartNotTheCalendar() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)),
                TERM_START.plusWeeks(2).atTime(7, 0));

        assertEquals(3, plan.week());
        assertEquals("\u5355\u5468", plan.parity());
    }

    // ============================================ times and schedules

    @Test
    public void eachVenueRingsItsOwnSchedule() {
        BellSchedule standard = schedule("s-standard", "Standard", A_BUILDING,
                8 * 60 + 15, 40, 15);
        BellSchedule lab = schedule("s-lab", "Lab", "b\u697C", 9 * 60, 45, 15);
        Course inStandard = course(1L, 1, 1, 1);
        inStandard.location = A_VENUE;
        Course inLab = course(2L, 1, 1, 1);
        inLab.location = "B\u697C201";

        TodayPlan plan = TodayPlan.of(Arrays.asList(inStandard, inLab), semester(),
                Arrays.asList(standard, lab), null, MONDAY.atTime(7, 0), 3);

        assertEquals(1L, plan.headline().courseId());
        assertEquals(LocalDateTime.of(MONDAY, LocalTime.of(8, 15)),
                plan.headline().startAt());
        assertEquals(1, plan.following().size());
        assertEquals("the lab course starts on its own table, at nine",
                LocalDateTime.of(MONDAY, LocalTime.of(9, 0)),
                plan.following().get(0).startAt());
    }

    @Test
    public void aVenueThatMatchesNothingFallsBackToTheDefaultSchedule() {
        BellSchedule early = schedule("s-early", "Early", "b\u697C", 7 * 60, 30, 15);
        Course nowhere = course(1L, 1, 1, 1);
        nowhere.location = "\u6559\u5B66\u697CZ999";

        TodayPlan plan = TodayPlan.of(courses(nowhere), semester(),
                Collections.singletonList(early), early.id(), MONDAY.atTime(6, 0), 3);

        assertEquals("the default table is what an unmatched venue rings",
                LocalDateTime.of(MONDAY, LocalTime.of(7, 0)), plan.headline().startAt());
    }

    @Test
    public void overlappingCoursesBothAppearEarliestFirst() {
        Course first = course(2L, 1, 1, 4);
        Course second = course(1L, 1, 2, 3);

        TodayPlan plan = plan(Arrays.asList(first, second), MONDAY.atTime(7, 0));

        assertNotNull(plan.following());
        assertEquals("the one that starts first is the headline", 2L,
                plan.headline().courseId());
        assertEquals("the overlapping one still gets its own line", 1, plan.following().size());
        assertEquals(1L, plan.following().get(0).courseId());
    }

    @Test
    public void aCourseBeyondItsSchedulesPeriodsIsDroppedRatherThanClamped() {
        // Three periods on the table, and a course on periods 4-5. The model allows
        // the numbers; the table does not, and a clamped time would be a confident
        // wrong one.
        BellSchedule shortDay = schedule("s-short", "Short", A_BUILDING, 8 * 60 + 15,
                40, 3);

        TodayPlan plan = TodayPlan.of(courses(course(1L, 1, 4, 5)), semester(),
                Collections.singletonList(shortDay), null, MONDAY.atTime(7, 0), 3);

        assertEquals(TodayPlan.State.NO_CLASS_TODAY, plan.state());
    }

    @Test
    public void noBellSchedulesMeansNothingCanBePlaced() {
        TodayPlan plan = TodayPlan.of(courses(course(1L, 1, 1, 2)), semester(),
                Collections.<BellSchedule>emptyList(), null, MONDAY.atTime(7, 0), 3);

        assertEquals(TodayPlan.State.NO_CLASS_TODAY, plan.state());
    }

    @Test
    public void aSessionReadsItsOwnTimesAndPeriods() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(7, 0));
        TodayPlan.Session session = plan.headline();

        assertEquals("08:15 - 09:35", session.timeRange());
        assertEquals("\u7B2C 1-2 \u8282", session.periodLabel());
        assertEquals("Standard", session.scheduleName());
    }

    @Test
    public void aSinglePeriodIsNotWrittenAsARange() {
        TodayPlan plan = plan(courses(course(1L, 1, 3, 3)), MONDAY.atTime(7, 0));

        assertEquals("\u7B2C 3 \u8282", plan.headline().periodLabel());
    }

    // ============================================ the list after the headline

    @Test
    public void theFollowingListIsCappedAndTheRestIsCounted() {
        List<Course> five = Arrays.asList(
                course(1L, 1, 1, 1), course(2L, 1, 3, 3), course(3L, 1, 5, 5),
                course(4L, 1, 6, 6), course(5L, 1, 8, 8));

        TodayPlan plan = plan(five, MONDAY.atTime(7, 0), 2);

        assertEquals(2, plan.following().size());
        assertEquals("five classes, one headline, two shown, two left over",
                2, plan.laterToday());
        assertEquals(2L, plan.following().get(0).courseId());
        assertEquals(3L, plan.following().get(1).courseId());
    }

    @Test
    public void nothingFollowsTheLastClassOfTheDay() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 1)), MONDAY.atTime(7, 0), 3);

        assertTrue(plan.following().isEmpty());
        assertEquals(0, plan.laterToday());
    }

    @Test
    public void aWidgetWithNoRoomForRowsStillHasAHeadline() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 1)), MONDAY.atTime(7, 0), 0);

        assertNotNull(plan.headline());
        assertTrue("a zero budget means no rows, not no plan", plan.following().isEmpty());
    }

    // ============================================ when to refresh

    @Test
    public void theRefreshIsAimedAtTheEndOfTheClassInProgress() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(8, 20));

        assertEquals(plan.current().endAt(), plan.nextRefreshAt());
    }

    @Test
    public void theRefreshIsAimedAtTheStartOfTheNextClass() {
        TodayPlan plan = plan(courses(course(1L, 1, 3, 4)), MONDAY.atTime(8, 20));

        assertEquals(plan.next().startAt(), plan.nextRefreshAt());
    }

    @Test
    public void anEmptyEveningRefreshesAfterMidnight() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(23, 0));

        assertEquals(MONDAY.plusDays(1).atTime(0, 5), plan.nextRefreshAt());
    }

    @Test
    public void theMidnightRefreshIsAfterMidnightNotOnIt() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(23, 0));

        assertTrue("waking exactly at 00:00 races the calendar itself",
                plan.nextRefreshAt().isAfter(MONDAY.plusDays(1).atStartOfDay()));
    }

    @Test
    public void theDayCountKeepsTheClassesThatAreAlreadyOver() {
        // Three classes, all finished by half past twelve. The lists the widget draws
        // from are empty by then, but the day still held three classes and the entry
        // line has to say so.
        TodayPlan plan = plan(Arrays.asList(course(1L, 1, 1, 1), course(2L, 1, 3, 3),
                course(3L, 1, 6, 6)), MONDAY.atTime(12, 30));

        assertEquals(3, plan.todayCount());
        assertNull(plan.headline());
        assertTrue(plan.following().isEmpty());
    }

    @Test
    public void anEmptyDayCountsZero() {
        TodayPlan none = TodayPlan.of(Collections.<Course>emptyList(), semester(),
                schedules(), null, MONDAY.atTime(9, 0), 3);
        TodayPlan noTimetable = TodayPlan.of(courses(course(1L, 1, 1, 2)), null,
                schedules(), null, MONDAY.atTime(9, 0), 3);

        assertEquals(0, none.todayCount());
        assertEquals(0, noTimetable.todayCount());
    }

    @Test
    public void anEmptyPlanStillHasADayToShow() {
        TodayPlan plan = plan(courses(course(1L, 1, 1, 2)), MONDAY.atTime(7, 0));

        assertEquals(MONDAY, plan.day());
        assertFalse(plan.semesterLabel().isEmpty());
    }

    // ---------------------------------------------------------------- fixtures

    private static TodayPlan plan(List<Course> courses, LocalDateTime now) {
        return plan(courses, now, 3);
    }

    private static TodayPlan plan(List<Course> courses, LocalDateTime now,
            int maxFollowing) {
        return TodayPlan.of(courses, semester(), schedules(), null, now, maxFollowing);
    }

    private static Semester semester() {
        return new Semester(1L, TERM_START, "Test term");
    }

    private static List<Course> courses(Course... courses) {
        return new ArrayList<>(Arrays.asList(courses));
    }

    /** A course in the A building, every week of the term, unless changed. */
    private static Course course(long id, int dayOfWeek, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = id;
        course.name = "Course " + id;
        course.teacher = "";
        course.location = A_VENUE;
        course.dayOfWeek = dayOfWeek;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    /** The one table the fixture courses ring: 08:15 onwards, forty-minute periods. */
    private static List<BellSchedule> schedules() {
        return Collections.singletonList(schedule("s-standard", "Standard", A_BUILDING,
                8 * 60 + 15, 40, ScheduleUtils.PERIODS_PER_DAY));
    }

    private static BellSchedule schedule(String id, String name, String keyword,
            int firstStartMinute, int periodMinutes, int periods) {
        int[] starts = new int[periods];
        int[] ends = new int[periods];
        for (int index = 0; index < periods; index++) {
            starts[index] = firstStartMinute + index * periodMinutes;
            ends[index] = starts[index] + periodMinutes;
        }
        return new BellSchedule(id, name, starts, ends,
                Collections.singletonList(keyword), periodMinutes);
    }
}
