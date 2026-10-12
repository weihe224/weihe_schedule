package com.weihe.schedule.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * One term: when it starts, how long it runs, and what it is called.
 *
 * <p>A semester owns its courses and its bell schedules, so looking at a past
 * term shows that term's timetable rather than a filtered view of the current
 * one. The start date is stored as the Monday of week 1, matching how every
 * screen computes weeks.</p>
 *
 * <p>The length is per term. It used to be the single constant
 * {@link ScheduleUtils#SEMESTER_WEEKS}, which made a term that ran long or short
 * unable to say so; now each term carries its own count and that constant is the
 * count a term has when nothing says otherwise. The last day is derived from the
 * two, never stored, so a term cannot claim one length and show the dates of
 * another.</p>
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

    /**
     * Fewest weeks a term may be set to.
     *
     * <p>One, not zero: a term with no weeks has no last day after its first,
     * and every screen that asks "which week is it" would have nothing to
     * answer. A single week is odd but coherent.</p>
     */
    public static final int MIN_WEEKS = 1;

    /**
     * Most weeks a term may be set to.
     *
     * <p>Tied to {@link ScheduleUtils#SEMESTER_WEEKS} on purpose, because that
     * constant is also the ceiling every course week is validated against. Letting
     * a term run longer than the weeks a course can name would produce a term with
     * weeks no timetable could fill.</p>
     */
    public static final int MAX_WEEKS = ScheduleUtils.SEMESTER_WEEKS;

    private final long id;
    private final LocalDate start;
    private final String name;
    private final int weeks;

    /**
     * @param id    stable identifier, never 0 for a stored semester
     * @param start Monday of week 1
     * @param name  user-chosen label, or empty to derive one from the date
     */
    public Semester(long id, LocalDate start, String name) {
        this(id, start, name, ScheduleUtils.SEMESTER_WEEKS);
    }

    /**
     * @param weeks how many weeks the term runs; clamped to
     *              {@code [MIN_WEEKS, MAX_WEEKS]} so a damaged stored value
     *              cannot produce a term no screen can render
     */
    public Semester(long id, LocalDate start, String name, int weeks) {
        this.id = id;
        this.start = ScheduleUtils.mondayOf(start == null ? LocalDate.now() : start);
        this.name = name == null ? "" : name.trim();
        this.weeks = clampWeeks(weeks);
    }

    /** Keeps a week count inside the range every screen can handle. */
    public static int clampWeeks(int value) {
        return Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, value));
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

    /** How many weeks the term runs, always within {@code [MIN_WEEKS, MAX_WEEKS]}. */
    public int weeks() {
        return weeks;
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

    /**
     * Last day of the term, derived from its own start and week count.
     *
     * <p>{@code weeks - 1} because the start date is the first day of week 1: a
     * one-week term ends six days after it starts, and an eighteen-week term
     * covers eighteen whole weeks from that Monday.</p>
     */
    public LocalDate lastDay() {
        return start.plusWeeks(weeks).minusDays(1);
    }

    /** A copy with a different label; an empty label means "derive it again". */
    public Semester renamed(String newName) {
        return new Semester(id, start, newName, weeks);
    }

    /** A copy starting on a different Monday. */
    public Semester startingOn(LocalDate newStart) {
        return new Semester(id, newStart, name, weeks);
    }

    /** A copy running for a different number of weeks. */
    public Semester withWeeks(int newWeeks) {
        return new Semester(id, start, name, newWeeks);
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
