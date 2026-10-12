package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;

/**
 * Forty minutes on from a period's start.
 *
 * <p>Every period on a real timetable is forty minutes, so that is what the screen
 * writes into the end as soon as a start is chosen -- one interaction instead of
 * two. It is a suggestion rather than a rule: a period may be any length. The
 * editor's own fixtures describe forty-five minute periods, and it reports an end
 * that falls before its start rather than deriving one, so the end field stays
 * editable. What is checked here is the arithmetic and the blast radius: applying
 * the suggestion moves one period's end and touches nothing else.</p>
 */
public class BellScheduleEndTest {

    private static final String[] STARTS = {
        "08:15", "08:55", "09:55", "10:40", "11:20", "13:20", "14:00", "15:00",
        "15:40", "16:35", "17:15", "18:10", "18:50", "19:35", "20:20"
    };

    // ================================================== the arithmetic

    @Test
    public void fortyMinutesAfterTheStart() {
        assertEquals("08:55", BellSchedule.label(BellSchedule.endOf(minutes("08:15"))));
        assertEquals("09:35", BellSchedule.label(BellSchedule.endOf(minutes("08:55"))));
        assertEquals("10:20", BellSchedule.label(BellSchedule.endOf(minutes("09:40"))));
    }

    @Test
    public void theHourIsCarriedRatherThanTheMinutesWrapped() {
        assertEquals("13:00", BellSchedule.label(BellSchedule.endOf(minutes("12:20"))));
        assertEquals("00:40", BellSchedule.label(BellSchedule.endOf(0)));
    }

    @Test
    public void anEveningPeriodEndsInTheDayItBelongsTo() {
        // The app writes a period past midnight as 24:xx, so period 15 still belongs
        // to the day whose timetable it is. The arithmetic agrees with that rule.
        assertEquals("24:20", BellSchedule.label(BellSchedule.endOf(minutes("23:40"))));
        assertEquals("24:05", BellSchedule.label(BellSchedule.endOf(minutes("23:25"))));
    }

    @Test
    public void theSuggestionNeverRunsPastTheBoundTheAppWritesWithin() {
        assertEquals("29:59", BellSchedule.label(BellSchedule.endOf(29 * 60 + 50)));
    }

    // ================================================== what the screen suggests

    @Test
    public void theSuggestionIsFortyMinutesOnFromEachStart() {
        BellScheduleEditor editor = editorWith(STARTS);
        assertEquals("08:55", editor.suggestedEndText(1));
        assertEquals("09:35", editor.suggestedEndText(2));
        assertEquals("10:35", editor.suggestedEndText(3));   // 09:55 + 40
        assertEquals("21:00", editor.suggestedEndText(15));
    }

    @Test
    public void changingOneStartMovesOnlyThatPeriodsEnd() {
        BellScheduleEditor editor = editorWith(STARTS);
        ArrayList<String> endsBefore = endsOf(editor);

        editor.setStartText(3, "09:50");
        editor.setEndText(3, editor.suggestedEndText(3));

        assertEquals("10:30", editor.endText(3));
        for (int period = 1; period <= editor.periodCount(); period++) {
            if (period != 3) {
                assertEquals("period " + period + " must not move",
                        endsBefore.get(period - 1), editor.endText(period));
            }
        }
    }

    @Test
    public void noOtherStartIsTouched() {
        // The breaks between periods live in the starts. Nothing here may close
        // them: the periods are fifteen separate arrangements, not a chain.
        BellScheduleEditor editor = editorWith(STARTS);
        editor.setStartText(5, "11:30");

        assertEquals("08:15", editor.startText(1));
        assertEquals("09:55", editor.startText(3));
        assertEquals("11:30", editor.startText(5));
        assertEquals("13:20", editor.startText(6));
    }

    @Test
    public void theBreaksBetweenPeriodsSurviveASave() {
        BellScheduleEditor editor = editorWith(STARTS);
        BellSchedule built = editor.build();
        // 09:35 to 09:55 is a twenty minute break; 10:35 to 10:40 is five.
        assertEquals(20, built.startMinute(3) - built.endMinute(2));
        assertEquals(5, built.startMinute(4) - built.endMinute(3));
    }

