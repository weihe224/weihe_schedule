package com.weihe.schedule.rule;

import java.util.List;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * Everything the course detail screen shows, worked out without a view.
 *
 * <p>A value object rather than a screen full of formatting decisions. The detail
 * page has a lot of small rules in it -- what an empty teacher reads as, how a run
 * of periods is written, when a time must not be shown at all -- and none of those
 * are things a rendered screen can be asked about. Kept here, they run under a
 * plain JVM test.</p>
 *
 * <h2>Two things it refuses to make up</h2>
 *
 * <p>A missing teacher or venue reads as {@link #NOT_FILLED}. An absent field is
 * ordinary -- plenty of courses have no room booked yet -- and a blank gap beside
 * a label looks like a rendering fault.</p>
 *
 * <p>A time it cannot establish reads as {@link #TIME_UNAVAILABLE}, with the
 * reason alongside. The start time comes from the bell schedule the course rings
 * to, through the same {@link BellSchedules#resolve} the timetable uses, and the
 * same check the reminder planner makes: no schedules at all, or a period the
 * chosen schedule does not reach. In either case
 * {@link BellSchedule#timeRange} would still return something -- it clamps -- and
 * that something would be a confident wrong time. A detail page is exactly where
 * a wrong time would be believed.</p>
 */
public final class CourseDetail {

    /** What any field the user has not filled in reads as. */
    public static final String NOT_FILLED = "\u672A\u586B\u5199";

    /** Shown instead of a time that cannot be established. */
    public static final String TIME_UNAVAILABLE = "\u5177\u4F53\u65F6\u95F4\u6682\u4E0D\u53EF\u7528";

    /** Shown as the title when a course somehow has no name. */
    public static final String UNNAMED = "\u672A\u547D\u540D\u8BFE\u7A0B";

    private final boolean present;
    private final long courseId;
    private final String name;
    private final String teacher;
    private final String location;
    private final String semesterLabel;
    private final String semesterRange;
    private final String dayName;
    private final String periodText;
    private final String weekRange;
    private final String parity;
    private final String timeText;
    private final String timeNote;
    private final String scheduleName;
    private final int color;

    private CourseDetail(boolean present, long courseId, String name, String teacher,
            String location, String semesterLabel, String semesterRange, String dayName,
            String periodText, String weekRange, String parity, String timeText,
            String timeNote, String scheduleName, int color) {
        this.present = present;
        this.courseId = courseId;
        this.name = name;
        this.teacher = teacher;
        this.location = location;
        this.semesterLabel = semesterLabel;
        this.semesterRange = semesterRange;
        this.dayName = dayName;
        this.periodText = periodText;
        this.weekRange = weekRange;
        this.parity = parity;
        this.timeText = timeText;
        this.timeNote = timeNote;
        this.scheduleName = scheduleName;
        this.color = color;
    }

    /**
     * Describes a course.
     *
     * @param course    the course, or {@code null} when it is no longer there
     * @param semester  the semester it belongs to, or {@code null} when unknown
     * @param schedules the bell schedules to read a time from
     * @return the description; a null course gives {@link #isPresent()} false
     *         rather than an invented one
     */
    public static CourseDetail of(Course course, Semester semester,
            List<BellSchedule> schedules) {
        return of(course, semester, schedules, null);
    }

    /**
     * The same, under the default schedule the user has chosen.
     *
     * <p>The extra argument exists because this class is framework-free and cannot
     * reach the store that holds the choice. Without it the screen would resolve a
     * venue-less course against the legacy fallback while the timetable card beside
     * it resolved the same course against the user's choice -- the detail page and
     * the timetable disagreeing about what time a lesson starts, which is the one
     * outcome this feature exists to prevent.</p>
     *
     * @param defaultId the user's default schedule id, or {@code null} for "the
     *                  legacy fallback", which is what a caller with no store to
     *                  read has always had
     */
    public static CourseDetail of(Course course, Semester semester,
            List<BellSchedule> schedules, String defaultId) {
        if (course == null) {
            return missing();
        }
        ReminderPlan.Skipped problem = ReminderPlan.problemWith(course, schedules);
        boolean timeKnown = problem == null;
        BellSchedule schedule = timeKnown
                ? BellSchedules.resolve(schedules, course.scheduleName, course.location,
                        defaultId)
                : null;
        return new CourseDetail(true, course.id,
                filled(course.name, UNNAMED),
                filled(course.teacher, NOT_FILLED),
                filled(course.location, NOT_FILLED),
                semester == null ? NOT_FILLED : semester.label(),
                semester == null ? ""
                        : ScheduleUtils.formatDate(semester.start()) + " - "
                                + ScheduleUtils.formatDate(semester.lastDay()),
                ScheduleUtils.dayName(course.dayOfWeek),
                periods(course),
                weekRangeOf(course),
                ScheduleUtils.parityName(course),
                timeKnown ? schedule.timeRange(course.startPeriod, course.endPeriod)
                        : TIME_UNAVAILABLE,
                timeKnown ? "" : ReminderPlan.text(problem),
                timeKnown ? schedule.name() : "",
                course.color);
    }

    /** The description of a course that is not there. */
    public static CourseDetail missing() {
        return new CourseDetail(false, 0L, "", NOT_FILLED, NOT_FILLED, NOT_FILLED, "",
                "", "", "", "", TIME_UNAVAILABLE, "", "", 0);
    }

    /** Whether this describes a real course. */
    public boolean isPresent() {
        return present;
    }

    public long courseId() {
        return courseId;
    }

    public String name() {
        return name;
    }

    public String teacher() {
        return teacher;
    }

    public String location() {
        return location;
    }

    /** The semester's own label, or {@link #NOT_FILLED} when it is unknown. */
    public String semesterLabel() {
        return semesterLabel;
    }

    /** The semester's first and last day, or empty when it is unknown. */
    public String semesterRange() {
        return semesterRange;
    }

    /** Monday to Sunday, as the timetable writes it. */
    public String dayName() {
        return dayName;
    }

    /** {@code 第 3-4 节}, or {@code 第 3 节} for a single period. */
    public String periodText() {
        return periodText;
    }

    /** The teaching weeks the course runs in, without the parity. */
    public String weekRange() {
        return weekRange;
    }

    /** Every week, odd weeks or even weeks. */
    public String parity() {
        return parity;
    }

    /** {@code 09:55 - 11:15}, or {@link #TIME_UNAVAILABLE}. */
    public String timeText() {
        return timeText;
    }

    /** Why the time is unavailable, or empty when it is known. */
    public String timeNote() {
        return timeNote;
    }

    /** The bell schedule the time came from, or empty when there is no time. */
    public String scheduleName() {
        return scheduleName;
    }

    /** Whether the start and end times could be established at all. */
    public boolean timeKnown() {
        return present && timeNote.isEmpty();
    }

    public int color() {
        return color;
    }

    /** The one-line summary: {@code 周一 · 第 3-4 节}. */
    public String headline() {
        if (!present) {
            return "";
        }
        return dayName + " \u00B7 " + periodText;
    }

    /** {@code 第1-16周 · 每周}, the same wording the timetable cards use. */
    public String weekRule() {
        if (!present) {
            return "";
        }
        return weekRange + " \u00B7 " + parity;
    }

    // ------------------------------------------------------------- helpers

    private static String filled(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String periods(Course course) {
        if (course.startPeriod == course.endPeriod) {
            return "\u7B2C " + course.startPeriod + " \u8282";
        }
        return "\u7B2C " + course.startPeriod + "-" + course.endPeriod + " \u8282";
    }

    private static String weekRangeOf(Course course) {
        if (course.startWeek == course.endWeek) {
            return "\u7B2C" + course.startWeek + "\u5468";
        }
        return "\u7B2C" + course.startWeek + "-" + course.endWeek + "\u5468";
    }
}
