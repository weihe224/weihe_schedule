package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseQuery;

/**
 * The half of the search screen that is not a view: what the list is searched
 * against, and what it holds after the timetable moves underneath it.
 *
 * <p>{@link CourseQuery#search} itself is covered by {@code CourseQueryTest}.
 * What is checked here is the wiring the screen depends on -- that the list comes
 * from the active semester, and that re-reading the repository after an edit, a
 * delete or a semester switch changes what a search finds. Those are the
 * behaviours an emulator would otherwise be the only way to see, and they are
 * ordinary repository reads, so they run here.</p>
 *
 * <p>Lives in {@code com.weihe.schedule.data} for the same reason the other
 * repository tests do: {@link CourseRepository#useStoreForNextInstance} and the
 * repository's own constructor are package-private test seams.</p>
 */
public class CourseSearchFlowTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");

    // ============================================ current semester only

    @Test
    public void aSearchOnlySeesTheActiveSemester() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        repository.upsertCourse(course("Calculus", "Prof. Li", "A203"));
        Semester second = repository.addSemester(LocalDate.parse("2027-02-22"), "Spring");
        // addSemester does not switch to what it added; the semester screen does
        // that explicitly, and the search has to follow whatever is active.
        repository.setActiveSemester(second);
        repository.upsertCourse(course("Calculus", "Prof. Wang", "D305"));

        // The active semester is the second one, so only its Calculus is visible.
        assertEquals(second.id(), repository.getActiveSemester().id());
        List<Course> found = CourseQuery.search(repository.getCourses(), "Calculus");
        assertEquals(1, found.size());
        assertEquals("Prof. Wang", found.get(0).teacher);

        repository.setActiveSemester(first);
        List<Course> inFirst = CourseQuery.search(repository.getCourses(), "Calculus");
        assertEquals(1, inFirst.size());
        assertEquals("Prof. Li", inFirst.get(0).teacher);
    }

    @Test
    public void switchingSemesterChangesWhatASearchFinds() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        repository.upsertCourse(course("Calculus", "", "A203"));
        Semester second = repository.addSemester(LocalDate.parse("2027-02-22"), "Spring");
        repository.setActiveSemester(second);
        repository.upsertCourse(course("Optics", "", "D305"));

        assertEquals(1, CourseQuery.search(repository.getCourses(), "Optics").size());
        assertEquals(0, CourseQuery.search(repository.getCourses(), "Calculus").size());

        repository.setActiveSemester(first);

        assertEquals("the other term's course must not leak in", 0,
                CourseQuery.search(repository.getCourses(), "Optics").size());
        assertEquals(1, CourseQuery.search(repository.getCourses(), "Calculus").size());
        assertEquals("and the active semester really did move back",
                first.id(), repository.getActiveSemester().id());
    }

    // ============================================ the timetable moving underneath

    @Test
    public void aDeletedCourseLeavesTheResults() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course calculus = course("Calculus", "Prof. Li", "A203");
        repository.upsertCourse(calculus);
        repository.upsertCourse(course("Optics", "Prof. Wang", "D305"));
        assertEquals(2, CourseQuery.search(repository.getCourses(), "o").size());

        repository.deleteCourse(calculus.id);
        repository.reload();

        List<Course> found = CourseQuery.search(repository.getCourses(), "o");
        assertEquals(1, found.size());
        assertEquals("Optics", found.get(0).name);
        assertFalse("the deleted course must not come back",
                found.get(0).name.equals("Calculus"));
    }

    @Test
    public void anEditedCourseIsFoundUnderItsNewName() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = course("Calculus", "Prof. Li", "A203");
        repository.upsertCourse(course);

        course.name = "Advanced Calculus";
        course.location = "D305";
        repository.upsertCourse(course);
        repository.reload();

        assertEquals("the old name is gone", 0,
                CourseQuery.search(repository.getCourses(), "Calculus I").size());
        List<Course> found = CourseQuery.search(repository.getCourses(), "Advanced");
        assertEquals(1, found.size());
        assertEquals("D305", found.get(0).location);
    }

    @Test
    public void aSearchAfterAReloadSeesWhatIsActuallyStored() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.upsertCourse(course("Calculus", "Prof. Li", "A203"));

        // A second repository over the same store is what another screen sees.
        CourseRepository other = repositoryFor(preferences);
        other.upsertCourse(course("Optics", "Prof. Wang", "D305"));

        repository.reload();

        assertEquals("re-reading storage is what makes the new course findable",
                1, CourseQuery.search(repository.getCourses(), "Optics").size());
        assertEquals(2, CourseQuery.search(repository.getCourses(), "o").size());
    }

    @Test
    public void anImportReplacesWhatASearchFinds() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.upsertCourse(course("Calculus", "Prof. Li", "A203"));
        assertEquals(1, CourseQuery.search(repository.getCourses(), "Calculus").size());

        List<Course> imported = new ArrayList<>();
        imported.add(course("Optics", "Prof. Wang", "D305"));
        repository.replaceCourses(imported);
        repository.reload();

        assertEquals("the replaced timetable is the one being searched", 0,
                CourseQuery.search(repository.getCourses(), "Calculus").size());
        assertEquals(1, CourseQuery.search(repository.getCourses(), "Optics").size());
    }

    // ============================================ what a search must not do

    @Test
    public void searchingWritesNothingToStorage() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.upsertCourse(course("Calculus", "Prof. Li", "A203"));
        repository.upsertCourse(course("Optics", "Prof. Wang", "D305"));
        Map<String, Object> before = preferences.snapshot();
        int commits = preferences.commitCount();

        for (String keyword : new String[] { "Calculus", "Prof", "D305", "o", "zzz",
                "", "   " }) {
            CourseQuery.search(repository.getCourses(), keyword);
        }

        assertEquals("a search is a read, and only a read", before,
                preferences.snapshot());
        assertEquals(commits, preferences.commitCount());
    }

    @Test
    public void searchingDoesNotDisturbTheCoursesItRead() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.upsertCourse(course("Calculus", "Prof. Li", "A203"));
        repository.upsertCourse(course("Optics", "Prof. Wang", "D305"));
        List<Course> courses = repository.getCourses();
        String before = describeAll(courses);

        CourseQuery.search(courses, "o");
        CourseQuery.search(courses, "");
        CourseQuery.search(courses, "Prof");

        assertEquals(before, describeAll(courses));
    }

    // ============================================ same name, different course

    @Test
    public void twoCoursesSharingANameComeBackAsTwoDistinguishableResults() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course first = course("Calculus", "Prof. Li", "A203");
        first.dayOfWeek = 1;
        Course second = course("Calculus", "Prof. Wang", "D305");
        second.dayOfWeek = 3;
        repository.upsertCourse(first);
        repository.upsertCourse(second);
        repository.reload();

        List<Course> found = CourseQuery.search(repository.getCourses(), "Calculus");

        assertEquals(2, found.size());
        assertNotNull(found.get(0).id);
        assertFalse("the two must be told apart by id, not by name",
                found.get(0).id == found.get(1).id);
        assertFalse("and they are genuinely different classes",
                found.get(0).dayOfWeek == found.get(1).dayOfWeek);
        // The id is what the detail screen is opened with, so each result must
        // resolve to its own course and not to its namesake.
        assertEquals("Prof. Li", repository.findCourse(found.get(0).id).teacher);
        assertEquals("Prof. Wang", repository.findCourse(found.get(1).id).teacher);
    }

    @Test
    public void searchingAnEmptyTimetableFindsNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        for (Course course : repository.getCourses()) {
            repository.deleteCourse(course.id);
        }
        repository.reload();

        assertTrue(repository.getCourses().isEmpty());
        assertTrue(CourseQuery.search(repository.getCourses(), "a").isEmpty());
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static Course course(String name, String teacher, String location) {
        Course course = new Course();
        course.name = name;
        course.teacher = teacher;
        course.location = location;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    /** Every stored field, for "did searching change anything" comparisons. */
    private static String describeAll(List<Course> courses) {
        StringBuilder text = new StringBuilder();
        for (Course course : courses) {
            text.append(course.id).append('|').append(course.name).append('|')
                    .append(course.teacher).append('|').append(course.location)
                    .append('|').append(course.dayOfWeek).append('|')
                    .append(course.startPeriod).append('|').append(course.endPeriod)
                    .append('|').append(course.parity).append('\n');
        }
        return text.toString();
    }

}