    @Test
    public void theSavedEndIsStillTheEndField() {
        // Compatibility: the model keeps both fields and the end is written to the
        // one it always was, so nothing reading a schedule has to change.
        BellSchedule editor = editorWith(STARTS).build();
        assertEquals(BellSchedule.PERIODS, editor.periodCount());
        for (int period = 1; period <= BellSchedule.PERIODS; period++) {
            assertEquals("period " + period,
                    editor.startMinute(period) + BellSchedule.PERIOD_MINUTES,
                    editor.endMinute(period));
        }
    }

    @Test
    public void aScheduleAlreadyFortyMinutesIsUnchangedByARoundTrip() {
        BellSchedule original = new BellSchedule("T", minutesOf(STARTS),
                endsFrom(STARTS), Collections.emptyList());
        BellSchedule rebuilt = new BellScheduleEditor(original, new ArrayList<String>())
                .build();
        for (int period = 1; period <= BellSchedule.PERIODS; period++) {
            assertEquals("start " + period,
                    original.startMinute(period), rebuilt.startMinute(period));
            assertEquals("end " + period,
                    original.endMinute(period), rebuilt.endMinute(period));
        }
    }

    @Test
    public void aFortyFiveMinutePeriodIsStillRepresentable() {
        // The length the editor's own fixtures use. Nothing about the suggestion may
        // take it away: the field keeps whatever the user sets, and the check that
        // rejects an inverted end still applies.
        BellScheduleEditor editor = editorWith(STARTS);
        editor.setEndText(1, "09:00");
        assertEquals("09:00", editor.endText(1));
        assertEquals(45, editor.build().endMinute(1) - editor.build().startMinute(1));
        assertEquals("the suggestion is still forty", "08:55", editor.suggestedEndText(1));
    }

    @Test
    public void anEndBeforeItsStartIsStillReported() {
        BellScheduleEditor editor = editorWith(STARTS);
        editor.setStartText(2, "09:00");
        editor.setEndText(2, "08:00");
        assertTrue("the inverted end is still a problem", !editor.problems().isEmpty());
    }

    @Test
    public void aStartThatCannotBeReadSuggestsNothingAndDoesNotThrow() {
        BellScheduleEditor editor = BellScheduleEditor.blank(new ArrayList<String>());
        editor.setStartText(1, "not a time");
        assertEquals("", editor.suggestedEndText(1));
        for (int period = 1; period <= BellSchedule.PERIODS; period++) {
            editor.suggestedEndText(period);
        }
    }

    @Test
    public void everySpellingOfAStartSuggestsTheSameEnd() {
        for (String spelling : new String[] {"8:15", "08:15", "8.15", "815", "0815"}) {
            BellScheduleEditor editor = BellScheduleEditor.blank(new ArrayList<String>());
            editor.setStartText(1, spelling);
            assertEquals("end of " + spelling, "08:55", editor.suggestedEndText(1));
        }
    }

    // ================================================== fixtures

    private static int minutes(String text) {
        return BellSchedule.parseLoose(text).intValue();
    }

    private static BellScheduleEditor editorWith(String[] starts) {
        return new BellScheduleEditor(new BellSchedule("T", minutesOf(starts),
                endsFrom(starts), Collections.emptyList()), new ArrayList<String>());
    }

    private static int[] minutesOf(String[] texts) {
        int[] values = new int[texts.length];
        for (int i = 0; i < texts.length; i++) {
            values[i] = minutes(texts[i]);
        }
        return values;
    }

    private static int[] endsFrom(String[] starts) {
        int[] values = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            values[i] = BellSchedule.endOf(minutes(starts[i]));
        }
        return values;
    }

    private static ArrayList<String> endsOf(BellScheduleEditor editor) {
        ArrayList<String> ends = new ArrayList<>();
        for (int period = 1; period <= editor.periodCount(); period++) {
            ends.add(editor.endText(period));
        }
        return ends;
    }
}