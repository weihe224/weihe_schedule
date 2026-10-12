package com.weihe.schedule.rule;

import java.util.ArrayList;
import java.util.List;

import com.weihe.schedule.model.Course;

/**
 * Which stored courses are "the same course" when one of them changes colour.
 *
 * <p>A course that meets twice a week is stored twice: the timetable holds
 * arrangements, not subjects, so 高等数学 on Monday and 高等数学 on Thursday are two
 * records with two ids. Repainting one leaves the other behind, which is why the
 * editor offers to do both at once.</p>
 *
 * <h2>What counts as the same</h2>
 *
 * <p>Only the name, and only exactly: after trimming, the two names must be equal
 * character for character. Nothing here matches loosely, folds case or looks at
 * substrings -- two courses called 大学英语 and 大学英语（二）are different courses,
 * and guessing otherwise would repaint a course the user never opened.</p>
 *
 * <p>Teacher, room, day, periods and weeks are deliberately <em>not</em> compared.
 * Those differences are the reason one subject became several records in the first
 * place, so requiring them to match would find nothing to sync.</p>
 *
 * <h2>What it will not reach</h2>
 *
 * <p>The caller passes one semester's courses -- {@code CourseRepository.getCourses()}
 * returns the active term only -- so a same-named course in another term is out of
 * reach by construction rather than by a check that could be forgotten. The record
 * being edited is excluded by id, never by name or by its position in the list.</p>
 */
public final class CourseColorSync {

    private CourseColorSync() {
    }

    /** The name as it is compared: trimmed, and otherwise left exactly as typed. */
    public static String normalize(String name) {
        return name == null ? "" : name.trim();
    }

    /**
     * The other arrangements of the course being edited, in list order.
     *
     * @param courses one semester's courses
     * @param editing the record the editor has open, excluded by id
     */
    public static List<Course> sameCourseAs(List<Course> courses, Course editing) {
        List<Course> matches = new ArrayList<>();
        if (courses == null || editing == null) {
            return matches;
        }
        String wanted = normalize(editing.name);
        if (wanted.isEmpty()) {
            // A course with no name cannot be recognised as another's twin, and a
            // blank name would match every other blank one.
            return matches;
        }
        for (Course course : courses) {
            if (course == null || course.id == editing.id) {
                continue;
            }
            if (normalize(course.name).equals(wanted)) {
                matches.add(course);
            }
        }
        return matches;
    }

    /**
     * Those of {@code courses} that are not already {@code color}.
     *
     * <p>Returned separately so the confirmation can say how many records will
     * actually change, rather than how many happen to share the name.</p>
     */
    public static List<Long> needingColor(List<Course> courses, int color) {
        List<Long> ids = new ArrayList<>();
        if (courses == null) {
            return ids;
        }
        for (Course course : courses) {
            if (course != null && course.color != color) {
                ids.add(Long.valueOf(course.id));
            }
        }
        return ids;
    }

    /**
     * One line per arrangement, for the confirmation.
     *
     * <p>The name is the same on every line, so each one carries the time instead:
     * that is the only thing that tells two arrangements of one course apart, and
     * the only way the user can see the width of what they are about to change.</p>
     */
    public static String describe(List<Course> courses) {
        StringBuilder text = new StringBuilder();
        if (courses == null) {
            return text.toString();
        }
        for (Course course : courses) {
            if (course == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append('\u00B7').append(' ').append(CourseConflicts.describe(course));
        }
        return text.toString();
    }
}
