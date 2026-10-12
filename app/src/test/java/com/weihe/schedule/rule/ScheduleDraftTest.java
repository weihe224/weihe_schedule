package com.weihe.schedule.rule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;

/**
 * What the bell-schedule editor carries across a rebuild.
 *
 * <p>Most of this is about text staying text. A period field is a text field that
 * happens to hold a time, so it spends part of its life holding something that is
 * not a time yet -- {@code 8.0}, {@code 8:}, a lone {@code 8} -- and the moment
 * anyone parses, trims or re-formats it, the caret is somewhere the user did not
 * put it. The draft never looks inside the strings.</p>
 *
 * <p>What these tests cannot show is that Android really rebuilds the activity.
 * That is checked on a device.</p>
 */
public class ScheduleDraftTest {

    /** A valid 15-period timetable, ready to edit. */
    private static BellScheduleEditor editor() {
        return BellScheduleEditor.blank(new ArrayList<String>());
    }

    // ============================================ text stays text

    @Test
    public void halfTypedTextSurvivesAsTyped() {
        String[] halfTyped = {"8.0", "8：", "8", "8:", "08.0", " 8 ", "abc"};

        for (String typed : halfTyped) {
            BellScheduleEditor editor = editor();
            editor.setStartText(1, typed);
            editor.setEndText(1, typed);

            ScheduleDraft draft = ScheduleDraft.of(editor);

            assertEquals("a half-typed time must survive exactly: [" + typed + "]",
                    typed, draft.startText(1));
            assertEquals(typed, draft.endText(1));
        }
    }

    @Test
    public void aHalfTypedTimeComesBackIntoTheEditorUnchanged() {
        BellScheduleEditor source = editor();
        source.setStartText(3, "8.0");
        source.setEndText(3, "8：");
        source.setStartText(4, "");

        // Carry it away and put it into a fresh editor, the way a rebuild does.
        ScheduleDraft draft = ScheduleDraft.of(source);
        BellScheduleEditor restored = editor();
        draft.applyTo(restored);

        assertEquals("8.0", restored.startText(3));
        assertEquals("8：", restored.endText(3));
        assertEquals("", restored.startText(4));
    }

    @Test
    public void everyPeriodsTwoTimesComeBack() {
        BellScheduleEditor source = editor();
        int count = source.periodCount();
        for (int period = 1; period <= count; period++) {
            source.setStartText(period, "s" + period);
            source.setEndText(period, "e" + period);
        }

        ScheduleDraft draft = ScheduleDraft.of(source);

        assertEquals(BellSchedule.PERIODS, count);
        assertEquals(count, draft.periodCount());
        for (int period = 1; period <= count; period++) {
            assertEquals("s" + period, draft.startText(period));
            assertEquals("e" + period, draft.endText(period));
        }
        // Thirty strings, all of them different, none of them swapped.
        assertEquals(count, draft.startTexts().size());
        assertEquals(count, draft.endTexts().size());
        assertNotEquals(draft.startText(1), draft.endText(1));
    }

    @Test
    public void theNameAndKeywordsComeBack() {
        BellScheduleEditor source = editor();
        source.setName("Lab building");
        source.setKeywords("Lab,Science");

        ScheduleDraft draft = ScheduleDraft.of(source);

        assertEquals("Lab building", draft.name());
        assertEquals("Lab,Science", draft.keywords());
    }

    @Test
    public void outOfRangePeriodsReadAsEmptyRatherThanFailing() {
        ScheduleDraft draft = ScheduleDraft.of(editor());

        assertEquals("", draft.startText(0));
        assertEquals("", draft.startText(-1));
        assertEquals("", draft.startText(99));
        assertEquals("", draft.endText(0));
        assertEquals("", draft.endText(99));
    }

    // ============================================ applying it back

    @Test
    public void applyingADraftRestoresEveryText() {
        BellScheduleEditor source = editor();
        source.setName("Second schedule");
        source.setKeywords("C,D");
        source.setStartText(2, "09:11");
        source.setEndText(2, "09:59");

        BellScheduleEditor restored = editor();
        ScheduleDraft.of(source).applyTo(restored);

        assertEquals("Second schedule", restored.name());
        assertEquals("C,D", restored.keywords());
        assertEquals("09:11", restored.startText(2));
        assertEquals("09:59", restored.endText(2));
    }

    @Test
    public void applyingADraftDoesNotChangeWhichScheduleIsBeingEdited() {
        // The target comes from the intent, and a draft holds no name for it. This
        // is the mistake this screen has made before in another form, when it
        // addressed schedules by their position in a list.
        BellScheduleEditor target = editor();
        String original = target.originalName();

        BellScheduleEditor other = editor();
        other.setName("Somewhere else");
        other.setStartText(1, "23:59");
        ScheduleDraft.of(other).applyTo(target);

        assertEquals("the edited schedule must not change",
                original, target.originalName());
        assertEquals("but its text is the draft's", "Somewhere else", target.name());
    }

