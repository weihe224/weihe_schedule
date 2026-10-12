package com.weihe.schedule.rule;

import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * What a week range and a parity actually add up to.
 *
 * <p>A course runs on the weeks in {@code startWeek..endWeek} that also match its
 * parity, so the two settings together decide something neither says alone. The
 * form used to show them separately -- a range in two number fields and a parity
 * in its own section -- which left the user to work out the combination, and hid
 * the case where it comes to nothing at all: weeks 1 to 1 on even weeks is a
 * course that never meets.</p>
 *
 * <h2>The rule is not repeated here</h2>
 *
 * <p>Which weeks a course occurs in is decided by
 * {@link ScheduleUtils#occursInWeek(Course, int)}, and the wording of the range by
 * {@link ScheduleUtils#weekRule(Course)}. Both are called rather than copied: a
 * second implementation of the parity rule is exactly the kind of thing that
 * drifts from the first.</p>
 *
 * <h2>What cannot be said</h2>
 *
 * <p>The model stores one contiguous range plus a parity, so a selection of
 * scattered weeks -- 1, 2, 5, 9 -- cannot exist and nothing here pretends
 * otherwise. Every summary this produces describes a range, which is the only
 * shape the timetable can hold.</p>
 */
public final class WeekSelection {

    private WeekSelection() {
    }

    /**
     * How many of the weeks in the range the course actually meets.
     *
     * <p>Zero is a real answer and a useful one: it means the range and the parity
     * contradict each other.</p>
     */
    public static int teachingWeeks(int startWeek, int endWeek, int parity) {
        if (startWeek > endWeek) {
            return 0;
        }
        Course scratch = scratch(startWeek, endWeek, parity);
        int count = 0;
        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            if (ScheduleUtils.occursInWeek(scratch, week)) {
                count++;
            }
        }
        return count;
    }

    /** Whether the range and the parity leave no week at all. */
    public static boolean meetsOnNoWeek(int startWeek, int endWeek, int parity) {
        return teachingWeeks(startWeek, endWeek, parity) == 0;
    }

    /**
     * One line describing the selection: {@code 第1-16周 · 双周 · 上课 8 周}.
     *
     * <p>The last part is the one the two separate controls never said, and the
     * only one that answers "how often does this actually happen".</p>
     */
    public static String summary(int startWeek, int endWeek, int parity) {
        int weeks = teachingWeeks(startWeek, endWeek, parity);
        String rule = ScheduleUtils.weekRule(scratch(startWeek, endWeek, parity));
        if (weeks == 0) {
            return rule + " \u00B7 \u4E0D\u4E0A\u8BFE";
        }
        return rule + " \u00B7 \u4E0A\u8BFE " + weeks + " \u5468";
    }

    /** A course carrying only the three fields the rule reads. */
    private static Course scratch(int startWeek, int endWeek, int parity) {
        Course scratch = new Course();
        scratch.startWeek = startWeek;
        scratch.endWeek = endWeek;
        scratch.parity = parity;
        return scratch;
    }
}
