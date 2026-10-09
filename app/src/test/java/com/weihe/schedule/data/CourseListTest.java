package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * Tests for the course list operations.
 *
 * <p>These rules used to be written inline inside {@code CourseRepository},
 * where the only way to check them was to install the app and add courses by
 * hand. Two of them -- copy-on-store and collision-free id assignment -- are
 * exactly the kind of thing that fails silently in production.</p>
 */
public class CourseListTest {

    private static Course course(long id, String name) {
        Course course = new Course();
        course.id = id;
        course.name = name;
        return course;
    }

    private static List<Course> listOf(Course... courses) {
        List<Course> list = new ArrayList<>();
        for (Course course : courses) {
            list.add(course);
        }
        return list;
    }

    @Test
    public void findReturnsTheStoredCourse() {
        Course found = CourseList.find(listOf(course(1, "Math"), course(2, "English")), 2L);

        assertEquals("English", found.name);
    }

    @Test
    public void findReturnsNullWhenTheIdIsUnknown() {
        assertNull(CourseList.find(listOf(course(1, "Math")), 99L));
    }

    @Test
    public void upsertAppendsACourseThatIsNotStoredYet() {
        List<Course> courses = new ArrayList<>();

        assertFalse(CourseList.upsert(courses, course(1, "Math")));

        assertEquals(1L, courses.size());
        assertEquals(1L, courses.get(0).id);
    }

    @Test
    public void upsertReplacesTheCourseWithTheSameId() {
        List<Course> courses = listOf(course(1, "Math"));

        assertTrue(CourseList.upsert(courses, course(1, "Physics")));

        assertEquals(1L, courses.size());
        assertEquals("Physics", courses.get(0).name);
    }

    @Test
    public void upsertStoresACopyRatherThanTheCallerInstance() {
        List<Course> courses = new ArrayList<>();
        Course edited = course(1, "Math");

        CourseList.upsert(courses, edited);
        edited.name = "changed after saving";

        assertEquals("Math", courses.get(0).name);
    }

    @Test
    public void removeDropsTheCourseAndReportsIt() {
        List<Course> courses = listOf(course(1, "Math"), course(2, "English"));

        assertTrue(CourseList.remove(courses, 1L));

        assertEquals(1L, courses.size());
        assertEquals(2L, courses.get(0).id);
    }

    @Test
    public void removeReportsNothingWhenTheIdIsUnknown() {
        List<Course> courses = listOf(course(1, "Math"));

        assertFalse(CourseList.remove(courses, 99L));

        assertEquals(1L, courses.size());
    }

    @Test
    public void nextIdKeepsThePreferredIdWhenItIsFree() {
        assertEquals(500L, CourseList.nextId(new ArrayList<Course>(), 500L));
    }

    @Test
    public void nextIdWalksPastIdsThatAreAlreadyTaken() {
        List<Course> courses = listOf(course(1000, "A"), course(1001, "B"));

        // 1000 and 1001 are taken, so the first free id is 1002.
        assertEquals(1002L, CourseList.nextId(courses, 1000L));

        // Once 1002 is stored as well, the search has to move on again.
        courses.add(course(1002, "C"));
        assertEquals(1003L, CourseList.nextId(courses, 1000L));
    }

    @Test
    public void ensureIdLeavesAnExistingIdAlone() {
        Course existing = course(7, "Math");

        assertEquals(7L, CourseList.ensureId(new ArrayList<Course>(), existing, 999L));
        assertEquals(7L, existing.id);
    }

    @Test
    public void ensureIdAssignsAFreshIdWhenTheCourseHasNone() {
        Course fresh = course(0, "Math");

        assertEquals(1000L, CourseList.ensureId(new ArrayList<Course>(), fresh, 1000L));
        assertEquals(1000L, fresh.id);
    }

    @Test
    public void aStoredEntryWithoutAnIdCannotAbsorbANewCourse() {
        // A damaged payload can leave a stored course with id 0. The new course
        // must still get a real id instead of quietly replacing that entry.
        List<Course> courses = listOf(course(0, "damaged entry"));
        Course fresh = course(0, "New");

        CourseList.ensureId(courses, fresh, 1000L);
        CourseList.upsert(courses, fresh);

        assertEquals(2L, courses.size());
        assertEquals(1000L, fresh.id);
        assertEquals("damaged entry", courses.get(0).name);
    }
}
