package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;

/**
 * Tests for the stored form of bell schedules.
 *
 * <p>Two things are being protected here. First, that a schedule survives a
 * write/read round trip with its times and keywords intact. Second -- and the
 * reason this class exists at all -- that a damaged payload degrades into the
 * built-in standard timetable instead of producing a schedule whose
 * {@code starts} and {@code ends} arrays disagree, which would throw the first
 * time a course asked for the end of its last period.</p>
 */
public class BellScheduleJsonTest {

    private static final String LAB = "\u5B9E\u9A8C\u697C\u4F5C\u606F";
    private static final String CHONGWEN = "\u5D07\u6587\u697C";

    @Test
    public void presetsSurviveARoundTrip() {
        List<BellSchedule> presets = BellSchedules.presets();

        List<BellSchedule> restored = BellScheduleJson.parse(
                BellScheduleJson.write(presets));

        assertEquals(presets.size(), restored.size());
        for (int i = 0; i < presets.size(); i++) {
            BellSchedule before = presets.get(i);
            BellSchedule after = restored.get(i);
            assertEquals(before.name(), after.name());
            assertEquals(before.periodCount(), after.periodCount());
            for (int period = 1; period <= before.periodCount(); period++) {
                assertEquals(before.startMinute(period), after.startMinute(period));
                assertEquals(before.endMinute(period), after.endMinute(period));
            }
        }
    }

    @Test
    public void keywordsSurviveARoundTrip() {
        List<BellSchedule> restored = BellScheduleJson.parse(BellScheduleJson.write(
                BellSchedules.presets()));

        // Each group keeps the venues it claims, and no two groups claim the same
        // one: index 0 is the A/F standard, index 1 the D/E group.
        assertTrue(restored.get(0).matchesLocation("\u6559\u5B66\u697CF\u697C F420"));
        assertTrue(restored.get(1).matchesLocation("\u6559\u5B66\u697CD\u697C D305"));
        assertFalse(restored.get(0).matchesLocation("\u6559\u5B66\u697CD\u697C D305"));
        assertFalse(restored.get(1).matchesLocation("\u6559\u5B66\u697CF\u697C F420"));
    }

    @Test
    public void aCustomScheduleKeepsItsTimes() {
        List<BellSchedule> custom = new ArrayList<>();
        custom.add(new BellSchedule(CHONGWEN,
                new int[] {500, 555, 620},
                new int[] {545, 600, 665},
                Arrays.asList(CHONGWEN)));

        BellSchedule restored = BellScheduleJson.parse(
                BellScheduleJson.write(custom)).get(0);

        assertEquals(CHONGWEN, restored.name());
        assertEquals(3, restored.periodCount());
        assertEquals("08:20 - 09:05", restored.timeRange(1, 1));
        assertEquals("10:20 - 11:05", restored.timeRange(3, 3));
        assertEquals(Arrays.asList(CHONGWEN), restored.locationKeywords());
    }

    @Test
    public void aScheduleWithoutKeywordsRoundTrips() {
        List<BellSchedule> custom = new ArrayList<>();
        custom.add(BellSchedules.standard()
                .withLocationKeywords(new ArrayList<String>()).withTimes(
                new int[] {500, 555, 620, 675, 860, 915, 980, 1035, 1160, 1215,
                        1270, 1320, 1370, 1420, 1470},
                new int[] {545, 600, 665, 720, 905, 960, 1025, 1080, 1205, 1260,
                        1315, 1365, 1415, 1465, 1515}));

        BellSchedule restored = BellScheduleJson.parse(
                BellScheduleJson.write(custom)).get(0);

        assertEquals(0, restored.locationKeywords().size());
        assertFalse(restored.matchesLocation("\u5B9E\u9A8C\u697C"));
        assertEquals("08:20 - 09:05", restored.timeRange(1, 1));
        assertEquals(BellSchedule.PERIODS, restored.periodCount());
    }

    @Test
    public void unreadablePayloadsYieldNoSchedules() {
        assertTrue(BellScheduleJson.parse(null).isEmpty());
        assertTrue(BellScheduleJson.parse("").isEmpty());
        assertTrue(BellScheduleJson.parse("   ").isEmpty());
        assertTrue(BellScheduleJson.parse("not json at all").isEmpty());
        assertTrue(BellScheduleJson.parse("{\"name\":\"single\"}").isEmpty());
    }

    @Test
    public void writingNothingProducesAnEmptyStore() {
        assertEquals("[]", BellScheduleJson.write(null));
        assertEquals("[]", BellScheduleJson.write(new ArrayList<BellSchedule>()));
    }

    @Test
    public void entriesThatAreNotObjectsAreSkipped() {
        String raw = "[null, 7, {\"name\":\"keep\"}, \"text\"]";

        List<BellSchedule> schedules = BellScheduleJson.parse(raw);

        assertEquals(1, schedules.size());
        assertEquals("keep", schedules.get(0).name());
    }

    @Test
    public void mismatchedTimeArraysFallBackToTheStandardTimetable() {
        // ends has five entries, starts none: without repair, asking this
        // schedule for the end of period 1 would read past the array.
        String raw = "[{\"name\":\"" + LAB + "\",\"keywords\":[\"" + CHONGWEN
                + "\"],\"starts\":[],\"ends\":[1,2,3,4,5]}]";

        BellSchedule repaired = BellScheduleJson.parse(raw).get(0);

        assertEquals(LAB, repaired.name());
        assertEquals(BellSchedules.standard().timeRange(1, 1), repaired.timeRange(1, 1));
        assertEquals(BellSchedule.PERIODS, repaired.periodCount());
        assertTrue(repaired.matchesLocation(CHONGWEN));
    }

    @Test
    public void timesOutsideTheDayAreRejectedTheSameWay() {
        String raw = "[{\"name\":\"bad\",\"starts\":[480,-5],\"ends\":[525,580]}]";

        BellSchedule repaired = BellScheduleJson.parse(raw).get(0);

        assertEquals(BellSchedules.standard().timeRange(1, 1), repaired.timeRange(1, 1));
    }

    @Test
    public void aMissingNameGetsAPlaceholderInsteadOfBlank() {
        String raw = "[{\"starts\":[480],\"ends\":[525]}]";

        BellSchedule restored = BellScheduleJson.parse(raw).get(0);

        assertFalse(restored.name().trim().isEmpty());
    }

    @Test
    public void aScheduleWithNoUsablePeriodsIsRepairedNotDropped() {
        String raw = "[{\"name\":\"\u7A7A\u7684\",\"starts\":[],\"ends\":[]}]";

        List<BellSchedule> schedules = BellScheduleJson.parse(raw);

        assertEquals(1, schedules.size());
        assertEquals(BellSchedule.PERIODS, schedules.get(0).periodCount());
    }
}
