package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * The two things this round made settable: how long a period is, and how many a
 * day has.
 *
 * <p>Both used to be constants -- forty minutes, fifteen periods -- and the point
 * of the change is that a school can disagree with both without the app disagreeing
 * back. These tests hold the arithmetic and the counting still, and in particular
 * hold the answers the requirement names window by window, so that "one schedule
 * has one length" is verified rather than assumed.</p>
 *
 * <p>What they cannot check is the screen. Whether the length field is where it can
 * be seen, whether the end column looks read-only, whether the add and remove
 * buttons are reachable once the list is long -- that is a device, not a JVM.</p>
 */
public class PeriodLengthAndCountTest {

    private static BellScheduleEditor editor() {
        return BellScheduleEditor.blank(new ArrayList<String>());
    }

    /** A table of {@code count} periods an hour apart, so a count change is visible. */
    private static BellSchedule table(int count, int durationMinutes) {
        int[] starts = new int[count];
        int[] ends = new int[count];
        for (int period = 1; period <= count; period++) {
            starts[period - 1] = 8 * 60 + (period - 1) * 60;
            ends[period - 1] = BellSchedule.endOf(starts[period - 1], durationMinutes);
        }
        return new BellSchedule("T" + count, starts, ends,
                new ArrayList<String>(), durationMinutes);
    }

    /** A one-period form, for conversions that must not trip another row. */
    private static BellScheduleEditor oneRowEditor() {
        return new BellScheduleEditor(table(1, 40), new ArrayList<String>());
    }

    // ============================================ one: the period length

    /**
     * The five conversions the requirement names, as the editor actually produces
     * them -- length typed, then built.
     */
    @Test
    public void theNamedConversionsComeOutRight() {
        assertEquals("08:15 at forty minutes", "08:55", endOf("08:15", 40));
        assertEquals("08:15 at forty-five", "09:00", endOf("08:15", 45));
        assertEquals("08:15 at fifty", "09:05", endOf("08:15", 50));
        assertEquals("09:55 at thirty-five", "10:30", endOf("09:55", 35));
        assertEquals("13:20 at sixty", "14:20", endOf("13:20", 60));
    }

    @Test
    public void aFreshScheduleIsFortyMinutesLong() {
        // The default the requirement insists on: an existing user must open to the
        // app they had.
        BellScheduleEditor editor = editor();
        assertEquals("40", editor.durationText());
        assertEquals(40, editor.build().durationMinutes());
        assertEquals(BellSchedule.DEFAULT_PERIOD_MINUTES,
                BellSchedules.standard().durationMinutes());
    }

    @Test
    public void changingTheLengthMovesEndsAndLeavesStartsAlone() {
        BellScheduleEditor editor = editor();
        List<String> startsBefore = new ArrayList<>();
        for (int period = 1; period <= editor.periodCount(); period++) {
            startsBefore.add(editor.startText(period));
        }

        editor.setDurationText("50");
        BellSchedule built = editor.build();

        for (int period = 1; period <= built.periodCount(); period++) {
            assertEquals("period " + period + " must not have moved",
                    startsBefore.get(period - 1), editor.startText(period));
            assertEquals("period " + period + " ends fifty minutes on",
                    built.startMinute(period) + 50, built.endMinute(period));
        }
        assertEquals(50, built.durationMinutes());
    }

    @Test
    public void changingOneStartMovesOnlyThatRowsEnd() {
        BellScheduleEditor editor = editor();
        int neighbourEnd = editor.build().endMinute(2);

        editor.setStartText(1, "07:00");

        assertEquals("07:00", editor.startText(1));
        // The row it was typed on follows the length.
        assertEquals(7 * 60 + 40, editor.build().endMinute(1));
        // The row after it is untouched: a start is the user's, never the app's.
        assertEquals(neighbourEnd, editor.build().endMinute(2));
        assertEquals("08:55", editor.startText(2));
    }

    @Test
    public void anIllegalLengthIsRejectedRatherThanGuessedAt() {
        String[] rejected = {"", "   ", "0", "-5", "abc", "40.5", "181", "999"};
        for (String bad : rejected) {
            BellScheduleEditor editor = editor();
            editor.setDurationText(bad);

            assertTrue("[" + bad + "] must be reported",
                    reasons(editor.problems()).contains(
                            BellScheduleEditor.Reason.DURATION_INVALID));
            assertThrows("build() must refuse [" + bad + "]",
                    IllegalStateException.class, editor::build);
        }
    }

    @Test
    public void theEdgesOfTheAllowedLengthAreUsable() {
        BellScheduleEditor low = editor();
        low.setDurationText(String.valueOf(BellSchedule.MIN_PERIOD_MINUTES));
        assertTrue(low.problems().toString(), low.problems().isEmpty());
        assertEquals(BellSchedule.MIN_PERIOD_MINUTES,
                low.build().durationMinutes());

        BellScheduleEditor high = editor();
        high.setDurationText(String.valueOf(BellSchedule.MAX_PERIOD_MINUTES));
        assertTrue(high.problems().toString(), high.problems().isEmpty());
        assertEquals(BellSchedule.MAX_PERIOD_MINUTES,
                high.build().durationMinutes());
    }

