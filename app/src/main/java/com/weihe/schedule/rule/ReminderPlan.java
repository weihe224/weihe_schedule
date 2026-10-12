package com.weihe.schedule.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * Works out when a class happens and when to warn about it.
 *
 * <p>This is the whole of the reminder calculation, and it is a plain function
 * library on purpose: no Android API, no alarm, no notification, and above all no
 * clock. The current time is an argument to every method that needs one, so a
 * test can stand at any moment it likes and the answer is reproducible.</p>
 *
 * <p>The week and bell-schedule rules are not re-implemented here. Which courses
 * run on a day is {@link CourseQuery#onDay}, which is the same call the timetable
 * screens use; which schedule a course rings to is
 * {@link BellSchedules#resolve}, which is what the on-screen time already comes
 * from. A second copy of either would eventually disagree with the first, and the
 * disagreement would show up as a reminder at the wrong minute.</p>
 *
 * <h2>The one rule this class does add: the week is not clamped</h2>
 *
 * <p>{@link ScheduleUtils#currentWeek} clamps its answer into {@code [1, 18]}.
 * That is right for the header (showing "week 1" beats a crash before term
 * starts) and wrong for a reminder: a date one week before term would read as
 * week 1 and produce a reminder a week early, and a date after the last day would
 * read as week 18 and produce one during the holidays. {@link #rawWeekOf}
 * therefore counts the weeks and keeps the result, and
 * {@link #isWithinSemester} is the gate. Inside the term the two agree exactly;
 * outside it, only this one is usable.</p>
 *
 * <h2>Refusing beats guessing</h2>
 *
 * <p>When a start time cannot be established -- no bell schedules at all, or a
 * period number the schedule does not reach -- the course is reported as skipped
 * with a reason instead of being given a plausible-looking time. See
 * {@link Skipped}.</p>
 */
public final class ReminderPlan {

    /** The shortest lead time the feature offers. */
    public static final int MIN_LEAD_MINUTES = 5;
    /** The longest lead time the feature offers. */
    public static final int MAX_LEAD_MINUTES = 15;
    /** What the settings screen starts on. */
    public static final int DEFAULT_LEAD_MINUTES = 10;
    /** How far ahead a single rebuild schedules. */
    public static final int DEFAULT_DAYS_AHEAD = 7;
    /** How many alarms one rebuild may end up holding. */
    public static final int DEFAULT_MAX_REMINDERS = 10;

    private static final Comparator<Reminder> BY_TRIGGER =
            Comparator.comparing(Reminder::triggerAt)
                    .thenComparingLong(Reminder::courseId)
                    .thenComparing(Reminder::classDay);

    private ReminderPlan() {
    }

    /**
     * Why a course that runs on a day produced no reminder.
     *
     * <p>Kept as data rather than a log line so a screen can explain itself
     * ("three courses could not be reminded because their schedule has no period
     * 12") and a test can assert on the cause instead of on the absence of a
     * result.</p>
     */
    public enum Skipped {
        /** The date is not a teaching day, or there is no semester at all. */
        OUTSIDE_SEMESTER,
        /** No bell schedule was available, so no start time could be read. */
        NO_SCHEDULES,
        /**
         * The course's periods do not exist in the schedule it rings to.
         *
         * <p>Includes the case the model allows but the app never produces by
         * hand: a custom schedule with fewer than fifteen periods, where asking
         * for period 12 would otherwise be silently clamped to the last period
         * this schedule does have.</p>
         */
        INVALID_PERIOD,
        /** The warning moment is not strictly in the future. */
        TRIGGER_IN_THE_PAST,
        /** The requested lead time is outside the supported band. */
        UNSUPPORTED_LEAD_TIME
    }

    /** A course (or a whole day) that was considered and produced nothing. */
    public static final class SkippedCourse {

        private final Course course;
        private final LocalDate day;
        private final Skipped reason;

        SkippedCourse(Course course, LocalDate day, Skipped reason) {
            this.course = course;
            this.day = day;
            this.reason = reason;
        }

        /** The course, or {@code null} when the reason applies to the whole day. */
        public Course course() {
            return course;
        }

        public LocalDate day() {
            return day;
        }

        public Skipped reason() {
            return reason;
        }

        /** A line fit for a log or a diagnostics list. */
        public String describe() {
            String what = course == null ? "\u5F53\u5929" : course.name;
            return what + "\uFF08" + day + "\uFF09\uFF1A" + text(reason);
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    /** What one calculation produced, and what it could not. */
    public static final class Plan {

        private final List<Reminder> reminders;
        private final List<SkippedCourse> skipped;

        Plan(List<Reminder> reminders, List<SkippedCourse> skipped) {
            this.reminders = Collections.unmodifiableList(reminders);
            this.skipped = Collections.unmodifiableList(skipped);
        }

        /** The reminders, earliest first. Never null, possibly empty. */
        public List<Reminder> reminders() {
            return reminders;
        }

        /** Everything that was considered and rejected, with the reason. */
        public List<SkippedCourse> skipped() {
            return skipped;
        }

        public boolean isEmpty() {
            return reminders.isEmpty();
        }

        /** The rejections carrying {@code reason}, for a per-reason summary. */
        public List<SkippedCourse> skippedFor(Skipped reason) {
            List<SkippedCourse> matches = new ArrayList<>();
            for (SkippedCourse entry : skipped) {
                if (entry.reason() == reason) {
                    matches.add(entry);
                }
            }
            return matches;
        }

        /** The rejection of one course, or null when it was not rejected. */
        public SkippedCourse skippedFor(long courseId) {
            for (SkippedCourse entry : skipped) {
                if (entry.course() != null && entry.course().id == courseId) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return reminders.size() + " \u6761\u63D0\u9192\uFF0C"
                    + skipped.size() + " \u6761\u8DF3\u8FC7";
        }
    }

    // ------------------------------------------------------------- the rules

    /**
     * The teaching week {@code date} falls in, counting from week 1.
     *
     * <p>Deliberately unclamped, unlike {@link ScheduleUtils#currentWeek}: a
     * caller has to be able to tell "week 0" (before term) and "week 19" (after
     * it) from a real teaching week. See the class comment.</p>
     *
     * @return the 1-based week, which may be zero or negative before the term and
     *         past {@link ScheduleUtils#SEMESTER_WEEKS} after it
     */
    public static int rawWeekOf(LocalDate semesterStart, LocalDate date) {
        if (semesterStart == null || date == null) {
            return 0;
        }
        LocalDate firstMonday = ScheduleUtils.mondayOf(semesterStart);
        long days = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, date);
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    /**
     * Whether {@code date} is a teaching day of the semester.
     *
     * <p>Based on the calendar span only. The model has no notion of holidays or
     * of a weekend lesson being moved, so a public holiday inside the term still
     * counts as a teaching day -- that is a limitation of the data, not a rule
     * invented here.</p>
     */
    public static boolean isWithinSemester(LocalDate semesterStart, LocalDate date) {
        if (semesterStart == null || date == null) {
            return false;
        }
        int week = rawWeekOf(semesterStart, date);
        return week >= 1 && week <= ScheduleUtils.SEMESTER_WEEKS;
    }

    /** Whether the feature supports this lead time. */
    public static boolean supportsLeadMinutes(int leadMinutes) {
        return leadMinutes >= MIN_LEAD_MINUTES && leadMinutes <= MAX_LEAD_MINUTES;
    }

    /**
     * The schedule a course rings to, or {@code null} when there is none to use.
     *
     * <p>An empty list means the app has no bell schedules to read from, which is
     * a refusal rather than a reason to fall back to built-in times.</p>
     */
    public static BellSchedule scheduleFor(Course course, List<BellSchedule> schedules) {
        return scheduleFor(course, schedules, null);
    }

    /**
     * The same, under the default schedule the user has chosen.
     *
     * <p>Threaded through rather than read here, because this is the framework-free
     * layer and the choice lives in the store. A caller with no store to read keeps
     * the legacy fallback, which is what it had before the choice existed --
     * whereas a caller that does read the store, the reminder scheduler and the
     * alarm receiver, passes it so the alarm agrees with the card the user is
     * looking at.</p>
     *
     * @param defaultId the user's default schedule id, or {@code null} for the
     *                  legacy fallback
     */
    public static BellSchedule scheduleFor(Course course, List<BellSchedule> schedules,
            String defaultId) {
        if (course == null || schedules == null || schedules.isEmpty()) {
            return null;
        }
        return BellSchedules.resolve(schedules, course.scheduleName, course.location,
                defaultId);
    }

    /**
     * When {@code course} starts on {@code day}.
     *
     * @return the moment, or {@code null} when it cannot be established -- use
     *         {@link #problemWith} to find out why
     */
    public static LocalDateTime startOf(Course course, List<BellSchedule> schedules,
            LocalDate day) {
        return startOf(course, schedules, day, null);
    }

    /** {@link #startOf(Course, List, LocalDate)} under the user's default. */
    public static LocalDateTime startOf(Course course, List<BellSchedule> schedules,
            LocalDate day, String defaultId) {
        if (day == null || problemWith(course, schedules) != null) {
            return null;
        }
        BellSchedule schedule = scheduleFor(course, schedules, defaultId);
        // A minute past 24:00 is allowed by the model (a late lab), and adding it
        // to midnight carries into the next day, which is what it means.
        return day.atStartOfDay().plusMinutes(schedule.startMinute(course.startPeriod));
    }

    /**
     * Why no start time can be computed for {@code course}, or {@code null} when
     * one can.
     *
     * <p>Separate from {@link #startOf} so the caller can report a reason rather
     * than just a missing value.</p>
     */
    public static Skipped problemWith(Course course, List<BellSchedule> schedules) {
        if (course == null) {
            return Skipped.INVALID_PERIOD;
        }
        if (schedules == null || schedules.isEmpty()) {
            return Skipped.NO_SCHEDULES;
        }
        if (!hasUsablePeriods(course)) {
            return Skipped.INVALID_PERIOD;
        }
        BellSchedule schedule = scheduleFor(course, schedules);
        if (schedule == null || course.endPeriod > schedule.periodCount()) {
            // The schedule does not reach this period. BellSchedule#startMinute
            // would clamp to its last period instead of failing, which would be a
            // confident wrong time rather than an honest refusal.
            return Skipped.INVALID_PERIOD;
        }
        return null;
    }

    /**
     * Whether the course's period numbers are self-consistent.
     *
     * <p>Only the numbers themselves -- at least one, ending no earlier than it
     * starts, and inside the app's own ceiling. Whether the applying schedule
     * reaches the last one is asked separately by {@link #problemWith}, against
     * that schedule's own {@link BellSchedule#periodCount()}, because that is the
     * only place the real day length is known. Checking the default fifteen here
     * instead used to make the schedule-aware test unreachable: a course on period
     * 18 of a twenty-period table was refused before anything looked at the table
     * it belonged to.</p>
     */
    private static boolean hasUsablePeriods(Course course) {
        return course.startPeriod >= 1
                && course.endPeriod >= course.startPeriod
                && course.endPeriod <= ScheduleUtils.MAX_PERIODS;
    }

    // ---------------------------------------------------------- calculation

    /**
     * The reminders for the classes on one day.
     *
     * @param courses    the active semester's courses
     * @param semester   the semester the day is judged against
     * @param schedules  the bell schedules to read start times from
     * @param day        the calendar day to look at
     * @param leadMinutes how long before the class to warn
     * @param now        the current moment, supplied by the caller
     * @return the reminders that are still ahead, plus the reasons for the rest
     */
    public static Plan onDate(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, LocalDate day, int leadMinutes,
            LocalDateTime now) {
        return onDate(courses, semester, schedules, day, leadMinutes, now, null);
    }

    /** {@link #onDate} under the user's default schedule. */
    public static Plan onDate(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, LocalDate day, int leadMinutes,
            LocalDateTime now, String defaultId) {
        List<Reminder> reminders = new ArrayList<>();
        List<SkippedCourse> skipped = new ArrayList<>();
        if (day == null || now == null) {
            return new Plan(reminders, skipped);
        }
        if (!supportsLeadMinutes(leadMinutes)) {
            skipped.add(new SkippedCourse(null, day, Skipped.UNSUPPORTED_LEAD_TIME));
            return new Plan(reminders, skipped);
        }
        if (semester == null || !isWithinSemester(semester.start(), day)) {
            skipped.add(new SkippedCourse(null, day, Skipped.OUTSIDE_SEMESTER));
            return new Plan(reminders, skipped);
        }

        int week = rawWeekOf(semester.start(), day);
        int weekday = day.getDayOfWeek().getValue();
        List<Course> today = CourseQuery.onDay(
                courses == null ? Collections.<Course>emptyList() : courses, week, weekday);

        // One reminder per course per day. The stored courses should already be
        // unique by id, but a hand-edited file or an import can break that, and a
        // duplicate would otherwise become a second alarm and a second
        // notification for one class.
        Set<Long> seen = new HashSet<>();
        for (Course course : today) {
            if (!seen.add(course.id)) {
                continue;
            }
            Skipped problem = problemWith(course, schedules);
            if (problem != null) {
                skipped.add(new SkippedCourse(course, day, problem));
                continue;
            }
            LocalDateTime startAt = startOf(course, schedules, day, defaultId);
            LocalDateTime triggerAt = startAt.minusMinutes(leadMinutes);
            if (!triggerAt.isAfter(now)) {
                // Covers both "already gone" and "the lead window has opened but
                // the class has not started"; warning now would be a surprise, so
                // the occurrence is dropped rather than fired late.
                skipped.add(new SkippedCourse(course, day, Skipped.TRIGGER_IN_THE_PAST));
                continue;
            }
            reminders.add(new Reminder(course.id, course.name, course.teacher,
                    course.location,
                    scheduleFor(course, schedules, defaultId).name(), course.color,
                    day, startAt, triggerAt, leadMinutes));
        }
        reminders.sort(BY_TRIGGER);
        return new Plan(reminders, skipped);
    }

    /**
     * The reminders for a run of days, earliest first.
     *
     * <p>{@code from} is the first <em>class</em> day considered, not the first
     * day a reminder may fire on: a class at 00:10 with a 15-minute lead is
     * triggered the evening before, and is found by looking at the class day.</p>
     *
     * @param days         how many calendar days to walk, starting at {@code from}
     * @param maxReminders the cap on returned reminders; zero or less means no cap
     */
    public static Plan upcoming(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, LocalDate from, int days, int leadMinutes,
            LocalDateTime now, int maxReminders) {
        return upcoming(courses, semester, schedules, from, days, leadMinutes, now,
                maxReminders, null);
    }

    /** {@link #upcoming} under the user's default schedule. */
    public static Plan upcoming(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, LocalDate from, int days, int leadMinutes,
            LocalDateTime now, int maxReminders, String defaultId) {
        List<Reminder> reminders = new ArrayList<>();
        List<SkippedCourse> skipped = new ArrayList<>();
        if (from == null || now == null || days <= 0) {
            return new Plan(reminders, skipped);
        }
        if (!supportsLeadMinutes(leadMinutes)) {
            skipped.add(new SkippedCourse(null, from, Skipped.UNSUPPORTED_LEAD_TIME));
            return new Plan(reminders, skipped);
        }
        if (semester == null) {
            skipped.add(new SkippedCourse(null, from, Skipped.OUTSIDE_SEMESTER));
            return new Plan(reminders, skipped);
        }

        boolean anyDayOutside = false;
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = from.plusDays(offset);
            if (!isWithinSemester(semester.start(), day)) {
                anyDayOutside = true;
                continue;
            }
            Plan dayPlan = onDate(courses, semester, schedules, day, leadMinutes, now,
                    defaultId);
            reminders.addAll(dayPlan.reminders());
            skipped.addAll(dayPlan.skipped());
        }
        if (anyDayOutside) {
            // One line for the whole run rather than one per day: the caller wants
            // to know the range runs past the term, not seven copies of it.
            skipped.add(new SkippedCourse(null, from, Skipped.OUTSIDE_SEMESTER));
        }

        reminders.sort(BY_TRIGGER);
        if (maxReminders > 0 && reminders.size() > maxReminders) {
            reminders = new ArrayList<>(reminders.subList(0, maxReminders));
        }
        return new Plan(reminders, skipped);
    }

    /**
     * The reminder for one class on one day, whether or not its moment has passed.
     *
     * <p>{@link #onDate} answers "what is still ahead", which is the wrong question
     * when an alarm has just gone off: at that instant the reminder it belongs to
     * has by definition already passed, and would be filtered out. This asks the
     * other question -- what does this class look like <em>now</em> -- so the
     * notification is built from the timetable as it currently stands rather than
     * from a copy taken when the alarm was registered. A class that has since been
     * moved, shortened or deleted simply has no answer here, which is how a stale
     * alarm ends up doing nothing instead of announcing a class that is not
     * happening.</p>
     *
     * @param courseId the class to look for
     * @param day      the nominal class day, as recorded when the alarm was set
     * @return the reminder, or null when the class does not run that day, its time
     *         cannot be established, or the request is not one this planner serves
     */
    public static Reminder forCourseOnDay(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, long courseId, LocalDate day, int leadMinutes) {
        return forCourseOnDay(courses, semester, schedules, courseId, day, leadMinutes,
                null);
    }

    /** {@link #forCourseOnDay} under the user's default schedule. */
    public static Reminder forCourseOnDay(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, long courseId, LocalDate day, int leadMinutes,
            String defaultId) {
        if (courses == null || semester == null || day == null
                || !supportsLeadMinutes(leadMinutes)
                || !isWithinSemester(semester.start(), day)) {
            return null;
        }
        int week = rawWeekOf(semester.start(), day);
        int weekday = day.getDayOfWeek().getValue();
        for (Course course : CourseQuery.onDay(courses, week, weekday)) {
            if (course.id != courseId) {
                continue;
            }
            if (problemWith(course, schedules) != null) {
                return null;
            }
            LocalDateTime startAt = startOf(course, schedules, day, defaultId);
            return new Reminder(course.id, course.name, course.teacher, course.location,
                    scheduleFor(course, schedules, defaultId).name(), course.color, day,
                    startAt, startAt.minusMinutes(leadMinutes), leadMinutes);
        }
        return null;
    }

    /** {@link #upcoming} with {@link #DEFAULT_DAYS_AHEAD} and the default cap. */
    public static Plan upcoming(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, LocalDate from, int leadMinutes,
            LocalDateTime now) {
        return upcoming(courses, semester, schedules, from, DEFAULT_DAYS_AHEAD,
                leadMinutes, now, DEFAULT_MAX_REMINDERS);
    }

    /** The human-readable form of a skip reason. */
    public static String text(Skipped reason) {
        if (reason == null) {
            return "";
        }
        switch (reason) {
            case OUTSIDE_SEMESTER:
                return "\u8BE5\u65E5\u671F\u4E0D\u5728\u5B66\u671F\u5185";
            case NO_SCHEDULES:
                return "\u6CA1\u6709\u53EF\u7528\u7684\u4F5C\u606F\u8868";
            case INVALID_PERIOD:
                return "\u8282\u6B21\u8D85\u51FA\u4F5C\u606F\u8868\u8303\u56F4";
            case TRIGGER_IN_THE_PAST:
                return "\u63D0\u9192\u65F6\u523B\u5DF2\u8FC7";
            case UNSUPPORTED_LEAD_TIME:
                return "\u4E0D\u652F\u6301\u7684\u63D0\u524D\u65F6\u95F4";
            default:
                return reason.name();
        }
    }
}
