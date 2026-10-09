package com.weihe.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import jxl.Workbook;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * The layer that turns an uploaded {@code .xls} into the parser's worksheet.
 *
 * <p>This is the only class in the project that reads somebody else's file, and
 * it is also where the two coordinate systems meet: jxl addresses a cell as
 * {@code (column, row)} while {@link TimetableGrid} addresses it as
 * {@code (row, column)}. Getting that swap wrong transposes the whole timetable
 * without raising anything, so the first test here pins the mapping rather than
 * the parsing.</p>
 *
 * <p>The workbooks are written in memory with the same jxl jar the app already
 * ships. Nothing is read from disk, so the tests need no fixture files, no
 * absolute paths and no network.</p>
 */
public class ExcelTimetableReaderTest {

    // ------------------------------------------------------------ row/column

    @Test
    public void aCourseLandsOnTheWeekdayOfItsColumnAndThePeriodOfItsRow()
            throws Exception {
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        xls.label(2, 1, "\u661F\u671F\u4E8C");
        xls.label(3, 1, "\u661F\u671F\u4E09");
        xls.periods(1, 2, 3);
        // Deliberately asymmetric: if rows and columns were swapped, B and C
        // would move to different weekdays and the set below would not match.
        xls.courseCell(1, 2, "A");
        xls.courseCell(2, 2, "B");
        xls.courseCell(3, 3, "C");
        xls.courseCell(3, 4, "D");

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(xls.build()));

