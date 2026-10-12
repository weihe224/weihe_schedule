package com.weihe.schedule.ui;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;

import org.junit.Test;

/**
 * The reading and writing side of the date and time pickers.
 *
 * <p>The dialogs themselves need a device; what they are initialised from and what
 * they hand back does not. These cover the part that has to survive contact with
 * data written before the pickers existed: a stored {@code 8.00}, a stored
 * {@code 2026-08-31}, an empty field, and something unreadable.</p>
 */
public class PickersTest {

    // ============================================ writing

    @Test
    public void datesAreWrittenOneWay() {
        assertEquals("2026-08-31", Pickers.formatDate(LocalDate.parse("2026-08-31")));
        assertEquals("2027-02-22", Pickers.formatDate(LocalDate.parse("2027-02-22")));
        assertEquals("2028-02-29", Pickers.formatDate(LocalDate.parse("2028-02-29")));
    }

    @Test
    public void timesAreZeroPaddedToFourCharacters() {
        assertEquals("08:00", Pickers.formatTime(8, 0));
        assertEquals("09:05", Pickers.formatTime(9, 5));
        assertEquals("13:30", Pickers.formatTime(13, 30));
        assertEquals("00:00", Pickers.formatTime(0, 0));
        assertEquals("23:59", Pickers.formatTime(23, 59));
    }

    // ============================================ reading, for where a dialog opens

    @Test
    public void aStoredDateIsWhereTheCalendarOpens() {
        assertEquals(LocalDate.parse("2026-08-31"),
                Pickers.dateOr("2026-08-31", LocalDate.parse("2000-01-01")));
    }

    @Test
    public void anEmptyOrUnreadableDateOpensOnTheFallback() {
        LocalDate fallback = LocalDate.parse("2026-08-31");
        assertEquals(fallback, Pickers.dateOr("", fallback));
        assertEquals(fallback, Pickers.dateOr("   ", fallback));
        assertEquals(fallback, Pickers.dateOr("not a date", fallback));
        assertEquals(fallback, Pickers.dateOr(null, fallback));
        assertEquals(fallback, Pickers.dateOr("2027-02-30", fallback));
    }

    @Test
    public void everySpellingOfATimeOpensOnTheSameHourAndMinute() {
        // The schedule has always accepted these; the clock has to open on the time
        // they mean rather than falling back to midnight.
        for (String spelling : new String[] {"08:00", "8:00", "8.00", "800", "0800",
                "8：00"}) {
            assertEquals("hour of " + spelling, 8, Pickers.hourOr(spelling, 0));
            assertEquals("minute of " + spelling, 0, Pickers.minuteOr(spelling, 0));
        }
        assertEquals("9", 9, Pickers.hourOr("09:35", 0));
        assertEquals("35", 35, Pickers.minuteOr("09:35", 0));
    }

    @Test
    public void anEmptyOrUnreadableTimeOpensOnTheFallback() {
        assertEquals(8, Pickers.hourOr("", 8));
        assertEquals(8, Pickers.hourOr("   ", 8));
        assertEquals(8, Pickers.hourOr("half typed", 8));
        assertEquals(8, Pickers.hourOr(null, 8));
        assertEquals(30, Pickers.minuteOr("", 30));
    }

    @Test
    public void aHalfTypedTimeDoesNotCrashTheClock() {
        // Nothing here may throw: these strings are what a stored field can hold
        // when it was written by an older build.
        for (String awkward : new String[] {"8.0", "8：", ":", ".", "99:99", "-1"}) {
            Pickers.hourOr(awkward, 8);
            Pickers.minuteOr(awkward, 0);
        }
    }

    // ============================================ round trips

    @Test
    public void aChosenTimeSurvivesTheRoundTripThroughItsOwnField() {
        // What the clock hands back has to be what the field shows and what the
        // schedule can read again.
        for (int hour = 0; hour < 24; hour++) {
            for (int minute : new int[] {0, 5, 30, 59}) {
                String written = Pickers.formatTime(hour, minute);
                assertEquals(written.length(), 5);
                assertEquals(hour, Pickers.hourOr(written, -1));
                assertEquals(minute, Pickers.minuteOr(written, -1));
            }
        }
    }

    @Test
    public void aChosenDateSurvivesTheRoundTripThroughItsOwnField() {
        LocalDate[] dates = {
            LocalDate.parse("2026-08-31"),
            LocalDate.parse("2027-02-22"),
            LocalDate.parse("2028-02-29"),   // leap day
            LocalDate.parse("2026-12-31"),   // year end
            LocalDate.parse("2027-01-01")    // year start
        };
        for (LocalDate date : dates) {
            assertEquals(date, Pickers.dateOr(Pickers.formatDate(date), null));
        }
    }

    @Test
    public void onlyTheStoredSpellingIsRead() {
        // Checked rather than assumed: parseDate reads yyyy-MM-dd and nothing else,
        // so a slash is not a second spelling to support -- it is simply not a date,
        // and the field falls back instead of throwing.
        assertEquals(LocalDate.parse("2026-08-31"),
                Pickers.dateOr("2026-08-31", null));
        assertEquals(null, Pickers.dateOr("2026/08/31", null));
        assertEquals(null, Pickers.dateOr("31-08-2026", null));
    }
}