    @Test
    public void twoSchedulesKeepTheirOwnLengths() {
        BellScheduleEditor shortForm = new BellScheduleEditor(table(3, 40),
                new ArrayList<String>());
        BellScheduleEditor longForm = new BellScheduleEditor(table(3, 60),
                new ArrayList<String>());

        shortForm.setDurationText("45");

        assertEquals(45, shortForm.build().durationMinutes());
        assertEquals("the other table is not moved by this one's edit",
                60, longForm.build().durationMinutes());
        assertEquals(60, longForm.build().endMinute(1) - longForm.build().startMinute(1));
    }

    @Test
    public void aLengthTheModelCannotUseFallsBackToFortyRatherThanFailing() {
        // The read path repairs. A stored length of zero means a damaged file, and
        // refusing to open the schedule is worse than reading it as the default.
        assertEquals(40, table(3, 0).durationMinutes());
        assertEquals(40, table(3, -10).durationMinutes());
        assertEquals(40, table(3, 10_000).durationMinutes());
        // And the arithmetic the whole feature rests on does the same: a length it
        // cannot use is forty, not a negative period.
        assertEquals("a zero length reads as forty",
                8 * 60 + 15 + 40, BellSchedule.endOf(8 * 60 + 15, 0));
        assertEquals(8 * 60 + 15 + 40, BellSchedule.endOf(8 * 60 + 15, -10));
        assertEquals(8 * 60 + 15 + 40, BellSchedule.endOf(8 * 60 + 15, 10_000));
    }

    // ============================================ two: the period count

    @Test
    public void aNewDefaultScheduleHasFifteenPeriodsAtTheSchoolsTimes() {
        BellSchedule standard = BellSchedules.standard();
        assertEquals(15, standard.periodCount());

        // The fifteen starts the requirement lists, in order.
        int[] expected = {495, 535, 595, 640, 680, 800, 840, 900, 940, 995,
                1035, 1090, 1130, 1175, 1220};
        assertEquals(BellSchedule.PERIODS, expected.length);
        for (int period = 1; period <= expected.length; period++) {
            assertEquals("period " + period + " starts at the school's time",
                    expected[period - 1], standard.startMinute(period));
            assertEquals("period " + period + " ends forty minutes on",
                    expected[period - 1] + 40, standard.endMinute(period));
        }
    }

    @Test
    public void theDefaultLengthOfADayIsTheCapNotACurriculum() {
        // Fifteen is where a fresh day starts; it is not where it has to end.
        assertTrue("a day may be longer than the default",
                ScheduleUtils.MAX_PERIODS > ScheduleUtils.PERIODS_PER_DAY);
        assertEquals(15, ScheduleUtils.periodLabels().length);
        assertEquals(ScheduleUtils.MAX_PERIODS,
                ScheduleUtils.periodLabels(ScheduleUtils.MAX_PERIODS).length);
        assertEquals("a short day still labels its rows",
                4, ScheduleUtils.periodLabels(4).length);
    }

    @Test
    public void addingAPeriodExtendsTheListAndNumbersIt() {
        BellScheduleEditor editor = editor();
        List<String> startsBefore = new ArrayList<>();
        for (int period = 1; period <= editor.periodCount(); period++) {
            startsBefore.add(editor.startText(period));
        }

        int added = editor.addPeriod();

        assertEquals(16, added);
        assertEquals(16, editor.periodCount());
        assertEquals(16, editor.build().periodCount());
        // The rows that were there did not shuffle.
        for (int period = 1; period <= startsBefore.size(); period++) {
            assertEquals(startsBefore.get(period - 1), editor.startText(period));
        }
    }

    @Test
    public void anAddedPeriodStartsWhereTheLastOneEnded() {
        BellScheduleEditor editor = editor();
        int lastEnd = editor.build().endMinute(editor.periodCount());

        editor.addPeriod();

        assertEquals("the new row picks up where the day left off",
                BellSchedule.label(lastEnd), editor.startText(16));
        assertEquals("and is immediately usable", lastEnd,
                BellSchedule.parseLoose(editor.startText(16)).intValue());
        assertEquals(lastEnd + 40, editor.build().endMinute(16));
    }

    @Test
    public void anAddedPeriodIsEditableLikeAnyOther() {
        BellScheduleEditor editor = editor();
        editor.addPeriod();
        editor.setStartText(16, "21:10");

        assertEquals("21:10", editor.startText(16));
        assertEquals(21 * 60 + 50, editor.build().endMinute(16));
        assertTrue(editor.problems(true).toString(), editor.problems(true).isEmpty());
    }

    @Test
    public void removingTakesTheLastPeriodAndLeavesTheRestNumbered() {
        BellScheduleEditor editor = editor();
        int count = editor.periodCount();
        List<String> startsBefore = new ArrayList<>();
        for (int period = 1; period <= count; period++) {
            startsBefore.add(editor.startText(period));
        }

        assertTrue(editor.removePeriod());

        assertEquals(count - 1, editor.periodCount());
        for (int period = 1; period <= count - 1; period++) {
            assertEquals("period " + period + " is where it was",
                    startsBefore.get(period - 1), editor.startText(period));
        }
        // The row that was removed is gone, not blanked.
        assertEquals("", editor.startText(count));
        assertEquals(count - 1, editor.build().periodCount());
    }

