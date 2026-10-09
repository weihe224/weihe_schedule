package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Read-only queries over the stored courses.
 *
 * <p>The filtering and ordering the schedule screen needs used to be written
 * inline inside the Activity. That made it impossible to test and impossible to
 * reuse -- and the week grid that is coming next asks exactly the same
 * questions ("what happens on this day", "what happens this week"). The rules
 * live here instead.</p>
 *
 * <p>Like {@link CourseConflicts}, this class deliberately touches no Android
 * API, so it runs under a plain JVM unit test. Neither method modifies the list
 * it was given.</p>
 */
public final class CourseQuery {

    private static final Comparator<Course> BY_START_THEN_END =
            Comparator.comparingInt((Course course) -> course.startPeriod)
                    .thenComparingInt(course -> course.endPeriod);

    private static final Comparator<Course> BY_DAY_THEN_PERIOD =
            Comparator.comparingInt((Course course) -> course.dayOfWeek)
                    .thenComparing(BY_START_THEN_END);

    private CourseQuery() {
    }

    /**
     * Courses that really happen on {@code day} of {@code week}, ordered by
     * period. Both the week window and the odd/even parity of each course are
     * taken into account.
     */
    public static List<Course> onDay(List<Course> courses, int week, int day) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.dayOfWeek == day
                    && ScheduleUtils.occursInWeek(course, week)) {
                result.add(course);
            }
        }
        result.sort(BY_START_THEN_END);
        return result;
    }

    /**
     * Everything that happens during {@code week}, ordered by weekday and then
     * by period. This is the shape a week grid needs.
     */
    public static List<Course> inWeek(List<Course> courses, int week) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (ScheduleUtils.occursInWeek(course, week)) {
                result.add(course);
            }
        }
        result.sort(BY_DAY_THEN_PERIOD);
        return result;
    }

    /**
     * Every stored course, ordered by weekday and then by period.
     *
     * <p>This is the semester-wide view: no week filtering at all, so a course
     * that only runs for part of the term still appears once. Each block in that
     * view carries its own week rule instead.</p>
     */
    public static List<Course> all(List<Course> courses) {
        List<Course> result = new ArrayList<>(courses);
        result.sort(BY_DAY_THEN_PERIOD);
        return result;
    }

    /**
     * Courses that begin exactly at {@code period} on {@code day}.
     *
     * <p>Several can share one slot. In the semester view that is normal -- they
     * simply run in different weeks -- so the grid draws them side by side
     * instead of hiding all but one.</p>
     */
    public static List<Course> startingAt(List<Course> courses, int day, int period) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.dayOfWeek == day && course.startPeriod == period) {
                result.add(course);
            }
        }
        result.sort(BY_START_THEN_END);
        return result;
    }

    /**
     * The largest number of courses sharing one weekday and start period.
     *
     * <p>The grid widens its day columns to this many lanes so that nothing is
     * ever drawn on top of anything else. Returns 1 for an empty list, since a
     * column always has room for one course.</p>
     */
    public static int maxParallel(List<Course> courses) {
        int worst = 1;
        for (Course course : courses) {
            int sharing = 0;
            for (Course other : courses) {
                if (other.dayOfWeek == course.dayOfWeek
                        && other.startPeriod == course.startPeriod) {
                    sharing++;
                }
            }
            worst = Math.max(worst, sharing);
        }
        return worst;
    }
}
