package com.weihe.schedule.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.design.Palette;

/** Parses the timetable layout exported by the school's Excel template. */
public final class TimetableImportParser {
    private static final Pattern COURSE_HEADER = Pattern.compile(
            "^([A-Za-z]{0,8}\\d{2,})\\s*[\\u3010\\[]([^\\u3011\\]]+)[\\u3011\\]].*$");
    private static final Pattern NAMED_HEADER = Pattern.compile(
            "^[\\u3010\\[]([^\\u3011\\]]+)[\\u3011\\]].*$");
    private static final Pattern DETAIL = Pattern.compile(
            "^([0-9\\s,\\uFF0C\\u3001;\\uFF1B\\-\\u2013\\u2014~\\uFF5E\\u81F3]+"
                    + "\\u5468(?:\\u5355\\u53CC\\u5468|\\u5355\\u5468|\\u53CC\\u5468)?)"
                    + "(?:\\s{2,}|\\t+)(.*)$");
    private static final Pattern RANGE_SEPARATOR = Pattern.compile(
            "(?:-|\\u2013|\\u2014|~|\\uFF5E|\\u81F3)+");

    /**
     * How many representative problems a {@link Report} carries.
     *
     * <p>A cap rather than a complete list: the screen shows a handful of examples
     * so the user can find the file's weak spot, and a report that grew with the
     * number of bad lines would be a second copy of the file. Every problem is
     * still <em>counted</em>, so nothing is hidden by the cap -- a file with 200
     * unreadable lines reports 200 and quotes at most five of them.</p>
     */
    public static final int MAX_ISSUES = 5;

    private TimetableImportParser() {
    }

    public static List<Course> parse(TimetableGrid grid)
            throws TimetableImportException {
        return parseWithReport(grid).courses();
    }

    /**
     * Parses a worksheet and reports what could not be turned into a course.
     *
     * <p>Same rules, same order, same result as {@link #parse}: this is the
     * implementation and {@code parse} is the convenience wrapper, so the two can
     * never drift apart. What it adds is a count of the content that was dropped
     * and why -- an import that silently loses rows looks exactly like one that
     * does not, which is the whole reason this exists.</p>
     *
     * <p>What is <em>not</em> counted matters as much. A blank cell, the covered
     * half of a merged cell, a blank line inside a cell and the rows that carry no
     * period number are all part of the layout, not evidence of a broken file, and
     * counting them would make every ordinary export look damaged.</p>
     *
     * @throws TimetableImportException when the worksheet is not a timetable at
     *                                  all -- no weekday header, or no period rows
     */
    public static Report parseWithReport(TimetableGrid grid)
            throws TimetableImportException {
        int headerRow = findHeaderRow(grid);
        if (headerRow < 0) {
            throw new TimetableImportException(
                    "\u672A\u627E\u5230\u201C\u661F\u671F\u4E00\u201D\u7B49\u65E5\u671F\u8868\u5934");
        }

        Map<Integer, Integer> dayColumns = dayColumns(grid, headerRow);
        if (dayColumns.isEmpty()) {
            throw new TimetableImportException("\u672A\u627E\u5230\u661F\u671F\u5217");
        }

        List<PeriodRow> periodRows = new ArrayList<>();
        Collector tooLong = new Collector();
        for (int row = headerRow + 1; row < grid.rowCount(); row++) {
            Integer period = parsePositiveInt(grid.cell(row, 0).trim());
            if (period == null) {
                continue;
            }
            if (period.intValue() > ScheduleUtils.MAX_PERIODS) {
                // Named, not dropped. The row has a period number, so the file does
                // describe a period the app cannot hold -- which is a fact about the
                // file worth telling the user, not a blank line to skip past.
                tooLong.note(Reason.PERIOD_OUT_OF_RANGE, row, 0, 1);
                continue;
            }
            periodRows.add(new PeriodRow(row, period.intValue()));
        }
        if (periodRows.isEmpty()) {
            throw new TimetableImportException("\u672A\u627E\u5230\u8282\u6B21\u884C");
        }
        int lastPeriod = periodRows.get(periodRows.size() - 1).period;

        List<Course> courses = new ArrayList<>();
        Collector collector = new Collector();
        for (Map.Entry<Integer, Integer> dayColumn : dayColumns.entrySet()) {
            int column = dayColumn.getKey();
            int day = dayColumn.getValue();
            for (PeriodRow periodRow : periodRows) {
                // The covered half of a merged cell holds a copy of the same text;
                // only its top-left corner is the course.
                if (!grid.isTopLeft(periodRow.row, column)) {
                    continue;
                }
                String text = grid.cell(periodRow.row, column);
                if (text.trim().isEmpty()) {
                    continue;
                }
                int endPeriod = Math.min(lastPeriod, periodForRow(periodRows,
                        grid.mergedEndRow(periodRow.row, column)));
                parseCell(courses, collector, text, day, periodRow.row,
                        periodRow.period, Math.max(periodRow.period, endPeriod));
            }
        }
        return new Report(courses, collectorMergedWith(collector, tooLong));
    }

