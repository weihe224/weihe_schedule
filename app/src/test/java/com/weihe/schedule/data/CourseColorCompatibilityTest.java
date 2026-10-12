package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * The course colour palette changed; stored courses must not.
 *
 * <p>A course keeps its own ARGB. That is what makes repainting the palette safe:
 * a course coloured before the change is stored as a number, so it keeps the
 * number and looks exactly as it did, while a course created afterwards starts
 * from a more vivid default. The failure this class exists to rule out is the
 * opposite arrangement -- a colour stored as an <em>index</em> into the palette,
 * which would silently repaint every existing course the moment the array was
 * reordered or resized.</p>
 *
 * <p>Lives in {@code data} rather than {@code design} because it drives the real
 * store, and the repository's test seam is package-visible (see AGENTS.md). The
 * pure palette-shape claims are in {@code design/CoursePaletteShapeTest}.</p>
 */
public class CourseColorCompatibilityTest {

    private static final int LEGACY_BLUE = 0xFF3C6A8E;
    private static final int LEGACY_GREEN = 0xFF3A7563;

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((android.content.Context) null);
    }

    @Test
    public void aCourseKeepsItsOwnStoredColourAcrossARestart() {
        // The whole compatibility argument in one test: a course written with the
        // old palette's colour still reads back as that exact colour.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = new Course();
        course.id = 1L;
        course.name = "Legacy";
        course.color = LEGACY_BLUE;
        repository.upsertCourse(course);

        CourseRepository reopened = repositoryFor(preferences);
        Course read = null;
        for (Course candidate : reopened.getCourses()) {
            if ("Legacy".equals(candidate.name)) {
                read = candidate;
            }
        }
        assertNotNull("the course must survive a restart", read);
        assertEquals("a stored colour must not be repainted by a palette change",
                LEGACY_BLUE, read.color);
    }

    @Test
    public void aColourThatIsNotInThePaletteIsStillKept() {
        // The store must not "repair" a colour just because it is not one of the
        // nine on offer. A course created when the picker offered a different set
        // still has a legitimate colour, and rounding it to the nearest current
        // swatch would repaint the user's timetable behind their back.
        //
        // A fresh store is seeded with sample courses, so the one under test is
        // found by name rather than assumed to be first.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = new Course();
        course.id = 4242L;
        course.name = "Retired colour";
        course.color = 0xFF123456;
        repository.upsertCourse(course);

        Course read = null;
        for (Course candidate : repository.getCourses()) {
            if ("Retired colour".equals(candidate.name)) {
                read = candidate;
            }
        }
        assertNotNull("the course must have been stored", read);
        assertEquals(0xFF123456, read.color);
    }

    @Test
    public void aCourseWithNoStoredColourFallsBackToTheDefault() {
        // The fallback has to be a real colour from the palette, not zero, or a
        // repaired course would be drawn in transparent black.
        FakePreferences preferences = new FakePreferences();
        preferences.edit().putString("courses",
                "[{\"id\":9999,\"name\":\"NoColour\",\"day\":1,\"startPeriod\":1,"
                        + "\"endPeriod\":2,\"startWeek\":1,\"endWeek\":16,"
                        + "\"parity\":0}]").commit();
        CourseRepository repository = repositoryFor(preferences);
        Course read = null;
        for (Course candidate : repository.getCourses()) {
            if ("NoColour".equals(candidate.name)) {
                read = candidate;
            }
        }
        assertNotNull("the course must have been read", read);
        assertEquals(Course.DEFAULT_COLOR, read.color);
    }

    @Test
    public void aLegacyColourSurvivesABackupRoundTrip() {
        Course source = new Course();
        source.id = 7L;
        source.name = "Legacy";
        source.color = LEGACY_GREEN;
        long semesterId = 100L;
        Semester semester = new Semester(semesterId, LocalDate.of(2026, 9, 7), "");
        Map<Long, List<Course>> bySemester = new HashMap<>();
        bySemester.put(Long.valueOf(semesterId), List.of(source));

        // A backup with no schedule at all is refused, so one is carried: the
        // point of this test is the colour, not a minimal document.
        String json = BackupCodec.write(BackupCodec.of(List.of(semester), bySemester,
                List.of(BellSchedules.standard()), semesterId,
                "2026-10-12T00:00:00", "1.0"));
        BackupCodec.Result result = BackupCodec.parse(json);
        assertTrue("the backup must be accepted: " + result.problems(),
                result.isOk());
        List<Course> restored = result.document().coursesOf(semesterId);
        assertEquals(1, restored.size());
        assertEquals("a backup written before the palette change still restores "
                        + "its own colour",
                LEGACY_GREEN, restored.get(0).color);
    }

    @Test
    public void copyKeepsTheSourceColour() {
        // Duplicating a course must not quietly re-colour it: the copy is a
        // second instance of the same course, and the user picked the colour.
        Course original = new Course();
        original.id = 3L;
        original.name = "Original";
        original.color = LEGACY_BLUE;

        assertEquals("a copy must keep the colour it was made from",
                LEGACY_BLUE, original.copy().color);
    }

    @Test
    public void aNewCourseStillStartsFromThePaletteDefault() {
        // The other half of the contract: what changes is only what a *new*
        // course looks like, and it must be a colour the picker can show as
        // selected.
        Course fresh = new Course();
        boolean inPalette = false;
        for (int candidate : Palette.COURSE_COLORS) {
            if (candidate == fresh.color) {
                inPalette = true;
            }
        }
        assertTrue("a new course must start on a selectable colour", inPalette);
    }
}
