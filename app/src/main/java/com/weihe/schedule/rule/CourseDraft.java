package com.weihe.schedule.rule;

/**
 * What the course form is holding before it is saved.
 *
 * <p>The form does not write into its {@link com.weihe.schedule.model.Course} as
 * the user types. The course object keeps the values that were read from storage,
 * the views hold what is being edited, and the two meet in
 * {@code applyForm} only when the save button is pressed. That means a course
 * object alone cannot describe an unfinished form: it holds neither the raw week
 * text, which is an {@code int} on the model and so cannot be half-typed, nor a
 * value the user has changed but not yet committed. This class is the form's
 * contents instead of the course's.</p>
 *
 * <h2>Absent values</h2>
 *
 * <p>Every piece of it can be missing, and missing means "leave the field as it
 * was initialised". A {@code null} string or the {@link #NO_INDEX} position is not
 * an empty value and not position zero -- it says the state did not carry this
 * field, so normal initialisation stands. That way a state written by an older
 * build, or by a build that saved fewer fields, degrades one field at a time
 * instead of producing a form of blanks.</p>
 *
 * <h2>No Android</h2>
 *
 * <p>Nothing here knows about {@code Bundle} or about views. The activity maps
 * between this and the saved state, which is what keeps the interesting half --
 * that a half-typed week or an unusual colour survives the round trip -- testable
 * on the JVM.</p>
 */
public final class CourseDraft {

    /**
     * A position that is not a position: the field was not in the saved state, or
     * what was saved is not one of the values the control can show.
     */
    public static final int NO_INDEX = -1;

    /**
     * A colour the form did not carry.
     *
     * <p>Zero is safe as the marker because no real colour is zero: a course's
     * colour is always an opaque ARGB value from the palette, and even a course
     * that has never been given one holds {@code Course.DEFAULT_COLOR}.</p>
     */
    public static final int NO_COLOR = 0;

    private final String name;
    private final String teacher;
    private final String location;
    private final String startWeek;
    private final String endWeek;
    private final int dayIndex;
    private final int startPeriodIndex;
    private final int endPeriodIndex;
    private final int parityIndex;
    private final int scheduleIndex;
    private final int color;

    /**
     * @param name              typed course name, or null when not carried
     * @param teacher           typed teacher, or null when not carried
     * @param location          typed venue, or null when not carried
     * @param startWeek         raw text of the first-week field, or null
     * @param endWeek           raw text of the last-week field, or null
     * @param dayIndex          weekday spinner position, or {@link #NO_INDEX}
     * @param startPeriodIndex  first-period spinner position, or {@link #NO_INDEX}
     * @param endPeriodIndex    last-period spinner position, or {@link #NO_INDEX}
     * @param parityIndex       every/odd/even radio position, or {@link #NO_INDEX}
     * @param scheduleIndex     bell-schedule spinner position, or {@link #NO_INDEX}
     * @param color             chosen colour, or {@link #NO_COLOR}
     */
    public CourseDraft(String name, String teacher, String location,
            String startWeek, String endWeek, int dayIndex, int startPeriodIndex,
            int endPeriodIndex, int parityIndex, int scheduleIndex, int color) {
        this.name = name;
        this.teacher = teacher;
        this.location = location;
        this.startWeek = startWeek;
        this.endWeek = endWeek;
        this.dayIndex = dayIndex;
        this.startPeriodIndex = startPeriodIndex;
        this.endPeriodIndex = endPeriodIndex;
        this.parityIndex = parityIndex;
        this.scheduleIndex = scheduleIndex;
        this.color = color;
    }

    /**
     * {@code index} when the control really has that position, else
     * {@link #NO_INDEX}.
     *
     * <p>The saved state and the screen it is restored into are not guaranteed to
     * offer the same choices. A bell-schedule list can have lost an entry since the
     * state was written, so a position that was valid then can point past the end
     * of the spinner now. Selecting past the end throws; treating the position as
     * absent instead leaves the field on the value normal initialisation gave it,
     * which is the answer that cannot be wrong.</p>
     */
    public static int usableIndex(int index, int count) {
        return index >= 0 && index < count ? index : NO_INDEX;
    }

    /** Typed course name, or null when the state did not carry one. */
    public String name() {
        return name;
    }

    /** Typed teacher, or null. */
    public String teacher() {
        return teacher;
    }

    /** Typed venue, or null. */
    public String location() {
        return location;
    }

    /** Raw text of the first-week field, exactly as typed, or null. */
    public String startWeek() {
        return startWeek;
    }

    /** Raw text of the last-week field, exactly as typed, or null. */
    public String endWeek() {
        return endWeek;
    }

    public int dayIndex() {
        return dayIndex;
    }

    public int startPeriodIndex() {
        return startPeriodIndex;
    }

    public int endPeriodIndex() {
        return endPeriodIndex;
    }

    public int parityIndex() {
        return parityIndex;
    }

    public int scheduleIndex() {
        return scheduleIndex;
    }

    /** Chosen colour, or {@link #NO_COLOR} when the state did not carry one. */
    public int color() {
        return color;
    }

    /** Whether the state carried a colour at all. */
    public boolean hasColor() {
        return color != NO_COLOR;
    }
}
