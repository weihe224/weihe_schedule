package com.weihe.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * One term: when it starts, and what it is called.
 *
 * <p>A semester owns its courses and its bell schedules, so looking at a past
 * term shows that term's timetable rather than a filtered view of the current
 * one. The start date is stored as the Monday of week 1, matching how every
 * screen computes weeks.</p>
 *
 * <p>The label is derived rather than stored unless the user typed one, so a
 * semester created in spring and later moved to autumn renames itself instead of
 * lying.</p>
 */
public final class Semester {

    /** Autumn terms start in August or later, matching {@link #label()}. */
    public static final int AUTUMN_FROM_MONTH = 8;
    /** Spring terms start in February or later. */
    public static final int SPRING_FROM_MONTH = 2;

    private final long id;
    private final LocalDate start;
    private final String name;

    /**
     * @param id    stable identifier, never 0 for a stored semester
     * @param start Monday of week 1
     * @param name  user-chosen label, or empty to derive one from the date
     */
    public Semester(long id, LocalDate start, String name) {
        this.id = id;
        this.start = ScheduleUtils.mondayOf(start == null ? LocalDate.now() : start);
        this.name = name == null ? "" : name.trim();
    }

    /** A semester starting on {@code start}, with a derived label. */
    public static Semester of(long id, LocalDate start) {
        return new Semester(id, start, "");
    }

    public long id() {
        return id;
    }

    /** Monday of week 1. */
    public LocalDate start() {
        return start;
    }

    /** The user-chosen label, empty when the label is derived. */
    public String name() {
        return name;
    }

    /** True when the label was typed by hand rather than derived. */
    public boolean hasCustomName() {
        return !name.isEmpty();
    }

    /** What the semester is called on screen, for example {@code 2026 秋季学期}. */
    public String label() {
        if (hasCustomName()) {
            return name;
        }
        return start.getYear() + " " + seasonName();
    }

    /** {@code 春季学期} or {@code 秋季学期}, from the start month. */
    public String seasonName() {
        return start.getMonthValue() >= AUTUMN_FROM_MONTH
                ? "\u79CB\u5B63\u5B66\u671F" : "\u6625\u5B63\u5B66\u671F";
    }

    /** Last day of the term, counting {@link ScheduleUtils#SEMESTER_WEEKS}. */
    public LocalDate lastDay() {
        return start.plusWeeks(ScheduleUtils.SEMESTER_WEEKS).minusDays(1);
    }

    /** A copy with a different label; an empty label means "derive it again". */
    public Semester renamed(String newName) {
        return new Semester(id, start, newName);
    }

    /** A copy starting on a different Monday. */
    public Semester startingOn(LocalDate newStart) {
        return new Semester(id, newStart, name);
    }

    /**
     * A sensible start date for the term after this one.
     *
     * <p>Autumn is followed by spring, spring by autumn, and both land on the
     * Monday closest to the usual teaching start.</p>
     */
    public LocalDate nextStart() {
        if (start.getMonthValue() >= AUTUMN_FROM_MONTH) {
            return anchor(LocalDate.of(start.getYear() + 1, 2, 18));
        }
        return anchor(LocalDate.of(start.getYear(), 8, 28));
    }

    /** A semester that follows this one and carries a derived label. */
    public Semester next(long newId) {
        return Semester.of(newId, nextStart());
    }

    private static LocalDate anchor(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Override
    public String toString() {
        return label() + "(" + start + ")";
    }
}