    /**
     * The cell scan's collector with the period-number scan's findings folded in.
     *
     * <p>Separate only because the period scan has to finish before the cell scan
     * can run -- the rows it accepts decide what {@code lastPeriod} is. Keeping one
     * collector and passing it in would mean the period scan ran against a collector
     * the cell scan had not been given yet.</p>
     */
    private static Collector collectorMergedWith(Collector main, Collector extra) {
        main.merge(extra);
        return main;
    }

    /**
     * Why a piece of content did not become a course.
     *
     * <p>Four kinds, because they mean four different things to the person holding
     * the file: content that belongs to no course, content that is not a course
     * detail line at all, a detail line whose weeks cannot be read, and a detail
     * line whose teacher and venue were written with a single separator so the
     * venue could not be separated out.</p>
     */
    public enum Reason {
        /** A non-empty line that appears before any course header in its cell. */
        UNATTRIBUTED_LINE,
        /** A non-empty line under a header that is not a week detail line. */
        UNRECOGNISED_DETAIL,
        /** A detail line whose week expression cannot be read. */
        UNREADABLE_WEEKS,
        /** A detail line that yielded one field where teacher and venue were expected. */
        INCOMPLETE_FIELDS,
        /**
         * A row whose period number is past the app's ceiling.
         *
         * <p>Reported rather than skipped. The old behaviour was to ignore any row
         * numbered above the default fifteen, which made a file with a sixteenth
         * period look like an ordinary fifteen-period file whose last row happened
         * to be empty -- the one outcome an import must never produce, because the
         * user cannot tell it from success.</p>
         */
        PERIOD_OUT_OF_RANGE
    }

    /**
     * One piece of content that produced no course, and where it is.
     *
     * <p>Deliberately position and reason only, never the cell's text: the summary
     * is meant to be scannable, and a course cell carries a teacher's name and a
     * venue that the user did not ask to see repeated on a warning screen.</p>
     */
    public static final class Issue {
        private final Reason reason;
        private final int row;
        private final int day;
        private final int line;

        Issue(Reason reason, int row, int day, int line) {
            this.reason = reason;
            this.row = row;
            this.day = day;
            this.line = line;
        }

        public Reason reason() {
            return reason;
        }

        /** The worksheet row, 1-based, as a spreadsheet numbers it. */
        public int row() {
            return row + 1;
        }

        /** Weekday, 1 = Monday, or 0 when the column names no weekday. */
        public int day() {
            return day;
        }

        /** The line inside the cell, 1-based. */
        public int line() {
            return line;
        }

        /** {@code 第 3 行 星期一：周次写法无法识别}. */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append("\u7B2C ").append(row + 1).append(" \u884C");
            if (day > 0) {
                text.append(' ').append(ScheduleUtils.dayName(day));
            }
            return text.append('\uFF1A').append(TimetableImportParser.text(reason))
                    .toString();
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    /**
     * Collects the problems, counting all of them but keeping only a few examples.
     *
     * <p>The first example of each kind is kept before any second example of any
     * kind, so a file whose only fault is one repeated mistake still shows what
     * kind of mistake it is rather than five rows of the same sentence. The counts
     * are unaffected: every problem is tallied whether or not it is quoted.</p>
     */
    private static final class Collector {
        private final List<Issue> firstOfEachKind = new ArrayList<>();
        private final List<Issue> further = new ArrayList<>();
        private final Map<Reason, Integer> counts = new EnumMap<>(Reason.class);
        private int total;

