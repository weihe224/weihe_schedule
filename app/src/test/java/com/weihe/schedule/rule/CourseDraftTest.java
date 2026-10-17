package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

/**
 * What the course form carries across a rebuild.
 *
 * <p>The form's real state is not the {@link com.weihe.schedule.model.Course} it
 * is editing: the course object still holds what storage said, and a week field
 * caught mid-keystroke holds text that has no place on that model. These tests are
 * about the two things that can go wrong with the part that is carried -- text
 * being tidied into something the user did not type, and a value being silently
 * turned into a default -- plus the positions that must not be trusted.</p>
 *
 * <p>None of this proves that Android really rebuilds the activity and hands the
 * bundle back. That is a device behaviour and is verified on a device.</p>
 */
public class CourseDraftTest {

    // ============================================ everything comes back

    @Test
    public void everyValueComesBackUnchanged() {
        CourseDraft draft = new CourseDraft("Algorithms", "Prof. Li", "Building A 203",
                "3", "14", 2, 4, 5, 1, 2, 0xFF3C6A8E, null);

        assertEquals("Algorithms", draft.name());
        assertEquals("Prof. Li", draft.teacher());
        assertEquals("Building A 203", draft.location());
        assertEquals("3", draft.startWeek());
        assertEquals("14", draft.endWeek());
        assertEquals(2, draft.dayIndex());
        assertEquals(4, draft.startPeriodIndex());
        assertEquals(5, draft.endPeriodIndex());
        assertEquals(1, draft.parityIndex());
        assertEquals(2, draft.scheduleIndex());
        assertEquals(0xFF3C6A8E, draft.color());
        assertTrue(draft.hasColor());
    }

    @Test
    public void theWeekFieldsKeepTheirRawText() {
        // Nothing here is a number, and none of it may be turned into one.
        for (String typed : new String[] {"", "1", "12", "1a", " 3 ", "0"}) {
            CourseDraft draft = new CourseDraft("c", "", "", typed, typed,
                    CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_INDEX,
                    CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_COLOR, null);

            assertEquals("week text must survive exactly: [" + typed + "]",
                    typed, draft.startWeek());
            assertEquals(typed, draft.endWeek());
        }
    }

    @Test
    public void everyWeekRangeTheShortcutsProduceSurvives() {
        // The three quick chips write these, and the chips' own appearance is
        // recomputed from the two fields afterwards rather than stored.
        int[][] ranges = {{1, 18}, {1, 8}, {9, 18}, {1, 16}, {5, 5}};
        for (int[] range : ranges) {
            CourseDraft draft = new CourseDraft("c", "", "",
                    String.valueOf(range[0]), String.valueOf(range[1]),
                    CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_INDEX,
                    CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_COLOR, null);

            assertEquals(String.valueOf(range[0]), draft.startWeek());
            assertEquals(String.valueOf(range[1]), draft.endWeek());
        }
    }

    @Test
    public void theParityChoiceComesBack() {
        // 0 = every week, 1 = odd, 2 = even: the row's own order.
        for (int index = 0; index <= 2; index++) {
            CourseDraft draft = new CourseDraft("c", "", "", "1", "18",
                    0, 0, 1, index, 0, CourseDraft.NO_COLOR, null);
            assertEquals(index, draft.parityIndex());
        }
    }

    @Test
    public void aChosenColourComesBack() {
        CourseDraft draft = new CourseDraft("c", "", "", "1", "18",
                0, 0, 1, 0, 0, 0xFF8B5E3C, null);
        assertTrue(draft.hasColor());
        assertEquals(0xFF8B5E3C, draft.color());
    }

    // ============================================ empty is not absent

