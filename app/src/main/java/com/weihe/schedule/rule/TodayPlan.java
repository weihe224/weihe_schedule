package com.weihe.schedule.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * Today, as a home-screen widget would show it: what is on, what is next, what
 * comes after.
 *
 * <p>A value object rather than a screen's worth of logic. The widget answers four
 * questions -- is a class running, when does the next one start, what follows it,
 * and what should be said when there is nothing -- and each of those has a rule
 * that is worth testing without a launcher: the teaching week, the odd/even
 * parity, and which bell schedule a venue rings to are all things that have been
 * got wrong elsewhere in this app before.</p>
 *
 * <p>Nothing here is re-implemented. Which courses run today is
 * {@link CourseQuery#onDay}; the week is {@link ReminderPlan#rawWeekOf}; a venue's
 * times are {@link BellSchedules#resolve}; a period's clock is
 * {@link BellSchedule#startMinute}. A second copy of any of those would eventually
 * disagree with the timetable screen, and the disagreement would show up as a
 * widget naming a class that is not happening.</p>
 *
 * <h2>What it deliberately is not</h2>
 *
 * <p>It is not {@link ReminderPlan}. That answers "which alarms are still ahead",
 * which drops everything whose lead time has passed and says nothing about a class
 * already under way. A widget has to show the class the student is sitting in and
 * the ones still to come, so it walks the whole day and then decides which of them
 * is the headline.</p>
 */
public final class TodayPlan {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    /** Minutes past midnight the widget treats as the start of a new day. */
    static final int MINUTE_AFTER_MIDNIGHT = 5;

    /** Earliest first; ties fall back to id so two views of a day agree. */
    private static final Comparator<Session> BY_START =
            Comparator.comparing(Session::startAt)
                    .thenComparingLong(Session::courseId);

    /** What the widget should be saying right now. */
    public enum State {
        /** A class is running. */
        IN_CLASS,
        /** Nothing is running and something starts later today. */
        UPCOMING,
        /** The day is a teaching day but nothing is left. */
        NO_CLASS_TODAY,
        /** There is no timetable at all: no semester, or no courses in it. */
        NO_TIMETABLE,
        /** The date is before or after the term. */
        OUTSIDE_TERM
    }

    /** One class placed on a day: a course plus the wall-clock times it runs at. */
    public static final class Session {

        private final long courseId;
        private final String name;
        private final String location;
        private final String teacher;
        private final String scheduleName;
        private final int startPeriod;
        private final int endPeriod;
        private final int color;
        private final LocalDateTime startAt;
        private final LocalDateTime endAt;

        Session(Course course, String scheduleName, LocalDateTime startAt,
                LocalDateTime endAt) {
            this.courseId = course.id;
            this.name = course.name == null ? "" : course.name;
            this.location = course.location == null ? "" : course.location;
            this.teacher = course.teacher == null ? "" : course.teacher;
            this.scheduleName = scheduleName == null ? "" : scheduleName;
            this.startPeriod = course.startPeriod;
            this.endPeriod = course.endPeriod;
            this.color = course.color;
            this.startAt = startAt;
            this.endAt = endAt;
        }

        public long courseId() {
            return courseId;
        }

        public String name() {
            return name;
        }

        public String location() {
            return location;
        }

        public String teacher() {
            return teacher;
        }

        /** The bell schedule the times came from. */
        public String scheduleName() {
            return scheduleName;
        }

        public int startPeriod() {
            return startPeriod;
        }

        public int endPeriod() {
            return endPeriod;
        }

        /** The course colour, so the widget can carry the same cue as the cards. */
        public int color() {
            return color;
        }

        public LocalDateTime startAt() {
            return startAt;
        }

        public LocalDateTime endAt() {
            return endAt;
        }

        /** {@code 08:15 - 08:55}. */
        public String timeRange() {
            return CLOCK.format(startAt) + " - " + CLOCK.format(endAt);
        }

        /** {@code 第 3-4 节}, or {@code 第 3 节} for a single period. */
        public String periodLabel() {
            if (endPeriod <= startPeriod) {
                return "\u7B2C " + startPeriod + " \u8282";
            }
            return "\u7B2C " + startPeriod + "-" + endPeriod + " \u8282";
        }

        public boolean started(LocalDateTime now) {
            return now != null && !now.isBefore(startAt);
        }

        /** Running now: started and not yet over. */
        public boolean inProgress(LocalDateTime now) {
            return now != null && !now.isBefore(startAt) && now.isBefore(endAt);
        }

        /** Over: the end has been reached. */
        public boolean ended(LocalDateTime now) {
            return now != null && !now.isBefore(endAt);
        }

        @Override
        public String toString() {
            return name + " " + timeRange();
        }
    }

    private final State state;
    private final LocalDate day;
    private final String semesterLabel;
    private final int week;
    private final String parity;
    private final Session current;
    private final Session next;
    private final Session previous;
    private final List<Session> following;
    private final int laterToday;
    /** Every class placed on the day, including the ones already over. */
    private final int todayCount;
    /** The moment this plan describes, so every derived answer uses one clock. */
    private final LocalDateTime now;

    private TodayPlan(State state, LocalDate day, String semesterLabel, int week,
            String parity, Session current, Session next, Session previous,
            List<Session> following, int laterToday, int todayCount,
            LocalDateTime now) {
        this.state = state;
        this.day = day;
        this.semesterLabel = semesterLabel;
        this.week = week;
        this.parity = parity;
        this.current = current;
        this.next = next;
        this.previous = previous;
        this.following = Collections.unmodifiableList(following);
        this.laterToday = laterToday;
        this.todayCount = todayCount;
        this.now = now;
    }

    /**
     * Works out what the widget shows.
     *
     * @param courses    the active semester's courses
     * @param semester   the semester the day belongs to, or {@code null}
     * @param schedules  the bell schedules to read times from
     * @param defaultId  the user's default schedule, or {@code null}
     * @param now        the moment to describe; never null
     * @param maxFollowing how many classes after the headline to return, at most
     * @return the plan; never null
     */
    public static TodayPlan of(List<Course> courses, Semester semester,
            List<BellSchedule> schedules, String defaultId, LocalDateTime now,
            int maxFollowing) {
        if (now == null) {
            throw new IllegalArgumentException("a moment is required");
        }
        LocalDate day = now.toLocalDate();
        if (semester == null || courses == null || courses.isEmpty()) {
            return empty(State.NO_TIMETABLE, day, semester, 0, now);
        }
        int week = ReminderPlan.rawWeekOf(semester.start(), day);
        if (!ReminderPlan.isWithinSemester(semester.start(), day)) {
            return empty(State.OUTSIDE_TERM, day, semester, week, now);
        }

        List<Session> today = new ArrayList<>();
        for (Course course : CourseQuery.onDay(courses, week,
                day.getDayOfWeek().getValue())) {
            Session session = place(course, schedules, defaultId, day);
            if (session != null) {
                today.add(session);
            }
        }
        today.sort(BY_START);

        Session current = null;
        for (Session session : today) {
            if (session.inProgress(now)) {
                current = session;
                break;
            }
        }
        Session next = null;
        for (Session session : today) {
            if (session.startAt().isAfter(now)) {
                next = session;
                break;
            }
        }

        // The most recent class that is already over. Only used to fill an otherwise
        // empty second card on the widgets: a card that says "已结束" is honest about
        // a class that happened, where a blank slot looks like a missing timetable.
        Session previous = null;
        for (Session session : today) {
            if (!now.isBefore(session.endAt())) {
                previous = session;
            }
        }

        Session headline = current != null ? current : next;
        List<Session> following = new ArrayList<>();
        int later = 0;
        if (headline != null) {
            int from = today.indexOf(headline) + 1;
            int limit = Math.max(0, maxFollowing);
            for (int index = from; index < today.size(); index++) {
                if (following.size() < limit) {
                    following.add(today.get(index));
                } else {
                    later++;
                }
            }
        }

        State state = current != null ? State.IN_CLASS
                : next != null ? State.UPCOMING : State.NO_CLASS_TODAY;
        return new TodayPlan(state, day, semester.label(), week,
                ScheduleUtils.parityName(week), current, next, previous, following,
                later, today.size(), now);
    }

    private static TodayPlan empty(State state, LocalDate day, Semester semester,
            int week, LocalDateTime now) {
        return new TodayPlan(state, day,
                semester == null ? "" : semester.label(), week,
                week <= 0 ? "" : ScheduleUtils.parityName(week),
                null, null, null, new ArrayList<Session>(), 0, 0, now);
    }

    /**
     * Places one course on the day, or refuses.
     *
     * <p>Refusing is the honest answer when the venue's schedule does not reach the
     * course's last period: {@link BellSchedule#endMinute} clamps, and a clamped
     * time would be a confident wrong one -- the same reasoning as the reminder
     * planner's.</p>
     */
    private static Session place(Course course, List<BellSchedule> schedules,
            String defaultId, LocalDate day) {
        if (course == null || schedules == null || schedules.isEmpty()) {
            return null;
        }
        if (course.startPeriod < 1 || course.endPeriod < course.startPeriod) {
            return null;
        }
        BellSchedule schedule = BellSchedules.resolve(schedules, course.scheduleName,
                course.location, defaultId);
        if (schedule == null || course.endPeriod > schedule.periodCount()) {
            return null;
        }
        LocalDateTime startAt = day.atStartOfDay()
                .plusMinutes(schedule.startMinute(course.startPeriod));
        // A period after midnight is written 24:xx by the model; adding it to
        // midnight carries into the next day, which is what it means.
        LocalDateTime endAt = day.atStartOfDay()
                .plusMinutes(schedule.endMinute(course.endPeriod));
        return new Session(course, schedule.name(), startAt, endAt);
    }

    public State state() {
        return state;
    }

    public LocalDate day() {
        return day;
    }

    /**
     * The moment this plan was built for.
     *
     * <p>Kept so that anything derived from a plan -- a countdown's label, whether a
     * class has started -- is computed against the clock the plan itself used,
     * instead of quietly asking the system for a second, slightly different now.</p>
     */
    public LocalDateTime now() {
        return now;
    }

    /** The semester's own label, or empty when there is no semester. */
    public String semesterLabel() {
        return semesterLabel;
    }

    /** The teaching week, counted from the semester start; may be outside 1..18. */
    public int week() {
        return week;
    }

    /** {@code 单周} or {@code 双周} for this week, or empty outside the term. */
    public String parity() {
        return parity;
    }

    /** The class happening now, or null. */
    public Session current() {
        return current;
    }

    /** The next class to start today, or null. */
    public Session next() {
        return next;
    }

    /** The class the headline describes: what is running, else what is next. */
    public Session headline() {
        return current != null ? current : next;
    }

    /** The classes after the headline, earliest first, at most {@code maxFollowing}. */
    public List<Session> following() {
        return following;
    }

    /**
     * The class that finished most recently today, or null when none has.
     *
     * <p>Not part of "what is next": it exists so a widget with a free second slot can
     * fill it with something true rather than leave a gap that reads as a missing
     * timetable entry.</p>
     */
    public Session previous() {
        return previous;
    }

    /** How many today's classes are neither the headline nor in {@link #following}. */
    public int laterToday() {
        return laterToday;
    }

    /**
     * How many classes the day holds in total, finished ones included.
     *
     * <p>This is what a "see all of today" entry counts. It is deliberately not the
     * length of any list this class exposes: those only describe what is left, and a
     * widget that said "one class today" at four in the afternoon would be lying
     * about the morning.</p>
     */
    public int todayCount() {
        return todayCount;
    }

    /**
     * The moment the widget counts down to: the end of the class in progress, or
     * the start of the next one. Null when there is nothing to count down to.
     */
    public LocalDateTime countdownAt() {
        if (current != null) {
            return current.endAt();
        }
        return next == null ? null : next.startAt();
    }

    /**
     * When the widget next has to be redrawn to stay truthful.
     *
     * <p>Three things change what it should say: the class in progress ending, the
     * next class starting, and midnight moving "today" on. The earliest of those is
     * the one worth waking up for; with nothing on, that is the day rollover. The
     * rollover is a few minutes past midnight rather than exactly at it, because a
     * device that wakes at 00:00:00 and asks "what day is it" is taking part in a
     * race it does not need to run.</p>
     *
     * <p>An alarm scheduled from here is also what keeps the widget correct while
     * the app is closed -- the periodic update the platform allows is half an hour
     * coarse, which would show the wrong class for up to that long.</p>
     */
    public LocalDateTime nextRefreshAt() {
        LocalDateTime next = day.plusDays(1).atTime(0, MINUTE_AFTER_MIDNIGHT);
        if (current != null && current.endAt().isBefore(next)) {
            next = current.endAt();
        }
        if (this.next != null && this.next.startAt().isBefore(next)) {
            next = this.next.startAt();
        }
        return next;
    }

    @Override
    public String toString() {
        return state + " " + day + " current=" + current + " next=" + next
                + " following=" + following.size() + " later=" + laterToday;
    }
}
