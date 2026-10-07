package com.weihe.schedule;

import java.io.Serializable;

public class Course implements Serializable {
    public static final int PARITY_ALL = 0;
    public static final int PARITY_ODD = 1;
    public static final int PARITY_EVEN = 2;

    /**
     * Colour a brand new course starts with. Kept here rather than in the
     * persistence layer so the model stays the single owner of its own default;
     * {@link Palette#COURSE_COLORS} reuses it as the first selectable colour.
     */
    public static final int DEFAULT_COLOR = 0xFF0F766E;

    public long id;
    public String name = "";
    public String teacher = "";
    public String location = "";
    public int dayOfWeek = 1;
    public int startPeriod = 1;
    public int endPeriod = 2;
    public int startWeek = 1;
    public int endWeek = ScheduleUtils.SEMESTER_WEEKS;
    public int parity = PARITY_ALL;
    public int color = DEFAULT_COLOR;
    /**
     * Name of the bell schedule this course rings to, or empty for "decide by
     * venue". Kept as a name rather than an index so that reordering the
     * schedules in the editor cannot silently repoint existing courses.
     */
    public String scheduleName = "";

    /**
     * Clock span of this course, for example {@code 08:00 - 09:40}.
     *
     * <p>Derived, never stored: it depends on which bell schedule applies, which
     * is decided by {@link BellSchedules#resolve} from the name above and the
     * venue. Keeping it here saves every screen from resolving the schedule
     * again, and it travels with {@link #copy()} for the same reason.</p>
     */
    public String timeRange = "";

    public Course() {
    }

    public Course copy() {
        Course result = new Course();
        result.id = id;
        result.name = name;
        result.teacher = teacher;
        result.location = location;
        result.dayOfWeek = dayOfWeek;
        result.startPeriod = startPeriod;
        result.endPeriod = endPeriod;
        result.startWeek = startWeek;
        result.endWeek = endWeek;
        result.parity = parity;
        result.color = color;
        result.scheduleName = scheduleName;
        result.timeRange = timeRange;
        return result;
    }
}
