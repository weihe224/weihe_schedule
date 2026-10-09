package com.weihe.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

public class TimetableImportParserTest {
    @Test
    public void parsesMergedPeriodSpanAndMultipleDetails() throws Exception {
        TimetableGrid grid = grid();
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2-5\u5468\u5355\u53CC\u5468  \u82CF\u8001\u5E08  F202\n"
                + "6-9\u5468\u5355\u53CC\u5468  \u51CC\u8001\u5E08  F203");
        grid.merge(2, 1, 4, 1);

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(2, courses.size());
        assertEquals(1, courses.get(0).dayOfWeek);
        assertEquals(1, courses.get(0).startPeriod);
        assertEquals(3, courses.get(0).endPeriod);
        assertEquals(2, courses.get(0).startWeek);
        assertEquals(5, courses.get(0).endWeek);
        assertEquals(Course.PARITY_ALL, courses.get(0).parity);
        assertEquals(6, courses.get(1).startWeek);
        assertEquals(9, courses.get(1).endWeek);
    }

    @Test
    public void parsesTwoCoursesInOneCellAndParity() throws Exception {
        TimetableGrid grid = grid();
        grid.put(2, 1, "X32005\u3010\u667A\u80FD\u63A7\u5236\u3011\n"
                + "2-9\u5468\u5355\u53CC\u5468  \u961A\u8001\u5E08  A\n"
                + "ZF32015\u3010\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD\u3011\n"
                + "10-17\u5468\u53CC\u5468  \u5F90\u8001\u5E08  B");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(2, courses.size());
        assertEquals("\u667A\u80FD\u63A7\u5236", courses.get(0).name);
        assertEquals(Course.PARITY_ALL, courses.get(0).parity);
        assertEquals("\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD", courses.get(1).name);
        assertEquals(Course.PARITY_EVEN, courses.get(1).parity);
    }

    @Test
    public void collapsesConsecutiveWeeksIntoOneRange() throws Exception {
        TimetableGrid grid = grid();
        grid.put(2, 1, "X21003\u3010\u77E9\u9635\u8BBA\u3011\n"
                + "2,3,4,5,6\u5468  \u5434\u8001\u5E08  F120");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals(2, courses.get(0).startWeek);
        assertEquals(6, courses.get(0).endWeek);
    }

    @Test
    public void oddWeekDetailsStayOdd() throws Exception {
        TimetableGrid grid = grid();
        grid.put(2, 1, "G47007\u3010\u7EFC\u5408\u82F1\u8BED\u3011\n"
                + "2-17\u5468\u5355\u5468  \u535E\u8001\u5E08  E107");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(Course.PARITY_ODD, courses.get(0).parity);
    }

    @Test
    public void shiftWeeksKeepsTheOriginalWhenTheFirstWeekIsOne() throws Exception {
        Course source = course(2, 5, Course.PARITY_ALL);

        List<Course> shifted = TimetableImportParser.shiftWeeks(
                java.util.List.of(source), 1);

        assertEquals(1, shifted.size());
        assertNotEquals(source, shifted.get(0));
        assertEquals(2, shifted.get(0).startWeek);
        assertEquals(5, shifted.get(0).endWeek);
        assertEquals(Course.PARITY_ALL, shifted.get(0).parity);
    }

    @Test
    public void shiftWeeksMovesTheRangeAndFlipsOddEvenParity() throws Exception {
        Course source = course(2, 5, Course.PARITY_ALL);
        Course odd = course(6, 9, Course.PARITY_ODD);

        List<Course> shifted = TimetableImportParser.shiftWeeks(
                java.util.List.of(source, odd), 2);

        assertEquals(3, shifted.get(0).startWeek);
        assertEquals(6, shifted.get(0).endWeek);
        assertEquals(7, shifted.get(1).startWeek);
        assertEquals(10, shifted.get(1).endWeek);
        assertEquals(Course.PARITY_EVEN, shifted.get(1).parity);
        assertEquals(2, source.startWeek);
        assertEquals(Course.PARITY_ODD, odd.parity);
    }

    @Test
    public void shiftWeeksRejectsAWeekRangeThatWouldOverflow() {
        Course source = course(12, 17, Course.PARITY_ALL);

        try {
            TimetableImportParser.shiftWeeks(java.util.List.of(source), 3);
            fail("expected overflow to be rejected");
        } catch (TimetableImportException expected) {
            assertEquals(true, expected.getMessage().contains(
                    "\u7B2C17\u5468"));
        }
    }

    // ------------------------------------------------------ 行列映射（防转置）

    @Test
    public void everyCellKeepsTheWeekdayOfItsColumnAndThePeriodOfItsRow()
            throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u4E00",
                "\u661F\u671F\u4E8C", "\u661F\u671F\u4E09");
        // 刻意不对称：若行列被转置，B 与 C 会落到别的星期，整个集合就对不上。
        grid.put(2, 1, cell("A"));
        grid.put(2, 2, cell("B"));
        grid.put(3, 3, cell("C"));
        grid.put(4, 3, cell("D"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "A@1-1", "B@2-1", "C@3-2", "D@3-3")), coordinates(courses));
    }

    @Test
    public void dayHeadersAreRecognisedInEverySpellingTheParserSupports()
            throws Exception {
        TimetableGrid grid = timetable(1, "\u5468\u4E00", "\u661F\u671F\u4E8C",
                "\u793C\u62DC\u4E09", "\u5468\u56DB", "\u661F\u671F\u4E94",
                "\u5468\u516D", "\u661F\u671F\u65E5");
        for (int day = 1; day <= 7; day++) {
            grid.put(2, day, cell("D" + day));
        }

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "D1@1-1", "D2@2-1", "D3@3-1", "D4@4-1",
                "D5@5-1", "D6@6-1", "D7@7-1")), coordinates(courses));
    }

    @Test
    public void sundayIsAlsoSpeltWithTian() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u5929",
                "\u793C\u62DC\u5929", "\u5468\u65E5");
        grid.put(2, 1, cell("A"));
        grid.put(2, 2, cell("B"));
        grid.put(2, 3, cell("C"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "A@7-1", "B@7-1", "C@7-1")), coordinates(courses));
    }

    @Test
    public void aHeaderThatNamesNoWeekdayIsNotADayColumn() {
        TimetableGrid grid = new TimetableGrid(3, 4);
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u5468");
        grid.put(1, 2, "\u661F\u671F");
        grid.put(1, 3, "\u793C\u62DC");
        grid.put(2, 0, "1");

        try {
            TimetableImportParser.parse(grid);
            fail("expected a grid without weekday headers to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8868\u5934"));
        }
    }

    // -------------------------------------------------------- 课程信息解析

    @Test
    public void oneDetailLineFillsInEveryFieldTheParserOwns() throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u56DB");
        grid.put(3, 1, "ZF32015\u3010\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD\u3011\n"
                + "3-16\u5468\u5355\u5468  \u5F90\u8001\u5E08  \u6559\u5B66\u697C E302");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        Course course = courses.get(0);
        assertEquals("\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD", course.name);
        assertEquals("\u5F90\u8001\u5E08", course.teacher);
        assertEquals("\u6559\u5B66\u697C E302", course.location);
        assertEquals(4, course.dayOfWeek);
        assertEquals(2, course.startPeriod);
        assertEquals(2, course.endPeriod);
        assertEquals(3, course.startWeek);
        assertEquals(16, course.endWeek);
        assertEquals(Course.PARITY_ODD, course.parity);
        // 解析阶段不分配 id、不选作息表：id 由 replaceCourses 补齐，
        // 作息由上课地点在显示时决定。
        assertEquals(0L, course.id);
        assertEquals("", course.scheduleName);
        assertTrue("colour must come from the course palette",
                isACourseColour(course.color));
    }

    @Test
    public void aHeaderWithoutACourseCodeIsStillAccepted() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "\u3010\u9AD8\u7B49\u6570\u5B66\u3011\n"
                + "2-5\u5468  \u5F20\u8001\u5E08  A101");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals("\u9AD8\u7B49\u6570\u5B66", courses.get(0).name);
        assertEquals("\u5F20\u8001\u5E08", courses.get(0).teacher);
        assertEquals("A101", courses.get(0).location);
    }

    @Test
    public void aDetailLineWithoutALocationLeavesItBlank() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2-5\u5468  \u82CF\u8001\u5E08");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals("\u82CF\u8001\u5E08", courses.get(0).teacher);
        assertEquals("", courses.get(0).location);
    }

    @Test
    public void aDetailLineAboveAnyHeaderIsIgnored() throws Exception {
        TimetableGrid grid = timetable(2, "\u661F\u671F\u4E00");
        grid.put(2, 1, "2-5\u5468  \u5E08  \u5730");
        grid.put(3, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2-5\u5468  \u5E08  \u5730");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals("\u68C0\u6D4B\u6280\u672F", courses.get(0).name);
    }

    // ------------------------------------------------------ 空白与合并单元格

    @Test
    public void aBlankCellBetweenTwoCoursesDoesNotShiftEitherOfThem()
            throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u4E00");
        grid.put(2, 1, cell("A"));
        // 第 2 节整格留空
        grid.put(4, 1, cell("C"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "A@1-1", "C@1-3")), coordinates(courses));
    }

    @Test
    public void aBlankPeriodRowInsideTheGridKeepsTheNumbersBelowIt()
            throws Exception {
        // 手工构造：节次 1、2，然后一整行没有节次号，再是节次 4。
        TimetableGrid grid = new TimetableGrid(7, 2);
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u661F\u671F\u4E00");
        grid.put(2, 0, "1");
        grid.put(3, 0, "2");
        grid.put(5, 0, "4");
        grid.put(2, 1, cell("A"));
        grid.put(5, 1, cell("D"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "A@1-1", "D@1-4")), coordinates(courses));
    }

    @Test
    public void oneDetailLineInAMergedCellYieldsExactlyOneCourse()
            throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u4E00");
        grid.put(2, 1, cell("A"));
        grid.merge(2, 1, 4, 1);

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals("A", courses.get(0).name);
        assertEquals(1, courses.get(0).startPeriod);
        assertEquals(3, courses.get(0).endPeriod);
    }

    @Test
    public void textLeftInsideACoveredCellOfAMergeIsNotReadASecondTime()
            throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u4E00");
        grid.put(2, 1, cell("A"));
        grid.merge(2, 1, 3, 1);
        grid.put(3, 1, cell("B"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals("A", courses.get(0).name);
        assertEquals(2, courses.get(0).endPeriod);
    }

    // ------------------------------------------------------------ 边界情况

    @Test
    public void aCourseInTheLastColumnAndLastPeriodIsStillFound()
            throws Exception {
        TimetableGrid grid = timetable(3, "\u661F\u671F\u4E00",
                "\u661F\u671F\u4E8C", "\u661F\u671F\u4E09", "\u661F\u671F\u56DB",
                "\u661F\u671F\u4E94", "\u661F\u671F\u516D", "\u661F\u671F\u65E5");
        grid.put(4, 7, cell("EDGE"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "EDGE@7-3")), coordinates(courses));
    }

    @Test
    public void theDayHeaderIsFoundWithinTheFirstTwentyRows() throws Exception {
        TimetableGrid grid = new TimetableGrid(23, 2);
        grid.put(19, 0, "\u8282\u6B21");
        grid.put(19, 1, "\u661F\u671F\u4E00");
        grid.put(20, 0, "1");
        grid.put(20, 1, cell("DEEP"));

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(new java.util.TreeSet<>(java.util.Arrays.asList(
                "DEEP@1-1")), coordinates(courses));
    }

    @Test
    public void aDayHeaderBelowTheSearchWindowIsNotFound() {
        TimetableGrid grid = new TimetableGrid(23, 2);
        grid.put(20, 0, "\u8282\u6B21");
        grid.put(20, 1, "\u661F\u671F\u4E00");
        grid.put(21, 0, "1");

        try {
            TimetableImportParser.parse(grid);
            fail("expected a header past row 19 to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8868\u5934"));
        }
    }

    @Test
    public void separatedWeeksBecomeOneCoursePerRun() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2,5,8\u5468  \u5E08  \u5730");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(3, courses.size());
        assertEquals(2, courses.get(0).startWeek);
        assertEquals(2, courses.get(0).endWeek);
        assertEquals(5, courses.get(1).startWeek);
        assertEquals(5, courses.get(1).endWeek);
        assertEquals(8, courses.get(2).startWeek);
        assertEquals(8, courses.get(2).endWeek);
    }

    @Test
    public void touchingWeekRunsAreMergedButGappedOnesAreNot() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2-3,4-5,8\u5468  \u5E08  \u5730");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(2, courses.size());
        assertEquals(2, courses.get(0).startWeek);
        assertEquals(5, courses.get(0).endWeek);
        assertEquals(8, courses.get(1).startWeek);
        assertEquals(8, courses.get(1).endWeek);
    }

    @Test
    public void aWeekRangeWrittenBackwardsIsReadAsItsEndpoints() throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "9-4\u5468  \u5E08  \u5730");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertEquals(1, courses.size());
        assertEquals(4, courses.get(0).startWeek);
        assertEquals(9, courses.get(0).endWeek);
    }

    @Test
    public void aWeekRangePastTheEndOfTheSemesterContributesNoCourse()
            throws Exception {
        TimetableGrid grid = timetable(1, "\u661F\u671F\u4E00");
        grid.put(2, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "1-19\u5468  \u5E08  \u5730");

        List<Course> courses = TimetableImportParser.parse(grid);

        assertTrue(courses.isEmpty());
    }

    @Test
    public void aGridWithoutAnyWeekdayHeaderIsRejected() {
        TimetableGrid grid = new TimetableGrid(4, 2);
        grid.put(0, 0, "2026-2027\u5B66\u5E74\u7B2C\u4E00\u5B66\u671F");
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(2, 0, "1");

        try {
            TimetableImportParser.parse(grid);
            fail("expected a grid without weekday headers to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8868\u5934"));
        }
    }

    @Test
    public void aGridWithoutAnyPeriodRowIsRejected() {
        TimetableGrid grid = new TimetableGrid(4, 2);
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u661F\u671F\u4E00");
        grid.put(2, 1, cell("A"));

        try {
            TimetableImportParser.parse(grid);
            fail("expected a grid without period rows to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8282\u6B21\u884C"));
        }
    }

    // -------------------------------------------------------------- 夹具

    /**
     * 表头在第 1 行、第 0 列是节次号 1..periods、其余每列一个星期的网格。
     *
     * <p>与既有的 {@link #grid()} 并存：那个固定 7 行 3 列、只服务早先的用例，
     * 这里不动它，避免改动已通过的断言。</p>
     */
    private static TimetableGrid timetable(int periods, String... dayHeaders) {
        TimetableGrid grid = new TimetableGrid(periods + 2, dayHeaders.length + 1);
        grid.put(1, 0, "\u8282\u6B21");
        for (int i = 0; i < dayHeaders.length; i++) {
            grid.put(1, i + 1, dayHeaders[i]);
        }
        for (int period = 1; period <= periods; period++) {
            grid.put(period + 1, 0, String.valueOf(period));
        }
        return grid;
    }

    /** 一个完整的课程格：一行课程头 + 一行明细。 */
    private static String cell(String name) {
        return "X02018\u3010" + name + "\u3011\n2-5\u5468  \u5E08  \u5730";
    }

    /** 每门课的 {@code 课名@星期-起始节}，用于整体比对整张表的布局。 */
    private static java.util.Set<String> coordinates(List<Course> courses) {
        java.util.Set<String> result = new java.util.TreeSet<>();
        for (Course course : courses) {
            result.add(course.name + "@" + course.dayOfWeek + "-" + course.startPeriod);
        }
        return result;
    }

    private static boolean isACourseColour(int color) {
        for (int candidate : com.weihe.schedule.design.Palette.COURSE_COLORS) {
            if (candidate == color) {
                return true;
            }
        }
        return false;
    }

    private static Course course(int startWeek, int endWeek, int parity) {
        Course course = new Course();
        course.name = "\u6D4B\u8BD5\u8BFE\u7A0B";
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        course.parity = parity;
        return course;
    }

    private static TimetableGrid grid() {
        TimetableGrid grid = new TimetableGrid(7, 3);
        grid.put(0, 0, "2026-2027\u7B2C\u4E00\u5B66\u671F");
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u661F\u671F\u4E00");
        grid.put(1, 2, "\u661F\u671F\u4E8C");
        for (int period = 1; period <= 5; period++) {
            grid.put(period + 1, 0, String.valueOf(period));
        }
        return grid;
    }
}
