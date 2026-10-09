package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * Storage integrity: what actually reaches the store, what survives damage, and
 * what the read methods hand back.
 *
 * <p>Split from {@link CourseRepositoryTest} on purpose. That class covers what
 * the repository <em>decides</em> -- which schedule a course rings to, how
 * semesters are created and switched. This one covers what it <em>persists</em>:
 * deletion, damaged payloads, the one-off placeholder migration, and whether a
 * caller can reach into internal state through a returned collection. Keeping
 * them apart keeps both files readable, and it means none of the 34 existing
 * tests had to be touched.</p>
 *
 * <p>Every test builds its own {@link FakePreferences}, so nothing depends on
 * execution order. The store is the real {@link CourseRepository} running its
 * real constructor, including the storage migration, through the same
 * package-private {@code useStoreForNextInstance} seam the existing tests use.</p>
 */
public class CourseRepositoryStorageTest {

    private static final String KEY_COURSES_BACKUP = "courses_backup";
    private static final String KEY_BELL_SCHEDULES = "bell_schedules";

    /** A name an earlier build shipped, which the store still recognises. */
    private static final String RETIRED_NAME = "\u5B9E\u9A8C\u697C\u4F5C\u606F";

    // ----------------------------------------------------- 课程删除与持久化

    @Test
    public void deletingACourseRemovesItFromEveryRead() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long victim = repository.getCourses().get(0).id;

        repository.deleteCourse(victim);

