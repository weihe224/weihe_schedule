package com.weihe.schedule.rule;

import java.time.LocalDate;
import java.util.List;

import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * How many weeks a term runs, and what that length implies.
 *
 * <p>Pure arithmetic on a {@link Semester}, kept out of both the screens and the
 * store so the dialog, the repository and the tests all ask the same question of
 * the same code. Nothing here writes: it answers "is this count allowed", "what
 * does that make the last day", and "does any course outgrow it".</p>
 *
 * <p>The count and the start date are separate answers on purpose. The start date
 * says which calendar Monday week 1 is; the count says how many weeks the term
 * lasts from there. A caller that changes one must not silently change the
 * other, which is why this class never returns a start date.</p>
 */
public final class SemesterWeeks {

    /** The count a term has when nothing has been chosen for it. */
    public static final int DEFAULT = ScheduleUtils.SEMESTER_WEEKS;

    private SemesterWeeks() {
    }

    /** True when {@code weeks} is a count a term is allowed to have. */
    public static boolean isValid(int weeks) {
        return weeks >= Semester.MIN_WEEKS && weeks <= Semester.MAX_WEEKS;
    }

    /**
     * The stored count of {@code semester}, or the default when the term is null.
     *
     * <p>Read through the term rather than recomputed, so the dialog opens on what
     * the term actually holds instead of on a value derived from today's date.
     */
    public static int of(Semester semester) {
        return semester == null ? DEFAULT : semester.weeks();
    }

    /**
     * The last day of a term starting on {@code start} and running {@code weeks}.
     *
     * <p>The same arithmetic as {@link Semester#lastDay()}, for callers that have
     * the two values but not a term object yet -- the dialog previewing a count the
     * user has not committed has exactly that shape.</p>
     */
    public static LocalDate lastDay(LocalDate start, int weeks) {
        LocalDate monday = ScheduleUtils.mondayOf(
                start == null ? LocalDate.now() : start);
        return monday.plusWeeks(Semester.clampWeeks(weeks)).minusDays(1);
    }

    /**
     * The largest week number any of {@code courses} reaches, or 0 when there are
     * none.
     *
     * <p>An empty term returns 0 rather than 1 so a caller can tell "no courses"
     * from "courses that only reach week 1" without a second empty check.</p>
     */
    public static int maxWeekOf(List<Course> courses) {
        int max = 0;
        if (courses != null) {
            for (Course course : courses) {
                if (course != null && course.endWeek > max) {
                    max = course.endWeek;
                }
            }
        }
        return max;
    }

    /**
     * How many of {@code courses} run past week {@code weeks}.
     *
     * <p>Counted rather than listed because the dialog only needs the number to
     * say "有 N 门课程超出"; the timetable screen is where the user can see which.
     */
    public static int countBeyond(List<Course> courses, int weeks) {
        int count = 0;
        if (courses != null) {
            for (Course course : courses) {
                if (course != null && course.endWeek > weeks) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Whether shortening a term to {@code weeks} would leave a course outside it.
     *
     * <p>The question the dialog has to answer before it lets a count be confirmed:
     * a term that says sixteen weeks while holding a course in week eighteen has a
     * timetable the term cannot show. The answer is a refusal, never a quiet trim --
     * the weeks on a course are the user's data, not something a length setting may
     * rewrite.</p>
     */
    public static boolean wouldStrandCourses(List<Course> courses, int weeks) {
        return countBeyond(courses, weeks) > 0;
    }
}
