package com.weihe.schedule.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private TimetableImportParser() {
    }

    public static List<Course> parse(TimetableGrid grid)
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
        for (int row = headerRow + 1; row < grid.rowCount(); row++) {
            Integer period = parsePositiveInt(grid.cell(row, 0).trim());
            if (period != null && period <= ScheduleUtils.PERIODS_PER_DAY) {
                periodRows.add(new PeriodRow(row, period));
            }
        }
        if (periodRows.isEmpty()) {
            throw new TimetableImportException("\u672A\u627E\u5230\u8282\u6B21\u884C");
        }
        int lastPeriod = periodRows.get(periodRows.size() - 1).period;

        List<Course> courses = new ArrayList<>();
        for (Map.Entry<Integer, Integer> dayColumn : dayColumns.entrySet()) {
            int column = dayColumn.getKey();
            int day = dayColumn.getValue();
            for (PeriodRow periodRow : periodRows) {
                if (!grid.isTopLeft(periodRow.row, column)) {
                    continue;
                }
                String text = grid.cell(periodRow.row, column);
                if (text.trim().isEmpty()) {
                    continue;
                }
                int endPeriod = Math.min(lastPeriod, periodForRow(periodRows,
                        grid.mergedEndRow(periodRow.row, column)));
                parseCell(courses, text, day,
                        periodRow.period, Math.max(periodRow.period, endPeriod));
            }
        }
        return courses;
    }

    /**
     * Maps the week numbers in an exported timetable onto the semester calendar.
     *
     * <p>{@code sourceFirstWeek} is the semester week represented by week 1 in
     * the file. Odd/even rules are relative to the source timetable, so an odd
     * offset flips them after the mapping.</p>
     */
    public static List<Course> shiftWeeks(List<Course> courses,
            int sourceFirstWeek) throws TimetableImportException {
        if (sourceFirstWeek < 1
                || sourceFirstWeek > ScheduleUtils.SEMESTER_WEEKS) {
            throw new TimetableImportException(
                    "\u8D77\u59CB\u5468\u5FC5\u987B\u5728 1 \u5230 "
                            + ScheduleUtils.SEMESTER_WEEKS + " \u4E4B\u95F4");
        }
        int offset = sourceFirstWeek - 1;
        List<Course> shifted = new ArrayList<>();
        for (Course source : courses) {
            if (source.startWeek < 1 || source.endWeek < source.startWeek) {
                throw new TimetableImportException(
                        "\u8BFE\u7A0B\u201C" + source.name
                                + "\u201D\u7684\u5468\u6B21\u8303\u56F4\u65E0\u6548");
            }
            long mappedEnd = (long) source.endWeek + offset;
            if (mappedEnd > ScheduleUtils.SEMESTER_WEEKS) {
                int latestStart = Math.max(1,
                        ScheduleUtils.SEMESTER_WEEKS - source.endWeek + 1);
                throw new TimetableImportException(
                        "\u6587\u4EF6\u4E2D\u5305\u542B\u7B2C" + source.endWeek
                                + "\u5468\u8BFE\u7A0B\uFF0C\u8BE5\u8D77\u59CB\u5468\u4F1A\u8D85\u51FA\u7B2C"
                                + ScheduleUtils.SEMESTER_WEEKS
                                + "\u5468\u3002\u8BF7\u9009\u62E9\u4E0D\u665A\u4E8E\u7B2C"
                                + latestStart + "\u5468\u5F00\u59CB");
            }
            Course course = source.copy();
            course.startWeek += offset;
            course.endWeek = (int) mappedEnd;
            if ((offset & 1) == 1) {
                course.parity = flipParity(course.parity);
            }
            shifted.add(course);
        }
        return shifted;
    }

    private static int flipParity(int parity) {
        if (parity == Course.PARITY_ODD) {
            return Course.PARITY_EVEN;
        }
        if (parity == Course.PARITY_EVEN) {
            return Course.PARITY_ODD;
        }
        return Course.PARITY_ALL;
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

    private static void parseCell(List<Course> output, String text, int day,
            int startPeriod, int endPeriod) {
        Header header = null;
        String[] lines = text.split("\\r?\\n|\\r");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Header nextHeader = parseHeader(line);
            if (nextHeader != null) {
                header = nextHeader;
                continue;
            }
            if (header == null) {
                continue;
            }
            Matcher detail = DETAIL.matcher(line);
            if (!detail.matches()) {
                continue;
            }
            ParsedWeeks weeks = parseWeeks(detail.group(1));
            if (weeks == null) {
                continue;
            }
            String[] fields = detail.group(2).trim().split("\\s{2,}");
            String teacher = fields.length > 0 ? fields[0].trim() : "";
            String location = fields.length > 1 ? fields[1].trim() : "";
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
