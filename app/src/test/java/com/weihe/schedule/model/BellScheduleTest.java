package com.weihe.schedule.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests for a single bell schedule.
 *
 * <p>This is the half of the per-venue timing feature that can be checked
 * without a device, and it is also what protects
 * {@link ScheduleUtils#periodTime(int, int)}, which now delegates here.</p>
 */
public class BellScheduleTest {

    private static BellSchedule schedule(List<String> keywords) {
        return new BellSchedule("\u6D4B\u8BD5",
                new int[] {480, 535, 600},
                new int[] {525, 580, 645},
                keywords);
    }

    @Test
    public void everyGroupMatchesTheSchoolsTable() {
        BellSchedule af = BellSchedules.standard();
        BellSchedule bc = BellSchedules.bcBuilding();
        BellSchedule de = BellSchedules.deBuilding();

        // Periods 1-2 and 6 onwards are the same for the whole campus.
        for (BellSchedule schedule : new BellSchedule[] {af, bc, de}) {
            assertEquals("08:15 - 09:35", schedule.timeRange(1, 2));
            assertEquals("08:15 - 08:55", schedule.timeRange(1, 1));
            assertEquals("08:55 - 09:35", schedule.timeRange(2, 2));
            assertEquals("13:20 - 14:40", schedule.timeRange(6, 7));
            assertEquals("15:00 - 16:20", schedule.timeRange(8, 9));
            assertEquals("16:35 - 17:55", schedule.timeRange(10, 11));
            assertEquals("18:10 - 19:30", schedule.timeRange(12, 13));
            assertEquals("19:35 - 20:15", schedule.timeRange(14, 14));
            assertEquals("20:20 - 21:00", schedule.timeRange(15, 15));
        }

        // A, F and J301: period 3 alone, then the 10:40-12:00 block.
        assertEquals(BellSchedules.STANDARD_NAME, af.name());
        assertEquals("09:55 - 10:35", af.timeRange(3, 3));
        assertEquals("10:40 - 12:00", af.timeRange(4, 5));
        assertEquals("10:40 - 11:20", af.timeRange(4, 4));
        assertEquals("11:20 - 12:00", af.timeRange(5, 5));

        // B, C and J302: the 09:55-11:15 block, then period 5 on its own.
        assertEquals(BellSchedules.BC_NAME, bc.name());
        assertEquals("09:55 - 11:15", bc.timeRange(3, 4));
        assertEquals("09:55 - 10:35", bc.timeRange(3, 3));
        assertEquals("10:35 - 11:15", bc.timeRange(4, 4));
        assertEquals("11:20 - 12:00", bc.timeRange(5, 5));

        // D, E and J303: the whole morning runs later.
        assertEquals(BellSchedules.DE_NAME, de.name());
        assertEquals("10:15 - 11:35", de.timeRange(3, 4));
        assertEquals("10:15 - 10:55", de.timeRange(3, 3));
        assertEquals("10:55 - 11:35", de.timeRange(4, 4));
        assertEquals("11:40 - 12:20", de.timeRange(5, 5));
    }

    @Test
    public void theThreeGroupsDifferOnlyWhereTheTableSaysTheyDo() {
        BellSchedule af = BellSchedules.standard();
        BellSchedule bc = BellSchedules.bcBuilding();
        BellSchedule de = BellSchedules.deBuilding();

        // Period 4 is the one place where all three groups read differently:
        // 10:40, 10:35 and 10:55.
        assertEquals("10:40 - 11:20", af.timeRange(4, 4));
        assertEquals("10:35 - 11:15", bc.timeRange(4, 4));
        assertEquals("10:55 - 11:35", de.timeRange(4, 4));

        // A/F and B/C share periods 3 and 5, and differ only at period 4.
        assertEquals(af.timeRange(3, 3), bc.timeRange(3, 3));
        assertEquals(af.timeRange(5, 5), bc.timeRange(5, 5));
        assertTrue(!af.timeRange(4, 4).equals(bc.timeRange(4, 4)));

        // D/E differs from both at 3, 4 and 5 -- its whole morning is later.
        for (int period : new int[] {3, 4, 5}) {
            assertTrue("D/E period " + period + " should differ from A/F",
                    !de.timeRange(period, period).equals(af.timeRange(period, period)));
            assertTrue("D/E period " + period + " should differ from B/C",
                    !de.timeRange(period, period).equals(bc.timeRange(period, period)));
        }

        // Every other period is identical across all three.
        for (int period = 1; period <= 15; period++) {
            if (period >= 3 && period <= 5) {
                continue;
            }
            assertEquals(af.timeRange(period, period), bc.timeRange(period, period));
            assertEquals(af.timeRange(period, period), de.timeRange(period, period));
        }
    }

    @Test
    public void periodCountFollowsTheTable() {
        assertEquals(3L, schedule(new ArrayList<String>()).periodCount());
    }

    @Test
    public void timeRangeClampsTheStartUpToTheFirstPeriod() {
        BellSchedule schedule = schedule(new ArrayList<String>());

        assertEquals(schedule.timeRange(1, 1), schedule.timeRange(0, 0));
        assertEquals(schedule.timeRange(1, 1), schedule.timeRange(-7, -2));
    }

    @Test
    public void timeRangeClampsTheEndDownToTheLastPeriod() {
        BellSchedule schedule = schedule(new ArrayList<String>());

        assertEquals(schedule.timeRange(3, 3), schedule.timeRange(30, 90));
    }

    @Test
    public void timeRangeNeverInverts() {
        BellSchedule schedule = schedule(new ArrayList<String>());

        assertEquals(schedule.timeRange(2, 2), schedule.timeRange(2, 1));
    }

    @Test
    public void aScheduleWithNoKeywordsNeverMatchesALocation() {
        assertFalse(schedule(new ArrayList<String>()).matchesLocation("\u5B9E\u9A8C\u697C 302"));
        assertFalse(BellSchedules.standard().matchesLocation("\u4EFB\u610F\u5730\u70B9"));
    }

    @Test
    public void keywordsMatchInsideTheLocationText() {
        BellSchedule lab = schedule(Arrays.asList("\u5B9E\u9A8C\u697C"));

        assertTrue(lab.matchesLocation("\u5B9E\u9A8C\u697C 3-302"));
        assertTrue(lab.matchesLocation("  \u5B9E\u9A8C\u697C  "));
        assertFalse(lab.matchesLocation("\u535A\u5B66\u697C A203"));
    }

    @Test
    public void matchingIgnoresCaseAndBlankKeywordEntries() {
        BellSchedule mixed = schedule(Arrays.asList("  ", "Lab"));

        assertTrue(mixed.matchesLocation("physics lab 201"));
        assertFalse(mixed.matchesLocation(""));
        assertFalse(mixed.matchesLocation(null));
    }

    @Test
    public void labelPadsHoursAndMinutes() {
        assertEquals("00:00", BellSchedule.label(0));
        assertEquals("08:00", BellSchedule.label(480));
        assertEquals("09:05", BellSchedule.label(545));
        assertEquals("24:00", BellSchedule.label(24 * 60));
        assertEquals("00:00", BellSchedule.label(-5));
    }

    @Test
    public void parseLabelReadsBothOneAndTwoDigitHours() {
        assertEquals(480L, BellSchedule.parseLabel("08:00").intValue());
        assertEquals(485L, BellSchedule.parseLabel("8:05").intValue());
        assertEquals(485L, BellSchedule.parseLabel("  8:5  ").intValue());
    }

    @Test
    public void parseLabelRejectsAnythingThatIsNotATime() {
        assertNull(BellSchedule.parseLabel(null));
        assertNull(BellSchedule.parseLabel(""));
        assertNull(BellSchedule.parseLabel("abc"));
        assertNull(BellSchedule.parseLabel("8"));
        assertNull(BellSchedule.parseLabel(":30"));
        assertNull(BellSchedule.parseLabel("8:"));
        assertNull(BellSchedule.parseLabel("30:00"));
        assertNull(BellSchedule.parseLabel("08:60"));
    }

    @Test
    public void parseLabelAcceptsTheHoursLabelWritesForLatePeriods() {
        // 24:05 rather than 00:05: the app keeps an evening period on the same
        // day as the timetable it belongs to, so the parser has to agree.
        assertEquals(24 * 60L, BellSchedule.parseLabel("24:00").intValue());
        assertEquals(24 * 60 + 55L, BellSchedule.parseLabel("24:55").intValue());
        assertEquals(25 * 60L, BellSchedule.parseLabel("25:00").intValue());
        assertEquals(29 * 60 + 59L, BellSchedule.parseLabel("29:59").intValue());
    }

    @Test
    public void labelAndParseRoundTrip() {
        for (int minute = 0; minute < 24 * 60; minute += 7) {
            Integer parsed = BellSchedule.parseLabel(BellSchedule.label(minute));
            assertEquals((long) minute, parsed.intValue());
        }
        for (int minute = 24 * 60; minute < 29 * 60; minute += 11) {
            Integer parsed = BellSchedule.parseLabel(BellSchedule.label(minute));
            assertEquals((long) minute, parsed.intValue());
        }
    }

    @Test
    public void copyingKeepsTheOtherFields() {
        BellSchedule original = schedule(Arrays.asList("\u5B9E\u9A8C\u697C"));

        BellSchedule retimed = original.withTimes(
                new int[] {600, 700, 800}, new int[] {650, 750, 850});
        assertEquals(600L, retimed.startMinute(1));
        assertEquals("\u6D4B\u8BD5", retimed.name());
        assertEquals("\u5B9E\u9A8C\u697C", retimed.locationKeywords().get(0));

        BellSchedule renamed = original.renamed("\u65B0\u540D");
        assertEquals("\u65B0\u540D", renamed.name());
        assertEquals(480L, renamed.startMinute(1));
        assertEquals(3L, renamed.periodCount());
    }

    @Test
    public void aMismatchedTableIsRejected() {
        boolean thrown = false;
        try {
            new BellSchedule("bad", new int[] {1, 2}, new int[] {3},
                    new ArrayList<String>());
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void anEmptyTableIsRejected() {
        boolean thrown = false;
        try {
            new BellSchedule("bad", new int[0], new int[0], new ArrayList<String>());
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown);
    }
}
