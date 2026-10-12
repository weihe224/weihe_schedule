package com.weihe.schedule.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class ScheduleUtils {
    public static final int SEMESTER_WEEKS = 18;

    /**
     * Periods in a day for a schedule that has not chosen otherwise.
     *
     * <p>Fifteen is this school's day, and it is what every stored schedule
     * without a length of its own already has. It is the default rather than the
     * ceiling: {@link #MAX_PERIODS} is what stops a timetable growing without
     * bound.</p>
     */
    public static final int PERIODS_PER_DAY = 15;

    /**
     * Most periods a day may be set to.
     *
     * <p>A guard rail, not a curriculum. A school that runs a morning, an
     * afternoon and an evening session can legitimately reach the low twenties;
     * thirty leaves room for that while keeping the editor's list, the week grid
     * and the period spinners to a size a person can still scroll. Anything past
     * it is far more likely to be a typo than a timetable.</p>
     */
    public static final int MAX_PERIODS = 30;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d", Locale.CHINA);

    private static final DateTimeFormatter FULL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy\u5E74M\u6708d\u65E5", Locale.CHINA);

    private ScheduleUtils() {
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static int currentWeek(LocalDate semesterStart, LocalDate today) {
        LocalDate firstMonday = mondayOf(semesterStart);
        long days = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, today);
        int week = (int) Math.floorDiv(days, 7L) + 1;
        return Math.max(1, Math.min(SEMESTER_WEEKS, week));
    }

    public static LocalDate weekStart(LocalDate semesterStart, int week) {
        return mondayOf(semesterStart).plusWeeks(week - 1L);
    }

    /**
     * The calendar date of {@code day} (1 = Monday) inside {@code week}
     * (1-based).
     *
     * <p>Both arguments are clamped to their valid range, matching the
     * convention already used by {@link #dayName(int)} and
     * {@link #currentWeek(LocalDate, LocalDate)}, so a caller can never compute
     * a date outside the week it asked for.</p>
     */
    public static LocalDate dateOf(LocalDate semesterStart, int week, int day) {
        int safeWeek = Math.max(1, Math.min(SEMESTER_WEEKS, week));
        int safeDay = Math.max(1, Math.min(7, day));
        return weekStart(semesterStart, safeWeek).plusDays(safeDay - 1L);
    }

    public static boolean occursInWeek(Course course, int week) {
        if (week < course.startWeek || week > course.endWeek) {
            return false;
        }
        if (course.parity == Course.PARITY_ODD) {
            return week % 2 == 1;
        }
        if (course.parity == Course.PARITY_EVEN) {
            return week % 2 == 0;
        }
        return true;
    }

    public static String parityName(int week) {
        return week % 2 == 1 ? "\u5355\u5468" : "\u53CC\u5468";
    }

    public static String parityName(Course course) {
        if (course.parity == Course.PARITY_ODD) {
            return "\u5355\u5468";
        }
        if (course.parity == Course.PARITY_EVEN) {
            return "\u53CC\u5468";
        }
        return "\u6BCF\u5468";
    }

    public static String weekRule(Course course) {
        String range = course.startWeek == course.endWeek
                ? "\u7B2C" + course.startWeek + "\u5468"
                : "\u7B2C" + course.startWeek + "-"
                        + course.endWeek + "\u5468";
        return range + " \u00B7 " + parityName(course);
    }

    public static String dayName(int day) {
        String[] names = {
                "\u5468\u4E00", "\u5468\u4E8C", "\u5468\u4E09",
                "\u5468\u56DB", "\u5468\u4E94", "\u5468\u516D", "\u5468\u65E5"
        };
        return names[Math.max(1, Math.min(7, day)) - 1];
    }

    public static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }

    /** Long form used where a date is read on its own, for example in a dialog. */
    public static String formatFullDate(LocalDate date) {
        return date == null ? "" : FULL_DATE_FORMAT.format(date);
    }

    /**
     * Reads {@code yyyy-MM-dd}.
     *
     * @return the date, or {@code null} when the text is not one
     */
    public static LocalDate parseDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (RuntimeException notADate) {
            return null;
        }
    }

    /**
     * The span of a run of periods under the standard bell schedule.
     *
     * <p>Kept for callers that do not care which venue applies, and it is what
     * the tests pin. Anything that shows the time of a real course asks
     * {@link com.weihe.schedule.CourseRepository#timeRangeOf}, because a course in the lab building
     * does not ring with the standard schedule -- which is also why the week
     * overview shows no clock times at all.</p>
     */
    public static String periodTime(int startPeriod, int endPeriod) {
        return BellSchedules.standard().timeRange(startPeriod, endPeriod);
    }

    /** Period labels ("1" .. "15"), used to build the period pickers. */
    public static String[] periodLabels() {
        return periodLabels(PERIODS_PER_DAY);
    }

    /**
     * Period labels ("1" .. {@code count}), used to build the period pickers.
     *
     * <p>The count comes from the schedule that will actually apply, so a course
     * in a venue whose day runs to twenty periods is offered twenty and not the
     * school's default fifteen. Clamped to {@link #MAX_PERIODS}: a caller holding a
     * damaged count gets a short list instead of a spinner with ten thousand
     * entries.</p>
     */
    public static String[] periodLabels(int count) {
        int periods = Math.max(1, Math.min(MAX_PERIODS, count));
        String[] labels = new String[periods];
        for (int i = 0; i < periods; i++) {
            labels[i] = String.valueOf(i + 1);
        }
        return labels;
    }

    /**
     * The Monday-to-Sunday dates of a week, as {@code 10/5 - 10/11}.
     *
     * <p>Used by the week picker, where a bare week number does not say enough:
     * "第 7 周" and "第 8 周" are indistinguishable until you see the dates.</p>
     */
    public static String weekRange(LocalDate semesterStart, int week) {
        LocalDate monday = weekStart(semesterStart, week);
        return formatDate(monday) + " - " + formatDate(monday.plusDays(6));
    }

    /**
     * How many columns a grid of every week should use.
     *
     * <p>Chosen so the grid is as square as the screen allows: a full row is
     * quicker to scan than a long list, and four across keeps each cell wide
     * enough for a date range on a small phone. A short semester gets fewer
     * columns rather than a sparse grid.</p>
     */
    public static int weekPickerColumns(int weeks) {
        if (weeks <= 4) {
            return 2;
        }
        if (weeks <= 9) {
            return 3;
        }
        return 4;
    }
}
