package com.weihe.schedule;

import java.util.Arrays;

/** In-memory worksheet data together with merged-cell geometry. */
public final class TimetableGrid {
    private final String[][] cells;
    private final boolean[][] topLeft;
    private final int[][] endRows;
    private final int[][] endColumns;

    public TimetableGrid(int rows, int columns) {
        cells = new String[rows][columns];
        topLeft = new boolean[rows][columns];
        endRows = new int[rows][columns];
        endColumns = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            Arrays.fill(topLeft[row], true);
            for (int column = 0; column < columns; column++) {
                cells[row][column] = "";
                endRows[row][column] = row;
                endColumns[row][column] = column;
            }
        }
    }

    public int rowCount() {
        return cells.length;
    }

    public int columnCount() {
        return cells.length == 0 ? 0 : cells[0].length;
    }

    public void put(int row, int column, String value) {
        checkCell(row, column);
        cells[row][column] = value == null ? "" : value;
    }

    public String cell(int row, int column) {
        checkCell(row, column);
        return cells[row][column];
    }

    public void merge(int topRow, int leftColumn, int bottomRow, int rightColumn) {
        checkCell(topRow, leftColumn);
        checkCell(bottomRow, rightColumn);
        for (int row = topRow; row <= bottomRow; row++) {
            for (int column = leftColumn; column <= rightColumn; column++) {
                topLeft[row][column] = false;
            }
        }
        topLeft[topRow][leftColumn] = true;
        endRows[topRow][leftColumn] = bottomRow;
        endColumns[topRow][leftColumn] = rightColumn;
    }

    public boolean isTopLeft(int row, int column) {
        checkCell(row, column);
        return topLeft[row][column];
    }

    public int mergedEndRow(int row, int column) {
        checkCell(row, column);
        return endRows[row][column];
    }

    public int mergedEndColumn(int row, int column) {
        checkCell(row, column);
        return endColumns[row][column];
    }

    private void checkCell(int row, int column) {
        if (row < 0) {
            throw new IndexOutOfBoundsException("cell outside grid");
        }
        if (row >= rowCount()) {
            throw new IndexOutOfBoundsException("cell outside grid");
        }
        if (column < 0) {
            throw new IndexOutOfBoundsException("cell outside grid");
        }
        if (column >= columnCount()) {
            throw new IndexOutOfBoundsException("cell outside grid");
        }
    }
}
