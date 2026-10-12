package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * What "copy this course" does to the timetable.
 *
 * <p>The detail screen copies by taking the course's fields and dropping its id,
 * because the id is the only thing that makes a course the one it is. That is a
 * claim about the repository, so it is checked here: the copy has to arrive as a
 * record of its own with an id of its own, and the course it came from has to be
 * exactly as it was.</p>
 */
public class CourseCopyTest {

    @Test
    public void aCopyBecomesItsOwnRecordAndLeavesTheOriginalAlone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course original = course("Calculus");
        repository.upsertCourse(original);
        int before = repository.getCourses().size();

        repository.upsertCourse(asCopyOf(original));
        repository.reload();

        assertEquals("the term gained one course, not two", before + 1,
                repository.getCourses().size());
        Course stored = repository.findCourse(original.id);
        assertNotNull("the original is still there under its own id", stored);
        assertEquals("Calculus", stored.name);
        assertEquals(3, stored.dayOfWeek);
        assertEquals(5, stored.startPeriod);
    }

    @Test
    public void theCopyCarriesEveryFieldOfTheCourseItCameFrom() {
        Course original = course("Calculus");
        original.teacher = "Prof. Li";
        original.location = "Building A 203";
        original.startWeek = 3;
        original.endWeek = 11;
        original.parity = Course.PARITY_ODD;
        original.color = 0xFF3C6A8E;
        original.scheduleName = "Lab building";

        Course copy = asCopyOf(original);

        assertEquals(0L, copy.id);
        assertEquals(original.name, copy.name);
        assertEquals(original.teacher, copy.teacher);
        assertEquals(original.location, copy.location);
        assertEquals(original.dayOfWeek, copy.dayOfWeek);
        assertEquals(original.startPeriod, copy.startPeriod);
        assertEquals(original.endPeriod, copy.endPeriod);
        assertEquals(original.startWeek, copy.startWeek);
        assertEquals(original.endWeek, copy.endWeek);
        assertEquals(original.parity, copy.parity);
        assertEquals(original.color, copy.color);
        assertEquals(original.scheduleName, copy.scheduleName);
    }

    @Test
    public void savingACopyDoesNotChangeTheCourseItWasTakenFrom() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course original = course("Calculus");
        repository.upsertCourse(original);
        String before = describe(repository.findCourse(original.id));

        repository.upsertCourse(asCopyOf(original));
        repository.reload();

        assertEquals("the original is byte for byte what it was", before,
                describe(repository.findCourse(original.id)));
        // How many courses the term holds is not asserted here: the seeded term
        // starts with sample courses, so a fixed number would be a claim about the
        // seed rather than about copying. What matters is that every record has an
        // id of its own, which the loop below checks.
        List<Long> ids = new ArrayList<>();
        for (Course course : repository.getCourses()) {
            assertFalse("every record has an id of its own",
                    ids.contains(course.id));
            ids.add(course.id);
        }
    }

    @Test
    public void theCopyCanBeChangedWithoutTouchingTheOriginal() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course original = course("Calculus");
        repository.upsertCourse(original);

        Course copy = asCopyOf(original);
        copy.name = "Calculus (lab)";
        copy.dayOfWeek = 5;
        repository.upsertCourse(copy);
        repository.reload();

        assertEquals("Calculus", repository.findCourse(original.id).name);
        assertEquals(3, repository.findCourse(original.id).dayOfWeek);
        Course storedCopy = repository.findCourse(copy.id);
        assertNotNull(storedCopy);
        assertEquals("Calculus (lab)", storedCopy.name);
        assertEquals(5, storedCopy.dayOfWeek);
    }

    // ------------------------------------------------------------------ fixtures

    /** Exactly what the detail screen does: same fields, no identity. */
    private static Course asCopyOf(Course source) {
        Course copy = source.copy();
        copy.id = 0L;
        return copy;
    }

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static Course course(String name) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 3;
        course.startPeriod = 5;
        course.endPeriod = 6;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    private static String describe(Course course) {
        assertNotNull(course);
        return course.name + "|" + course.teacher + "|" + course.location + "|"
                + course.dayOfWeek + "|" + course.startPeriod + "|" + course.endPeriod
                + "|" + course.startWeek + "|" + course.endWeek + "|" + course.parity
                + "|" + course.color + "|" + course.scheduleName;
    }
}
