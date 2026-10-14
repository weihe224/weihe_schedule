package com.weihe.schedule.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jxl.Range;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import com.weihe.schedule.model.Course;

/** Reads an old-style Excel .xls workbook exported by the school system. */
public final class ExcelTimetableReader {

    /**
     * The largest sheet this reader will look at.
     *
     * <p>Sized against a real timetable, not against what a spreadsheet can hold.
     * A term's grid is a week of columns plus a header block, and a few dozen rows
     * once every period of every day is listed; 500 x 100 is an order of magnitude
     * of headroom. It matters because {@link TimetableGrid} allocates four arrays of
     * {@code rows x columns} before a single cell is read: a workbook that merely
     * declares a million rows by sixteen thousand columns -- a dimension record is
     * a header, not a promise -- asks for four arrays of sixteen billion entries,
     * which arrives as an {@code OutOfMemoryError} rather than as anything the
     * caller can report.</p>
     */
    private static final int MAX_ROWS = 500;
    private static final int MAX_COLUMNS = 100;

    /**
     * The longest cell text kept.
     *
     * <p>A cell in a timetable holds a course name, a teacher or a room, so a
     * thousand characters is already two orders of magnitude beyond anything real.
     * Longer is a corrupt or hostile file, and the whole sheet is refused rather
     * than quietly trimmed -- an import that silently drops text is worse than one
     * that says it cannot read the file.</p>
     */
    private static final int MAX_CELL_LENGTH = 1000;

    private ExcelTimetableReader() {
    }

    public static List<Course> read(InputStream input)
            throws IOException, BiffException, TimetableImportException {
        return readWithReport(input).courses();
    }

    /**
     * Reads a workbook and reports what could not be turned into a course.
     *
     * <p>The grid is built once and handed to the parser, so this adds no work to
     * an import -- it only stops the counts from being thrown away at the boundary
     * between the workbook and the parser.</p>
     */
    public static TimetableImportParser.Report readWithReport(InputStream input)
            throws IOException, BiffException, TimetableImportException {
        if (input == null) {
            throw new TimetableImportException(
                    "\u65E0\u6CD5\u6253\u5F00\u6240\u9009\u6587\u4EF6");
        }
        Workbook workbook = Workbook.getWorkbook(input);
        try {
            if (workbook.getNumberOfSheets() == 0) {
                throw new TimetableImportException(
                        "\u6587\u4EF6\u4E2D\u6CA1\u6709\u5DE5\u4F5C\u8868");
            }
            Sheet sheet = workbook.getSheet(0);
            int rows = sheet.getRows();
            int columns = sheet.getColumns();
            // Checked before the grid is built, because building it is the
            // allocation: four arrays of rows x columns, spent before one cell has
            // been looked at. Only sheet 0 is read, so the number of sheets needs no
            // bound of its own.
            if (rows > MAX_ROWS || columns > MAX_COLUMNS) {
                throw new TimetableImportException("\u8868\u683C\u5C3A\u5BF8\u8FC7\u5927"
                        + "\uFF08" + rows + " \u884C \u00D7 " + columns
                        + " \u5217\uFF09\uFF0C\u65E0\u6CD5\u5BFC\u5165");
            }
            TimetableGrid grid = new TimetableGrid(rows, columns);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    String contents = sheet.getCell(column, row).getContents();
                    if (contents != null && contents.length() > MAX_CELL_LENGTH) {
                        throw new TimetableImportException("\u8868\u683C\u4E2D\u6709"
                                + "\u5355\u5143\u683C\u7684\u6587\u5B57\u8FC7\u957F\uFF0C"
                                + "\u65E0\u6CD5\u5BFC\u5165");
                    }
                    grid.put(row, column, contents);
                }
            }
            for (Range range : sheet.getMergedCells()) {
                int topRow = range.getTopLeft().getRow();
                int leftColumn = range.getTopLeft().getColumn();
                int bottomRow = range.getBottomRight().getRow();
                int rightColumn = range.getBottomRight().getColumn();
                if (topRow >= 0 && topRow < grid.rowCount()
                        && leftColumn >= 0 && leftColumn < grid.columnCount()
                        && bottomRow >= topRow && bottomRow < grid.rowCount()
                        && rightColumn >= leftColumn
                        && rightColumn < grid.columnCount()) {
                    grid.merge(topRow, leftColumn, bottomRow, rightColumn);
                }
            }
            return TimetableImportParser.parseWithReport(grid);
        } finally {
            workbook.close();
        }
    }
}
