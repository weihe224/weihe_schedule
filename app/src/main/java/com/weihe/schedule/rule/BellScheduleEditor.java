package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;

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
        NAME_TAKEN
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
    private final String[] texts;
    private final List<String> existingNames;
    private String name;
    private String keywords;

    /**
     * @param source         schedule to start from
     * @param existingNames  names of every other stored schedule, for the
     *                       duplicate check; may be empty
     */
    public BellScheduleEditor(BellSchedule source, List<String> existingNames) {
        this.originalName = source.name();
        this.name = source.name();
        this.keywords = String.join("\uff0c", source.locationKeywords());
        this.texts = new String[source.periodCount() * 2];
        this.existingNames = existingNames == null
                ? new ArrayList<String>() : new ArrayList<>(existingNames);
        for (int period = 1; period <= source.periodCount(); period++) {
            texts[(period - 1) * 2] = BellSchedule.label(source.startMinute(period));
            texts[(period - 1) * 2 + 1] = BellSchedule.label(source.endMinute(period));
        }
    }

    /** A blank schedule of {@link BellSchedule#PERIODS} periods, derived from the standard one. */
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

    public int periodCount() {
        return texts.length / 2;
    }

    /** Start text of {@code period} (1-based), never null. */
    public String startText(int period) {
        return textAt((period - 1) * 2);
    }

    /** End text of {@code period} (1-based), never null. */
    public String endText(int period) {
        return textAt((period - 1) * 2 + 1);
    }

    public void setStartText(int period, String value) {
        texts[(period - 1) * 2] = value == null ? "" : value;
    }

    public void setEndText(int period, String value) {
        texts[(period - 1) * 2 + 1] = value == null ? "" : value;
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

        Integer previousEnd = null;
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
            if (end.intValue() <= start.intValue()) {
                problems.add(new Problem(period, Reason.INVERTED));
                continue;
            }
            previousEnd = end;
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
     * @throws IllegalStateException when {@link #problems()} is not empty; call
     *         that first and report the problems to the user
     */
    public BellSchedule build() {
        List<Problem> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("cannot build an invalid schedule: "
                    + problems);
        }
        int[] starts = new int[periodCount()];
        int[] ends = new int[periodCount()];
        for (int period = 1; period <= periodCount(); period++) {
            starts[period - 1] = BellSchedule.parseLoose(startText(period));
            ends[period - 1] = BellSchedule.parseLoose(endText(period));
        }
        return new BellSchedule(name().trim(), starts, ends, keywordList());
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
        String value = texts[index];
        return value == null ? "" : value;
    }
}