        assertEquals(java.util.Set.of("A@1-1", "B@2-1", "C@3-2", "D@3-3"),
                coordinates(courses));
    }

    @Test
    public void thePeriodNumbersComeFromTheFirstColumnNotFromTheRowOrder()
            throws Exception {
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        // Rows out of numeric order on purpose: the parser must trust the label
        // in column 0, not the position of the row.
        xls.label(0, 2, "3");
        xls.label(0, 3, "1");
        xls.courseCell(1, 3, "FIRST");

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(xls.build()));

        assertEquals(1, courses.size());
        assertEquals(1, courses.get(0).startPeriod);
    }

    @Test
    public void onlyTheFirstSheetIsRead() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableWorkbook book = Workbook.createWorkbook(out);
        WritableSheet first = book.createSheet("first", 0);
        first.addCell(new Label(0, 1, "\u8282\u6B21"));
        first.addCell(new Label(1, 1, "\u661F\u671F\u4E00"));
        first.addCell(new Label(0, 2, "1"));
        first.addCell(new Label(1, 2, cellText("FIRST")));
        WritableSheet second = book.createSheet("second", 1);
        second.addCell(new Label(0, 1, "\u8282\u6B21"));
        second.addCell(new Label(1, 1, "\u661F\u671F\u4E8C"));
        second.addCell(new Label(0, 2, "1"));
        second.addCell(new Label(1, 2, cellText("SECOND")));
        book.write();
        book.close();

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(out.toByteArray()));

        assertEquals(1, courses.size());
        assertEquals("FIRST", courses.get(0).name);
        assertEquals(1, courses.get(0).dayOfWeek);
    }

    // -------------------------------------------------------- merged cells

    @Test
    public void aMergedCellBecomesOneCourseSpanningTheMergedPeriods()
            throws Exception {
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        xls.periods(1, 2, 3);
        xls.courseCell(1, 2, "SPANS");
        xls.merge(1, 2, 1, 4);

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(xls.build()));

        assertEquals(1, courses.size());
        assertEquals("SPANS", courses.get(0).name);
        assertEquals(1, courses.get(0).startPeriod);
        assertEquals(3, courses.get(0).endPeriod);
    }

    @Test
    public void aMergedCellIsNotReadOncePerRowItCovers() throws Exception {
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        xls.periods(1, 2, 3, 4);
        xls.courseCell(1, 2, "ONCE");
        xls.merge(1, 2, 1, 5);

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(xls.build()));

        assertEquals(1, courses.size());
        assertEquals(4, courses.get(0).endPeriod);
    }

    @Test
    public void aMergeIsClippedToTheRangeTheSheetActuallyReports() throws Exception {
        // Two independent merges in the same column must not bleed into each
        // other: the first covers periods 1-2, the second periods 3-4.
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        xls.periods(1, 2, 3, 4);
        xls.courseCell(1, 2, "UP");
        xls.merge(1, 2, 1, 3);
        xls.courseCell(1, 4, "DOWN");
        xls.merge(1, 4, 1, 5);

        List<Course> courses = ExcelTimetableReader.read(
                new ByteArrayInputStream(xls.build()));

        assertEquals(2, courses.size());
        assertEquals("UP", courses.get(0).name);
        assertEquals(2, courses.get(0).endPeriod);
        assertEquals("DOWN", courses.get(1).name);
        assertEquals(3, courses.get(1).startPeriod);
        assertEquals(4, courses.get(1).endPeriod);
    }

    // ---------------------------------------------------------- bad input

    @Test
    public void aNullStreamIsRejectedWithTheAppsOwnMessage() {
        try {
            ExcelTimetableReader.read(null);
            fail("expected a null stream to be rejected");
        } catch (Exception expected) {
            assertTrue(expected instanceof TimetableImportException);
            assertEquals("\u65E0\u6CD5\u6253\u5F00\u6240\u9009\u6587\u4EF6",
                    expected.getMessage());
        }
    }

    @Test
    public void aWorkbookWithNothingInItIsRejectedAsHavingNoTimetable()
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableWorkbook book = Workbook.createWorkbook(out);
        book.createSheet("empty", 0);
        book.write();
        book.close();

        try {
            ExcelTimetableReader.read(new ByteArrayInputStream(out.toByteArray()));
            fail("expected an empty workbook to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8868\u5934"));
        }
    }

    @Test
    public void aWorksheetThatIsNotATimetableIsRejected() throws Exception {
        Xls xls = new Xls();
        xls.label(0, 0, "\u5B66\u751F\u540D\u5355");
        xls.label(0, 1, "\u5F20\u4E09");
        xls.label(1, 1, "\u674E\u56DB");

        try {
            ExcelTimetableReader.read(new ByteArrayInputStream(xls.build()));
            fail("expected a non-timetable sheet to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8868\u5934"));
        }
    }

    @Test
    public void aSheetWithWeekdayHeadersButNoPeriodRowsIsRejected()
            throws Exception {
        Xls xls = new Xls();
        xls.label(0, 1, "\u8282\u6B21");
        xls.label(1, 1, "\u661F\u671F\u4E00");
        xls.label(1, 2, cellText("NO PERIODS"));

        try {
            ExcelTimetableReader.read(new ByteArrayInputStream(xls.build()));
            fail("expected a sheet without period rows to be rejected");
        } catch (TimetableImportException expected) {
            assertTrue(expected.getMessage().contains("\u8282\u6B21\u884C"));
        }
    }

    @Test
    public void bytesThatAreNotAWorkbookAreRejectedByTheReader()
            throws Exception {
        byte[] garbage = new byte[64];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = (byte) (i * 7 + 1);
        }

        try {
            ExcelTimetableReader.read(new ByteArrayInputStream(garbage));
            fail("expected bytes that are not a workbook to be rejected");
        } catch (BiffException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void aZipArchiveIsRejectedByTheReader() throws Exception {
        // What a .xlsx really is, and what a user gets when the school system
        // hands out the modern format and the file is picked anyway.
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(zipBuffer);
        zip.putNextEntry(new java.util.zip.ZipEntry("xl/workbook.xml"));
        zip.write("<workbook/>".getBytes("UTF-8"));
        zip.closeEntry();
        zip.close();

        try {
            ExcelTimetableReader.read(new ByteArrayInputStream(zipBuffer.toByteArray()));
            fail("expected a zip to be rejected");
        } catch (BiffException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // -------------------------------------------------------------- helpers

    /** {@code name@day-period} for every course, so the whole layout is asserted. */
    private static java.util.Set<String> coordinates(List<Course> courses) {
        java.util.Set<String> result = new java.util.TreeSet<>();
        for (Course course : courses) {
            result.add(course.name + "@" + course.dayOfWeek + "-" + course.startPeriod);
        }
        return result;
    }

    /** A full course cell: one header line plus one detail line. */
    private static String cellText(String name) {
        return "X02018\u3010" + name + "\u3011\n2-5\u5468  \u5E08  \u5730";
    }

    /**
     * A worksheet written in memory with the jxl jar the app already depends on.
     *
     * <p>Note the argument order: {@code Label} and {@code mergeCells} both take
     * {@code (column, row)}, which is the opposite of {@link TimetableGrid}.</p>
     */
    private static final class Xls {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final WritableWorkbook book;
        private final WritableSheet sheet;

        private Xls() throws Exception {
            book = Workbook.createWorkbook(buffer);
            sheet = book.createSheet("sheet", 0);
        }

        private void label(int column, int row, String text) throws Exception {
            sheet.addCell(new Label(column, row, text));
        }

        private void periods(int... numbers) throws Exception {
            for (int i = 0; i < numbers.length; i++) {
                label(0, i + 2, String.valueOf(numbers[i]));
            }
        }

        private void courseCell(int column, int row, String name) throws Exception {
            label(column, row, cellText(name));
        }

        private void merge(int leftColumn, int topRow, int rightColumn, int bottomRow)
                throws Exception {
            sheet.mergeCells(leftColumn, topRow, rightColumn, bottomRow);
        }

        private byte[] build() throws Exception {
            book.write();
            book.close();
            return buffer.toByteArray();
        }
    }
}
