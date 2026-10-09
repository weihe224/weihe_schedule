package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Tests for the course form rules.
 *
 * <p>Before these rules moved out of {@code EditCourseActivity} the only way to
 * check them was to open the app and type into the form.</p>
 *
 * <p>No literal week numbers appear below: the last week of the semester is read
 * from {@link ScheduleUtils#SEMESTER_WEEKS} so these tests cannot start failing
 * for the wrong reason if the semester length changes.</p>
 */
public class CourseFormValidatorTest {

    private static final int LAST_WEEK = ScheduleUtils.SEMESTER_WEEKS;

    /** A form that should always be accepted. */
    private static List<CourseFormValidator.Problem> validForm() {
        return CourseFormValidator.check("Math", Integer.valueOf(1),
                Integer.valueOf(LAST_WEEK), 1, 2);
    }

    @Test
    public void aCompleteFormHasNoProblems() {
        assertTrue(validForm().isEmpty());
    }

    @Test
    public void aBlankNameIsRequired() {
        assertEquals(CourseFormValidator.Field.NAME,
                problemOf(CourseFormValidator.check("", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 1, 2)).field());
        assertEquals(CourseFormValidator.Reason.REQUIRED,
                problemOf(CourseFormValidator.check("   ", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 1, 2)).reason());
    }

    @Test
    public void aMissingWeekIsReportedAsNotANumber() {
        assertEquals(CourseFormValidator.Reason.NOT_A_NUMBER,
                problemOf(CourseFormValidator.check("Math", null,
                        Integer.valueOf(LAST_WEEK), 1, 2)).reason());
        assertEquals(CourseFormValidator.Field.END_WEEK,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        null, 1, 2)).field());
    }

    @Test
    public void aWeekBeforeTheSemesterIsOutOfRange() {
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(0),
                        Integer.valueOf(LAST_WEEK), 1, 2)).reason());
    }

    @Test
    public void aWeekAfterTheSemesterIsOutOfRange() {
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK + 1), 1, 2)).reason());
    }

    @Test
    public void theFirstAndLastWeekMayBeTheSame() {
        assertTrue(CourseFormValidator.check("Math", Integer.valueOf(5),
                Integer.valueOf(5), 1, 2).isEmpty());
    }

    @Test
    public void aStartWeekAfterTheEndWeekIsRejected() {
        assertEquals(CourseFormValidator.Reason.START_AFTER_END,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(9),
                        Integer.valueOf(3), 1, 2)).reason());
    }

    @Test
    public void theFirstAndLastPeriodMayBeTheSame() {
        assertTrue(CourseFormValidator.check("Math", Integer.valueOf(1),
                Integer.valueOf(LAST_WEEK), 4, 4).isEmpty());
    }

    @Test
    public void anInvertedPeriodRangeIsRejected() {
        assertEquals(CourseFormValidator.Reason.INVERTED,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 6, 2)).reason());
    }

    @Test
    public void periodsOutsideTheDayAreOutOfRange() {
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 0, 2)).reason());
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 1,
                        ScheduleUtils.PERIODS_PER_DAY + 1)).reason());
    }

    @Test
    public void everyProblemIsReportedInTheOrderTheFormIsRead() {
        List<CourseFormValidator.Problem> problems = CourseFormValidator.check(
                "  ", null, Integer.valueOf(LAST_WEEK + 1), 6, 2);

        assertEquals(4L, problems.size());
        assertEquals(CourseFormValidator.Field.NAME, problems.get(0).field());
        assertEquals(CourseFormValidator.Reason.REQUIRED, problems.get(0).reason());
        assertEquals(CourseFormValidator.Field.START_WEEK, problems.get(1).field());
        assertEquals(CourseFormValidator.Reason.NOT_A_NUMBER, problems.get(1).reason());
        assertEquals(CourseFormValidator.Field.END_WEEK, problems.get(2).field());
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE, problems.get(2).reason());
        assertEquals(CourseFormValidator.Field.PERIODS, problems.get(3).field());
        assertEquals(CourseFormValidator.Reason.INVERTED, problems.get(3).reason());
    }

    @Test
    public void anOutOfRangePeriodWinsOverAnInvertedRange() {
        // 0 is not a real period, so report the range problem rather than
        // pretending the ordering is the interesting issue.
        assertEquals(CourseFormValidator.Reason.OUT_OF_RANGE,
                problemOf(CourseFormValidator.check("Math", Integer.valueOf(1),
                        Integer.valueOf(LAST_WEEK), 0, -3)).reason());
    }

    @Test
    public void parseWeekOrNullTrimsSurroundingSpace() {
        assertEquals(8L, CourseFormValidator.parseWeekOrNull(" 8 ").intValue());
    }

    @Test
    public void parseWeekOrNullRejectsAnythingThatIsNotANumber() {
        assertNull(CourseFormValidator.parseWeekOrNull(null));
        assertNull(CourseFormValidator.parseWeekOrNull(""));
        assertNull(CourseFormValidator.parseWeekOrNull("   "));
        assertNull(CourseFormValidator.parseWeekOrNull("abc"));
        assertNull(CourseFormValidator.parseWeekOrNull("8.5"));
    }

    @Test
    public void parseWeekOrNullKeepsAValidNumber() {
        assertNotNull(CourseFormValidator.parseWeekOrNull("16"));
        assertEquals(16L, CourseFormValidator.parseWeekOrNull("16").intValue());
    }

    /** Convenience for the single-problem cases. */
    private static CourseFormValidator.Problem problemOf(
            List<CourseFormValidator.Problem> problems) {
        assertEquals(1L, problems.size());
        return problems.get(0);
    }
}