        assertNull("the deleted course must not be findable",
                repository.findCourse(victim));
        List<Long> remaining = idsOf(repository.getCourses());
        assertEquals(8, remaining.size());
        assertFalse("the id must be gone from the list too", remaining.contains(victim));
    }

    @Test
    public void deletingOneCourseLeavesEveryOtherCourseExactlyAsItWas() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<Course> before = repository.getCourses();
        long victim = before.get(2).id;
        List<String> expected = new ArrayList<>();
        for (Course course : before) {
            if (course.id != victim) {
                expected.add(describe(course));
            }
        }

        repository.deleteCourse(victim);

        List<String> actual = new ArrayList<>();
        for (Course course : repository.getCourses()) {
            actual.add(describe(course));
        }
        // Compared as an unordered bag: the repository sorts by start period, so
        // the list order is not part of what "unchanged" means here.
        assertEquals("every surviving course must be field-for-field unchanged",
                sorted(expected), sorted(actual));
    }

    @Test
    public void aDeleteSurvivesAReopen() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long victim = repository.getCourses().get(4).id;

        repository.deleteCourse(victim);

        CourseRepository reopened = repositoryFor(preferences);
        assertNull(reopened.findCourse(victim));
        assertEquals(8, reopened.getCourses().size());
    }

    @Test
    public void deletingACourseThatIsNotThereChangesNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<Long> before = idsOf(repository.getCourses());

        repository.deleteCourse(999999L);

        assertEquals(sortedIds(before), sortedIds(idsOf(repository.getCourses())));
        assertEquals(sortedIds(before),
                sortedIds(idsOf(repositoryFor(preferences).getCourses())));
    }

    @Test
    public void deletingEveryCourseLeavesATimetableThatStaysEmptyOnReopen() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        for (Long id : idsOf(repository.getCourses())) {
            repository.deleteCourse(id);
        }

        assertTrue(repository.getCourses().isEmpty());
        // This semester was born holding the sample timetable. Reopening must not
        // read "no payload" and seed the nine examples all over again.
        assertTrue("an emptied semester must stay empty",
                repositoryFor(preferences).getCourses().isEmpty());
    }

    @Test
    public void deletingInOneSemesterDoesNotTouchAnother() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        List<Long> firstIds = idsOf(repository.getCourses());

        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        repository.upsertCourse(course("kept in the second semester"));
        long secondVictim = idsOf(repository.getCourses()).get(0);

        repository.deleteCourse(secondVictim);

        repository.setActiveSemester(first);
        assertEquals(sortedIds(firstIds), sortedIds(idsOf(repository.getCourses())));
        repository.setActiveSemester(second);
        assertFalse(idsOf(repository.getCourses()).contains(secondVictim));
    }

    // -------------------------------------------------------- 数据损坏与备份

    @Test
    public void anEntryThatIsNotAnObjectIsSkippedAndTheRestSurvive() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},42,{\"id\":2,\"name\":\"B\"}]";
        storeCourses(preferences, payload);

        List<Course> read = repositoryFor(preferences).getCourses();

        assertEquals(2, read.size());
        assertEquals(List.of("A", "B"), namesOfCourses(read));
        assertEquals("the original payload must be kept aside",
                payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void aPayloadThatIsNotAnArrayAtAllReadsAsEmptyAndIsKeptAside() {
        FakePreferences preferences = new FakePreferences();
        String payload = "<html>this is not a timetable</html>";
        storeCourses(preferences, payload);

        assertTrue(repositoryFor(preferences).getCourses().isEmpty());
        assertEquals(payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void aValidEmptyArrayIsNotMistakenForDamage() {
        FakePreferences preferences = new FakePreferences();
        storeCourses(preferences, "[]");

        assertTrue(repositoryFor(preferences).getCourses().isEmpty());
        assertNull("an empty timetable is a state, not damage",
                preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void aNullEntryBesideAValidOneIsSkipped() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},null]";
        storeCourses(preferences, payload);

        List<Course> read = repositoryFor(preferences).getCourses();

        assertEquals(1, read.size());
        assertEquals(List.of("A"), namesOfCourses(read));
        assertEquals(payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void aPayloadWhereEveryEntryIsUnreadableKeepsTheWholeOriginal() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[1,2,3]";
        storeCourses(preferences, payload);

        assertTrue(repositoryFor(preferences).getCourses().isEmpty());
        assertEquals(payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void readingDamageRepeatedlyNeitherDuplicatesNorLosesCourses() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},42,{\"id\":2,\"name\":\"B\"}]";
        storeCourses(preferences, payload);
        CourseRepository repository = repositoryFor(preferences);

        List<Long> first = idsOf(repository.getCourses());
        repository.reload();
        List<Long> second = idsOf(repository.getCourses());
        repository.reload();
        List<Long> third = idsOf(repository.getCourses());

        assertEquals(2, first.size());
        assertEquals(first, second);
        assertEquals(first, third);
        // Re-reading a damaged store must not consume or overwrite the copy.
        assertEquals(payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void theDamagedOriginalSurvivesTheNextWrite() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},42,{\"id\":2,\"name\":\"B\"}]";
        storeCourses(preferences, payload);
        CourseRepository repository = repositoryFor(preferences);
        Course extra = course("added after the damage");

        repository.upsertCourse(extra);

        assertEquals("the skipped entry must not take the readable ones with it",
                payload, preferences.getString(KEY_COURSES_BACKUP, null));
        List<Course> reopened = repositoryFor(preferences).getCourses();
        assertEquals(3, reopened.size());
        assertNotNull(repositoryFor(preferences).findCourse(extra.id));
    }

    @Test
    public void damageInOneSemesterDoesNotAffectAnother() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        repository.upsertCourse(course("safe"));
        List<Long> secondIds = idsOf(repository.getCourses());

        // One readable entry beside one unreadable one, in the first semester.
        storeCourses(preferences, "[{\"id\":1,\"name\":\"A\"},42]", first);

        repository.setActiveSemester(first);
        assertEquals("the readable entry in the damaged semester still loads",
                List.of("A"), namesOfCourses(repository.getCourses()));
        repository.setActiveSemester(second);
        assertEquals(sortedIds(secondIds), sortedIds(idsOf(repository.getCourses())));
    }

    // ---------------------------------------------------- 作息表占位数据替换

    @Test
    public void aScheduleTheUserMadeIsLeftExactlyAsStored() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> mine = new ArrayList<>();
        mine.add(BellSchedules.standard().renamed("\u6211\u7684\u4F5C\u606F\u8868"));
        repository.saveBellSchedules(mine);
        String stored = preferences.getString(KEY_BELL_SCHEDULES, null);

        List<BellSchedule> read = repositoryFor(preferences).getBellSchedules();

        assertEquals(1, read.size());
        assertEquals("\u6211\u7684\u4F5C\u606F\u8868", read.get(0).name());
        assertEquals("nothing matched a retired template, so nothing may be rewritten",
                stored, preferences.getString(KEY_BELL_SCHEDULES, null));
    }

    @Test
    public void anUntouchedScheduleFromARetiredBuildIsReplacedByTheRealOne() {
        FakePreferences preferences = new FakePreferences();
        storeOnlySchedule(preferences, BellSchedules.standard().renamed(RETIRED_NAME));

        List<BellSchedule> read = repositoryFor(preferences).getBellSchedules();

        assertEquals(namesOfSchedules(BellSchedules.presets()), namesOfSchedules(read));
    }

    @Test
    public void theReplacementIsPersistedSoTheNextReadSeesItToo() {
        FakePreferences preferences = new FakePreferences();
        storeOnlySchedule(preferences, BellSchedules.standard().renamed(RETIRED_NAME));

        repositoryFor(preferences).getBellSchedules();

        assertEquals(namesOfSchedules(BellSchedules.presets()), namesOfSchedules(
                BellScheduleJson.parse(preferences.getString(KEY_BELL_SCHEDULES, null))));
    }

    @Test
    public void replacingRepeatedlyDoesNotDuplicateOrGrowTheList() {
        FakePreferences preferences = new FakePreferences();
        storeOnlySchedule(preferences, BellSchedules.standard().renamed(RETIRED_NAME));
        int expected = BellSchedules.presets().size();

        int first = repositoryFor(preferences).getBellSchedules().size();
        int second = repositoryFor(preferences).getBellSchedules().size();
        int third = repositoryFor(preferences).getBellSchedules().size();

        assertEquals(expected, first);
        assertEquals(expected, second);
        assertEquals(expected, third);
        assertEquals(expected, BellScheduleJson.parse(
                preferences.getString(KEY_BELL_SCHEDULES, null)).size());
    }

    @Test
    public void aStoredListTheUserEditedIsNeverRewrittenOnRead() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> edited = new ArrayList<>();
        edited.add(BellSchedules.standard()
                .withLocationKeywords(java.util.Arrays.asList("\u6559\u5B66\u697C")));
        edited.add(BellSchedules.deBuilding());
        repository.saveBellSchedules(edited);
        String stored = preferences.getString(KEY_BELL_SCHEDULES, null);

        List<BellSchedule> read = repositoryFor(preferences).getBellSchedules();

        assertEquals(2, read.size());
        assertEquals(stored, preferences.getString(KEY_BELL_SCHEDULES, null));
    }

    @Test
    public void aStoreThatNeverHeldSchedulesIsSeededOnceWithThePresets() {
        FakePreferences preferences = new FakePreferences();
        assertNull(preferences.getString(KEY_BELL_SCHEDULES, null));

        List<BellSchedule> read = repositoryFor(preferences).getBellSchedules();

        assertEquals(namesOfSchedules(BellSchedules.presets()), namesOfSchedules(read));
        assertEquals(namesOfSchedules(BellSchedules.presets()), namesOfSchedules(
                BellScheduleJson.parse(preferences.getString(KEY_BELL_SCHEDULES, null))));
        // And a second read must not seed a second copy.
        assertEquals(read.size(), repositoryFor(preferences).getBellSchedules().size());
    }

    // ----------------------------------------------- 读写一致性与可变对象

    @Test
    public void editingACourseHandedOutByGetCoursesDoesNotReachTheRepository() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course handedOut = repository.getCourses().get(0);
        String originalName = handedOut.name;

        handedOut.name = "MUTATED";
        handedOut.startPeriod = 14;

        Course reread = repository.getCourses().get(0);
        assertEquals(originalName, reread.name);
        assertNotEquals(14, reread.startPeriod);
        assertEquals(originalName, repositoryFor(preferences).getCourses().get(0).name);
    }

    @Test
    public void editingTheSemesterListHandedOutByGetSemestersDoesNotReachTheRepository() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<Semester> handedOut = repository.getSemesters();
        int size = handedOut.size();

        handedOut.clear();

        assertEquals(size, repository.getSemesters().size());
        assertEquals(size, repositoryFor(preferences).getSemesters().size());
    }

    @Test
    public void savingAScheduleListKeepsItsOwnCopyOfWhatTheCallerPassed() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> mine = new ArrayList<>(BellSchedules.presets());
        int saved = mine.size();

        repository.saveBellSchedules(mine);
        mine.clear();

        assertEquals("the save must not alias the caller's list",
                saved, repository.getBellSchedules().size());
        assertEquals(saved, repositoryFor(preferences).getBellSchedules().size());
    }

    @Test
    public void anEditIsVisibleToAReopenWithTheSameValues() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = course("persisted edit");
        course.teacher = "\u5F20\u8001\u5E08";
        course.location = "\u6559\u5B66\u697C D305";
        course.scheduleName = BellSchedules.DE_NAME;
        course.startPeriod = 5;
        course.endPeriod = 6;
        course.startWeek = 3;
        course.endWeek = 11;
        course.parity = Course.PARITY_ODD;

        repository.upsertCourse(course);

        Course reloaded = repositoryFor(preferences).findCourse(course.id);
        assertNotNull(reloaded);
        assertEquals(describe(course), describe(reloaded));
    }

    @Test
    public void aDeleteIsVisibleToAReopenWithTheSameValues() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long victim = repository.getCourses().get(1).id;

        repository.deleteCourse(victim);

        assertEquals(descriptionsOf(repository.getCourses()),
                descriptionsOf(repositoryFor(preferences).getCourses()));
    }

    // ------------------------------------------------------------------ 夹具

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    /** Puts a single bell schedule into the store, replacing whatever is there. */
    private static void storeOnlySchedule(FakePreferences preferences,
            BellSchedule schedule) {
        FakePreferences.Editor editor = preferences.edit();
        editor.putString(KEY_BELL_SCHEDULES,
                BellScheduleJson.write(java.util.Collections.singletonList(schedule)));
        editor.apply();
    }

    /**
     * Replaces the active semester's stored course payload with {@code payload}.
     *
     * <p>Goes through a real repository first so the semester exists and the id
     * used here is the one the code under test will read.</p>
     */
    private static void storeCourses(FakePreferences preferences, String payload) {
        storeCourses(preferences, payload, repositoryFor(preferences).getActiveSemester());
    }

    private static void storeCourses(FakePreferences preferences, String payload,
            Semester semester) {
        preferences.edit()
                .putString("courses:" + semester.id(), payload)
                .apply();
    }

    private static Course course(String name) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    private static List<Long> idsOf(List<Course> courses) {
        List<Long> ids = new ArrayList<>();
        for (Course course : courses) {
            ids.add(course.id);
        }
        return ids;
    }

    private static List<Long> sortedIds(List<Long> ids) {
        List<Long> copy = new ArrayList<>(ids);
        java.util.Collections.sort(copy);
        return copy;
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        java.util.Collections.sort(copy);
        return copy;
    }

    private static List<String> namesOfCourses(List<Course> courses) {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        return names;
    }

    private static List<String> namesOfSchedules(List<BellSchedule> schedules) {
        List<String> names = new ArrayList<>();
        for (BellSchedule schedule : schedules) {
            names.add(schedule.name());
        }
        return names;
    }

    private static List<String> descriptionsOf(List<Course> courses) {
        List<String> descriptions = new ArrayList<>();
        for (Course course : courses) {
            descriptions.add(describe(course));
        }
        return descriptions;
    }

    /** Every persisted field of a course, so "unchanged" means all of it. */
    private static String describe(Course course) {
        return course.id + "|" + course.name + "|" + course.teacher + "|"
                + course.location + "|" + course.dayOfWeek + "|" + course.startPeriod
                + "|" + course.endPeriod + "|" + course.startWeek + "|" + course.endWeek
                + "|" + course.parity + "|" + course.color + "|" + course.scheduleName;
    }
}
