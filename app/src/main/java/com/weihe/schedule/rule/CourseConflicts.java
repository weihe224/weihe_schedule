package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Time-conflict rules for courses.
 *
 * <p>The app previously let you save two courses in the exact same slot with no
 * warning at all, which quietly corrupts the schedule. The rules now live here,
 * outside any Activity.</p>
 *
 * <p>Note the deliberate design: this class touches no Android API. Every method
 * is pure logic and can be unit tested on the JVM without an emulator or a
 * device, which is exactly what makes the rule worth extracting. Compare it with
 * the Activities, which cannot be tested without a full Android runtime.</p>
 *
 * <p>Two courses clash when they are on the same weekday, their period ranges
 * overlap, and they have at least one week in common after the odd/even parity
 * rule is applied.</p>
 */
public final class CourseConflicts {

    private CourseConflicts() {
    }

    /** Whether the two courses are scheduled at the same time in at least one week. */
    public static boolean overlaps(Course first, Course second) {
        if (first.dayOfWeek != second.dayOfWeek) {
            return false;
        }
        if (first.startPeriod > second.endPeriod
                || second.startPeriod > first.endPeriod) {
            return false;
        }
        int from = Math.max(first.startWeek, second.startWeek);
        int to = Math.min(first.endWeek, second.endWeek);
        for (int week = from; week <= to; week++) {
            if (ScheduleUtils.occursInWeek(first, week)
                    && ScheduleUtils.occursInWeek(second, week)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every stored course that clashes with {@code candidate}.
     *
     * <p>The candidate itself is skipped when it already has an id, so editing an
     * existing course never reports a conflict with itself.</p>
     */
    public static List<Course> findConflicts(List<Course> courses, Course candidate) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (candidate.id != 0L && course.id == candidate.id) {
                continue;
            }
            if (overlaps(course, candidate)) {
                result.add(course);
            }
        }
        return result;
    }

    /** Ids of all courses in {@code courses} that clash with at least one other. */
    public static Set<Long> conflictingIds(List<Course> courses) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < courses.size(); i++) {
            for (int j = i + 1; j < courses.size(); j++) {
                Course first = courses.get(i);
                Course second = courses.get(j);
                if (overlaps(first, second)) {
                    ids.add(first.id);
                    ids.add(second.id);
                }
            }
        }
        return ids;
    }

    /** One-line summary of a course, used inside the save-time warning. */
    public static String describe(Course course) {
        return course.name + "\uFF08" + ScheduleUtils.dayName(course.dayOfWeek)
                + " " + course.startPeriod + "-" + course.endPeriod
                + "\u8282\uFF09";
    }
}
