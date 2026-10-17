package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import com.weihe.schedule.model.Course;

/**
 * Which text a course's note field resolves to, and what a save writes.
 *
 * <p>The grouping itself is {@link CourseColorSync}'s job and is tested there; what
 * is checked here is the part that is specific to notes -- that a record with none
 * of its own reads its siblings', and that saving from a second arrangement cannot
 * erase the note the course already had.</p>
 */
public class CourseNotesTest {

    private static Course course(long id, String note) {
        Course course = new Course();
        course.id = id;
        course.name = "\u8BFE" + id;
        course.note = note;
        return course;
    }

    // ------------------------------------------------------------------ blank

    @Test
    public void whitespaceIsNotANote() {
        assertTrue(CourseNotes.isBlank(null));
        assertTrue(CourseNotes.isBlank(""));
        assertTrue(CourseNotes.isBlank("   "));
        assertTrue(CourseNotes.isBlank("\n\n\t"));
        assertFalse(CourseNotes.isBlank("x"));
        assertFalse(CourseNotes.isBlank("  x  "));
    }

    // -------------------------------------------------------------- group note

    @Test
    public void aRecordWithANoteKeepsItsOwn() {
        Course own = course(1, "\u81EA\u5DF1\u7684");

        assertEquals("\u81EA\u5DF1\u7684", CourseNotes.groupNote(own,
                Arrays.asList(course(2, "\u522B\u4EBA\u7684"))));
    }

    @Test
    public void aRecordWithoutANoteReadsItsSiblings() {
        Course bare = course(1, "");

        assertEquals("\u65C1\u8FB9\u7684", CourseNotes.groupNote(bare,
                Arrays.asList(course(2, ""), course(3, "\u65C1\u8FB9\u7684"))));
    }

    @Test
    public void aCourseWithNoNotesAnywhereResolvesToEmpty() {
        assertEquals("", CourseNotes.groupNote(course(1, ""),
                Arrays.asList(course(2, ""), course(3, "  "))));
        assertEquals("", CourseNotes.groupNote(course(1, null), new ArrayList<Course>()));
        assertEquals("", CourseNotes.groupNote(null, null));
    }

    // ------------------------------------------------------------ what to store

    @Test
    public void theTypedNoteIsWhatGetsStored() {
        assertEquals("\u65B0\u5199\u7684",
                CourseNotes.noteToStore("  \u65B0\u5199\u7684  ", "\u65E7\u7684", false));
    }

    @Test
    public void anEditedCourseCanHaveItsNoteCleared() {
        // The field was filled with the course's note when the editor opened, so an
        // empty one means the user emptied it.
        assertEquals("", CourseNotes.noteToStore("", "\u65E7\u7684", false));
        assertEquals("", CourseNotes.noteToStore(null, "\u65E7\u7684", false));
    }

    @Test
    public void aNewArrangementJoinsTheNoteRatherThanClearingIt() {
        // The field was empty when the editor opened and the user never typed in it:
        // writing that emptiness through would erase a note they never saw.
        assertEquals("\u5DF2\u6709\u7684",
                CourseNotes.noteToStore("", "\u5DF2\u6709\u7684", true));
        assertEquals("\u5DF2\u6709\u7684",
                CourseNotes.noteToStore("   ", "\u5DF2\u6709\u7684", true));
    }

    @Test
    public void aNewCourseWithNoNoteAndNoSiblingsStoresNothing() {
        assertEquals("", CourseNotes.noteToStore("", "", true));
        assertEquals("", CourseNotes.noteToStore(null, null, true));
    }

    @Test
    public void aNewArrangementThatTypesItsOwnNoteKeepsIt() {
        assertEquals("\u81EA\u5DF1\u5199\u7684",
                CourseNotes.noteToStore("\u81EA\u5DF1\u5199\u7684", "\u5DF2\u6709\u7684", true));
    }

    @Test
    public void lineBreaksInsideTheNoteSurvive() {
        String typed = "\u7B2C\u4E00\u884C\n\u7B2C\u4E8C\u884C\n\n\u7B2C\u56DB\u884C";

        assertEquals(typed, CourseNotes.noteToStore(typed, "", false));
    }

    @Test
    public void onlyTheEndsAreTrimmed() {
        assertEquals("a\n\nb",
                CourseNotes.noteToStore("\n  a\n\nb  \n", "", false));
    }
}
