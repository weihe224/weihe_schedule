package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Which courses at one timetable slot are alternative arrangements of one
 * another, and so belong on a single stacked card.
 *
 * <p>The overview draws a whole term at once, so one slot is often taken by two
 * courses that never actually meet: one runs the first half of term and the
 * other the second. Drawing those side by side is what made the day columns too
 * narrow to read. They are one slot, so they belong on one card.</p>
 *
 * <p>The test is deliberately narrow. Two courses may share a card only when
 * they occupy the same weekday and the same period range <em>and</em> their
 * teaching weeks never meet. Two lessons that really do clash -- the same week,
 * the same time -- keep their own lanes, because hiding a clash would be worse
 * than showing a narrow column.</p>
 *
 * <p>This is a pure function library like the rest of {@code rule/}: no Android,
 * no storage, and the courses are only read. Nothing here merges or rewrites a
 * course; the stack is derived from the timetable every time it is drawn, so it
 * cannot drift away from it.</p>
 */
public final class CourseStacking {

    private CourseStacking() {
    }

    /**
     * Earliest week first, so a stack is led by the course that starts term.
     */
    private static final Comparator<Course> BY_WEEK =
            Comparator.comparingInt((Course course) -> course.startWeek)
                    .thenComparingInt(course -> course.endWeek)
                    .thenComparingLong(course -> course.id);

    /** Whether two courses describe the same weekday and the same period range. */
    public static boolean sameSlot(Course a, Course b) {
        if (a == null || b == null) {
            return false;
        }
        return a.dayOfWeek == b.dayOfWeek
                && a.startPeriod == b.startPeriod
                && a.endPeriod == b.endPeriod;
    }

    /**
     * Whether there is a week of the term in which both courses are taught.
     *
     * <p>Answered through {@link ScheduleUtils#occursInWeek}, which is what the
     * rest of the app already asks that question with -- a second copy here would
     * be a second thing to keep in step, and odd/even weeks are exactly where a
     * hand-rolled comparison goes wrong.</p>
     */
    public static boolean weeksMeet(Course a, Course b) {
        if (a == null || b == null) {
            return false;
        }
        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            if (ScheduleUtils.occursInWeek(a, week)
                    && ScheduleUtils.occursInWeek(b, week)) {
                return true;
            }
        }
        return false;
    }

    /** Whether two courses may share one stacked card. */
    public static boolean canStack(Course a, Course b) {
        return sameSlot(a, b) && !weeksMeet(a, b);
    }

    /**
     * Splits the courses of one slot into the stacks that slot needs.
     *
     * <p>Greedy in week order: each course joins the first stack whose members it
     * never shares a week with. That is not provably the fewest stacks possible,
     * which is the same trade {@link WeekGridPlan} already makes for its lanes --
     * simple enough to test, and a slot rarely holds enough alternatives for the
     * difference to show.</p>
     *
     * <p>A course whose weeks meet every existing stack starts a new one, so two
     * lessons that genuinely clash are never folded into one card.</p>
     *
     * @param slot courses that all describe the same slot, in any order
     * @return the stacks, each led by the course that starts term earliest
     */
    public static List<List<Course>> partition(List<Course> slot) {
        List<List<Course>> stacks = new ArrayList<>();
        if (slot == null || slot.isEmpty()) {
            return stacks;
        }
        List<Course> ordered = new ArrayList<>(slot);
        Collections.sort(ordered, BY_WEEK);
        for (Course course : ordered) {
            List<Course> host = null;
            for (List<Course> stack : stacks) {
                if (fitsIn(stack, course)) {
                    host = stack;
                    break;
                }
            }
            if (host == null) {
                host = new ArrayList<>();
                stacks.add(host);
            }
            host.add(course);
        }
        return stacks;
    }

    private static boolean fitsIn(List<Course> stack, Course course) {
        for (Course member : stack) {
            if (weeksMeet(member, course)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The units the grid's lane packing should see.
     *
     * <p>Each unit is either a lone course or a stack of alternatives, and each
     * unit occupies exactly one period range. That is what lets the packing
     * treat a stack as one thing: a stack takes the lane its slot needs, instead
     * of a lane per course, which is the whole point.</p>
     *
     * @param courses one cluster's courses, sorted by start period then end period
     * @return the units, in the order the courses came in
     */
    public static List<List<Course>> unitsOf(List<Course> courses) {
        List<List<Course>> units = new ArrayList<>();
        if (courses == null || courses.isEmpty()) {
            return units;
        }
        int index = 0;
        while (index < courses.size()) {
            // The courses of one slot are contiguous in the sorted order, so the
            // run can be taken in one pass without grouping first. Two courses
            // that start together but end differently are different slots, which
            // is why the run is bounded by the end period too.
            int runEnd = index;
            while (runEnd < courses.size()
                    && sameSlot(courses.get(index), courses.get(runEnd))) {
                runEnd++;
            }
            units.addAll(partition(courses.subList(index, runEnd)));
            index = runEnd;
        }
        return units;
    }
}
