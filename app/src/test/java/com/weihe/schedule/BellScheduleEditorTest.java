package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests for the rules behind the bell-schedule editor.
 *
 * <p>The screen is the only place that can type a bad time, so the checks live
 * here and are exercised without an emulator: every problem is reported at once,
 * and an invalid form cannot be turned into a schedule at all.</p>
 *
 * <p>Each test starts from {@link #editor()}, which is a full, valid timetable,
 * and then breaks exactly one thing, so a failure names one rule.</p>
 */
public class BellScheduleEditorTest {

    /** A valid 15-period timetable, ready to edit. */
    private static BellScheduleEditor editor() {
        return BellScheduleEditor.blank(new ArrayList<String>());
    }

    private static List<BellScheduleEditor.Reason> reasons(
            List<BellScheduleEditor.Problem> problems) {
        List<BellScheduleEditor.Reason> result = new ArrayList<>();
        for (BellScheduleEditor.Problem problem : problems) {
            result.add(problem.reason());
        }
        return result;
    }

    @Test
    public void aFreshEditorStartsFromTheStandardTimetable() {
        BellScheduleEditor editor = editor();

        assertEquals(BellSchedules.STANDARD_NAME, editor.name());
        assertEquals(BellSchedule.PERIODS, editor.periodCount());
        assertEquals("08:15", editor.startText(1));
        assertEquals("08:55", editor.endText(1));
        assertTrue(editor.problems().toString(), editor.problems().isEmpty());
    }

    @Test
    public void aBlankNameIsReportedOnce() {
        BellScheduleEditor editor = editor();
        editor.setName("   ");

        List<BellScheduleEditor.Problem> problems = editor.problems();

        assertEquals(1, problems.size());
        assertEquals(BellScheduleEditor.Reason.NAME_REQUIRED, problems.get(0).reason());
        assertEquals(0, problems.get(0).period());
    }

    @Test
    public void aNameUsedByAnotherScheduleIsRejected() {
        BellScheduleEditor editor = BellScheduleEditor.blank(
                Arrays.asList("\u5B9E\u9A8C\u697C\u4F5C\u606F", "  "));
        editor.setName("\u5B9E\u9A8C\u697C\u4F5C\u606F");

        assertEquals(Arrays.asList(BellScheduleEditor.Reason.NAME_TAKEN),
                reasons(editor.problems()));
    }

    @Test
    public void keepingItsOwnNameIsNotADuplicate() {
        BellScheduleEditor editor = BellScheduleEditor.blank(
                Arrays.asList(BellSchedules.STANDARD_NAME));

        assertTrue(editor.problems().isEmpty());
    }

    @Test
    public void everyTimeFormatPeopleTypeIsAccepted() {
        BellScheduleEditor editor = editor();
        editor.setStartText(1, "8.00");
        editor.setEndText(1, "8.45");
        editor.setStartText(2, "0855");
        editor.setEndText(2, "9\uff1a40");
        editor.setStartText(3, "10:00");
        editor.setEndText(3, "1045");
        editor.setStartText(4, "11.00");
        editor.setEndText(4, "11.45");
        editor.setStartText(5, "13:20");
        editor.setEndText(5, "14:05");
        editor.setStartText(6, "14:10");
        editor.setEndText(6, "14:55");
        editor.setStartText(7, "15:00");
        editor.setEndText(7, "1545");
        editor.setStartText(8, "16:00");
        editor.setEndText(8, "16:45");
        editor.setStartText(9, "19:00");
        editor.setEndText(9, "19:45");
        editor.setStartText(10, "20:00");
        editor.setEndText(10, "20:45");
        editor.setStartText(11, "21:00");
        editor.setEndText(11, "21:45");
        editor.setStartText(12, "22:00");
        editor.setEndText(12, "22:40");
        editor.setStartText(13, "23:00");
        editor.setEndText(13, "23:45");
        editor.setStartText(14, "24:00");
        editor.setEndText(14, "24:45");
        editor.setStartText(15, "24:50");
        editor.setEndText(15, "25:35");

        assertTrue(editor.problems().toString(), editor.problems().isEmpty());
        BellSchedule built = editor.build();
        assertEquals(480, built.startMinute(1));
        assertEquals(580, built.endMinute(2));
        assertEquals(600, built.startMinute(3));
        assertEquals(1440, built.startMinute(14));
        assertEquals(25 * 60 + 35, built.endMinute(15));
    }

    @Test
    public void aTimeThatIsNotATimeIsReported() {
        BellScheduleEditor editor = editor();
        editor.setStartText(4, "abc");

        List<BellScheduleEditor.Problem> problems = editor.problems();

        assertEquals(1, problems.size());
        assertEquals(BellScheduleEditor.Reason.TIME_INVALID, problems.get(0).reason());
        assertEquals(4, problems.get(0).period());
    }

    @Test
    public void aPeriodThatEndsBeforeItStartsIsReported() {
        BellScheduleEditor editor = editor();
        editor.setStartText(5, "10:00");
        editor.setEndText(5, "09:00");

        assertEquals(Arrays.asList(BellScheduleEditor.Reason.INVERTED),
                reasons(editor.problems()));
    }

    @Test
    public void aPeriodStartingBeforeThePreviousOneEndsIsWarnedAboutButNotBlocked() {
        // Three tidy periods, then the fourth moved back over the third.
        BellSchedule tidy = new BellSchedule("\u5E72\u51C0\u7684",
                new int[] {495, 575, 655, 735},
                new int[] {535, 615, 695, 775},
                new ArrayList<String>());
        BellScheduleEditor editor = new BellScheduleEditor(tidy,
                new ArrayList<String>());
        editor.setStartText(4, "10:35");
        editor.setEndText(4, "11:15");

        // Not a reason to refuse the save: the school's own timetable does this.
        assertTrue(editor.problems().toString(), editor.problems().isEmpty());
        assertEquals(Arrays.asList(Integer.valueOf(4)), editor.overlappingPeriods());
    }

    @Test
    public void aTidyTimetableHasNoOverlapsToWarnAbout() {
        BellSchedule tidy = new BellSchedule("\u5E72\u51C0\u7684",
                new int[] {495, 575, 655, 735},
                new int[] {535, 615, 695, 775},
                new ArrayList<String>());

        BellScheduleEditor editor = new BellScheduleEditor(tidy,
                new ArrayList<String>());

        assertTrue(editor.overlappingPeriods().isEmpty());
    }

    @Test
    public void theRealSchoolTimetableIsAcceptedAsItStands() {
        // The school's own hours, periods in order, must pass validation as-is:
        // this is the timetable the app ships with, so a rule that rejects it
        // would make the editor useless.
        BellScheduleEditor editor = editor();

        assertTrue(editor.problems().toString(), editor.problems().isEmpty());
        assertTrue(editor.overlappingPeriods().toString(),
                editor.overlappingPeriods().isEmpty());
    }

    @Test
    public void aBackToBackPeriodIsFine() {
        BellScheduleEditor editor = editor();
        editor.setStartText(12, "22:30");
        editor.setEndText(12, "23:30");
        editor.setStartText(13, "23:30");
        editor.setEndText(13, "24:30");
        editor.setStartText(14, "24:30");
        editor.setEndText(14, "25:15");
        editor.setStartText(15, "25:15");
        editor.setEndText(15, "26:00");

        assertTrue(editor.problems().toString(), editor.problems().isEmpty());
    }

    @Test
    public void allProblemsAreReportedAtOnceInRowOrder() {
        BellScheduleEditor editor = editor();
        editor.setName("  ");
        editor.setStartText(2, "nope");
        editor.setStartText(3, "12:00");
        editor.setEndText(3, "11:00");

        List<BellScheduleEditor.Problem> problems = editor.problems();

        assertEquals(3, problems.size());
        assertEquals(0, problems.get(0).period());
        assertEquals(2, problems.get(1).period());
        assertEquals(3, problems.get(2).period());
    }

    @Test
    public void anEmptyPeriodIsOnlyAProblemWhenEveryPeriodIsRequired() {
        BellScheduleEditor editor = editor();
        editor.setStartText(15, "");
        editor.setEndText(15, "");

        assertTrue(editor.problems(false).isEmpty());
        assertEquals(Arrays.asList(BellScheduleEditor.Reason.TIME_INVALID),
                reasons(editor.problems()));
    }

    @Test
    public void buildingAnInvalidFormIsRefused() {
        BellScheduleEditor editor = editor();
        editor.setName("");

        boolean threw = false;
        try {
            editor.build();
        } catch (IllegalStateException expected) {
            threw = true;
        }
        assertTrue("build() must not accept an invalid form", threw);
    }

    @Test
    public void keywordsAreSplitOnBothCommaStyles() {
        BellScheduleEditor editor = editor();
        editor.setKeywords(" \u5B9E\u9A8C\u697C\uFF0C \u5B9E\u8BAD\u697C , \uFF0C ");

        assertEquals(Arrays.asList("\u5B9E\u9A8C\u697C", "\u5B9E\u8BAD\u697C"),
                editor.keywordList());
        assertEquals(2, editor.build().locationKeywords().size());
    }

    @Test
    public void aBuiltScheduleCarriesTheEditedTimesAndKeywords() {
        BellScheduleEditor editor = editor();
        editor.setName("\u5D07\u6587\u697C\u4F5C\u606F");
        editor.setKeywords("\u5D07\u6587\u697C");
        editor.setStartText(1, "8:20");
        editor.setEndText(1, "9:00");
        editor.setStartText(2, "9:00");
        editor.setEndText(2, "9:40");

        BellSchedule built = editor.build();

        assertEquals("\u5D07\u6587\u697C\u4F5C\u606F", built.name());
        assertEquals("08:20 - 09:00", built.timeRange(1, 1));
        assertEquals("09:00 - 09:40", built.timeRange(2, 2));
        assertTrue(built.matchesLocation("\u5D07\u6587\u697C 302"));
        assertEquals(BellSchedules.STANDARD_NAME, editor.originalName());
    }

    @Test
    public void aScheduleWithOnePeriodIsAllowed() {
        BellSchedule single = new BellSchedule("\u5355\u8282\u4F5C\u606F",
                new int[] {495}, new int[] {535}, new ArrayList<String>());

        BellScheduleEditor editor = new BellScheduleEditor(single,
                new ArrayList<String>());

        assertEquals(1, editor.periodCount());
        assertTrue(editor.problems().isEmpty());
        assertEquals("08:15 - 08:55", editor.build().timeRange(1, 1));
    }
}