    @Test
    public void anAbsentNameLeavesTheEditorsOwnNameAlone() {
        BellScheduleEditor target = editor();
        String before = target.name();

        new ScheduleDraft(null, null, null, null).applyTo(target);

        assertEquals(before, target.name());
        assertEquals(BellSchedule.PERIODS, target.periodCount());
    }

    @Test
    public void aShorterDraftLeavesTheRemainingPeriodsAlone() {
        BellScheduleEditor target = editor();
        String untouched = target.startText(10);

        // A state from a schedule with fewer periods than this one.
        new ScheduleDraft(null, null, new String[] {"07:00"}, new String[] {"07:45"})
                .applyTo(target);

        assertEquals("07:00", target.startText(1));
        assertEquals(untouched, target.startText(10));
    }

    @Test
    public void aLongerDraftIsTruncatedRatherThanFailing() {
        String[] starts = new String[40];
        String[] ends = new String[40];
        Arrays.fill(starts, "08:00");
        Arrays.fill(ends, "08:45");

        BellScheduleEditor target = editor();
        new ScheduleDraft(null, null, starts, ends).applyTo(target);

        assertEquals("08:00", target.startText(1));
        assertEquals("08:00", target.startText(target.periodCount()));
    }

    @Test
    public void aDraftDoesNotAliasTheArrayItWasGiven() {
        String[] starts = {"08:00"};
        ScheduleDraft draft = new ScheduleDraft(null, null, starts, null);

        starts[0] = "tampered";

        assertEquals("a draft must own its copy", "08:00", draft.startText(1));
    }

    @Test
    public void theArraysHandedOutAreCopies() {
        BellScheduleEditor source = editor();
        List<String> starts = ScheduleDraft.of(source).startTexts();

        starts.set(0, "tampered");

        assertEquals("08:15", source.startText(1));
    }

    // ============================================ the form still works after

    @Test
    public void afterRestoringTheEditorStillValidates() {
        BellScheduleEditor source = editor();
        // Readable spellings of 08:00 and 08:45: parseLoose accepts a dot as the
        // separator, so this is a valid table written unusually.
        source.setStartText(1, "8.00");
        source.setEndText(1, "8.45");

        BellScheduleEditor restored = editor();
        ScheduleDraft.of(source).applyTo(restored);

        assertTrue("a restored valid table must still pass: "
                + restored.problems(), restored.problems().isEmpty());
        assertEquals(15, restored.build().periodCount());
    }

    @Test
    public void aTimeStillBeingTypedIsCarriedAndStillReported() {
        BellScheduleEditor source = editor();
        // 8： is a time half-entered -- the colon is there, the minutes are not --
        // so it cannot be read yet. That is not a reason to drop it.
        source.setStartText(1, "8：");

        BellScheduleEditor restored = editor();
        ScheduleDraft.of(source).applyTo(restored);

        assertEquals("the half-typed text must survive", "8：", restored.startText(1));
        assertFalse("and must still be reported as unreadable",
                restored.problems().isEmpty());
        assertEquals(BellScheduleEditor.Reason.TIME_INVALID,
                restored.problems().get(0).reason());
    }

    @Test
    public void afterRestoringAnUnreadableTimeIsStillReported() {
        BellScheduleEditor source = editor();
        source.setStartText(1, "nonsense");

        BellScheduleEditor restored = editor();
        ScheduleDraft.of(source).applyTo(restored);

        // Restoring must not launder a bad value into a good one: the validation
        // that would have complained before the rebuild still complains after it.
        assertFalse(restored.problems().isEmpty());
    }

    @Test
    public void afterRestoringTheEditorCanStillBeEdited() {
        BellScheduleEditor restored = editor();
        ScheduleDraft.of(editor()).applyTo(restored);

        restored.setStartText(5, "13:00");
        restored.setEndText(5, "13:45");

        assertEquals("13:00", restored.startText(5));
        assertEquals(15, restored.build().periodCount());
    }

    @Test
    public void anEmptyEditorRoundTripsAsAnEmptyEditor() {
        BellScheduleEditor source = editor();
        for (int period = 1; period <= source.periodCount(); period++) {
            source.setStartText(period, "");
            source.setEndText(period, "");
        }

        ScheduleDraft draft = ScheduleDraft.of(source);
        String[] empty = new String[BellSchedule.PERIODS];
        Arrays.fill(empty, "");

        assertArrayEquals(empty, draft.startTexts().toArray(new String[0]));
        assertArrayEquals(empty, draft.endTexts().toArray(new String[0]));
    }
}
