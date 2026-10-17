package com.weihe.schedule.model;

import java.io.Serializable;

public class Course implements Serializable {
    public static final int PARITY_ALL = 0;
    public static final int PARITY_ODD = 1;
    public static final int PARITY_EVEN = 2;

    /**
     * Colour a brand new course starts with. Kept here rather than in the
     * persistence layer so the model stays the single owner of its own default;
     * {@link com.weihe.schedule.design.Palette#COURSE_COLORS} reuses it as the first selectable colour.
     *
     * <p>A clear mid blue. It has to be the most common colour on screen --
     * most courses keep the default -- so it is the one that carries the app's
     * normal look, and it is the anchor the other eight are spread around.</p>
     *
     * <p>Not a stored-format concern: a course stores its own ARGB, so changing
     * this literal does not repaint anything that already exists. It only
     * decides what a course created from now on looks like, and what an import
     * falls back to when a file names no colour.</p>
     */
    public static final int DEFAULT_COLOR = 0xFF2878B5;

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
     * Free-text notes for the <em>course</em>, not for this arrangement of it.
     *
     * <p>A subject that meets twice a week is stored as two records, so a note
     * kept on only one of them would be invisible from the other. It is stored on
     * every record of the course and written to all of them at once -- exactly the
     * way {@link #color} already works -- which means the note travels with a
     * record when it is renamed or moved, and needs no separate table to keep in
     * step with the timetable.</p>
     *
     * <p>Empty means "no note", which is also what every course written before
     * this field existed reads back as.</p>
     */
    public String note = "";

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
        result.note = note;
        result.timeRange = timeRange;
        return result;
    }
}
