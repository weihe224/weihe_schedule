package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests for picking the bell schedule that applies to a course.
 *
 * <p>This is the rule the user described as "different venues ring at different
 * times, but I still want to override it by hand": an explicit choice wins, then
 * the venue, then the standard schedule.</p>
 */
public class BellSchedulesTest {

    private static final String LAB = "\u5B9E\u9A8C\u697C\u4F5C\u606F";
    private static final String EAST = "\u4E1C\u6821\u533A\u4F5C\u606F";

    /**
     * Two venue schedules with their own hours.
     *
     * <p>Built here rather than taken from {@code presets()}: a fresh install
     * ships one schedule, because a second one would mean inventing times for a
     * building nobody has described yet.</p>
     */
    private static List<BellSchedule> venueSchedules() {
        int periods = BellSchedule.PERIODS;
        int[] labStarts = new int[periods];
        int[] labEnds = new int[periods];
        int[] eastStarts = new int[periods];
        int[] eastEnds = new int[periods];
        for (int period = 0; period < periods; period++) {
            labStarts[period] = 8 * 60 + 10 + period * 45;
            labEnds[period] = labStarts[period] + 40;
            eastStarts[period] = 8 * 60 + 30 + period * 45;
            eastEnds[period] = eastStarts[period] + 40;
        }
        return new ArrayList<>(Arrays.asList(
                BellSchedules.standard(),
                new BellSchedule(LAB, labStarts, labEnds,
                        Arrays.asList("\u5B9E\u9A8C\u697C", "\u5B9E\u8BAD\u697C")),
                new BellSchedule(EAST, eastStarts, eastEnds,
                        Arrays.asList("\u4E1C\u6821\u533A", "\u4E1C\u533A"))));
    }

    @Test
    public void aFreshInstallShipsTheThreeSchedulesTheSchoolUses() {
        List<BellSchedule> presets = BellSchedules.presets();

        assertEquals(3, presets.size());
        assertEquals(BellSchedules.STANDARD_NAME, presets.get(0).name());
        assertEquals(BellSchedules.DE_NAME, presets.get(1).name());
        assertEquals(BellSchedules.BC_NAME, presets.get(2).name());
        // Every group claims its own buildings, so no two of them can fight over
        // the same venue and none is left unclaimed.
        for (BellSchedule preset : presets) {
            assertTrue(preset.name() + " should name its venues",
                    preset.locationKeywords().size() > 0);
        }
    }

    @Test
    public void everyScheduleDescribesEveryPeriodWithAUsableLength() {
        for (BellSchedule schedule : BellSchedules.presets()) {
            assertEquals((long) BellSchedule.PERIODS, schedule.periodCount());
            for (int period = 1; period <= schedule.periodCount(); period++) {
                int length = schedule.endMinute(period) - schedule.startMinute(period);
                // 40 minutes is the norm; the school's morning block splits
                // 9:55-11:15 unevenly, which is the table's business, not a bug.
                assertTrue("period " + period + " of " + schedule.name()
                        + " lasts " + length + " minutes", length >= 20 && length <= 90);
            }
        }
    }

    @Test
    public void theStandardScheduleStartsAtTheFirstPeriodOfTheDay() {
        BellSchedule standard = BellSchedules.standard();

        assertEquals("08:15", BellSchedule.label(standard.startMinute(1)));
        assertEquals("08:55", BellSchedule.label(standard.endMinute(1)));
        // The default has no breaks in it: that is the honest placeholder, and
        // the editor's quick fill is how the real ones get in.
        assertEquals(standard.endMinute(1), standard.startMinute(2));
    }

    @Test
    public void anExplicitNameWins() {
        BellSchedule chosen = BellSchedules.resolve(venueSchedules(), EAST,
                "\u5B9E\u9A8C\u697C 302");

        assertEquals(EAST, chosen.name());
    }

