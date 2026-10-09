package com.weihe.schedule.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The bell schedules the app knows about, and the rules that pick one.
 *
 * <p>Resolution order, which is what "automatic by venue, overridable by hand"
 * means in practice:</p>
 *
 * <ol>
 *   <li>the schedule the course names explicitly, if that name still exists;</li>
 *   <li>otherwise the first schedule whose location keywords appear in the
 *       course's location;</li>
 *   <li>otherwise, when the venue is blank or the table names no building that
 *       matches, the group the school's own table calls "other teaching places"
 *       -- see {@link #resolve};</li>
 *   <li>otherwise the standard schedule.</li>
 * </ol>
 *
 * <p>The school rings the same bells for periods 1, 2 and 6 onwards; only periods
 * 3 to 5 differ between buildings. Those three periods split the campus into
 * three groups, and the groups differ from each other at every one of them, so
 * there is no shorter way to describe this than three schedules.</p>
 *
 * <p>Every time here comes from the school's own table. Nothing in this class is
 * a guess.</p>
 *
 * <p>This class is framework-free and therefore fully testable on the JVM.</p>
 */
public final class BellSchedules {

    /** Name of the schedule for 教学楼 A, F and J301 室. Also the default. */
    public static final String STANDARD_NAME = "\u6559\u5B66\u697C\u4F5C\u606F";

    /** Name of the second group: 教学楼 B, C, J302 室 and other teaching places. */
    public static final String BC_NAME = "B/C \u697C\u4F5C\u606F";

    /** Name of the third group: 教学楼 D, E and J303 室. */
    public static final String DE_NAME = "D/E \u697C\u4F5C\u606F";

    /** Length of one teaching period, in minutes. Constant all day. */
    public static final int PERIOD_MINUTES = 40;

    /** When the first period of the day starts. */
    public static final int FIRST_PERIOD_START = 8 * 60 + 15;

    private BellSchedules() {
    }

    /**
     * The default timetable: 教学楼 A, F and J301 室.
     *
     * <p>Read line by line off the school's table: periods 1-2 are the
     * 08:15-09:35 block, period 3 is 09:55-10:35, periods 4-5 are the
     * 10:40-12:00 block, and from period 6 on the whole campus agrees.</p>
     */
    public static BellSchedule standard() {
        return new BellSchedule(STANDARD_NAME,
                new int[] {495, 535, 595, 640, 680, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 635, 680, 720, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("a\u697C", "f\u697C", "j301"));
    }

    /**
     * 教学楼 B, C and J302 室.
     *
     * <p>Periods 3-4 are one 09:55-11:15 block and period 5 runs 11:20-12:00.</p>
     */
    public static BellSchedule bcBuilding() {
        return new BellSchedule(BC_NAME,
                new int[] {495, 535, 595, 635, 680, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 635, 675, 720, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("b\u697C", "c\u697C", "j302"));
    }

    /**
     * The timetable for 教学楼 D, E and J303 室.
     *
     * <p>Their whole morning runs later: periods 3-4 are the 10:15-11:35 block and
     * period 5 does not ring until 11:40-12:20.</p>
     */
    public static BellSchedule deBuilding() {
        return new BellSchedule(DE_NAME,
                new int[] {495, 535, 615, 655, 700, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 655, 695, 740, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("d\u697C", "e\u697C", "j303"));
    }

    /**
     * Schedules a fresh install starts with, in the order they are offered.
     *
     * <p>The standard one comes first because it is the default. Every one carries
     * its own venue keywords, and each building belongs to exactly one of them, so
     * no keyword can outrank another. A venue the table does not name -- and a
     * course with no venue at all -- is handled by {@link #resolve}, not by a
     * catch-all keyword.</p>
     */
    public static List<BellSchedule> presets() {
        List<BellSchedule> schedules = new ArrayList<>();
        schedules.add(standard());
        schedules.add(deBuilding());
        schedules.add(bcBuilding());
        return schedules;
    }

    /**
     * An independent copy of {@code schedules}.
     *
     * <p>{@link BellSchedule} is immutable, so the copy is shallow on purpose:
     * what matters is that the returned list can be edited (schedules added,
     * removed or replaced) without touching the caller's list.</p>
     */
    public static List<BellSchedule> copyOf(List<BellSchedule> schedules) {
        List<BellSchedule> copy = new ArrayList<>();
        if (schedules != null) {
            for (BellSchedule schedule : schedules) {
                if (schedule != null) {
                    copy.add(schedule);
                }
            }
        }
        return copy;
    }

    /** The schedule with this name, or {@code null} when there is none. */
    public static BellSchedule byName(List<BellSchedule> schedules, String name) {
        if (schedules == null || name == null) {
            return null;
        }
        String wanted = name.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (BellSchedule schedule : schedules) {
            if (schedule.name().equals(wanted)) {
                return schedule;
            }
        }
        return null;
    }

    /** The first schedule whose keywords appear in {@code location}, or null. */
    public static BellSchedule forLocation(List<BellSchedule> schedules, String location) {
        if (schedules == null) {
            return null;
        }
        for (BellSchedule schedule : schedules) {
            if (schedule.matchesLocation(location)) {
                return schedule;
            }
        }
        return null;
    }

    /**
     * The schedule that actually applies to a course.
     *
     * <p>The third step is the interesting one. The school's table puts "other
     * teaching places" in the B/C group, so a venue the table does not name -- and
     * a course with no venue at all -- runs to those times. That cannot be a
     * keyword: a catch-all would have to be tried last, and being last means it
     * never wins, so the rule is written here where the order is explicit.</p>
     *
     * @param scheduleName the course's explicit choice, empty for "decide for me"
     * @param location     the course's venue text
     */
    public static BellSchedule resolve(List<BellSchedule> schedules,
            String scheduleName, String location) {
        BellSchedule chosen = byName(schedules, scheduleName);
        if (chosen != null) {
            return chosen;
        }
        chosen = forLocation(schedules, location);
        if (chosen != null) {
            return chosen;
        }
        chosen = byName(schedules, BC_NAME);
        if (chosen != null) {
            return chosen;
        }
        chosen = byName(schedules, STANDARD_NAME);
        return chosen != null ? chosen : standard();
    }
}