    @Test
    public void anEmptiedFieldIsNotTheSameAsAnUncarriedOne() {
        CourseDraft emptied = new CourseDraft("", "", "", "", "",
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_INDEX,
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_COLOR, null);
        CourseDraft absent = new CourseDraft(null, null, null, null, null,
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_INDEX,
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_COLOR, null);

        // The user who cleared a field gets an empty field back; a field that was
        // never in the state keeps whatever initialisation gave it.
        assertEquals("", emptied.name());
        assertEquals("", emptied.startWeek());
        assertNull(absent.name());
        assertNull(absent.startWeek());
        assertNotEquals(emptied.name(), absent.name());
    }

    @Test
    public void absentPositionsAndColoursAreReportedAsAbsent() {
        CourseDraft draft = new CourseDraft(null, null, null, null, null,
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_INDEX,
                CourseDraft.NO_INDEX, CourseDraft.NO_INDEX, CourseDraft.NO_COLOR, null);

        assertEquals(CourseDraft.NO_INDEX, draft.dayIndex());
        assertEquals(CourseDraft.NO_INDEX, draft.startPeriodIndex());
        assertEquals(CourseDraft.NO_INDEX, draft.endPeriodIndex());
        assertEquals(CourseDraft.NO_INDEX, draft.parityIndex());
        assertEquals(CourseDraft.NO_INDEX, draft.scheduleIndex());
        assertFalse(draft.hasColor());
        // Position zero is a real position -- Monday, the first period -- and must
        // not be mistaken for "absent".
        assertNotEquals(CourseDraft.NO_INDEX, 0);
    }

    @Test
    public void aRealColourIsNeverMistakenForNoColour() {
        // The marker only works because no stored colour is zero. A course that has
        // never been given one still holds Course.DEFAULT_COLOR.
        assertTrue("the marker must not collide with a real colour",
                com.weihe.schedule.model.Course.DEFAULT_COLOR != CourseDraft.NO_COLOR);
        CourseDraft withDefault = new CourseDraft("c", "", "", "1", "18",
                0, 0, 1, 0, 0, com.weihe.schedule.model.Course.DEFAULT_COLOR, null);
        assertTrue(withDefault.hasColor());
    }

    // ============================================ positions are not trusted

    @Test
    public void aPositionTheControlHasIsAccepted() {
        assertEquals(0, CourseDraft.usableIndex(0, 7));
        assertEquals(6, CourseDraft.usableIndex(6, 7));
        assertEquals(2, CourseDraft.usableIndex(2, 3));
    }

    @Test
    public void aPositionTheControlDoesNotHaveIsTreatedAsAbsent() {
        // A bell-schedule list can shrink while the state is away, so a position
        // saved against the old list can point past the end of the new one.
        // Selecting past the end throws; treating it as absent cannot.
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(7, 7));
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(99, 7));
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(-1, 7));
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(-5, 7));
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(0, 0));
        assertEquals(CourseDraft.NO_INDEX, CourseDraft.usableIndex(3, 0));
    }

    @Test
    public void usableIndexNeverThrowsForAnyPosition() {
        for (int count = 0; count <= 20; count++) {
            for (int index = -5; index <= 25; index++) {
                int usable = CourseDraft.usableIndex(index, count);
                boolean expected = index >= 0 && index < count;
                assertEquals("index " + index + " of " + count,
                        expected ? index : CourseDraft.NO_INDEX, usable);
            }
        }
    }

    // ============================================ it cannot switch targets

    @Test
    public void aDraftCarriesNothingThatCouldIdentifyACourse() {
        // Restoring a draft must never be able to point the form at a different
        // course, or turn an edit into a create. The guarantee is structural: there
        // is no identity here to be corrupted, and the id used to load the course
        // comes from the intent instead.
        for (Field field : CourseDraft.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            String fieldName = field.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse("a draft must not carry an identity: " + fieldName,
                    fieldName.contains("id") || fieldName.contains("original")
                            || fieldName.contains("existing"));
            assertFalse("a draft must not carry a course object: " + fieldName,
                    field.getType() == com.weihe.schedule.model.Course.class);
        }
    }
}
