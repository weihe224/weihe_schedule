package com.weihe.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
