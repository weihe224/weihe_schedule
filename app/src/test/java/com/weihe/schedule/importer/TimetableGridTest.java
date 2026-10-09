package com.weihe.schedule.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The worksheet model that sits between the reader and the parser.
 *
 * <p>Both of its jobs are structural rather than textual, and neither is visible
 * from the outside: a cell has to say whether it is the top-left corner of a
 * merged region, and where that region ends. The parser relies on exactly that
 * to read a course once instead of once per row it spans, so an error here shows
 * up as duplicated or mislocated courses rather than as an exception.</p>
 *
 * <p>Bounds are part of the contract too: the reader feeds the grid cell
 * coordinates taken from the sheet, so out-of-range access has to fail loudly
 * instead of silently reading a neighbouring cell.</p>
 */
public class TimetableGridTest {

    @Test
    public void aFreshGridIsBlankAndEveryCellStartsAsItsOwnTopLeft() {
        TimetableGrid grid = new TimetableGrid(3, 4);

        assertEquals(3, grid.rowCount());
        assertEquals(4, grid.columnCount());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                assertEquals("", grid.cell(row, column));
                assertTrue(grid.isTopLeft(row, column));
                assertEquals(row, grid.mergedEndRow(row, column));
                assertEquals(column, grid.mergedEndColumn(row, column));
            }
        }
    }

    @Test
    public void putTreatsNullAsBlankText() {
        TimetableGrid grid = new TimetableGrid(1, 2);

        grid.put(0, 1, null);

        assertEquals("", grid.cell(0, 1));
    }

    @Test
    public void aMergeLeavesOnlyTheTopLeftCellMarkedAsTopLeft() {
        TimetableGrid grid = new TimetableGrid(5, 3);

        grid.merge(1, 1, 3, 2);

        for (int row = 1; row <= 3; row++) {
            for (int column = 1; column <= 2; column++) {
                boolean expected = row == 1 && column == 1;
                assertEquals("cell " + row + "," + column,
                        expected, grid.isTopLeft(row, column));
            }
        }
        // Everything outside the merge is untouched.
        assertTrue(grid.isTopLeft(0, 0));
        assertTrue(grid.isTopLeft(4, 2));
        assertTrue(grid.isTopLeft(1, 0));
    }

    @Test
    public void aMergeRemembersItsOwnBottomRightCorner() {
        TimetableGrid grid = new TimetableGrid(6, 4);

        grid.merge(2, 1, 5, 3);

        assertEquals(5, grid.mergedEndRow(2, 1));
        assertEquals(3, grid.mergedEndColumn(2, 1));
        // The covered cells keep their own default self-reference; only the
        // top-left carries the region.
        assertEquals(5, grid.mergedEndRow(5, 3));
        assertEquals(3, grid.mergedEndColumn(5, 3));
    }

    @Test
    public void aOneCellMergeIsStillTheTopLeftOfItself() {
        TimetableGrid grid = new TimetableGrid(3, 3);

        grid.merge(1, 1, 1, 1);

        assertTrue(grid.isTopLeft(1, 1));
        assertEquals(1, grid.mergedEndRow(1, 1));
        assertEquals(1, grid.mergedEndColumn(1, 1));
    }

    @Test
    public void twoSeparateMergesEachKeepTheirOwnTopLeft() {
        // A sheet reports every merged range at once and the reader applies them
        // in order, so one range must not clear another range's top-left cell.
        TimetableGrid grid = new TimetableGrid(6, 4);

        grid.merge(1, 1, 3, 1);
        grid.merge(1, 3, 3, 3);

        assertTrue(grid.isTopLeft(1, 1));
        assertTrue(grid.isTopLeft(1, 3));
        assertEquals(3, grid.mergedEndRow(1, 1));
        assertEquals(1, grid.mergedEndColumn(1, 1));
        assertEquals(3, grid.mergedEndRow(1, 3));
        assertEquals(3, grid.mergedEndColumn(1, 3));
        // The cells between the two merges stay their own top-left.
        assertTrue(grid.isTopLeft(2, 2));
        assertTrue(grid.isTopLeft(1, 2));
    }

    @Test
    public void cellsOutsideTheGridAreRejected() {
        TimetableGrid grid = new TimetableGrid(2, 3);

        assertRejected(grid, -1, 0);
        assertRejected(grid, 0, -1);
        assertRejected(grid, 2, 0);
        assertRejected(grid, 0, 3);
    }

    @Test
    public void aMergeReachingOutsideTheGridIsRejected() {
        TimetableGrid grid = new TimetableGrid(3, 3);

        try {
            grid.merge(1, 1, 3, 1);
            fail("expected the merge to be rejected");
        } catch (IndexOutOfBoundsException expected) {
            assertEquals("cell outside grid", expected.getMessage());
        }
    }

    @Test
    public void aGridWithNoRowsReportsNoColumns() {
        // columnCount() must not reach into cells[0] when there are no rows; the
        // reader builds exactly this grid for a worksheet that holds nothing.
        TimetableGrid grid = new TimetableGrid(0, 5);

        assertEquals(0, grid.rowCount());
        assertEquals(0, grid.columnCount());
        assertRejected(grid, 0, 0);
    }

    private static void assertRejected(TimetableGrid grid, int row, int column) {
        try {
            grid.cell(row, column);
            fail("expected cell " + row + "," + column + " to be rejected");
        } catch (IndexOutOfBoundsException expected) {
            assertEquals("cell outside grid", expected.getMessage());
        }
    }

    @Test
    public void aGridWithRowsButNoColumnsReportsItsRowsAndRejectsAccess() {
        TimetableGrid grid = new TimetableGrid(3, 0);

        assertEquals(3, grid.rowCount());
        assertEquals(0, grid.columnCount());
        assertRejected(grid, 0, 0);
        assertRejected(grid, 2, 0);
    }
}
