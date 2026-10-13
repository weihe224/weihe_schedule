package com.weihe.schedule.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * One upcoming class, with the moment to warn about it.
 *
 * <p>Produced only by {@link ReminderPlan}. Everything here is derived from the
 * course, the semester and the bell schedule; nothing is invented, and nothing
 * is read from a clock -- the current time is always an argument to the planner,
 * so the same inputs always give the same answer.</p>
 *
 * <p>Both times are {@link LocalDateTime}, deliberately. Turning a wall-clock
 * moment into an absolute instant needs a time zone, and the zone belongs to the
 * device the alarm is going on, not to this calculation. Keeping it out means the
 * whole planner is testable without a zone, and the scheduling layer is the only
 * place that has to think about one -- see {@link #startMillis(ZoneId)} and
 * {@link #triggerMillis(ZoneId)}.</p>
 */
public final class Reminder {

    private final long courseId;
    private final String courseName;
    private final String teacher;
    private final String location;
    private final String scheduleName;
    private final int color;
    private final int startPeriod;
    private final int endPeriod;
    private final LocalDate classDay;
    private final LocalDateTime startAt;
    private final LocalDateTime triggerAt;
    private final int leadMinutes;

    Reminder(long courseId, String courseName, String teacher, String location,
            String scheduleName, int color, int startPeriod, int endPeriod,
            LocalDate classDay, LocalDateTime startAt,
            LocalDateTime triggerAt, int leadMinutes) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacher = teacher;
        this.location = location;
        this.scheduleName = scheduleName;
        this.color = color;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.classDay = classDay;
        this.startAt = startAt;
        this.triggerAt = triggerAt;
        this.leadMinutes = leadMinutes;
    }

    public long courseId() {
        return courseId;
    }

    public String courseName() {
        return courseName;
    }

    public String teacher() {
        return teacher;
    }

    public String location() {
        return location;
    }

    /** The bell schedule the start time was read from. */
    public String scheduleName() {
        return scheduleName;
    }

    public int color() {
        return color;
    }

    /**
     * The first period of the class.
     *
     * <p>Carried so a notification can say which periods it is about. The start
     * time already implies them, but a student reads a timetable in periods, and
     * "第 3-4 节" is what they compare against the sheet on the wall.</p>
     */
    public int startPeriod() {
        return startPeriod;
    }

    /** The last period of the class; equal to {@link #startPeriod()} for a single. */
    public int endPeriod() {
        return endPeriod;
    }

    /**
     * The calendar day the class belongs to.
     *
     * <p>Not necessarily the day the class starts on. A period after midnight --
     * the model allows a start minute up to 29:59 -- starts on the following
     * calendar day while still belonging to the timetable day it is written
     * under. Two reminders for the same course are told apart by this day, so it
     * is the nominal day rather than the derived one.</p>
     */
    public LocalDate classDay() {
        return classDay;
    }

    /** When the class starts. May fall on the day after {@link #classDay()}. */
    public LocalDateTime startAt() {
        return startAt;
    }

    /** When to warn: {@link #startAt()} minus the lead time. */
    public LocalDateTime triggerAt() {
        return triggerAt;
    }

    /** How many minutes before the class the user asked to be warned. */
    public int leadMinutes() {
        return leadMinutes;
    }

    /** {@link #startAt()} as an absolute instant in {@code zone}. */
    public long startMillis(ZoneId zone) {
        return toMillis(startAt, zone);
    }

    /** {@link #triggerAt()} as an absolute instant in {@code zone}. */
    public long triggerMillis(ZoneId zone) {
        return toMillis(triggerAt, zone);
    }

    private static long toMillis(LocalDateTime moment, ZoneId zone) {
        if (zone == null) {
            throw new IllegalArgumentException("a zone is required to place a moment in time");
        }
        // ZonedDateTime resolves a gap or an overlap in the zone's own rules; the
        // alternative, assuming a fixed offset, would be wrong twice a year in
        // most of the world.
        return ZonedDateTime.of(moment, zone).toInstant().toEpochMilli();
    }

    /**
     * Whether two reminders are for the same class of the same course.
     *
     * <p>Two courses at the same hour are two reminders and must both be kept;
     * the same course on the same day is one reminder however many times it was
     * asked for.</p>
     */
    public boolean sameOccurrence(Reminder other) {
        return other != null && other.courseId == courseId
                && other.classDay.equals(classDay);
    }

    /** The identity of this occurrence, for de-duplication and notification ids. */
    public String occurrenceKey() {
        return courseId + "@" + classDay;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Reminder)) {
            return false;
        }
        Reminder that = (Reminder) other;
        return courseId == that.courseId
                && color == that.color
                && startPeriod == that.startPeriod
                && endPeriod == that.endPeriod
                && leadMinutes == that.leadMinutes
                && classDay.equals(that.classDay)
                && startAt.equals(that.startAt)
                && triggerAt.equals(that.triggerAt)
                && courseName.equals(that.courseName)
                && teacher.equals(that.teacher)
                && location.equals(that.location)
                && scheduleName.equals(that.scheduleName);
    }

    @Override
    public int hashCode() {
        int result = Long.valueOf(courseId).hashCode();
        result = 31 * result + classDay.hashCode();
        result = 31 * result + startAt.hashCode();
        result = 31 * result + triggerAt.hashCode();
        result = 31 * result + leadMinutes;
        result = 31 * result + courseName.hashCode();
        result = 31 * result + teacher.hashCode();
        result = 31 * result + location.hashCode();
        result = 31 * result + scheduleName.hashCode();
        result = 31 * result + color;
        result = 31 * result + startPeriod;
        result = 31 * result + endPeriod;
        return result;
    }

    @Override
    public String toString() {
        return courseName + " " + startAt + " (\u63D0\u9192 " + triggerAt + ")";
    }
}
