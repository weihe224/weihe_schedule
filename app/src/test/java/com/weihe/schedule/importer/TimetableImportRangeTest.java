package com.weihe.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * What importing does once a day can be longer than fifteen periods.
 *
 * <p>The parser used to drop a row past the fifteenth without saying so, which
 * meant a file for a twenty-period school imported as a quiet truncation: the
 * courses were missing and nothing said why. Silent data loss is the one outcome an
 * import must never have, so the rule now is that anything past the ceiling is
 * reported by name and counted, and the file still gives up everything it can.</p>
 */
public class TimetableImportRangeTest {

    /** A grid with {@code periods} numbered rows and two teaching columns. */
    private static TimetableGrid gridWith(int periods) {
        TimetableGrid grid = new TimetableGrid(periods + 2, 3);
        grid.put(0, 0, "2026-2027\u7B2C\u4E00\u5B66\u671F");
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u661F\u671F\u4E00");
        grid.put(1, 2, "\u661F\u671F\u4E8C");
        for (int period = 1; period <= periods; period++) {
            grid.put(period + 1, 0, String.valueOf(period));
        }
        return grid;
    }

    /** One course on Monday from {@code startPeriod} to {@code endPeriod}. */
    private static void putCourse(TimetableGrid grid, int startPeriod, int endPeriod) {
        grid.put(startPeriod + 1, 1, "X02018\u3010\u68C0\u6D4B\u6280\u672F\u3011\n"
                + "2-5\u5468\u5355\u53CC\u5468  \u82CF\u8001\u5E08  F202");
        if (endPeriod > startPeriod) {
            grid.merge(startPeriod + 1, 1, endPeriod + 1, 1);
        }
    }

    @Test
    public void aCoursePastTheDefaultIsImported() throws Exception {
        // Nineteen is past fifteen but inside the ceiling, so it is an ordinary
        // course: a school that runs a longer day must be able to import its own
        // timetable.
        TimetableGrid grid = gridWith(20);
        putCourse(grid, 17, 18);

        TimetableImportParser.Report report =
                TimetableImportParser.parseWithReport(grid);

        assertEquals("the course must be imported, not dropped",
                1, report.scheduleCount());
        assertEquals(17, report.courses().get(0).startPeriod);
        assertEquals(18, report.courses().get(0).endPeriod);
        assertFalse("and nothing is reported as dropped", report.hasIssues());
    }

    @Test
    public void aCoursePastTheCeilingIsReportedRatherThanDroppedSilently() throws Exception {
        TimetableGrid grid = gridWith(ScheduleUtils.MAX_PERIODS + 4);
        putCourse(grid, ScheduleUtils.MAX_PERIODS + 2, ScheduleUtils.MAX_PERIODS + 3);

        TimetableImportParser.Report report =
                TimetableImportParser.parseWithReport(grid);

        assertFalse("the file must not look like it imported cleanly",
                report.issues().isEmpty());
        assertTrue("the reason must be the period range, and it must be counted",
                report.countOf(TimetableImportParser.Reason.PERIOD_OUT_OF_RANGE) > 0);
        assertEquals("the user has to be told, not left with a shorter timetable",
                0, report.courseCount());
    }

    @Test
    public void aFileWithBothIsPartlyImportedAndSaysWhatWasLost() throws Exception {
        // The important case: something usable and something past the ceiling in the
        // same file. The usable courses must arrive, and the rest must be reported --
        // never both dropped, never both kept.
        TimetableGrid grid = gridWith(ScheduleUtils.MAX_PERIODS + 4);
        putCourse(grid, 3, 4);
        grid.put(ScheduleUtils.MAX_PERIODS + 3, 2,
                "ZF32015\u3010\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD\u3011\n"
                        + "2-9\u5468\u5355\u53CC\u5468  \u5F90\u8001\u5E08  B");

        TimetableImportParser.Report report =
                TimetableImportParser.parseWithReport(grid);

        assertEquals("the in-range course is imported", 1, report.scheduleCount());
        assertTrue(report.countOf(TimetableImportParser.Reason.PERIOD_OUT_OF_RANGE) > 0);
    }

    @Test
    public void theOutOfRangeExplanationNamesTheCeiling() throws Exception {
        String text = TimetableImportParser.text(
                TimetableImportParser.Reason.PERIOD_OUT_OF_RANGE);

        assertFalse("there must be something to show", text.isEmpty());
        assertTrue("the message must say the limit, so the user can act on it: " + text,
                text.contains(String.valueOf(ScheduleUtils.MAX_PERIODS)));
    }

    @Test
    public void theWholeConfiguredRangeImportsWithoutAnIssue() throws Exception {
        // Every period a schedule can hold must be importable, up to the ceiling.
        TimetableGrid grid = gridWith(ScheduleUtils.MAX_PERIODS);
        putCourse(grid, ScheduleUtils.MAX_PERIODS - 1, ScheduleUtils.MAX_PERIODS);

        TimetableImportParser.Report report =
                TimetableImportParser.parseWithReport(grid);

        assertEquals(1, report.scheduleCount());
        assertEquals(ScheduleUtils.MAX_PERIODS - 1,
                report.courses().get(0).startPeriod);
        assertEquals(ScheduleUtils.MAX_PERIODS,
                report.courses().get(0).endPeriod);
        assertFalse(report.hasIssues());
    }

    @Test
    public void aWorksheetWithNothingRecognisableIsRefusedRatherThanGuessedAt() throws Exception {
        // A header-only worksheet, the shape a blank export has. There is nothing to
        // import, and the parser says so with its own exception rather than returning
        // a half-built list or throwing at one of the array bounds.
        TimetableGrid grid = new TimetableGrid(2, 3);
        grid.put(0, 0, "2026-2027\u7B2C\u4E00\u5B66\u671F");
        grid.put(1, 0, "\u8282\u6B21");

        try {
            TimetableImportParser.parseWithReport(grid);
            throw new AssertionError("a grid with no day columns must be refused");
        } catch (TimetableImportException expected) {
            assertFalse("and it must say why", expected.getMessage() == null
                    || expected.getMessage().trim().isEmpty());
        }
    }

    @Test
    public void aWorksheetWithDaysButNoPeriodNumbersIsRefused() throws Exception {
        // The other half-blank shape: the days are named but no row says which period
        // it is, so there is no period to place a course on.
        TimetableGrid grid = new TimetableGrid(2, 3);
        grid.put(0, 0, "2026-2027\u7B2C\u4E00\u5B66\u671F");
        grid.put(1, 0, "\u8282\u6B21");
        grid.put(1, 1, "\u661F\u671F\u4E00");
        grid.put(1, 2, "\u661F\u671F\u4E8C");

        try {
            TimetableImportParser.parseWithReport(grid);
            throw new AssertionError("a grid with no period rows must be refused");
        } catch (TimetableImportException expected) {
            assertFalse(expected.getMessage() == null
                    || expected.getMessage().trim().isEmpty());
        }
    }
}
