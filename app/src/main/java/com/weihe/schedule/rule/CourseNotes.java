package com.weihe.schedule.rule;

import java.util.List;

import com.weihe.schedule.model.Course;

/**
 * The note a course carries, as opposed to the note one arrangement of it carries.
 *
 * <p>A subject that meets twice a week is two records, so a note belongs to the
 * course and has to read the same from either of them. {@link CourseColorSync}
 * already answers "which records are the same course", so this class asks it
 * rather than deciding again: the note is shared across exactly the arrangements
 * whose colours are shared.</p>
 *
 * <h2>Why the note lives on the records</h2>
 *
 * <p>It is stored on each record and written to all of them at once, which is what
 * {@link com.weihe.schedule.model.Course#color} already does. A side table keyed by
 * course name was the other option and was rejected for one reason: renaming a
 * course splits it from its arrangements, and a name-keyed table would then leave
 * the renamed record pointing at a key that no longer exists. The note would
 * vanish from the record the user had just edited. Storing it on the record means
 * a rename carries it along, and the arrangements left behind keep their own copy,
 * so nothing is ever silently lost.</p>
 */
public final class CourseNotes {

    private CourseNotes() {
    }

    /** Whether a note says nothing, counting whitespace and blank lines as nothing. */
    public static boolean isBlank(String note) {
        return note == null || note.trim().isEmpty();
    }

    /**
     * The note this course carries, whichever arrangement is asked.
     *
     * <p>The course's own note wins; otherwise the first sibling that has one. The
     * fallback is what lets a newly added arrangement read the note the course
     * already had, and it repairs the one case where records can disagree -- an
     * arrangement written by a path that did not go through the note-aware save.</p>
     *
     * @param course     the arrangement being read
     * @param sameCourse its siblings, as returned by
     *                   {@link CourseColorSync#sameCourseAs(List, Course)}
     */
    public static String groupNote(Course course, List<Course> sameCourse) {
        if (course != null && !isBlank(course.note)) {
            return course.note;
        }
        if (sameCourse != null) {
            for (Course sibling : sameCourse) {
                if (sibling != null && !isBlank(sibling.note)) {
                    return sibling.note;
                }
            }
        }
        return "";
    }

    /**
     * What to actually store when the editor saves.
     *
     * <p>Normally the field wins, including when it is empty: clearing a note is a
     * deliberate act and has to stick. The one exception is a course that did not
     * exist before -- a second arrangement of a course whose note was written on
     * the first one. Its field was empty when the editor opened, so writing that
     * emptiness through would erase a note the user never saw. Such a record joins
     * the course's note instead.</p>
     *
     * @param typed      what the user left in the field
     * @param groupNote  the note the course already carries, or empty
     * @param isNew      whether this record is being created rather than edited
     */
    public static String noteToStore(String typed, String groupNote, boolean isNew) {
        String trimmed = typed == null ? "" : typed.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        if (isNew && !isBlank(groupNote)) {
            return groupNote;
        }
        return "";
    }
}
