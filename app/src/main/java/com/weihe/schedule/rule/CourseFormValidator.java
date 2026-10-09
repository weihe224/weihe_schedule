package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Validation rules for the course form.
 *
 * <p>The rules used to live inside {@code EditCourseActivity.validateAndSave()},
 * tangled up with {@code setError} calls, focus handling and Toasts. Because
 * they were welded to the View there was no way to check them except by typing
 * into the running app.</p>
 *
 * <p>This class answers one question -- "what is wrong with this form?" -- and
 * reports it as data. It never touches an Android API, so it runs under a plain
 * JVM test. Note the deliberate split of responsibilities:</p>
 *
 * <ul>
 *   <li>this class decides <em>which field</em> is wrong and <em>why</em>;</li>
 *   <li>the Activity decides how to phrase that to the user.</li>
 * </ul>
 *
 * <p>Keeping the wording out of here is what lets the same rules be reused with
 * different messages later.</p>
 */
public final class CourseFormValidator {

    /** The input a problem belongs to. */
    public enum Field {
        NAME,
        START_WEEK,
        END_WEEK,
        /** The period pickers; there is no text field to attach an error to. */
        PERIODS
    }

    /** Why the input is unacceptable. */
    public enum Reason {
        /** A required text was blank. */
        REQUIRED,
        /** A week field could not be read as a number. */
        NOT_A_NUMBER,
        /** A number was outside the allowed range. */
        OUT_OF_RANGE,
        /** The first week is later than the last week. */
        START_AFTER_END,
        /** The first period is later than the last period. */
        INVERTED
    }

    /** One concrete complaint about the form. */
    public static final class Problem {
        private final Field field;
        private final Reason reason;

        Problem(Field field, Reason reason) {
            this.field = field;
            this.reason = reason;
        }

        public Field field() {
            return field;
        }

        public Reason reason() {
            return reason;
        }

        @Override
        public String toString() {
            return field + "/" + reason;
        }
    }

    private CourseFormValidator() {
    }

    /**
     * Reads a week number out of user input.
     *
     * @return the trimmed value, or {@code null} when the text is not a number.
     */
    public static Integer parseWeekOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Checks the whole form.
     *
     * <p>Every problem is reported, in the order a person reads the form, so the
     * caller can highlight all offending fields at once instead of making the
     * user fix one thing per attempt.</p>
     *
     * @param startWeek parsed start week, or {@code null} when unreadable
     * @param endWeek   parsed end week, or {@code null} when unreadable
     * @return an empty list when the form may be saved
     */
    public static List<Problem> check(String name, Integer startWeek,
            Integer endWeek, int startPeriod, int endPeriod) {
        List<Problem> problems = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            problems.add(new Problem(Field.NAME, Reason.REQUIRED));
        }

        checkWeek(problems, Field.START_WEEK, startWeek);
        checkWeek(problems, Field.END_WEEK, endWeek);

        if (startWeek != null && endWeek != null
                && startWeek.intValue() > endWeek.intValue()) {
            problems.add(new Problem(Field.START_WEEK, Reason.START_AFTER_END));
        }

        if (startPeriod < 1 || startPeriod > ScheduleUtils.PERIODS_PER_DAY
                || endPeriod < 1 || endPeriod > ScheduleUtils.PERIODS_PER_DAY) {
            problems.add(new Problem(Field.PERIODS, Reason.OUT_OF_RANGE));
        } else if (startPeriod > endPeriod) {
            problems.add(new Problem(Field.PERIODS, Reason.INVERTED));
        }

        return problems;
    }

    private static void checkWeek(List<Problem> problems, Field field, Integer week) {
        if (week == null) {
            problems.add(new Problem(field, Reason.NOT_A_NUMBER));
        } else if (week.intValue() < 1
                || week.intValue() > ScheduleUtils.SEMESTER_WEEKS) {
            problems.add(new Problem(field, Reason.OUT_OF_RANGE));
        }
    }
}