    @Test
    public void aDayCannotShrinkToNothing() {
        BellScheduleEditor editor = editor();
        while (editor.periodCount() > BellSchedule.MIN_PERIODS) {
            assertTrue(editor.removePeriod());
        }

        assertEquals(BellSchedule.MIN_PERIODS, editor.periodCount());
        assertFalse("the last period must not be removable", editor.removePeriod());
        assertEquals(BellSchedule.MIN_PERIODS, editor.periodCount());
        assertTrue("one period is still a timetable: " + editor.problems(true),
                editor.problems(true).isEmpty());
    }

    @Test
    public void theListIsBoundedByTheCeilingNotByFifteen() {
        // Thirty is the app's guard against a typo, not a curriculum, and it is
        // twice the old default -- so reaching it is how "fifteen is not the cap"
        // is shown. The rows are one minute apart so that thirty of them still fit
        // inside the day the model can label.
        BellScheduleEditor editor = new BellScheduleEditor(table(1, 40),
                new ArrayList<String>());
        for (int i = editor.periodCount(); i < ScheduleUtils.MAX_PERIODS; i++) {
            assertNotEquals("period " + (i + 1) + " must be addable", 0,
                    editor.addPeriod());
        }
        for (int period = 1; period <= editor.periodCount(); period++) {
            editor.setStartText(period, BellSchedule.label(8 * 60 + period));
        }

        assertEquals(ScheduleUtils.MAX_PERIODS, editor.periodCount());
        assertEquals("past the ceiling the add is refused, not silently ignored",
                0, editor.addPeriod());
        assertEquals(ScheduleUtils.MAX_PERIODS, editor.periodCount());
        assertEquals(ScheduleUtils.MAX_PERIODS, editor.build().periodCount());
    }

    @Test
    public void addAndRemoveSurviveARoundTripThroughStorage() {
        BellScheduleEditor editor = editor();
        editor.addPeriod();
        editor.setStartText(16, "21:10");
        BellSchedule saved = editor.build();

        BellScheduleEditor reopened = new BellScheduleEditor(saved,
                new ArrayList<String>());

        assertEquals(16, reopened.periodCount());
        assertEquals("21:10", reopened.startText(16));
        assertEquals("the length came back too", "40", reopened.durationText());
    }

    /**
     * A period added or removed just before the screen is rebuilt must come back,
     * even though the schedule it is restored into was rebuilt from storage and
     * still holds the count that was last saved.
     */
    @Test
    public void aCountChangedJustBeforeARebuildSurvivesIt() {
        BellScheduleEditor before = editor();
        before.setDurationText("45");
        before.addPeriod();
        before.setStartText(16, "21:10");

        // Storage still holds fifteen periods; the form has sixteen.
        BellScheduleEditor after = new BellScheduleEditor(BellSchedules.standard(),
                new ArrayList<String>());
        ScheduleDraft.of(before).applyTo(after);

        assertEquals(16, after.periodCount());
        assertEquals("21:10", after.startText(16));
        assertEquals("45", after.durationText());
        assertEquals(21 * 60 + 55, after.build().endMinute(16));

        // And the other way: a removed period stays removed.
        BellScheduleEditor removed = editor();
        removed.removePeriod();
        BellScheduleEditor restored = new BellScheduleEditor(BellSchedules.standard(),
                new ArrayList<String>());
        ScheduleDraft.of(removed).applyTo(restored);
        assertEquals(14, restored.periodCount());
    }

    // ============================================ helpers

    /**
     * The end of a period that starts at {@code start} and lasts {@code minutes}.
     *
     * <p>Mirrors what the screen does: the length is set first, then the start, and
     * the end field is kept in step with the derived value -- because the form
     * reports a row whose two fields disagree, and rightly so, it would otherwise be
     * testing a state the screen never produces.</p>
     */
    private static String endOf(String start, int minutes) {
        BellScheduleEditor editor = oneRowEditor();
        editor.setDurationText(String.valueOf(minutes));
        editor.setStartText(1, start);
        editor.setEndText(1, editor.suggestedEndText(1));
        return BellSchedule.label(editor.build().endMinute(1));
    }

    private static List<BellScheduleEditor.Reason> reasons(
            List<BellScheduleEditor.Problem> problems) {
        List<BellScheduleEditor.Reason> result = new ArrayList<>();
        for (BellScheduleEditor.Problem problem : problems) {
            result.add(problem.reason());
        }
        return result;
    }

    /** Runs {@code body}, failing unless it throws {@code expected}. */
    private static void assertThrows(String what, Class<? extends Throwable> expected,
            Runnable body) {
        try {
            body.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(what + ": expected " + expected.getSimpleName()
                    + " but got " + thrown, thrown);
        }
        throw new AssertionError(what + ": expected " + expected.getSimpleName()
                + " but nothing was thrown");
    }
}