        void note(Reason reason, int row, int day, int line) {
            total++;
            Integer current = counts.get(reason);
            counts.put(reason, Integer.valueOf(current == null ? 1 : current + 1));
            if (current == null) {
                firstOfEachKind.add(new Issue(reason, row, day, line));
            } else if (further.size() < MAX_ISSUES) {
                further.add(new Issue(reason, row, day, line));
            }
        }

        List<Issue> examples() {
            List<Issue> examples = new ArrayList<>(firstOfEachKind);
            for (Issue issue : further) {
                if (examples.size() >= MAX_ISSUES) {
                    break;
                }
                examples.add(issue);
            }
            return examples;
        }

        /**
         * Folds another collector's findings into this one.
         *
         * <p>Used for the period-number scan, which runs before the cell scan and
         * keeps its own tally. Re-noting through {@link #note} rather than copying
         * the lists keeps the "first of each kind" ordering and the example cap
         * working across both sources, which copying would bypass.</p>
         */
        void merge(Collector other) {
            for (Map.Entry<Reason, Integer> entry : other.counts.entrySet()) {
                for (int i = 0; i < entry.getValue().intValue(); i++) {
                    note(entry.getKey(), 0, 0, 0);
                }
            }
        }
    }

    /**
     * What one worksheet produced: the courses, and the content that was dropped.
     *
     * <p>The two counts that matter are kept apart on purpose. A file can hold few
     * courses and many arrangements -- one course meeting in three separate week
     * runs is three arrangements -- and a user comparing the screen against their
     * spreadsheet needs to know which number they are reading.</p>
     */
    public static final class Report {
        private final List<Course> courses;
        private final List<Issue> issues;
        private final Map<Reason, Integer> counts;
        private final int issueCount;
        private final int courseCount;

        Report(List<Course> courses, Collector collector) {
            this.courses = Collections.unmodifiableList(new ArrayList<>(courses));
            this.issues = Collections.unmodifiableList(collector.examples());
            this.counts = new EnumMap<>(collector.counts);
            this.issueCount = collector.total;
            Set<String> names = new LinkedHashSet<>();
            for (Course course : courses) {
                names.add(course.name);
            }
            this.courseCount = names.size();
        }

        /** The arrangements that were recognised, in worksheet order. */
        public List<Course> courses() {
            return courses;
        }

        /** How many arrangements were recognised; one per week run. */
        public int scheduleCount() {
            return courses.size();
        }

        /** How many distinct course names those arrangements belong to. */
        public int courseCount() {
            return courseCount;
        }

        /** How many pieces of content were dropped, across every reason. */
        public int issueCount() {
            return issueCount;
        }

        /** How many were dropped for one particular reason. */
        public int countOf(Reason reason) {
            Integer count = counts.get(reason);
            return count == null ? 0 : count.intValue();
        }

        /**
         * At most {@link #MAX_ISSUES} examples, one per kind of problem first.
         *
         * <p>Ordered so every kind present appears before any kind repeats, which is
         * what makes the list readable when one mistake covers a whole file.</p>
         */
        public List<Issue> issues() {
            return issues;
        }

        public boolean hasIssues() {
            return issueCount > 0;
        }

        /** Whether the file yielded anything at all. */
        public boolean isUsable() {
            return !courses.isEmpty();
        }

        @Override
        public String toString() {
            return courseCount + " \u95E8 / " + courses.size() + " \u6761\uFF0C"
                    + issueCount + " \u5904\u672A\u8BC6\u522B";
        }
    }

