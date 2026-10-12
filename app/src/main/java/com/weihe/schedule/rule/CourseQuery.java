package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 * API, so it runs under a plain JVM unit test. No method here modifies the list
 * it was given, or any course in it.</p>
 */
public final class CourseQuery {

    private static final Comparator<Course> BY_START_THEN_END =
            Comparator.comparingInt((Course course) -> course.startPeriod)
                    .thenComparingInt(course -> course.endPeriod);

    private static final Comparator<Course> BY_DAY_THEN_PERIOD =
            Comparator.comparingInt((Course course) -> course.dayOfWeek)
                    .thenComparing(BY_START_THEN_END);

    /**
     * The order search results come back in: the week's own, then the id.
     *
     * <p>The id is what makes it a total order. Two courses can share a weekday
     * and a period -- the semester view draws them side by side -- and without a
     * final tiebreak their relative order would depend on the list they arrived
     * in, so the same search could return them the other way round.</p>
     */
    private static final Comparator<Course> BY_DAY_THEN_PERIOD_THEN_ID =
            BY_DAY_THEN_PERIOD.thenComparingLong(course -> course.id);

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
     * The courses whose name, teacher or venue contains {@code keyword}.
     *
     * <p>Matching is a plain case-insensitive substring test, and that is the whole
     * of it. A Chinese course name has no case and no word boundaries, so "高数"
     * has to find "高等数学" without the app carrying a pinyin table or a fuzzy
     * matcher; and the three fields are the ones a person actually remembers about
     * a class. A term's courses are what the caller passes in, so a search can only
     * ever see one semester -- the isolation is the caller's choice of list, not a
     * filter applied here.</p>
     *
     * <p>The keyword is trimmed, so a name pasted with a stray space still matches,
     * and lower-cased with {@link Locale#ROOT} rather than the default locale,
     * because the Turkish locale lower-cases "I" to a dotless "ı" and would make
     * the same search behave differently on a different phone.</p>
     *
     * <p>An empty or blank keyword matches <em>nothing</em> rather than everything.
     * "No keyword" is not a search for the whole term: the screen has a state for
     * it, and returning forty courses would be answering a question nobody asked.</p>
     *
     * <p>Neither the list nor the courses in it are modified -- the result holds the
     * same objects in a new list -- and the order is the timetable's own, with the
     * id as the final tiebreak so two courses in one slot always come back the same
     * way round.</p>
     */
    public static List<Course> search(List<Course> courses, String keyword) {
        List<Course> result = new ArrayList<>();
        String needle = normalise(keyword);
        if (courses == null || needle.isEmpty()) {
            return result;
        }
        for (Course course : courses) {
            if (contains(course.name, needle)
                    || contains(course.teacher, needle)
                    || contains(course.location, needle)) {
                result.add(course);
            }
        }
        result.sort(BY_DAY_THEN_PERIOD_THEN_ID);
        return result;
    }

    /** Lower case with the surrounding space removed; empty when there is nothing to look for. */
    private static String normalise(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether one field holds the needle; a missing field holds nothing. */
    private static boolean contains(String field, String needle) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(needle);
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
