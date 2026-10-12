package com.weihe.schedule.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

/**
 * The month a date picker draws.
 *
 * <p>A calendar laid out one day out puts every date under the wrong weekday, and
 * nothing about the result looks wrong at a glance -- it is the kind of fault that
 * survives a screenshot review and then misdates a term. So the layout is checked as
 * arithmetic rather than by eye.</p>
 *
 * <h2>The invariant</h2>
 *
 * <p>Every non-blank cell at index {@code i} must fall in column {@code i % 7}, and
 * its own weekday must be that column's -- Monday first. That single check covers
 * the blanks, the length of the month, the leap year and the year boundary at once,
 * and it cannot be satisfied by a grid that is off by a day.</p>
 */
public class PickersCalendarTest {

    private static final LocalDate[] MONTHS = {
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2028, 2, 1)
    };

    @Test
    public void everyDaySitsUnderItsOwnWeekday() {
        for (LocalDate month : MONTHS) {
            List<LocalDate> cells = Pickers.monthCells(month);
            for (int i = 0; i < cells.size(); i++) {
                LocalDate cell = cells.get(i);
                if (cell != null) {
                    assertEquals("column of " + cell + " in " + month,
                            (i % 7) + 1, cell.getDayOfWeek().getValue());
                }
            }
        }
    }

    @Test
    public void theGridIsWholeWeeks() {
        for (LocalDate month : MONTHS) {
            int size = Pickers.monthCells(month).size();
            assertEquals("cells in " + month, 0, size % 7);
            assertTrue(size >= 28 && size <= 42);
        }
    }

    @Test
    public void theBlanksAreOnlyAtTheEnds() {
        // Blanks before the first day and after the last are how the month is padded
        // into whole weeks. A blank between two days would be a missing date, which
        // the eye reads as a gap in the month rather than as a layout fault.
        for (LocalDate month : MONTHS) {
            boolean started = false;
            boolean finished = false;
            int expectedDay = 1;
            for (LocalDate cell : Pickers.monthCells(month)) {
                if (cell == null) {
                    assertTrue("blank between days in " + month,
                            !started || finished);
                    continue;
                }
                assertTrue("a day after the month ended in " + month, !finished);
                assertEquals("day order in " + month, expectedDay,
                        cell.getDayOfMonth());
                expectedDay++;
                started = true;
                finished = cell.getDayOfMonth() == month.lengthOfMonth();
            }
            assertTrue("the month never started in " + month, started);
            assertTrue("the month never ended in " + month, finished);
        }
    }

    @Test
    public void theBlanksBeforeTheFirstMatchTheWeekdayItFallsOn() {
        // 2026-09-01 is a Tuesday, so exactly one blank precedes it.
        List<LocalDate> september = Pickers.monthCells(LocalDate.of(2026, 9, 1));
        assertNull(september.get(0));
        assertEquals(LocalDate.of(2026, 9, 1), september.get(1));

        // 2026-06-01 is a Monday: no blanks at all.
        assertEquals(LocalDate.of(2026, 6, 1),
                Pickers.monthCells(LocalDate.of(2026, 6, 1)).get(0));

        // 2026-11-01 is a Sunday: six blanks, and it lands in the last column.
        List<LocalDate> november = Pickers.monthCells(LocalDate.of(2026, 11, 1));
        for (int i = 0; i < 6; i++) {
            assertNull(november.get(i));
        }
        assertEquals(LocalDate.of(2026, 11, 1), november.get(6));
    }

    @Test
    public void everyDayOfTheMonthAppearsExactlyOnce() {
        for (LocalDate month : MONTHS) {
            List<LocalDate> cells = Pickers.monthCells(month);
            assertEquals("days in " + month, month.lengthOfMonth(), countDays(cells));
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                assertTrue("missing " + month.withDayOfMonth(day),
                        cells.contains(month.withDayOfMonth(day)));
            }
        }
    }

    @Test
    public void februaryInALeapYearHasTwentyNine() {
        assertEquals(29, countDays(Pickers.monthCells(LocalDate.of(2028, 2, 1))));
        assertEquals(28, countDays(Pickers.monthCells(LocalDate.of(2026, 2, 1))));
        assertTrue(Pickers.monthCells(LocalDate.of(2028, 2, 1))
                .contains(LocalDate.of(2028, 2, 29)));
    }

    @Test
    public void aMonthBeginningOnSundayNeedsSixRows() {
        // The worst case for height: 2026-11-01 is a Sunday, so six blanks plus
        // thirty days runs to six weeks.
        assertEquals(42, Pickers.monthCells(LocalDate.of(2026, 11, 1)).size());
        assertEquals(35, Pickers.monthCells(LocalDate.of(2026, 6, 1)).size());
    }

    @Test
    public void anyDayOfTheMonthLaysOutTheSameGrid() {
        // The picker is handed a date, not necessarily the first of the month.
        assertEquals(Pickers.monthCells(LocalDate.of(2026, 9, 1)),
                Pickers.monthCells(LocalDate.of(2026, 9, 17)));
        assertEquals(Pickers.monthCells(LocalDate.of(2026, 12, 1)),
                Pickers.monthCells(LocalDate.of(2026, 12, 31)));
    }

    @Test
    public void theGridNeverReachesIntoAnotherMonth() {
        for (LocalDate month : MONTHS) {
            for (LocalDate cell : Pickers.monthCells(month)) {
                if (cell != null) {
                    assertEquals("month of " + cell, month.getMonthValue(),
                            cell.getMonthValue());
                    assertEquals("year of " + cell, month.getYear(), cell.getYear());
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int countDays(List<LocalDate> cells) {
        int days = 0;
        for (LocalDate cell : cells) {
            if (cell != null) {
                days++;
            }
        }
        return days;
    }
}