    /** The human-readable form of a drop reason. */
    public static String text(Reason reason) {
        if (reason == null) {
            return "";
        }
        switch (reason) {
            case UNATTRIBUTED_LINE:
                return "\u6709\u4E00\u884C\u5185\u5BB9\u4E0D\u5C5E\u4E8E\u4EFB\u4F55"
                        + "\u8BFE\u7A0B";
            case UNRECOGNISED_DETAIL:
                return "\u8FD9\u884C\u5185\u5BB9\u4E0D\u662F\u53EF\u8BC6\u522B\u7684"
                        + "\u8BFE\u7A0B\u8BE6\u60C5";
            case UNREADABLE_WEEKS:
                return "\u5468\u6B21\u5199\u6CD5\u65E0\u6CD5\u8BC6\u522B";
            case INCOMPLETE_FIELDS:
                return "\u6559\u5E08\u4E0E\u5730\u70B9\u6CA1\u6709\u5206\u5F00\uFF0C"
                        + "\u5730\u70B9\u672A\u80FD\u8BC6\u522B";
            case PERIOD_OUT_OF_RANGE:
                return "\u8282\u6B21\u8D85\u51FA\u4E86\u5E94\u7528\u652F\u6301\u7684 "
                        + ScheduleUtils.MAX_PERIODS + " \u8282\uFF0C\u8BE5\u884C\u672A"
                        + "\u5BFC\u5165";
            default:
                return reason.name();
        }
    }

    private static int periodForRow(List<PeriodRow> rows, int targetRow) {
        for (PeriodRow row : rows) {
            if (row.row == targetRow) {
                return row.period;
            }
        }
        return rows.get(rows.size() - 1).period;
    }

    private static int findHeaderRow(TimetableGrid grid) {
        int limit = Math.min(grid.rowCount(), 20);
        for (int row = 0; row < limit; row++) {
            if (!dayColumns(grid, row).isEmpty()) {
                return row;
            }
        }
        return -1;
    }

