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
            TimetableGrid grid = new TimetableGrid(sheet.getRows(), sheet.getColumns());
            for (int row = 0; row < sheet.getRows(); row++) {
                for (int column = 0; column < sheet.getColumns(); column++) {
                    grid.put(row, column, sheet.getCell(column, row).getContents());
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
