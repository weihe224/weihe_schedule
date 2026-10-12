package com.weihe.schedule.rule;

import java.time.LocalDate;

import com.weihe.schedule.model.ScheduleUtils;

/**
 * Which week and weekday the timetable should be showing for "today", and
 * whether it is already showing them.
 *
 * <p>Two answers the home screen needs when the user asks to be taken back to
 * now. Both used to be worked out inside the activity, where nothing could check
 * them: the rule that decides whether the "back to today" control is even
 * offering anything is the part most worth testing, and it was the part that had
 * no test.</p>
 *
 * <h2>The week comes from the timetable's own arithmetic</h2>
 *
 * <p>{@link #week} is {@link ScheduleUtils#currentWeek}, not a second opinion
 * about which week a date falls in. That method clamps to
 * {@code [1, SEMESTER_WEEKS]}, so a date before the term reads as week 1 and a
 * date after it reads as the last week -- the app's existing answer for dates
 * outside the term, kept here rather than revisited.</p>
 *
 * <h2>Already there means both halves</h2>
 *
 * <p>{@link #isShowing} compares the week <em>and</em> the weekday. The same
 * weekday of a different week is not today: it is exactly the state the control
 * exists for, and deciding on the weekday alone would hide the way back from
 * anyone who had stepped to another week.</p>
 */
public final class TodayTarget {

    private TodayTarget() {
    }

    /**
     * The week {@code today} falls in, on the timetable's own terms.
     *
     * <p>Clamped by {@link ScheduleUtils#currentWeek}: before the term this is
     * week 1, after it the last week.</p>
     */
    public static int week(LocalDate semesterStart, LocalDate today) {
        return ScheduleUtils.currentWeek(semesterStart, today);
    }

    /** The weekday to show, 1 (Monday) to 7 (Sunday), as the screen numbers them. */
    public static int day(LocalDate today) {
        return today.getDayOfWeek().getValue();
    }

    /**
     * Whether the screen is already showing this week and today.
     *
     * <p>When it is, the "back to today" control has nothing to offer, so the
     * screen quiets it; when it is not -- a different day, a different week, or
     * both -- it is the way back.</p>
     */
    public static boolean isShowing(int selectedWeek, int selectedDay,
            LocalDate semesterStart, LocalDate today) {
        return selectedWeek == week(semesterStart, today) && selectedDay == day(today);
    }
}
