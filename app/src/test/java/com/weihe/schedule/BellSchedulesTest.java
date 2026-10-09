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

    // ------------------------------------------------------- the real presets

    /** Resolution against the three schedules a fresh install ships with. */
    private static String resolveOnPresets(String location) {
        return BellSchedules.resolve(BellSchedules.presets(), "", location).name();
    }

    /**
     * The building letter standing on its own, in front of a room number.
     *
     * <p>This is the form the app's own sample courses use ("教学楼 A203") and the
     * form its own venue hint suggests, and it contains no "A楼": a plain
     * substring search matched none of them, so every course in every building
     * fell through to the B/C group. For a D/E room that is twenty minutes early
     * in periods 3 to 5 -- the app telling a student the wrong time to turn up.</p>
     */
    @Test
    public void aBuildingLetterInFrontOfARoomNumberSelectsThatBuildingsSchedule() {
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("\u6559\u5B66\u697C A203"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("\u6559\u5B66\u697C F305"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets("\u6559\u5B66\u697C B203"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets("\u6559\u5B66\u697C C203"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697C D305"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697C E302"));

        // No "教学楼" at all, and a prefix that is not a building.
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("D305"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("A203"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u56DB\u6559D305"));
    }

    @Test
    public void theBuildingLetterFormStillWorksWhenShangKeLouIsAttached() {
        // The older, already-working spelling must not regress.
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("D\u697C305"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("A\u697C203"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697CD\u697C305"));
    }

    @Test
    public void aVenueThatNamesNoBuildingFallsBackToTheOtherTeachingPlaces() {
        // The school's table puts "other teaching places" in the B/C group, and
        // nothing used to check that against the real presets -- the only test of
        // the fallback used a hand-built list with no B/C schedule in it.
        assertEquals(BellSchedules.BC_NAME,
                resolveOnPresets("\u73B0\u4EE3\u5DE5\u4E1A\u8BAD\u7EC3\u4E2D\u5FC3\u8BAD2307"));
        assertEquals(BellSchedules.BC_NAME,
                resolveOnPresets("\u73B0\u4EE3\u4EA4\u901A\u5DE5\u7A0B\u4E2D\u5FC3\u4EA47509"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets("\u4E1C\u64CD\u573A"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets(""));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets(null));
    }

    @Test
    public void aRoomNumberKeywordDoesNotWidenIntoABuilding() {
        // "j301" is a room keyword, not "<letter>楼", so it must stay a plain
        // substring test: a J-room that is not one of the three named ones must
        // not start selecting the A/F schedule on the strength of its letter.
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("J301 \u5BA4"));
        assertNull(BellSchedules.forLocation(BellSchedules.presets(), "J999 \u5BA4"));
        // The same for a keyword that is not a building at all.
        assertNull(BellSchedules.forLocation(BellSchedules.presets(), "\u5B9E\u9A8C\u697C301"));
    }

    /**
     * Every venue the sample timetable seeds, against the schedule it should get.
     *
     * <p>Pinned as a table rather than left to the resolution tests above: the
     * first thing a new user sees is these nine courses, and eight of them used to
     * resolve to the wrong group.</p>
     */
    @Test
    public void theSampleVenuesReachTheScheduleTheirBuildingRings() {
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("\u6559\u5B66\u697C A203"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("\u6559\u5B66\u697C F305"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets("\u6559\u5B66\u697C C203"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697C D102"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697C E302"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("\u6559\u5B66\u697C A201"));
        assertEquals(BellSchedules.BC_NAME, resolveOnPresets("\u4E1C\u64CD\u573A"));
        assertEquals(BellSchedules.STANDARD_NAME, resolveOnPresets("J301 \u5BA4"));
        assertEquals(BellSchedules.DE_NAME, resolveOnPresets("\u6559\u5B66\u697C E402"));
    }

    @Test
    public void theCorrectedVenueShowsTheBuildingsOwnClockTimes() {
        // The name is not the point; the minutes are. Periods 3-5 are where the
        // three groups disagree, and where the wrong group used to be twenty
        // minutes early for a D/E room.
        BellSchedule de = BellSchedules.resolve(BellSchedules.presets(), "",
                "\u6559\u5B66\u697C D305");
        assertEquals("10:15 - 10:55", de.timeRange(3, 3));
        assertEquals("10:55 - 11:35", de.timeRange(4, 4));
        assertEquals("11:40 - 12:20", de.timeRange(5, 5));

        BellSchedule standard = BellSchedules.resolve(BellSchedules.presets(), "",
                "\u6559\u5B66\u697C A203");
        assertEquals("09:55 - 10:35", standard.timeRange(3, 3));
        assertEquals("10:40 - 11:20", standard.timeRange(4, 4));

        BellSchedule bc = BellSchedules.resolve(BellSchedules.presets(), "",
                "\u6559\u5B66\u697C C203");
        assertEquals("10:35 - 11:15", bc.timeRange(4, 4));
    }

    @Test
    public void anExplicitChoiceStillBeatsTheVenueAfterTheMatchingChange() {
        assertEquals(BellSchedules.BC_NAME, BellSchedules.resolve(
                BellSchedules.presets(), BellSchedules.BC_NAME,
                "\u6559\u5B66\u697C D305").name());
    }
}
