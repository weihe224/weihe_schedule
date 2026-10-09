package com.weihe.schedule;

import java.util.List;
import com.weihe.schedule.model.Course;

/**
 * List operations over the stored courses.
 *
 * <p>{@link CourseRepository} used to do two very different jobs at once: talk
 * to {@code SharedPreferences}, and decide how a course joins or leaves a list.
 * The second job is ordinary logic that has nothing to do with storage, so it
 * lives here -- where it can be exercised on the JVM -- and the repository is
 * left as a thin adapter.</p>
 *
 * <p>Two rules are worth calling out because both used to be buried in the
 * repository and neither was verifiable:</p>
 *
 * <ul>
 *   <li>{@link #upsert} stores a <em>copy</em>, so a caller that keeps editing
 *       its own object cannot mutate what was saved.</li>
 *   <li>{@link #ensureId} walks forward from a preferred id until it finds a
 *       free one. The old code used a bare {@code System.currentTimeMillis()},
 *       which silently collides when two courses are created in the same
 *       millisecond.</li>
 * </ul>
 *
 * <p>Ids are assumed to be unique within a list; that is what {@link #ensureId}
 * and {@link #nextId} exist to guarantee.</p>
 */
public final class CourseList {

    private CourseList() {
    }

    /** The course with this id, or {@code null} when the list has none. */
    public static Course find(List<Course> courses, long id) {
        for (Course course : courses) {
            if (course.id == id) {
                return course;
            }
        }
        return null;
    }

    /**
     * Adds {@code course}, replacing any stored course that carries the same id.
     *
     * @return {@code true} when a course was replaced rather than appended
     */
    public static boolean upsert(List<Course> courses, Course course) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == course.id) {
                courses.set(i, course.copy());
                return true;
            }
        }
        courses.add(course.copy());
        return false;
    }

    /**
     * Removes the course with this id.
     *
     * @return {@code true} when a course was actually removed
     */
    public static boolean remove(List<Course> courses, long id) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).id == id) {
                courses.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * An id no stored course uses yet, starting from {@code preferred}.
     *
     * <p>Walking forward keeps ids unique while preserving their readable,
     * time-ordered shape.</p>
     */
    public static long nextId(List<Course> courses, long preferred) {
        long candidate = preferred;
        while (find(courses, candidate) != null) {
            candidate++;
        }
        return candidate;
    }

    /**
     * Gives {@code course} a fresh id when it does not have one yet.
     *
     * <p>The id is assigned before the course is placed in the list, so a
     * malformed stored entry that happens to carry id 0 cannot absorb a brand
     * new course -- which is exactly what used to happen.</p>
     *
     * @return the id the course ends up with
     */
    public static long ensureId(List<Course> courses, Course course, long preferred) {
        if (course.id == 0L) {
            course.id = nextId(courses, preferred);
        }
        return course.id;
    }
}