    @Test
    public void theLocationDecidesWhenNoNameIsGiven() {
        assertEquals(LAB, BellSchedules.resolve(venueSchedules(), "",
                "\u5B9E\u9A8C\u697C 3-302").name());
        assertEquals(EAST, BellSchedules.resolve(venueSchedules(), "",
                "\u4E1C\u6821\u533A \u6559\u5B66\u697C 101").name());
    }

    @Test
    public void anUnknownLocationFallsBackToTheStandardScheduleWhenThereIsNoOtherGroup() {
        // This list has no "other teaching places" schedule, so the fallback is the
        // standard one. With the real presets the same venue goes to the B/C group
        // instead, which BellScheduleTest pins down.
        assertEquals(BellSchedules.STANDARD_NAME,
                BellSchedules.resolve(venueSchedules(), "",
                        "\u535A\u5B66\u697C A203").name());
    }

    @Test
    public void aStaleNameFallsBackToTheLocation() {
        // The user deleted the schedule this course pointed at; the venue still
        // tells us which one to use.
        assertEquals(LAB, BellSchedules.resolve(venueSchedules(),
                "\u5DF2\u5220\u9664\u7684\u4F5C\u606F", "\u5B9E\u9A8C\u697C 302").name());
    }

    @Test
    public void anEmptyListStillYieldsTheBuiltInStandardSchedule() {
        BellSchedule chosen = BellSchedules.resolve(new ArrayList<BellSchedule>(), "",
                "\u5B9E\u9A8C\u697C");

        assertEquals(BellSchedules.STANDARD_NAME, chosen.name());
        assertEquals("08:15 - 08:55", chosen.timeRange(1, 1));
    }

    @Test
    public void theStoredStandardScheduleIsPreferredOverTheBuiltInOne() {
        List<BellSchedule> edited = new ArrayList<>();
        int[] starts = new int[BellSchedule.PERIODS];
        int[] ends = new int[BellSchedule.PERIODS];
        for (int period = 0; period < BellSchedule.PERIODS; period++) {
            starts[period] = 8 * 60 + 20 + period * 50;
            ends[period] = starts[period] + 40;
        }
        edited.add(BellSchedules.standard().withTimes(starts, ends));

        BellSchedule chosen = BellSchedules.resolve(edited, "", "");

        assertEquals("08:20 - 09:00", chosen.timeRange(1, 1));
    }

    @Test
    public void byNameIgnoresSurroundingSpaceAndRejectsBlanks() {
        assertNotNull(BellSchedules.byName(venueSchedules(), "  " + LAB + "  "));
        assertNull(BellSchedules.byName(venueSchedules(), ""));
        assertNull(BellSchedules.byName(venueSchedules(), "   "));
        assertNull(BellSchedules.byName(venueSchedules(), null));
        assertNull(BellSchedules.byName(venueSchedules(), "\u4E0D\u5B58\u5728"));
    }

    @Test
    public void forLocationPicksTheFirstMatchingSchedule() {
        BellSchedule chosen = BellSchedules.forLocation(venueSchedules(),
                "\u5B9E\u9A8C\u697C");

        assertNotNull(chosen);
        assertEquals(LAB, chosen.name());
        assertNull(BellSchedules.forLocation(venueSchedules(), "\u64CD\u573A"));
    }

    @Test
    public void resolveReturnsAScheduleWithUsableTimesEvenWithNoInput() {
        BellSchedule chosen = BellSchedules.resolve(null, null, null);

        assertNotNull(chosen);
        assertEquals("08:15 - 08:55", chosen.timeRange(1, 1));
    }

    @Test
    public void editingATimeTableLeavesTheOriginalAlone() {
        BellSchedule original = BellSchedules.standard();
        BellSchedule edited = original.withTimes(new int[] {500}, new int[] {545});

        assertEquals("08:15 - 08:55", original.timeRange(1, 1));
        assertEquals("08:20 - 09:05", edited.timeRange(1, 1));
        assertSame(BellSchedules.STANDARD_NAME, original.name());
    }

    @Test
    public void aVenueScheduleWithNoKeywordsNeverMatchesALocation() {
        assertTrue(!BellSchedules.standard().matchesLocation("\u5B9E\u9A8C\u697C"));
    }
}
