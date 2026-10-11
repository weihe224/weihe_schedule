package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * The reminder calculation.
 *
 * <p>Every test pins a date and a clock. Nothing here reads the system time, so
 * the suite says the same thing whenever it runs.</p>
 *
 * <p>Most of the arithmetic uses {@link #hourly()}, a schedule whose periods are
 * an hour apart starting at 08:00. The real school schedules have breaks baked
 * into them, which makes an assertion like "period 5 is at 11:20" a fact worth
 * checking but a poor unit of measurement: with an hourly schedule the period
 * number is also the hour, so a wrong period shows up as a wrong hour. The real
 * schedules are still used, for the things only they can show -- which schedule a
 * course resolves to, and that two buildings ring at different times.</p>
 */
public class ReminderPlanTest {

    /** A Monday. Week 1 runs 2026-08-31 .. 2026-09-06. */
    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate WEEK1_MON = TERM_START;
    private static final LocalDate WEEK1_WED = LocalDate.parse("2026-09-02");
    private static final LocalDate WEEK2_WED = LocalDate.parse("2026-09-09");
    private static final LocalDate WEEK3_WED = LocalDate.parse("2026-09-16");
    private static final LocalDate WEEK5_WED = LocalDate.parse("2026-09-30");
    private static final LocalDate WEEK6_WED = LocalDate.parse("2026-10-07");
    private static final LocalDate WEEK18_SUN = LocalDate.parse("2027-01-03");
    private static final LocalDate BEFORE_TERM = LocalDate.parse("2026-08-24");
    private static final LocalDate AFTER_TERM = LocalDate.parse("2027-01-04");

    private static final int LEAD = 10;

    /**
     * The name every fixture course and every fixture schedule shares.
     *
     * <p>{@code BellSchedules#resolve} falls back to the built-in standard schedule
     * when a course names no schedule and its venue matches no keyword -- so a
     * custom schedule with no keywords is simply never chosen, and a test that
     * expected its own times would quietly be measuring the preset instead. Naming
     * it on the course is what makes these tests measure the schedule they think
     * they are measuring. The venue route is covered separately, against the real
     * presets.</p>
     */
    private static final String TEST_SCHEDULE = "Test schedule";

    // =================================================== the week and its edges

    @Test
    public void weekOneStartsOnTheSemesterStartDate() {
        assertEquals(1, ReminderPlan.rawWeekOf(TERM_START, TERM_START));
        assertEquals(1, ReminderPlan.rawWeekOf(TERM_START, LocalDate.parse("2026-09-06")));
        assertEquals(2, ReminderPlan.rawWeekOf(TERM_START, LocalDate.parse("2026-09-07")));
    }

    @Test
    public void theWeekCountIsNotClampedLikeTheHeaderIs() {
        // ScheduleUtils.currentWeek would say 1 and 18 here. Both would be wrong
        // for a reminder, and both are exactly what this method exists to avoid.
        assertEquals(0, ReminderPlan.rawWeekOf(TERM_START, BEFORE_TERM));
        assertEquals(-1, ReminderPlan.rawWeekOf(TERM_START, LocalDate.parse("2026-08-17")));
        assertEquals(18, ReminderPlan.rawWeekOf(TERM_START, WEEK18_SUN));
        assertEquals(19, ReminderPlan.rawWeekOf(TERM_START, AFTER_TERM));

        // The header's answer, for contrast, is the clamped one.
        assertEquals(1, ScheduleUtils.currentWeek(TERM_START, BEFORE_TERM));
        assertEquals(18, ScheduleUtils.currentWeek(TERM_START, AFTER_TERM));
    }

    @Test
    public void theTermsEdgesAreTheFirstAndLastTeachingWeeks() {
        assertTrue(ReminderPlan.isWithinSemester(TERM_START, TERM_START));
        assertTrue(ReminderPlan.isWithinSemester(TERM_START, WEEK18_SUN));
        assertFalse(ReminderPlan.isWithinSemester(TERM_START, BEFORE_TERM));
        assertFalse(ReminderPlan.isWithinSemester(TERM_START, AFTER_TERM));
        assertFalse(ReminderPlan.isWithinSemester(null, TERM_START));
        assertFalse(ReminderPlan.isWithinSemester(TERM_START, null));
    }

    @Test
    public void aDayBeforeTermProducesNothingAndSaysWhy() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), semester(), hourly(),
                BEFORE_TERM, LEAD, at("2026-08-01T00:00"));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.OUTSIDE_SEMESTER).size());
    }

    @Test
    public void aDayAfterTermProducesNothingAndSaysWhy() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), semester(), hourly(),
                AFTER_TERM, LEAD, at("2027-01-01T00:00"));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.OUTSIDE_SEMESTER).size());
    }

    @Test
    public void aMissingSemesterProducesNothingRatherThanGuessing() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), null, hourly(),
                WEEK1_MON, LEAD, at("2026-08-01T00:00"));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.OUTSIDE_SEMESTER).size());
    }

    // ================================================= the ordinary calculation

    @Test
    public void aNormalWeekdayCourseIsScheduledTenMinutesBeforeItStarts() {
        // Monday of week 1, period 1, which the hourly schedule starts at 08:00.
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T07:00"));

        assertEquals(1, plan.reminders().size());
        Reminder reminder = plan.reminders().get(0);
        assertEquals(1L, reminder.courseId());
        assertEquals(LocalDateTime.parse("2026-08-31T08:00"), reminder.startAt());
        assertEquals(LocalDateTime.parse("2026-08-31T07:50"), reminder.triggerAt());
        assertEquals(LEAD, reminder.leadMinutes());
        assertEquals(WEEK1_MON, reminder.classDay());
        assertTrue(plan.skipped().isEmpty());
    }

    @Test
    public void differentPeriodsOnTheSameDayStartAtDifferentTimes() {
        List<Course> monday = Arrays.asList(
                course(1L, 1, 1, 1),
                course(2L, 1, 3, 3),
                course(3L, 1, 5, 5));

        ReminderPlan.Plan plan = ReminderPlan.onDate(monday, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(3, plan.reminders().size());
        assertEquals(LocalDateTime.parse("2026-08-31T08:00"),
                plan.reminders().get(0).startAt());
        assertEquals(LocalDateTime.parse("2026-08-31T10:00"),
                plan.reminders().get(1).startAt());
        assertEquals(LocalDateTime.parse("2026-08-31T12:00"),
                plan.reminders().get(2).startAt());
    }

    @Test
    public void onlyTheCoursesOfThatWeekdayAreReturned() {
        List<Course> courses = Arrays.asList(
                course(1L, 1, 1, 1),     // Monday
                course(2L, 3, 1, 1),     // Wednesday
                course(3L, 7, 1, 1));    // Sunday

        ReminderPlan.Plan plan = ReminderPlan.onDate(courses, semester(), hourly(),
                WEEK1_WED, LEAD, at("2026-08-01T00:00"));

        assertEquals(1, plan.reminders().size());
        assertEquals(2L, plan.reminders().get(0).courseId());
    }

    // ======================================================= week window rules

    @Test
    public void anOddWeekCourseRunsInOddWeeksOnly() {
        Course odd = course(1L, 3, 1, 1);
        odd.parity = Course.PARITY_ODD;
        List<Course> courses = Arrays.asList(odd);

        assertEquals("week 1 is odd", 1,
                onDate(courses, WEEK1_WED).reminders().size());
        assertTrue("week 2 is even",
                onDate(courses, WEEK2_WED).reminders().isEmpty());
        assertEquals("week 3 is odd", 1,
                onDate(courses, WEEK3_WED).reminders().size());
    }

    @Test
    public void anEvenWeekCourseRunsInEvenWeeksOnly() {
        Course even = course(1L, 3, 1, 1);
        even.parity = Course.PARITY_EVEN;
        List<Course> courses = Arrays.asList(even);

        assertTrue("week 1 is odd",
                onDate(courses, WEEK1_WED).reminders().isEmpty());
        assertEquals("week 2 is even", 1,
                onDate(courses, WEEK2_WED).reminders().size());
        assertTrue("week 3 is odd",
                onDate(courses, WEEK3_WED).reminders().isEmpty());
    }

    @Test
    public void anEveryWeekCourseRunsInBothKindsOfWeek() {
        List<Course> courses = Arrays.asList(course(1L, 3, 1, 1));

        assertEquals(1, onDate(courses, WEEK1_WED).reminders().size());
        assertEquals(1, onDate(courses, WEEK2_WED).reminders().size());
    }

    @Test
    public void theStartAndEndWeeksAreBothIncluded() {
        Course bounded = course(1L, 3, 1, 1);
        bounded.startWeek = 3;
        bounded.endWeek = 5;
        List<Course> courses = Arrays.asList(bounded);

        assertTrue("before the window", onDate(courses, WEEK2_WED).reminders().isEmpty());
        assertEquals("first week of the window", 1,
                onDate(courses, WEEK3_WED).reminders().size());
        assertEquals("last week of the window", 1,
                onDate(courses, WEEK5_WED).reminders().size());
        assertTrue("after the window", onDate(courses, WEEK6_WED).reminders().isEmpty());
    }

    @Test
    public void aCourseThatRunsInOneWeekOnlyProducesOneReminder() {
        Course single = course(1L, 3, 1, 1);
        single.startWeek = 4;
        single.endWeek = 4;

        ReminderPlan.Plan plan = ReminderPlan.upcoming(
                Arrays.asList(single), semester(), hourly(),
                LocalDate.parse("2026-09-14"), 21, LEAD, at("2026-09-01T00:00"), 0);

        assertEquals(1, plan.reminders().size());
        assertEquals(LocalDate.parse("2026-09-23"), plan.reminders().get(0).classDay());
    }

    @Test
    public void aBoundedWindowAndParityApplyTogether() {
        Course oddWindow = course(1L, 3, 1, 1);
        oddWindow.startWeek = 1;
        oddWindow.endWeek = 4;
        oddWindow.parity = Course.PARITY_ODD;

        // Weeks 1 and 3 are odd and inside the window; 2 and 4 are outside one or
        // the other test.
        assertEquals(1, onDate(Arrays.asList(oddWindow), WEEK1_WED).reminders().size());
        assertTrue(onDate(Arrays.asList(oddWindow), WEEK2_WED).reminders().isEmpty());
        assertEquals(1, onDate(Arrays.asList(oddWindow), WEEK3_WED).reminders().size());
        assertTrue(onDate(Arrays.asList(oddWindow), WEEK6_WED).reminders().isEmpty());
    }

    // =========================================================== bell schedules

    @Test
    public void aCustomScheduleDecidesTheStartTime() {
        BellSchedule late = periods(TEST_SCHEDULE, 600, 660, 720);   // 10:00, 11:00, 12:00
        Course atSecond = course(1L, 1, 2, 2);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(atSecond), semester(),
                Arrays.asList(late), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(1, plan.reminders().size());
        assertEquals(LocalDateTime.parse("2026-08-31T11:00"),
                plan.reminders().get(0).startAt());
    }

    @Test
    public void twoBuildingsRingAtDifferentTimesForTheSamePeriod() {
        // The real presets: period 3 is 09:55 in the standard schedule and 10:15
        // in the D/E one. Same period number, different start.
        Course inA = course(1L, 1, 3, 3);
        inA.location = "A203";
        inA.scheduleName = "";   // let the venue decide
        inA.scheduleName = "";   // let the venue decide
        Course inD = course(2L, 1, 3, 3);
        inD.location = "D305";
        inD.scheduleName = "";
        inD.scheduleName = "";

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(inA, inD), semester(),
                BellSchedules.presets(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(2, plan.reminders().size());
        Reminder a = plan.reminders().get(0);
        Reminder d = plan.reminders().get(1);
        assertEquals(1L, a.courseId());
        assertEquals(LocalDateTime.parse("2026-08-31T09:55"), a.startAt());
        assertEquals(2L, d.courseId());
        assertEquals(LocalDateTime.parse("2026-08-31T10:15"), d.startAt());
    }

    @Test
    public void theScheduleNameOnTheCourseOverridesTheVenue() {
        Course inD = course(1L, 1, 3, 3);
        inD.location = "D305";
        inD.scheduleName = "";
        inD.scheduleName = "";                                 // would pick D/E
        inD.scheduleName = BellSchedules.STANDARD_NAME;        // but the course says otherwise

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(inD), semester(),
                BellSchedules.presets(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(1, plan.reminders().size());
        assertEquals(LocalDateTime.parse("2026-08-31T09:55"),
                plan.reminders().get(0).startAt());
        assertEquals(BellSchedules.STANDARD_NAME, plan.reminders().get(0).scheduleName());
    }

    @Test
    public void theReminderNamesTheScheduleItUsed() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 1)), semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(TEST_SCHEDULE, plan.reminders().get(0).scheduleName());
    }

    @Test
    public void withNoSchedulesNothingIsComputedAtAll() {
        ReminderPlan.Plan empty = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), semester(),
                Collections.<BellSchedule>emptyList(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));
        ReminderPlan.Plan missing = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 1, 2)), semester(),
                null, WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertTrue(empty.isEmpty());
        assertTrue(missing.isEmpty());
        assertEquals(1, empty.skippedFor(ReminderPlan.Skipped.NO_SCHEDULES).size());
        assertEquals(1, missing.skippedFor(ReminderPlan.Skipped.NO_SCHEDULES).size());
        assertNull(ReminderPlan.startOf(course(1L, 1, 1, 2),
                Collections.<BellSchedule>emptyList(), WEEK1_MON));
        assertNull(ReminderPlan.scheduleFor(course(1L, 1, 1, 2),
                Collections.<BellSchedule>emptyList()));
    }

    @Test
    public void aPeriodTheScheduleDoesNotReachIsRefusedRatherThanClamped() {
        // Eight periods, so period 12 does not exist. BellSchedule#startMinute
        // would quietly return period 8's time; that is a confident wrong answer
        // and this must not be one.
        BellSchedule shortDay = periods(TEST_SCHEDULE, 480, 540, 600, 660, 720, 780, 840, 900);
        Course tooLate = course(1L, 1, 12, 12);
        Course justFits = course(2L, 1, 8, 8);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(tooLate, justFits),
                semester(), Arrays.asList(shortDay), WEEK1_MON, LEAD,
                at("2026-08-31T00:00"));

        assertEquals("only the course the schedule can place", 1,
                plan.reminders().size());
        assertEquals(2L, plan.reminders().get(0).courseId());
        ReminderPlan.SkippedCourse refused = plan.skippedFor(1L);
        assertNotNull(refused);
        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD, refused.reason());
        // And the clamp really would have produced a time, which is the point.
        assertEquals(900, shortDay.startMinute(12));
    }

    @Test
    public void anEndPeriodBeyondTheScheduleIsRefusedToo() {
        BellSchedule shortDay = periods(TEST_SCHEDULE, 480, 540, 600);
        Course spanning = course(1L, 1, 2, 9);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(spanning), semester(),
                Arrays.asList(shortDay), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertTrue(plan.isEmpty());
        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD, plan.skippedFor(1L).reason());
    }

    // ================================================== impossible course data

    @Test
    public void aPeriodBelowOneIsRefused() {
        Course broken = course(1L, 1, 0, 2);

        assertRefusedAsInvalidPeriod(broken);
    }

    @Test
    public void aPeriodPastTheEndOfTheDayIsRefused() {
        Course broken = course(1L, 1, 1, ScheduleUtils.PERIODS_PER_DAY + 1);

        assertRefusedAsInvalidPeriod(broken);
    }

    @Test
    public void anEndPeriodBeforeTheStartPeriodIsRefused() {
        Course broken = course(1L, 1, 6, 2);

        assertRefusedAsInvalidPeriod(broken);
    }

    @Test
    public void startOfReturnsNothingRatherThanAGuessForImpossibleData() {
        assertNull(ReminderPlan.startOf(course(1L, 1, 0, 2), hourly(), WEEK1_MON));
        assertNull(ReminderPlan.startOf(course(1L, 1, 1, 99), hourly(), WEEK1_MON));
        assertNull(ReminderPlan.startOf(null, hourly(), WEEK1_MON));
        assertNull(ReminderPlan.startOf(course(1L, 1, 1, 1), hourly(), null));
        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD,
                ReminderPlan.problemWith(course(1L, 1, 0, 2), hourly()));
        assertNull(ReminderPlan.problemWith(course(1L, 1, 1, 2), hourly()));
    }

    private static void assertRefusedAsInvalidPeriod(Course broken) {
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(broken), semester(),
                hourly(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertTrue("no reminder may be invented for " + broken.startPeriod + ".."
                + broken.endPeriod, plan.isEmpty());
        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD, plan.skippedFor(1L).reason());
    }

    // ================================================================ lead time

    @Test
    public void fiveTenAndFifteenMinutesAllShiftTheTrigger() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1));

        assertEquals(LocalDateTime.parse("2026-08-31T07:55"),
                onlyReminder(courses, 5).triggerAt());
        assertEquals(LocalDateTime.parse("2026-08-31T07:50"),
                onlyReminder(courses, 10).triggerAt());
        assertEquals(LocalDateTime.parse("2026-08-31T07:45"),
                onlyReminder(courses, 15).triggerAt());
    }

    @Test
    public void theStartTimeDoesNotMoveWithTheLeadTime() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1));

        assertEquals(onlyReminder(courses, 5).startAt(), onlyReminder(courses, 15).startAt());
    }

    @Test
    public void aLeadTimeOutsideTheSupportedBandIsRefused() {
        assertTrue(ReminderPlan.supportsLeadMinutes(5));
        assertTrue(ReminderPlan.supportsLeadMinutes(10));
        assertTrue(ReminderPlan.supportsLeadMinutes(15));
        assertFalse(ReminderPlan.supportsLeadMinutes(0));
        assertFalse(ReminderPlan.supportsLeadMinutes(4));
        assertFalse(ReminderPlan.supportsLeadMinutes(-10));
        assertFalse(ReminderPlan.supportsLeadMinutes(16));

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, 0, at("2026-08-31T00:00"));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.UNSUPPORTED_LEAD_TIME).size());
    }

    @Test
    public void theDefaultLeadTimeIsOneOfTheThreeOffered() {
        assertEquals(10, ReminderPlan.DEFAULT_LEAD_MINUTES);
        assertTrue(ReminderPlan.supportsLeadMinutes(ReminderPlan.DEFAULT_LEAD_MINUTES));
        assertTrue(ReminderPlan.supportsLeadMinutes(ReminderPlan.MIN_LEAD_MINUTES));
        assertTrue(ReminderPlan.supportsLeadMinutes(ReminderPlan.MAX_LEAD_MINUTES));
    }

    // ================================================== midnight and day edges

    @Test
    public void aReminderCanFallOnTheDayBeforeTheClass() {
        // Period 1 at 00:10; warning fifteen minutes earlier lands at 23:55 the
        // previous evening.
        BellSchedule justAfterMidnight = periods(TEST_SCHEDULE, 10, 70, 130);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 3, 1, 1)),
                semester(), Arrays.asList(justAfterMidnight), WEEK2_WED, 15,
                at("2026-09-08T20:00"));

        assertEquals(1, plan.reminders().size());
        Reminder reminder = plan.reminders().get(0);
        assertEquals(LocalDateTime.parse("2026-09-09T00:10"), reminder.startAt());
        assertEquals(LocalDateTime.parse("2026-09-08T23:55"), reminder.triggerAt());
        assertEquals("the class still belongs to its own timetable day",
                WEEK2_WED, reminder.classDay());
    }

    @Test
    public void aPeriodAfterMidnightStartsOnTheNextCalendarDay() {
        // The model allows a start minute up to 29:59, so 25:00 is legal and
        // means one in the morning of the following day.
        BellSchedule lateLab = periods(TEST_SCHEDULE, 25 * 60, 26 * 60);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 3, 1, 1)),
                semester(), Arrays.asList(lateLab), WEEK2_WED, LEAD,
                at("2026-09-08T20:00"));

        assertEquals(1, plan.reminders().size());
        Reminder reminder = plan.reminders().get(0);
        assertEquals(LocalDateTime.parse("2026-09-10T01:00"), reminder.startAt());
        assertEquals(LocalDateTime.parse("2026-09-10T00:50"), reminder.triggerAt());
        assertEquals(WEEK2_WED, reminder.classDay());
    }

    @Test
    public void aMonthAndAYearBoundaryAreCountedCorrectly() {
        // Week 18 runs 2026-12-28 .. 2027-01-03, which crosses both.
        assertEquals(18, ReminderPlan.rawWeekOf(TERM_START, LocalDate.parse("2026-12-31")));
        assertEquals(18, ReminderPlan.rawWeekOf(TERM_START, LocalDate.parse("2027-01-01")));
        assertTrue(ReminderPlan.isWithinSemester(TERM_START, LocalDate.parse("2027-01-01")));

        Course thursday = course(1L, 4, 1, 1);
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(thursday), semester(),
                hourly(), LocalDate.parse("2026-12-31"), LEAD,
                at("2026-12-01T00:00"));

        assertEquals(1, plan.reminders().size());
        assertEquals(LocalDateTime.parse("2026-12-31T08:00"),
                plan.reminders().get(0).startAt());
    }

    // ============================================================ past reminders

    @Test
    public void anExpiredReminderIsNotReturned() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T07:55"));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.TRIGGER_IN_THE_PAST).size());
    }

    @Test
    public void aTriggerExactlyAtTheCurrentMomentCountsAsPast() {
        // 08:00 start, 10 minute lead -> 07:50. Standing exactly at 07:50.
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T07:50"));

        assertTrue("a reminder for this instant would fire immediately, not ahead",
                plan.isEmpty());
        assertEquals(ReminderPlan.Skipped.TRIGGER_IN_THE_PAST,
                plan.skippedFor(1L).reason());
    }

    @Test
    public void oneMinuteBeforeTheTriggerItIsStillReturned() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T07:49"));

        assertEquals(1, plan.reminders().size());
    }

    @Test
    public void aClassThatHasNotStartedButWhoseLeadWindowHasIsDropped() {
        // 08:00 start, 10 minute lead, standing at 07:57: the warning moment has
        // gone even though the class has not begun. Warning now would be a
        // surprise rather than a heads-up.
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T07:57"));

        assertTrue(plan.isEmpty());
        assertEquals(ReminderPlan.Skipped.TRIGGER_IN_THE_PAST,
                plan.skippedFor(1L).reason());
    }

    @Test
    public void oneExpiredCourseDoesNotHideALaterOne() {
        List<Course> courses = Arrays.asList(
                course(1L, 1, 1, 1),    // 08:00, trigger 07:50
                course(2L, 1, 3, 3));   // 10:00, trigger 09:50

        ReminderPlan.Plan plan = ReminderPlan.onDate(courses, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T08:30"));

        assertEquals(1, plan.reminders().size());
        assertEquals(2L, plan.reminders().get(0).courseId());
        assertEquals(ReminderPlan.Skipped.TRIGGER_IN_THE_PAST,
                plan.skippedFor(1L).reason());
    }

    // ==================================== simultaneous and duplicate classes

    @Test
    public void twoCoursesAtTheSameHourAreBothKept() {
        Course first = course(1L, 1, 3, 3);
        first.name = "Physics";
        Course second = course(2L, 1, 3, 4);
        second.name = "Chemistry";

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(first, second),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals("neither may overwrite the other", 2, plan.reminders().size());
        assertEquals(plan.reminders().get(0).startAt(),
                plan.reminders().get(1).startAt());
        assertNotSameOccurrence(plan.reminders().get(0), plan.reminders().get(1));
    }

    @Test
    public void threeCoursesAtTheSameHourAllSurvive() {
        List<Course> courses = Arrays.asList(
                course(1L, 1, 5, 5), course(2L, 1, 5, 5), course(3L, 1, 5, 5));

        ReminderPlan.Plan plan = ReminderPlan.onDate(courses, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(3, plan.reminders().size());
    }

    @Test
    public void theSameCourseListedTwiceProducesOneReminder() {
        Course duplicated = course(7L, 1, 3, 3);

        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(duplicated, duplicated), semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals("one class, one reminder", 1, plan.reminders().size());
    }

    @Test
    public void aCourseMeetingTwiceOnTheSameDayKeepsBothOccurrences() {
        // Same id, two different periods: two separate classes that happen to
        // share an id are still two classes.
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1), course(2L, 1, 6, 6));

        ReminderPlan.Plan plan = ReminderPlan.onDate(courses, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(2, plan.reminders().size());
    }

    private static void assertNotSameOccurrence(Reminder first, Reminder second) {
        assertFalse(first.sameOccurrence(second));
        assertFalse(first.equals(second));
    }

    // ============================================================== date ranges

    @Test
    public void aRangeReturnsOneReminderPerClassInTimeOrder() {
        // Monday and Wednesday, one class each, over a fortnight.
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1), course(2L, 3, 2, 2));

        ReminderPlan.Plan plan = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, 14, LEAD, at("2026-08-31T00:00"), 0);

        assertEquals(4, plan.reminders().size());
        assertEquals(LocalDateTime.parse("2026-08-31T07:50"),
                plan.reminders().get(0).triggerAt());
        assertEquals(LocalDateTime.parse("2026-09-02T08:50"),
                plan.reminders().get(1).triggerAt());
        assertEquals(LocalDateTime.parse("2026-09-07T07:50"),
                plan.reminders().get(2).triggerAt());
        assertEquals(LocalDateTime.parse("2026-09-09T08:50"),
                plan.reminders().get(3).triggerAt());
        assertAscending(plan.reminders());
    }

    @Test
    public void aRangeSkipsDaysOutsideTheTerm() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1));

        ReminderPlan.Plan plan = ReminderPlan.upcoming(courses, semester(), hourly(),
                BEFORE_TERM, 14, LEAD, at("2026-08-01T00:00"), 0);

        // Only the second week of that window is inside the term.
        assertEquals(1, plan.reminders().size());
        assertEquals(WEEK1_MON, plan.reminders().get(0).classDay());
        assertEquals("the range is reported as leaving the term once, not per day",
                1, plan.skippedFor(ReminderPlan.Skipped.OUTSIDE_SEMESTER).size());
    }

    @Test
    public void aRangeEntirelyOutsideTheTermIsEmpty() {
        ReminderPlan.Plan plan = ReminderPlan.upcoming(
                Arrays.asList(course(1L, 1, 1, 1)), semester(), hourly(),
                LocalDate.parse("2027-02-01"), 14, LEAD, at("2027-01-01T00:00"), 0);

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.OUTSIDE_SEMESTER).size());
    }

    @Test
    public void aRangeStartingTodayStillOffersWhatIsLeftOfToday() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1), course(2L, 1, 6, 6));

        ReminderPlan.Plan plan = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, 1, LEAD, at("2026-08-31T09:00"), 0);

        assertEquals("the 08:00 class has gone, the 13:00 one has not", 1,
                plan.reminders().size());
        assertEquals(2L, plan.reminders().get(0).courseId());
    }

    @Test
    public void theRangeIsCappedAndKeepsTheSoonest() {
        List<Course> courses = Arrays.asList(
                course(1L, 1, 1, 1), course(2L, 2, 1, 1), course(3L, 3, 1, 1));

        ReminderPlan.Plan plan = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, 7, LEAD, at("2026-08-31T00:00"), 2);

        assertEquals(2, plan.reminders().size());
        assertEquals(1L, plan.reminders().get(0).courseId());
        assertEquals(2L, plan.reminders().get(1).courseId());
    }

    @Test
    public void aNonPositiveRangeLengthProducesNothing() {
        assertTrue(ReminderPlan.upcoming(Arrays.asList(course(1L, 1, 1, 1)), semester(),
                hourly(), WEEK1_MON, 0, LEAD, at("2026-08-31T00:00"), 0).isEmpty());
        assertTrue(ReminderPlan.upcoming(Arrays.asList(course(1L, 1, 1, 1)), semester(),
                hourly(), WEEK1_MON, -3, LEAD, at("2026-08-31T00:00"), 0).isEmpty());
        assertTrue(ReminderPlan.upcoming(Arrays.asList(course(1L, 1, 1, 1)), semester(),
                hourly(), null, 7, LEAD, at("2026-08-31T00:00"), 0).isEmpty());
    }

    @Test
    public void theDefaultRangeCoversAWeekAndIsCapped() {
        List<Course> courses = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            for (int period = 1; period <= 5; period++) {
                courses.add(course(day * 100L + period, day, period, period));
            }
        }

        ReminderPlan.Plan plan = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(ReminderPlan.DEFAULT_MAX_REMINDERS, plan.reminders().size());
        assertAscending(plan.reminders());
        assertTrue(plan.reminders().get(0).triggerAt()
                .isBefore(plan.reminders().get(9).triggerAt()));
    }

    @Test
    public void anEmptyCourseListProducesAnEmptyPlan() {
        ReminderPlan.Plan plan = ReminderPlan.upcoming(Collections.<Course>emptyList(),
                semester(), hourly(), WEEK1_MON, 7, LEAD, at("2026-08-31T00:00"), 0);

        assertTrue(plan.isEmpty());
        assertTrue(plan.skipped().isEmpty());
    }

    @Test
    public void aNullCourseListIsTreatedAsEmpty() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(null, semester(), hourly(),
                WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertTrue(plan.isEmpty());
        assertTrue(plan.skipped().isEmpty());
    }

    @Test
    public void aNullDayOrClockProducesNothing() {
        assertTrue(ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)), semester(),
                hourly(), null, LEAD, at("2026-08-31T00:00")).isEmpty());
        assertTrue(ReminderPlan.onDate(Arrays.asList(course(1L, 1, 1, 1)), semester(),
                hourly(), WEEK1_MON, LEAD, null).isEmpty());
    }

    private static void assertAscending(List<Reminder> reminders) {
        for (int i = 1; i < reminders.size(); i++) {
            assertFalse("reminders must come out in time order",
                    reminders.get(i).triggerAt().isBefore(reminders.get(i - 1).triggerAt()));
        }
    }

    // ============================================================ repeatability

    @Test
    public void theSameInputAlwaysGivesTheSameAnswer() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1), course(2L, 3, 2, 2));
        LocalDateTime now = at("2026-08-31T00:00");

        ReminderPlan.Plan once = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, 7, LEAD, now, 0);
        ReminderPlan.Plan twice = ReminderPlan.upcoming(courses, semester(), hourly(),
                WEEK1_MON, 7, LEAD, now, 0);

        assertEquals(once.reminders(), twice.reminders());
        assertEquals(once.reminders().size(), twice.reminders().size());
    }

    @Test
    public void aReminderComparesByValue() {
        List<Course> courses = Arrays.asList(course(1L, 1, 1, 1));

        Reminder first = onlyReminder(courses, LEAD);
        Reminder second = onlyReminder(courses, LEAD);
        Reminder differentLead = onlyReminder(courses, 5);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.equals(differentLead));
        assertFalse(first.equals(null));
        assertFalse(first.equals("not a reminder"));
    }

    @Test
    public void anOccurrenceKeyTellsOneClassFromAnother() {
        Reminder monday = onlyReminderOn(WEEK1_MON, Arrays.asList(course(1L, 1, 1, 1)), LEAD);
        Reminder wednesday = onlyReminderOn(WEEK1_WED,
                Arrays.asList(course(1L, 3, 1, 1)), LEAD);

        assertEquals("1@2026-08-31", monday.occurrenceKey());
        assertEquals("1@2026-09-02", wednesday.occurrenceKey());
        assertTrue(monday.sameOccurrence(onlyReminder(
                Arrays.asList(course(1L, 1, 9, 9)), LEAD)));
        assertFalse(monday.sameOccurrence(wednesday));
        assertFalse(monday.sameOccurrence(null));
    }

    @Test
    public void theReminderCarriesTheCourseDetailsTheNotificationNeeds() {
        Course course = course(42L, 1, 1, 1);
        course.name = "Linear Algebra";
        course.teacher = "Prof. Li";
        course.location = "A203";
        course.color = 0xFF112233;

        Reminder reminder = onlyReminder(Arrays.asList(course), LEAD);

        assertEquals(42L, reminder.courseId());
        assertEquals("Linear Algebra", reminder.courseName());
        assertEquals("Prof. Li", reminder.teacher());
        assertEquals("A203", reminder.location());
        assertEquals(0xFF112233, reminder.color());
    }

    // ================================================================== zones

    @Test
    public void theTimesBecomeInstantsOnlyWhenAZoneIsSupplied() {
        Reminder reminder = onlyReminder(Arrays.asList(course(1L, 1, 1, 1)), LEAD);
        long shanghai = reminder.startMillis(ZoneId.of("Asia/Shanghai"));
        long utc = reminder.startMillis(ZoneId.of("UTC"));

        assertEquals(OffsetDateTime.parse("2026-08-31T08:00+08:00").toInstant()
                .toEpochMilli(), shanghai);
        assertEquals("the same wall clock, eight hours apart as an instant",
                8L * 60L * 60L * 1000L, utc - shanghai);
        assertEquals(10L * 60L * 1000L, shanghai - reminder.triggerMillis(
                ZoneId.of("Asia/Shanghai")));
    }

    @Test
    public void aZoneIsRequiredRatherThanAssumed() {
        Reminder reminder = onlyReminder(Arrays.asList(course(1L, 1, 1, 1)), LEAD);

        try {
            reminder.startMillis(null);
            fail("expected a zone to be demanded");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void aDaylightSavingGapIsResolvedByTheZoneNotByAFixedOffset() {
        // On 2027-03-14 the clocks in New York jump from 02:00 to 03:00, so 02:30
        // does not exist. java.time resolves such a gap forward; a hand-rolled
        // fixed offset would silently produce an instant that is an hour out.
        // The term has to start in February for the date to be a teaching day.
        Semester spring = new Semester(2L, LocalDate.parse("2027-02-01"), "Spring");
        BellSchedule gap = periods(TEST_SCHEDULE, 2 * 60 + 30);

        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 7, 1, 1)),
                spring, Arrays.asList(gap), LocalDate.parse("2027-03-14"), LEAD,
                at("2027-02-01T00:00"));

        assertEquals(1, plan.reminders().size());
        Reminder reminder = plan.reminders().get(0);
        assertEquals(LocalDateTime.parse("2027-03-14T02:30"), reminder.startAt());
        assertEquals(OffsetDateTime.parse("2027-03-14T03:30:00-04:00").toInstant()
                .toEpochMilli(), reminder.startMillis(ZoneId.of("America/New_York")));
    }

    // ================================================================ fixtures

    /** A Monday-to-Sunday semester starting on 2026-08-31. */
    private static Semester semester() {
        return new Semester(1L, TERM_START, "Test term");
    }

    private static LocalDateTime at(String isoLocalDateTime) {
        return LocalDateTime.parse(isoLocalDateTime);
    }

    private static Course course(long id, int dayOfWeek, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = id;
        course.name = "Course " + id;
        course.teacher = "";
        course.location = "";
        course.dayOfWeek = dayOfWeek;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        course.parity = Course.PARITY_ALL;
        course.scheduleName = TEST_SCHEDULE;
        return course;
    }

    /**
     * Periods an hour apart from 08:00, so period N starts at 07:00 + N hours.
     *
     * <p>No venue keywords, so it never wins a venue match and cannot make an
     * unrelated test depend on it.</p>
     */
    private static List<BellSchedule> hourly() {
        int[] starts = new int[ScheduleUtils.PERIODS_PER_DAY];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = 8 * 60 + i * 60;
        }
        return Arrays.asList(periods(TEST_SCHEDULE, starts));
    }

    private static BellSchedule periods(String name, int... starts) {
        int[] ends = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            ends[i] = starts[i] + 45;
        }
        return new BellSchedule(name, starts, ends, Collections.<String>emptyList());
    }

    @Test
    public void everySkipReasonHasSomethingReadableToSay() {
        for (ReminderPlan.Skipped reason : ReminderPlan.Skipped.values()) {
            String text = ReminderPlan.text(reason);
            assertNotNull(text);
            assertFalse("a reason must say something: " + reason, text.trim().isEmpty());
            assertFalse("a reason shown to a user must not be the enum name: " + reason,
                    text.equals(reason.name()));
        }
        assertEquals("", ReminderPlan.text(null));
    }

    @Test
    public void aSkippedCourseDescribesItselfForALogOrAScreen() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(Arrays.asList(course(1L, 1, 0, 2)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        ReminderPlan.SkippedCourse skipped = plan.skippedFor(1L);
        assertNotNull(skipped);
        assertTrue(skipped.describe(), skipped.describe().contains("Course 1"));
        assertTrue(skipped.describe(), skipped.describe().contains("2026-08-31"));
        assertFalse(skipped.describe(), skipped.describe().contains("INVALID_PERIOD"));
    }

    @Test
    public void aRejectionIsFindableByCourseAndByReason() {
        ReminderPlan.Plan plan = ReminderPlan.onDate(
                Arrays.asList(course(1L, 1, 0, 2), course(2L, 1, 1, 1)),
                semester(), hourly(), WEEK1_MON, LEAD, at("2026-08-31T00:00"));

        assertEquals(1, plan.skippedFor(ReminderPlan.Skipped.INVALID_PERIOD).size());
        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD, plan.skippedFor(1L).reason());
        assertNull("a course that produced a reminder has no rejection",
                plan.skippedFor(2L));
        assertNull(plan.skippedFor(999L));
    }

    private static ReminderPlan.Plan onDate(List<Course> courses, LocalDate day) {
        return ReminderPlan.onDate(courses, semester(), hourly(), day, LEAD,
                at("2026-08-01T00:00"));
    }

    private static Reminder onlyReminder(List<Course> courses, int leadMinutes) {
        return onlyReminderOn(WEEK1_MON, courses, leadMinutes);
    }

    /** The single reminder a day should produce, or a failure naming the day. */
    private static Reminder onlyReminderOn(LocalDate day, List<Course> courses,
            int leadMinutes) {
        ReminderPlan.Plan plan = ReminderPlan.onDate(courses, semester(), hourly(), day,
                leadMinutes, day.atStartOfDay().minusDays(7));
        assertEquals(plan.skipped().toString(), 1, plan.reminders().size());
        return plan.reminders().get(0);
    }
}
