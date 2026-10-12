package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * What a repository instance sees after the active semester changes elsewhere.
 *
 * <p>Every screen builds its own {@link CourseRepository} and keeps it for the
 * life of the screen, so the semester can be switched by a different instance
 * while this one is stopped. Its reads are keyed by the semester it adopted, and
 * {@code reload()} is the call that is supposed to bring it back in line with
 * storage.</p>
 *
 * <p>It did not. Reload dropped the cached lists but kept the adopted semester, so
 * an instance open on one term went on reading that term's key after a switch to
 * another -- and when the term it kept reading had nothing in it, every read came
 * back empty. The home screen showed no courses for a day that had them while the
 * overview, built after the switch, showed them. That is the report these tests
 * come from.</p>
 *
 * <p>The term an install begins with carries sample courses, so nothing here
 * assumes a term starts empty; the empty term is one the test creates.</p>
 */
public class ActiveSemesterReloadTest {

    private static final LocalDate TERM_TWO = LocalDate.parse("2027-02-22");

    @Test
    public void reloadFollowsASwitchMadeByAnotherInstance() {
        FakePreferences preferences = new FakePreferences();
        // The screen that stays open, holding its own repository.
        CourseRepository open = repositoryFor(preferences);
        Semester first = open.getActiveSemester();
        int seeded = open.getCourses().size();
        assertTrue("the first term is the seeded one", seeded > 0);

        // Another screen switches to a second term and puts one course in it.
        CourseRepository elseWhere = repositoryFor(preferences);
        Semester second = elseWhere.addSemester(TERM_TWO, "");
        elseWhere.setActiveSemester(second);
        elseWhere.upsertCourse(course("Optics"));

        open.reload();

        assertEquals("the open instance must follow the switch",
                second.id(), open.getActiveSemester().id());
        List<Course> seen = open.getCourses();
        assertEquals("the new term holds exactly what was put in it",
                1, seen.size());
        assertEquals("Optics", seen.get(0).name);
    }

    @Test
    public void anEmptyTermSwitchedToElsewhereIsNotShownAsTheOldTermsCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository open = repositoryFor(preferences);
        assertTrue(open.getCourses().size() > 0);

        // A second screen adds a term and switches to it, leaving it empty.
        CourseRepository elseWhere = repositoryFor(preferences);
        Semester empty = elseWhere.addSemester(TERM_TWO, "");
        elseWhere.setActiveSemester(empty);

        open.reload();

        assertEquals("the new term is empty, and that is the truth",
                0, open.getCourses().size());
    }

    @Test
    public void switchingBackIsSeenToo() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository open = repositoryFor(preferences);
        Semester first = open.getActiveSemester();
        int seeded = open.getCourses().size();

        CourseRepository elseWhere = repositoryFor(preferences);
        Semester second = elseWhere.addSemester(TERM_TWO, "");
        elseWhere.setActiveSemester(second);
        open.reload();
        assertEquals(0, open.getCourses().size());

        // Back to the term that has the courses. This is the reported case: they
        // existed the whole time and the home screen could not see them.
        elseWhere.setActiveSemester(first);
        open.reload();

        assertEquals(first.id(), open.getActiveSemester().id());
        assertEquals("the courses were there the whole time", seeded,
                open.getCourses().size());
    }

    @Test
    public void aWriteAfterReloadLandsInTheSemesterThatIsNowActive() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository open = repositoryFor(preferences);
        Semester first = open.getActiveSemester();

        CourseRepository elseWhere = repositoryFor(preferences);
        Semester second = elseWhere.addSemester(TERM_TWO, "");
        elseWhere.setActiveSemester(second);

        open.reload();
        open.upsertCourse(course("Optics"));

        // Stored against the term that is active now, not the one this instance
        // was opened on -- otherwise the course would vanish into a term nobody is
        // looking at.
        CourseRepository reader = repositoryFor(preferences);
        reader.setActiveSemester(second);
        assertEquals(1, reader.getCourses().size());
        assertEquals("Optics", reader.getCourses().get(0).name);

        reader.setActiveSemester(first);
        for (Course course : reader.getCourses()) {
            assertNotEquals("the old term must not have received it",
                    "Optics", course.name);
        }
    }

    @Test
    public void reloadStillDropsTheOtherCaches() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository open = repositoryFor(preferences);
        int semesters = open.getSemesters().size();

        // Another screen adds a term.
        CourseRepository elseWhere = repositoryFor(preferences);
        elseWhere.addSemester(TERM_TWO, "");

        open.reload();

        // Re-adopting the active semester must not have cost the rest of what
        // reload is for.
        assertEquals(semesters + 1, open.getSemesters().size());
        assertNotNull(open.getActiveSemester());
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static Course course(String name) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 2;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }
}
