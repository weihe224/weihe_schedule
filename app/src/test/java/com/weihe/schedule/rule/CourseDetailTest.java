package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
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
 * What the course detail page shows.
 *
 * <p>The page itself needs a device; the decisions it renders do not.
 * {@link CourseDetail} holds all of them -- how an empty teacher reads, how a run
 * of periods is written, when a time must be withheld rather than shown -- so this
 * checks the page's content without drawing it.</p>
 */
public class CourseDetailTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final String SCHEDULE = "Test schedule";

    // ============================================ the ordinary course

    @Test
    public void aNormalCourseExposesEveryFieldThePageShows() {
        Course course = course("Linear Algebra", 3, 3, 4);
        course.teacher = "Prof. Li";
        course.location = "A203";
        course.startWeek = 1;
        course.endWeek = 16;

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertTrue(detail.isPresent());
        assertEquals(course.id, detail.courseId());
        assertEquals("Linear Algebra", detail.name());
        assertEquals("Prof. Li", detail.teacher());
        assertEquals("A203", detail.location());
        assertEquals("Test term", detail.semesterLabel());
        // The app writes dates the short way everywhere (the settings screen shows
        // the same range), so the detail page does too.
        assertEquals("8/31 - 1/3", detail.semesterRange());
        assertEquals(ScheduleUtils.dayName(3), detail.dayName());
        assertEquals("\u7B2C 3-4 \u8282", detail.periodText());
        assertEquals("\u7B2C1-16\u5468", detail.weekRange());
        assertEquals(ScheduleUtils.parityName(course), detail.parity());
        assertEquals("10:00 - 11:45", detail.timeText());
        assertEquals(SCHEDULE, detail.scheduleName());
        assertTrue(detail.timeKnown());
        assertEquals(course.color, detail.color());
    }

    @Test
    public void aSinglePeriodIsWrittenWithoutARange() {
        CourseDetail detail = CourseDetail.of(course("Lab", 1, 5, 5),
                semester(), hourly());

        assertEquals("\u7B2C 5 \u8282", detail.periodText());
    }

    @Test
    public void theHeadlinePairsTheDayWithThePeriods() {
        CourseDetail detail = CourseDetail.of(course("Lab", 2, 3, 4),
                semester(), hourly());

        assertEquals(ScheduleUtils.dayName(2) + " \u00B7 \u7B2C 3-4 \u8282",
                detail.headline());
    }

    @Test
    public void theWeekRuleMatchesTheWordingTheTimetableCardsUse() {
        Course course = course("Lab", 1, 1, 2);
        course.startWeek = 3;
        course.endWeek = 11;
        course.parity = Course.PARITY_ODD;

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals(ScheduleUtils.weekRule(course), detail.weekRule());
    }

    // ============================================ long text

    @Test
    public void aLongNameVenueAndTeacherAreKeptWhole() {
        String longName = "\u4E2D\u56FD\u73B0\u5F53\u4EE3\u6587\u5B66\u4E0E\u6587\u5316"
                + "\u7814\u7A76\u4E13\u9898\u8BFE\u7A0B\uFF08\u542B\u5B9E\u8DF5\u73AF\u8282\uFF09";
        String longVenue = "\u535A\u5B66\u697C\u4E09\u53F7\u6559\u5B66\u697C\u897F\u533A"
                + " A\u5EA7 203 \u5BA4\uFF08\u4ECE\u4E1C\u95E8\u8FDB\u5165\uFF09";
        String longTeacher = "\u5F20\u8001\u5E08\u3001\u674E\u8001\u5E08\uFF08\u8054\u5408"
                + "\u6388\u8BFE\uFF09";
        Course course = course(longName, 1, 1, 2);
        course.location = longVenue;
        course.teacher = longTeacher;

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals("a long name must reach the page intact", longName, detail.name());
        assertEquals(longVenue, detail.location());
        assertEquals(longTeacher, detail.teacher());
    }

    @Test
    public void surroundingWhitespaceIsTrimmedButTheTextIsNotShortened() {
        Course course = course("  Data Structures  ", 1, 1, 2);
        course.teacher = "  Prof. Wang  ";
        course.location = "  C203  ";

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals("Data Structures", detail.name());
        assertEquals("Prof. Wang", detail.teacher());
        assertEquals("C203", detail.location());
    }

    // ============================================ empty fields

    @Test
    public void anEmptyTeacherOrVenueReadsAsNotFilled() {
        Course course = course("Physics", 1, 1, 2);
        course.teacher = "";
        course.location = "";

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals(CourseDetail.NOT_FILLED, detail.teacher());
        assertEquals(CourseDetail.NOT_FILLED, detail.location());
        assertFalse("never a blank, and never null",
                detail.teacher().trim().isEmpty() || detail.location().trim().isEmpty());
    }

    @Test
    public void aFieldOfNothingButSpacesCountsAsEmptyToo() {
        Course course = course("Physics", 1, 1, 2);
        course.teacher = "   ";
        course.location = "\t";

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals(CourseDetail.NOT_FILLED, detail.teacher());
        assertEquals(CourseDetail.NOT_FILLED, detail.location());
    }

    @Test
    public void aCourseWithNoNameIsCalledSomethingRatherThanNothing() {
        Course course = course("", 1, 1, 2);

        CourseDetail detail = CourseDetail.of(course, semester(), hourly());

        assertEquals(CourseDetail.UNNAMED, detail.name());
    }

    @Test
    public void withNoSemesterTheSemesterFieldsSaySoRatherThanGuessing() {
        CourseDetail detail = CourseDetail.of(course("Physics", 1, 1, 2), null, hourly());

        assertEquals(CourseDetail.NOT_FILLED, detail.semesterLabel());
        assertEquals("", detail.semesterRange());
        assertTrue("the rest of the page is unaffected", detail.isPresent());
        assertEquals("\u7B2C 1-2 \u8282", detail.periodText());
    }

    // ============================================ the parity rule

    @Test
    public void everyKindOfWeekRuleIsNamed() {
        Course weekly = course("A", 1, 1, 1);
        Course odd = course("B", 1, 1, 1);
        odd.parity = Course.PARITY_ODD;
        Course even = course("C", 1, 1, 1);
        even.parity = Course.PARITY_EVEN;

        assertEquals(ScheduleUtils.parityName(weekly),
                CourseDetail.of(weekly, semester(), hourly()).parity());
        assertEquals("\u5355\u5468", CourseDetail.of(odd, semester(), hourly()).parity());
        assertEquals("\u53CC\u5468", CourseDetail.of(even, semester(), hourly()).parity());
        assertEquals("\u6BCF\u5468", CourseDetail.of(weekly, semester(), hourly()).parity());
    }

    // ============================================ the bell schedule

    @Test
    public void theTimeComesFromTheScheduleTheCourseRingsTo() {
        // Two buildings, two clocks, same period numbers.
        Course inA = course("A", 1, 3, 3);
        inA.location = "A203";
        inA.scheduleName = "";
        Course inD = course("D", 1, 3, 3);
        inD.location = "D305";
        inD.scheduleName = "";

        CourseDetail a = CourseDetail.of(inA, semester(), BellSchedules.presets());
        CourseDetail d = CourseDetail.of(inD, semester(), BellSchedules.presets());

        assertEquals("09:55 - 10:35", a.timeText());
        assertEquals("10:15 - 10:55", d.timeText());
        assertEquals(BellSchedules.STANDARD_NAME, a.scheduleName());
        assertEquals(BellSchedules.DE_NAME, d.scheduleName());
    }

    @Test
    public void theConsecutivePeriodsOfOneBlockShareOneRange() {
        CourseDetail detail = CourseDetail.of(course("Lab", 1, 3, 5),
                semester(), hourly());

        assertEquals("\u7B2C 3-5 \u8282", detail.periodText());
        // 10:00 start of period 3 to 12:45 end of period 5, from one schedule.
        assertEquals("10:00 - 12:45", detail.timeText());
    }

    // ============================================ when no time can be known

    @Test
    public void withNoSchedulesTheTimeIsWithheldRatherThanGuessed() {
        CourseDetail detail = CourseDetail.of(course("Physics", 1, 1, 2), semester(),
                Collections.<BellSchedule>emptyList());

        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertFalse(detail.timeKnown());
        assertEquals("", detail.scheduleName());
        assertEquals(ReminderPlan.text(ReminderPlan.Skipped.NO_SCHEDULES),
                detail.timeNote());
        assertTrue("and it must not look like a time", detail.timeText().contains(
                "\u6682\u4E0D\u53EF\u7528"));
    }

    @Test
    public void aNullScheduleListIsTheSameAsNone() {
        CourseDetail detail = CourseDetail.of(course("Physics", 1, 1, 2), semester(), null);

        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertFalse(detail.timeKnown());
    }

    @Test
    public void aPeriodTheScheduleCannotPlaceIsWithheldToo() {
        // Eight periods, so a course at period 12 has no start time that is not an
        // invention. BellSchedule#timeRange would clamp and answer anyway.
        List<BellSchedule> shortDay = Arrays.asList(
                periods(480, 540, 600, 660, 720, 780, 840, 900));

        CourseDetail detail = CourseDetail.of(course("Physics", 1, 12, 12),
                semester(), shortDay);

        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertEquals(ReminderPlan.text(ReminderPlan.Skipped.INVALID_PERIOD),
                detail.timeNote());
    }

    @Test
    public void aCourseEndingPastTheScheduleIsWithheldAsWell() {
        List<BellSchedule> shortDay = Arrays.asList(periods(480, 540, 600));

        CourseDetail detail = CourseDetail.of(course("Physics", 1, 2, 9),
                semester(), shortDay);

        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertFalse(detail.timeKnown());
    }

    @Test
    public void anImpossiblePeriodIsWithheldEvenWithGoodSchedules() {
        Course broken = course("Physics", 1, 1, 1);
        broken.endPeriod = ScheduleUtils.PERIODS_PER_DAY + 1;

        CourseDetail detail = CourseDetail.of(broken, semester(), hourly());

        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertEquals(ReminderPlan.text(ReminderPlan.Skipped.INVALID_PERIOD),
                detail.timeNote());
    }

    @Test
    public void thePeriodsAreStillShownWhenTheTimeCannotBe() {
        CourseDetail detail = CourseDetail.of(course("Physics", 1, 12, 12), semester(),
                Arrays.asList(periods(480, 540)));

        // The requirement in one assertion: say what the periods are, and say the
        // clock time is unavailable -- do not silently drop either.
        assertEquals("\u7B2C 12 \u8282", detail.periodText());
        assertEquals(CourseDetail.TIME_UNAVAILABLE, detail.timeText());
        assertFalse(detail.timeNote().isEmpty());
    }

    // ============================================ the course that is gone

    @Test
    public void aMissingCourseIsReportedRatherThanInvented() {
        CourseDetail detail = CourseDetail.of(null, semester(), hourly());

        assertFalse(detail.isPresent());
        assertEquals(0L, detail.courseId());
        assertEquals("", detail.name());
        assertEquals(CourseDetail.NOT_FILLED, detail.teacher());
        assertEquals(CourseDetail.NOT_FILLED, detail.location());
        assertEquals(CourseDetail.NOT_FILLED, detail.semesterLabel());
        assertEquals("", detail.headline());
        assertEquals("", detail.weekRule());
        assertFalse("a course that is not there has no time either",
                detail.timeKnown());
    }

    @Test
    public void missingIsTheSameShapeAsANullCourse() {
        CourseDetail fromNull = CourseDetail.of(null, semester(), hourly());
        CourseDetail direct = CourseDetail.missing();

        assertEquals(direct.isPresent(), fromNull.isPresent());
        assertEquals(direct.name(), fromNull.name());
        assertEquals(direct.timeText(), fromNull.timeText());
        assertEquals(direct.teacher(), fromNull.teacher());
    }

    // ============================================ reading it again

    @Test
    public void describingTheSameCourseTwiceSaysTheSameThing() {
        Course course = course("Physics", 2, 3, 4);
        course.teacher = "Prof. Li";
        course.location = "A203";

        CourseDetail once = CourseDetail.of(course, semester(), hourly());
        CourseDetail twice = CourseDetail.of(course, semester(), hourly());

        assertEquals(once.name(), twice.name());
        assertEquals(once.periodText(), twice.periodText());
        assertEquals(once.timeText(), twice.timeText());
        assertEquals(once.weekRule(), twice.weekRule());
    }

    @Test
    public void anEditedCourseDescribesItsNewSelf() {
        // The page re-reads by id on resume; this is that, without the screen.
        Course course = course("Physics", 2, 3, 4);
        course.location = "A203";
        CourseDetail before = CourseDetail.of(course, semester(), hourly());

        course.name = "Physics II";
        course.location = "D305";
        course.endPeriod = 5;
        CourseDetail after = CourseDetail.of(course, semester(), hourly());

        assertEquals("Physics", before.name());
        assertEquals("A203", before.location());
        assertEquals("Physics II", after.name());
        assertEquals("D305", after.location());
        assertEquals("\u7B2C 3-5 \u8282", after.periodText());
        assertFalse(before.name().equals(after.name()));
    }

    @Test
    public void aCourseMovedToAnotherScheduleReportsTheNewTime() {
        Course course = course("Physics", 1, 3, 3);
        course.location = "A203";
        course.scheduleName = "";
        CourseDetail inStandard = CourseDetail.of(course, semester(), BellSchedules.presets());

        course.location = "D305";
        CourseDetail inDe = CourseDetail.of(course, semester(), BellSchedules.presets());

        assertFalse("the two buildings do not ring alike",
                inStandard.timeText().equals(inDe.timeText()));
        assertEquals(BellSchedules.STANDARD_NAME, inStandard.scheduleName());
        assertEquals(BellSchedules.DE_NAME, inDe.scheduleName());
    }

    // ---------------------------------------------------------------- fixtures

    private static Semester semester() {
        return new Semester(1L, TERM_START, "Test term");
    }

    private static Course course(String name, int dayOfWeek, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = 42L;
        course.name = name;
        course.teacher = "";
        course.location = "";
        course.dayOfWeek = dayOfWeek;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        course.parity = Course.PARITY_ALL;
        course.scheduleName = SCHEDULE;
        return course;
    }

    /** Periods an hour apart from 08:00, so period N starts at 07:00 + N hours. */
    private static List<BellSchedule> hourly() {
        int[] starts = new int[ScheduleUtils.PERIODS_PER_DAY];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = 8 * 60 + i * 60;
        }
        return Arrays.asList(periods(starts));
    }

    private static BellSchedule periods(int... starts) {
        int[] ends = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            ends[i] = starts[i] + 45;
        }
        return new BellSchedule(SCHEDULE, starts, ends, new ArrayList<String>());
    }
}
