package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * A bell schedule while it is being edited.
 *
 * <p>The editor needs to hold half-typed text ("8:", "0", "") that is not yet a
 * valid time, so it cannot talk to the immutable {@link BellSchedule} directly.
 * Everything the screen can get wrong is decided here, in plain Java, which
 * means the rules are covered by JVM tests instead of by typing into the app:</p>
 *
 * <ul>
 *   <li>{@link #problems()} reports every unusable field at once;</li>
 *   <li>{@link #build()} refuses to produce a schedule until the form is clean.</li>
 * </ul>
 *
 * <h2>Periods are a list, not a fixed fifteen</h2>
 *
 * <p>The form grows and shrinks with {@link #addPeriod()} and
 * {@link #removePeriod()}. Nothing here counts to fifteen: the count is the length
 * of the arrays the form is holding, and the only numbers that bound it are
 * {@link BellSchedule#MIN_PERIODS} (one period is a timetable; none is not) and
 * {@link ScheduleUtils#MAX_PERIODS} (a guard against a typo, not a curriculum).</p>
 *
 * <h2>The length lives here too</h2>
 *
 * <p>{@link #PERIOD_MINUTES} is the default. A schedule being edited carries its
 * own {@link #durationMinutes()}, which is what its ends are derived from and what
 * gets stored -- so changing one venue's length cannot move another's bells.</p>
 */
public final class BellScheduleEditor {

    /** Why a field cannot be used. */
    public enum Reason {
        /** The name is blank. */
        NAME_REQUIRED,
        /** A time field is not a time. */
        TIME_INVALID,
        /** A period ends before it starts. */
        INVERTED,
        /** Another schedule already carries this name. */
        NAME_TAKEN,
        /** The period length is not a usable number of minutes. */
        DURATION_INVALID,
        /** The day would have to be shorter than one period. */
        TOO_FEW_PERIODS,
        /** The day would reach past {@link ScheduleUtils#MAX_PERIODS}. */
        TOO_MANY_PERIODS
    }

    /** One complaint about the form. */
    public static final class Problem {
        private final int period;
        private final Reason reason;

        Problem(int period, Reason reason) {
            this.period = period;
            this.reason = reason;
        }

        /** 1-based period number, or 0 for a problem with the schedule itself. */
        public int period() {
            return period;
        }

        public Reason reason() {
            return reason;
        }

        @Override
        public String toString() {
            return (period == 0 ? "schedule" : "period " + period) + "/" + reason;
        }
    }

    private final String originalName;
    private final List<String> existingNames;
    /**
     * Start and end text per period, two entries each, in that order.
     *
     * <p>A {@code List} rather than an array because the form adds and removes
     * rows: the count is the length, and growing an array by hand on every "add a
     * period" tap is the kind of bookkeeping that goes wrong at the boundary.</p>
     */
    private final List<String> texts = new ArrayList<>();
    private String name;
    private String keywords;
    private String durationText;

    /**
     * @param source         schedule to start from
     * @param existingNames  names of every other stored schedule, for the
     *                       duplicate check; may be empty
     */
    public BellScheduleEditor(BellSchedule source, List<String> existingNames) {
        this.originalName = source.name();
        this.name = source.name();
        this.keywords = String.join("\uff0c", source.locationKeywords());
        this.durationText = String.valueOf(source.durationMinutes());
        this.existingNames = existingNames == null
                ? new ArrayList<String>() : new ArrayList<>(existingNames);
        for (int period = 1; period <= source.periodCount(); period++) {
            texts.add(BellSchedule.label(source.startMinute(period)));
            texts.add(BellSchedule.label(source.endMinute(period)));
        }
    }

    /** A blank schedule of {@link ScheduleUtils#PERIODS_PER_DAY} periods, derived from the standard one. */
    public static BellScheduleEditor blank(List<String> existingNames) {
        return new BellScheduleEditor(BellSchedules.standard(), existingNames);
    }

    /** The name the schedule had when editing started. */
    public String originalName() {
        return originalName;
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        name = value == null ? "" : value;
    }

    public String keywords() {
        return keywords == null ? "" : keywords;
    }

    public void setKeywords(String value) {
        keywords = value == null ? "" : value;
    }

    /** Raw text of the period-length field, as typed. Never null. */
    public String durationText() {
        return durationText == null ? "" : durationText;
    }

    public void setDurationText(String value) {
        durationText = value == null ? "" : value;
    }

    /**
     * The period length the field describes, or {@code null} when it is not a
     * usable one.
     *
     * <p>Strict, unlike the model's own clamp: the field is what the user is
     * looking at, and silently turning a typed {@code 0} into forty would save
     * something nobody asked for. {@link #problems()} reports the rejection
     * instead.</p>
     */
    public Integer durationMinutes() {
        String trimmed = durationText().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(trimmed);
        } catch (NumberFormatException notANumber) {
            return null;
        }
        if (minutes < BellSchedule.MIN_PERIOD_MINUTES
                || minutes > BellSchedule.MAX_PERIOD_MINUTES) {
            return null;
        }
        return Integer.valueOf(minutes);
    }

    /**
     * The length to compute ends with, defaulting when the field is unusable.
     *
     * <p>Used only to draw a suggestion while the user is still typing. What is
     * saved goes through {@link #build()}, which insists on a usable value.</p>
     */
    private int effectiveDurationMinutes() {
        Integer minutes = durationMinutes();
        return minutes == null
                ? BellSchedule.DEFAULT_PERIOD_MINUTES : minutes.intValue();
    }

    /**
     * The length as a number to show in a sentence.
     *
     * <p>Falls back to the default while the field is mid-edit, so a warning that
     * quotes "each period is N minutes" still reads as a sentence when the user has
     * cleared the box.</p>
     */
    public String effectiveDurationText() {
        return String.valueOf(effectiveDurationMinutes());
    }

    public int periodCount() {
        return texts.size() / 2;
    }

    /** Start text of {@code period} (1-based), never null. */
    public String startText(int period) {
        return textAt((period - 1) * 2);
    }

    /** End text of {@code period} (1-based), never null. */
    public String endText(int period) {
        return textAt((period - 1) * 2 + 1);
    }

    /**
     * The end this period has: its start plus {@link #durationMinutes()}, or empty
     * when the start cannot be read.
     *
     * <p>What the screen shows in the end column, and what {@link #build()} stores.
     * The end is not independently editable -- one schedule has one length, and a
     * table whose rows each disagreed with it would have no length at all -- so this
     * is a derived value rather than a suggestion, and the two must agree.</p>
     */
    public String suggestedEndText(int period) {
        Integer start = BellSchedule.parseLoose(startText(period));
        return start == null ? ""
                : BellSchedule.label(BellSchedule.endOf(start.intValue(),
                        effectiveDurationMinutes()));
    }

    public void setStartText(int period, String value) {
        setTextAt((period - 1) * 2, value);
    }

    /**
     * Kept so the stored shape of a period is unchanged, and used by a restored draft
     * to put a row back exactly as the user saw it.
     *
     * <p>No longer what an end comes from: {@link #build()} derives every end from
     * its start and the schedule's length, and {@link #problems(boolean)} no longer
     * judges this field, so writing to it changes what the form displays and nothing
     * that is saved. A table that arrives with ends of its own -- an old schedule, a
     * restored draft -- is therefore read correctly and re-derived on save.</p>
     */
    public void setEndText(int period, String value) {
        setTextAt((period - 1) * 2 + 1, value);
    }

    /**
     * Appends a period to the end of the form.
     *
     * <p>The new row starts at the previous period's end, which is the one guess
     * that is usually right: a school that adds a period adds it straight after the
     * last one. The row is appended and nothing else is touched -- no existing
     * start moves, no number is renumbered, and no course is consulted. That
     * matters because this runs on every tap and none of it is committed until the
     * save button is pressed.</p>
     *
     * @return the 1-based number of the new period, or 0 when the form is already
     *         at {@link ScheduleUtils#MAX_PERIODS}
     */
    public int addPeriod() {
        if (periodCount() >= ScheduleUtils.MAX_PERIODS) {
            return 0;
        }
        String next;
        if (periodCount() == 0) {
            next = BellSchedule.label(BellSchedules.FIRST_PERIOD_START);
        } else {
            int last = periodCount();
            Integer lastEnd = BellSchedule.parseLoose(endText(last));
            // Fall back to the start of the last period when its end cannot be
            // read, so a half-filled form still gets a plausible time rather than
            // an empty field the user has to fill before anything else works.
            Integer lastStart = BellSchedule.parseLoose(startText(last));
            int from = lastEnd != null ? lastEnd.intValue()
                    : lastStart != null ? lastStart.intValue()
                    : BellSchedules.FIRST_PERIOD_START;
            next = BellSchedule.label(from);
        }
        texts.add(next);
        texts.add(BellSchedule.label(
                BellSchedule.endOf(BellSchedule.parseLoose(next).intValue(),
                        effectiveDurationMinutes())));
        return periodCount();
    }

    /**
     * Removes the last period of the form.
     *
     * <p>Only the last one. Deleting from the middle would renumber every row
     * below it, and a course stores its period as a number -- so a timetable edited
     * that way would silently move lessons, which is a far worse outcome than
     * making the user delete from the bottom.</p>
     *
     * @return true when a period was removed; false when there is only one left
     */
    public boolean removePeriod() {
        if (periodCount() <= BellSchedule.MIN_PERIODS) {
            return false;
        }
        texts.remove(texts.size() - 1);
        texts.remove(texts.size() - 1);
        return true;
    }

    /** The keywords as stored: split on Chinese and ASCII commas, blanks dropped. */
    public List<String> keywordList() {
        List<String> result = new ArrayList<>();
        for (String part : keywords().split("[,;\uff0c\uff1b\u3001]+")) {
            String keyword = part.trim();
            if (!keyword.isEmpty()) {
                result.add(keyword);
            }
        }
        return result;
    }

    /**
     * Every reason the form cannot be saved, in the order the rows are read.
     *
     * @param requireAllPeriods whether periods without any time are a problem;
     *        false while the user is still filling the form in
     */
    public List<Problem> problems(boolean requireAllPeriods) {
        List<Problem> problems = new ArrayList<>();
        String trimmedName = name().trim();
        if (trimmedName.isEmpty()) {
            problems.add(new Problem(0, Reason.NAME_REQUIRED));
        } else if (isNameTaken(trimmedName)) {
            problems.add(new Problem(0, Reason.NAME_TAKEN));
        }

        if (durationMinutes() == null) {
            problems.add(new Problem(0, Reason.DURATION_INVALID));
        }
        if (periodCount() < BellSchedule.MIN_PERIODS) {
            problems.add(new Problem(0, Reason.TOO_FEW_PERIODS));
        } else if (periodCount() > ScheduleUtils.MAX_PERIODS) {
            problems.add(new Problem(0, Reason.TOO_MANY_PERIODS));
        }

        for (int period = 1; period <= periodCount(); period++) {
            String startValue = startText(period).trim();
            String endValue = endText(period).trim();
            if (startValue.isEmpty() && endValue.isEmpty()) {
                if (requireAllPeriods) {
                    problems.add(new Problem(period, Reason.TIME_INVALID));
                }
                continue;
            }
            Integer start = BellSchedule.parseLoose(startValue);
            Integer end = BellSchedule.parseLoose(endValue);
            if (start == null || end == null) {
                problems.add(new Problem(period, Reason.TIME_INVALID));
                continue;
            }
            // The end field is not an input any more, but it is still what the row
            // shows, and the screen keeps it equal to start-plus-length on every
            // change -- so an end that reads backwards means the two have come apart,
            // which is worth saying rather than saving over. A restored draft and an
            // older stored table both travel through here, and a row whose end was
            // left stale by either is reported instead of quietly re-derived.
            if (end.intValue() <= start.intValue()) {
                problems.add(new Problem(period, Reason.INVERTED));
                continue;
            }
        }
        return problems;
    }

    /**
     * Periods that start before the previous one ends.
     *
     * <p>Not a reason to refuse a save. A real timetable can do this: this
     * school rings period 3 at 9:55-10:35 and period 4 at 10:15-10:55 in the same
     * building, because the periods are not one queue. The editor says so rather
     * than blocking, and leaves the judgement to the person who can see the
     * timetable on paper.</p>
     *
     * @return 1-based period numbers whose start precedes the previous end
     */
    public List<Integer> overlappingPeriods() {
        List<Integer> overlaps = new ArrayList<>();
        Integer previousStart = null;
        Integer previousEnd = null;
        for (int period = 1; period <= periodCount(); period++) {
            Integer start = BellSchedule.parseLoose(startText(period).trim());
            Integer end = BellSchedule.parseLoose(endText(period).trim());
            if (start == null || end == null) {
                continue;
            }
            if (previousEnd != null && previousStart != null
                    && start.intValue() < previousEnd.intValue()) {
                overlaps.add(Integer.valueOf(period));
            }
            previousStart = start;
            previousEnd = end;
        }
        return overlaps;
    }

    /** Every reason the form cannot be saved, requiring all periods to be filled. */
    public List<Problem> problems() {
        return problems(true);
    }

    /**
     * The schedule the form describes.
     *
     * <p>Each end is recomputed from its start and the form's length rather than
     * taken from the text the field happens to be showing. The two agree whenever
     * the screen kept them in step, and recomputing is what makes them agree when
     * it did not -- a length typed into the field a moment ago has not yet been
     * applied to fifteen end texts, and this is where that gap closes. It is also
     * what stops a stale end from being stored when the length changed and the
     * user saved without touching any row.</p>
     *
     * @throws IllegalStateException when {@link #problems()} is not empty; call
     *         that first and report the problems to the user
     */
    public BellSchedule build() {
        List<Problem> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("cannot build an invalid schedule: "
                    + problems);
        }
        int durationMinutes = durationMinutes().intValue();
        int[] starts = new int[periodCount()];
        int[] ends = new int[periodCount()];
        for (int period = 1; period <= periodCount(); period++) {
            starts[period - 1] = BellSchedule.parseLoose(startText(period));
            ends[period - 1] = BellSchedule.endOf(starts[period - 1],
                    durationMinutes);
        }
        return new BellSchedule(name().trim(), starts, ends, keywordList(),
                durationMinutes);
    }

    private boolean isNameTaken(String candidate) {
        if (candidate.equals(originalName)) {
            return false;
        }
        for (String existing : existingNames) {
            if (existing != null && existing.trim().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String textAt(int index) {
        if (index < 0 || index >= texts.size()) {
            return "";
        }
        String value = texts.get(index);
        return value == null ? "" : value;
    }

    private void setTextAt(int index, String value) {
        if (index < 0 || index >= texts.size()) {
            return;
        }
        texts.set(index, value == null ? "" : value);
    }
}
