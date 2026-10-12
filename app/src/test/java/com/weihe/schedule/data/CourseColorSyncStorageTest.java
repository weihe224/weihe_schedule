package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseColorSync;

/**
 * Repainting a course's arrangements through the store.
 *
 * <p>The rule that decides who matches is covered by {@code CourseColorSyncTest}.
 * What is checked here is what reaches the disk: that one batch call moves every
 * arrangement and nothing else, that another term's same-named course is left
 * alone, and that every other field of every record survives untouched.</p>
 *
 * <p>Every course these tests add carries a name of its own. The seeded term holds
 * sample courses, and some of those share a name with each other, so a test written
 * against a real-looking name like 高等数学 would be counting the seed as well as
 * its own records.</p>
 */
public class CourseColorSyncStorageTest {

    private static final int NEW_COLOR = 0xFFA95E17;
    private static final String UNIQUE = "ZZ \u6D4B\u8BD5\u8BFE\u7A0B";

    // ============================================ one arrangement only

    @Test
    public void changingTheColourOfOneRecordLeavesItsSiblingsAlone() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        Course thursday = add(repository, UNIQUE, 0xFF1F7F63, 4, 5);

        monday.color = NEW_COLOR;
        repository.upsertCourse(monday);
        repository.reload();

        assertEquals("only the edited record moved", NEW_COLOR,
                repository.findCourse(monday.id).color);
        assertEquals("the other arrangement kept its colour", 0xFF1F7F63,
                repository.findCourse(thursday.id).color);
    }

    // ============================================ syncing

    @Test
    public void syncingRepaintsEveryArrangementInOneCall() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        add(repository, UNIQUE, 0xFF1F7F63, 4, 5);
        add(repository, UNIQUE, 0xFF1F7F63, 5, 7);

        Course edited = repository.findCourse(monday.id);
        edited.color = NEW_COLOR;
        repository.upsertCourse(edited);
        List<Course> affected = CourseColorSync.sameCourseAs(
                repository.getCourses(), edited);
        int changed = repository.recolorCourses(
                CourseColorSync.needingColor(affected, NEW_COLOR), NEW_COLOR);
        repository.reload();

        assertEquals("the two other arrangements changed", 2, changed);
        assertEquals("and all three now agree", 3, countNamed(repository, UNIQUE));
        for (Course course : repository.getCourses()) {
            if (UNIQUE.equals(course.name)) {
                assertEquals("every arrangement is the new colour", NEW_COLOR,
                        course.color);
            }
        }
    }

    @Test
    public void theColourOfEveryArrangementAgreesAfterSyncing() {
        // Home, the week grid and the detail page all read this one method, so
        // agreeing here is what "the screens agree" means without a device.
        CourseRepository repository = repository(new FakePreferences());
        Course a = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        add(repository, UNIQUE, 0xFF1F7F63, 3, 3);
        add(repository, UNIQUE, 0xFF1F7F63, 5, 5);

        Course edited = repository.findCourse(a.id);
        edited.color = NEW_COLOR;
        repository.upsertCourse(edited);
        repository.recolorCourses(CourseColorSync.needingColor(
                CourseColorSync.sameCourseAs(repository.getCourses(), edited),
                NEW_COLOR), NEW_COLOR);
        repository.reload();

        List<Integer> colors = new ArrayList<>();
        for (Course course : repository.getCourses()) {
            if (UNIQUE.equals(course.name)) {
                colors.add(Integer.valueOf(course.color));
            }
        }
        assertEquals(3, colors.size());
        for (Integer color : colors) {
            assertEquals(Integer.valueOf(NEW_COLOR), color);
        }
    }

    // ============================================ nothing else is touched

    @Test
    public void syncingChangesTheColourAndNothingElse() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        Course thursday = add(repository, UNIQUE, 0xFF1F7F63, 4, 5);
        thursday.teacher = "\u674E\u8001\u5E08";
        thursday.location = "\u5B9E\u9A8C\u697C B305";
        thursday.endPeriod = 6;
        thursday.startWeek = 9;
        thursday.endWeek = 16;
        thursday.parity = Course.PARITY_ODD;
        thursday.scheduleName = "B/C \u697C\u4F5C\u606F";
        repository.upsertCourse(thursday);

        Course before = repository.findCourse(thursday.id);
        String fieldsBefore = fieldsOf(before);

        Course edited = repository.findCourse(monday.id);
        edited.color = NEW_COLOR;
        repository.upsertCourse(edited);
        repository.recolorCourses(
                Collections.singletonList(Long.valueOf(thursday.id)), NEW_COLOR);
        repository.reload();

        Course after = repository.findCourse(thursday.id);
        assertNotNull(after);
        assertEquals("every field but the colour is identical", fieldsBefore,
                fieldsOf(after));
        assertEquals("and the colour is the new one", NEW_COLOR, after.color);
        assertEquals("the id never changes", thursday.id, after.id);
    }

    @Test
    public void aRecordIsNeverDeletedAndRecreated() {
        CourseRepository repository = repository(new FakePreferences());
        Course a = add(repository, UNIQUE, 0xFF1F7F63, 2, 3);
        Course b = add(repository, UNIQUE, 0xFF1F7F63, 4, 3);
        int countBefore = countNamed(repository, UNIQUE);

        repository.recolorCourses(
                Arrays.asList(Long.valueOf(a.id), Long.valueOf(b.id)), NEW_COLOR);
        repository.reload();

        assertEquals("the count is unchanged", countBefore, countNamed(repository, UNIQUE));
        assertNotNull(repository.findCourse(a.id));
        assertNotNull(repository.findCourse(b.id));
        assertEquals("the ids are the ones that were there before", a.id,
                repository.findCourse(a.id).id);
        assertEquals(b.id, repository.findCourse(b.id).id);
    }

    // ============================================ another term is out of reach

    @Test
    public void aSameNamedCourseInAnotherTermIsNotRepainted() {
        CourseRepository repository = repository(new FakePreferences());
        Semester first = repository.getActiveSemester();
        Course inFirst = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);

        // A second term, with a course of the same name, and made active.
        Semester second = repository.addSemester(LocalDate.parse("2027-02-22"), "");
        repository.setActiveSemester(second);
        Course inSecond = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);

        Course edited = repository.findCourse(inSecond.id);
        edited.color = NEW_COLOR;
        repository.upsertCourse(edited);
        // The repository hands over one term's courses, so the sync cannot see the
        // other term's record at all -- checked rather than assumed.
        assertTrue("this term holds one record, so it has no siblings",
                CourseColorSync.sameCourseAs(repository.getCourses(), edited).isEmpty());
        repository.recolorCourses(CourseColorSync.needingColor(
                CourseColorSync.sameCourseAs(repository.getCourses(), edited),
                NEW_COLOR), NEW_COLOR);
        repository.reload();
        assertEquals("this term's record did change", NEW_COLOR,
                repository.findCourse(inSecond.id).color);

        // Back to the first term: its record still has the old colour.
        repository.setActiveSemester(first);
        repository.reload();
        assertEquals("the other term's record was not repainted", 0xFF1F7F63,
                repository.findCourse(inFirst.id).color);
        assertEquals(1, countNamed(repository, UNIQUE));
    }

    @Test
    public void nothingIsOfferedFromAnotherTermEvenWithTheSameName() {
        CourseRepository repository = repository(new FakePreferences());
        Semester first = repository.getActiveSemester();
        Semester second = repository.addSemester(LocalDate.parse("2027-02-22"), "");

        repository.setActiveSemester(second);
        Course here = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        repository.setActiveSemester(first);
        add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        repository.setActiveSemester(second);
        repository.reload();

        Course edited = repository.findCourse(here.id);
        assertTrue("this term holds exactly one, so nothing is offered",
                CourseColorSync.sameCourseAs(repository.getCourses(), edited).isEmpty());
    }

    // ============================================ cancelling, and compatibility

    @Test
    public void cancellingChangesNothingAtAll() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        Course thursday = add(repository, UNIQUE, 0xFF1F7F63, 4, 5);
        String before = fieldsOf(repository.findCourse(thursday.id));

        // What dismissing the dialog does: nothing but a reload, so the editor's
        // unsaved edit never reaches the others.
        repository.reload();

        assertEquals("the other arrangement is exactly as it was", before,
                fieldsOf(repository.findCourse(thursday.id)));
        assertEquals(0xFF1F7F63, repository.findCourse(monday.id).color);
    }

    @Test
    public void aColourThePaletteNoLongerOffersIsStillSynced() {
        // The compatibility path: a stored colour outside the palette is still a
        // colour, and repainting onto it is allowed like any other.
        CourseRepository repository = repository(new FakePreferences());
        Course a = add(repository, UNIQUE, 0xFF1F7F63, 1, 1);
        Course b = add(repository, UNIQUE, 0xFF1F7F63, 3, 3);
        int legacy = 0xFF525F6B;

        int changed = repository.recolorCourses(
                Arrays.asList(Long.valueOf(a.id), Long.valueOf(b.id)), legacy);
        repository.reload();

        assertEquals(2, changed);
        assertEquals(legacy, repository.findCourse(a.id).color);
        assertEquals(legacy, repository.findCourse(b.id).color);
    }

    @Test
    public void syncingToTheColourAlreadyHeldWritesNothing() {
        CourseRepository repository = repository(new FakePreferences());
        Course a = add(repository, UNIQUE, 0xFF1F7F63, 2, 3);

        assertEquals("no change, so no write", 0,
                repository.recolorCourses(
                        Collections.singletonList(Long.valueOf(a.id)), 0xFF1F7F63));
        assertEquals(0, repository.recolorCourses(null, NEW_COLOR));
        assertEquals(0, repository.recolorCourses(new ArrayList<Long>(), NEW_COLOR));
        assertEquals("an id that is not there changes nothing", 0,
                repository.recolorCourses(
                        Collections.singletonList(Long.valueOf(-1L)), NEW_COLOR));
    }

    // ============================================ fixtures

    private static CourseRepository repository(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static Course add(CourseRepository repository, String name, int color,
            int day, int startPeriod) {
        Course course = new Course();
        course.name = name;
        course.color = color;
        course.dayOfWeek = day;
        course.startPeriod = startPeriod;
        course.endPeriod = startPeriod + 1;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        repository.upsertCourse(course);
        return course;
    }

    private static int countNamed(CourseRepository repository, String name) {
        int count = 0;
        for (Course course : repository.getCourses()) {
            if (name.equals(course.name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Every field a sync must leave alone.
     *
     * <p>The colour is deliberately not in here: the point of the test is that the
     * rest of the record is byte for byte the same, so the one field that is meant
     * to move has to be compared on its own.</p>
     */
    private static String fieldsOf(Course course) {
        assertNotNull(course);
        return course.id + "|" + course.name + "|" + course.teacher + "|"
                + course.location + "|" + course.dayOfWeek + "|" + course.startPeriod
                + "|" + course.endPeriod + "|" + course.startWeek + "|" + course.endWeek
                + "|" + course.parity + "|" + course.scheduleName;
    }
}