    private static Map<Integer, Integer> dayColumns(TimetableGrid grid, int row) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int column = 0; column < grid.columnCount(); column++) {
            int day = dayForHeader(grid.cell(row, column));
            if (day > 0) {
                result.put(column, day);
            }
        }
        return result;
    }

    private static int dayForHeader(String value) {
        if (value == null) {
            return 0;
        }
        String text = value.trim().replace(" ", "").replace("\u00A0", "");
        int offset = -1;
        if (text.startsWith("\u661F\u671F")) {
            offset = 2;
        } else if (text.startsWith("\u5468")) {
            offset = 1;
        } else if (text.startsWith("\u793C\u62DC")) {
            offset = 2;
        }
        if (offset < 0 || text.length() <= offset) {
            return 0;
        }
        switch (text.charAt(offset)) {
            case '\u4E00':
                return 1;
            case '\u4E8C':
                return 2;
            case '\u4E09':
                return 3;
            case '\u56DB':
                return 4;
            case '\u4E94':
                return 5;
            case '\u516D':
                return 6;
            case '\u65E5':
            case '\u5929':
                return 7;
            default:
                return 0;
        }
    }

    private static Integer parsePositiveInt(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            int number = Integer.parseInt(value.substring(0, end));
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Reads one non-empty cell: course headers, then the detail lines under them.
     *
     * <p>Every branch that drops a line says so through {@code collector}. The two
     * that stay silent are deliberate: a blank line carries nothing, and a header
     * line is not dropped at all -- it starts the course the lines below belong
     * to.</p>
     *
     * @param row the worksheet row, 0-based, kept so a problem can be located
     */
    private static void parseCell(List<Course> output, Collector collector, String text,
            int day, int row, int startPeriod, int endPeriod) {
        Header header = null;
        String[] lines = text.split("\\r?\\n|\\r");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            Header nextHeader = parseHeader(line);
            if (nextHeader != null) {
                header = nextHeader;
                continue;
            }
            if (header == null) {
                // Content with no course above it in this cell, so nothing it can
                // be attached to.
                collector.note(Reason.UNATTRIBUTED_LINE, row, day, index + 1);
                continue;
            }
            Matcher detail = DETAIL.matcher(line);
            if (!detail.matches()) {
                collector.note(Reason.UNRECOGNISED_DETAIL, row, day, index + 1);
                continue;
            }
            ParsedWeeks weeks = parseWeeks(detail.group(1));
            if (weeks == null) {
                collector.note(Reason.UNREADABLE_WEEKS, row, day, index + 1);
                continue;
            }
            String[] fields = detail.group(2).trim().split("\\s{2,}");
            String teacher = fields.length > 0 ? fields[0].trim() : "";
            String location = fields.length > 1 ? fields[1].trim() : "";
            if (fields.length < 2 && containsWhitespace(teacher)) {
                // One field that itself contains a space is two fields written with
                // a single separator. The course is still imported, but the venue is
                // inside the teacher's name, so the user has to be told.
                collector.note(Reason.INCOMPLETE_FIELDS, row, day, index + 1);
            }
            for (WeekRange range : weeks.ranges) {
                Course course = new Course();
                course.name = header.name;
                course.teacher = teacher;
                course.location = location;
                course.dayOfWeek = day;
                course.startPeriod = startPeriod;
                course.endPeriod = endPeriod;
                course.startWeek = range.start;
                course.endWeek = range.end;
                course.parity = weeks.parity;
                course.color = colorFor(header.code + header.name);
                output.add(course);
            }
        }
    }

    private static boolean containsWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static Header parseHeader(String line) {
        Matcher withCode = COURSE_HEADER.matcher(line);
        if (withCode.matches()) {
            return new Header(withCode.group(1), withCode.group(2).trim());
        }
        Matcher named = NAMED_HEADER.matcher(line);
        if (named.matches()) {
            return new Header("", named.group(1).trim());
        }
        return null;
    }

    private static ParsedWeeks parseWeeks(String value) {
        String text = value.trim();
        int parity = Course.PARITY_ALL;
        if (text.endsWith("\u5355\u53CC\u5468")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("\u5355\u5468")) {
            parity = Course.PARITY_ODD;
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("\u53CC\u5468")) {
            parity = Course.PARITY_EVEN;
            text = text.substring(0, text.length() - 2);
        }
        if (!text.endsWith("\u5468")) {
            return null;
        }
        text = text.substring(0, text.length() - 1);
        text = text.replace('\uFF0C', ',').replace('\u3001', ',')
                .replace('\uFF1B', ';');
        List<WeekRange> ranges = new ArrayList<>();
        for (String part : text.split("[,;]+")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] ends = RANGE_SEPARATOR.split(token);
            Integer start = parsePositiveInt(ends[0].trim());
            Integer end = ends.length > 1 ? parsePositiveInt(ends[1].trim()) : start;
            if (start == null || end == null) {
                return null;
            }
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }
            if (end > ScheduleUtils.SEMESTER_WEEKS) {
                return null;
            }
            ranges.add(new WeekRange(start, end));
        }
        if (ranges.isEmpty()) {
            return null;
        }
        List<WeekRange> merged = new ArrayList<>();
        for (WeekRange range : ranges) {
            if (!merged.isEmpty()
                    && merged.get(merged.size() - 1).end + 1 == range.start) {
                WeekRange previous = merged.remove(merged.size() - 1);
                merged.add(new WeekRange(previous.start, range.end));
            } else {
                merged.add(range);
            }
        }
        return new ParsedWeeks(merged, parity);
    }

    private static int colorFor(String seed) {
        int index = Math.floorMod(seed.hashCode(), Palette.COURSE_COLORS.length);
        return Palette.COURSE_COLORS[index];
    }

    private static final class Header {
        private final String code;
        private final String name;

        private Header(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private static final class PeriodRow {
        private final int row;
        private final int period;

        private PeriodRow(int row, int period) {
            this.row = row;
            this.period = period;
        }
    }

    private static final class WeekRange {
        private final int start;
        private final int end;

        private WeekRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class ParsedWeeks {
        private final List<WeekRange> ranges;
        private final int parity;

        private ParsedWeeks(List<WeekRange> ranges, int parity) {
            this.ranges = ranges;
            this.parity = parity;
        }
    }
}
