package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import com.weihe.schedule.importer.TimetableGrid;
import com.weihe.schedule.importer.TimetableImportParser;
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
 * deletion, damaged payloads, the one-off placeholder migration, the import undo
 * snapshot and its lifecycle, and whether a caller can reach into internal state
 * through a returned collection. Keeping them apart keeps both files readable, and
 * it means none of the 34 existing tests had to be touched.</p>
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

    // ------------------------------------------------------ 导入撤销快照

    /**
     * An import replaces the whole term, so the snapshot is the only way back.
     *
     * <p>The undo state lives in {@code courses_previous:<semesterId>} and is
     * written in the same commit as the import, which is why these tests check the
     * store's keys directly as well as the repository's own answers.</p>
     */

    @Test
    public void importingReplacesTheTimetableAndUndoBringsItBack() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<String> before = descriptionsOf(repository.getCourses());
        assertEquals("the sample timetable is the starting point", 9, before.size());

        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertEquals(1, repository.getCourses().size());
        assertTrue(repository.hasPreviousCourses());

        assertTrue(repository.restorePreviousCourses());

        // Compared as an unordered bag, the same way the deletion tests compare:
        // the seeded timetable is handed back in creation order while a stored
        // payload is sorted by start period, and the screens sort for themselves,
        // so list order is not part of what "restored" means here.
        assertEquals("every course must come back field for field",
                sorted(before), sorted(descriptionsOf(repository.getCourses())));
        assertFalse("the import has been undone, so it is no longer offered",
                repository.hasPreviousCourses());
    }

    @Test
    public void undoingRestoresTheExactPayloadThatWasStored() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        // A payload the reader repairs rather than one this code wrote: putting it
        // back through the ordinary writer would produce a different string, so
        // this is what proves the snapshot holds the raw text.
        String stored = "[{\"id\":7,\"name\":\"A\",\"startPeriod\":3},{\"id\":8,\"name\":\"B\"}]";
        storeCourses(preferences, stored);
        repository.reload();
        assertNotNull(repository.findCourse(7L));

        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertTrue(repository.restorePreviousCourses());

        assertEquals("undoing must put the store back byte for byte, not rebuild it",
                stored, preferences.getString(coursesKeyOf(active), null));
    }

    @Test
    public void onlyTheMostRecentImportCanBeUndone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();

        assertTrue(repository.replaceCourses(List.of(course("first import"))));
        String afterFirst = preferences.getString(coursesKeyOf(active), null);
        assertTrue(repository.replaceCourses(List.of(course("second import"))));

        assertTrue(repository.restorePreviousCourses());

        assertEquals("undo must land on the state the second import replaced",
                List.of("first import"), namesOfCourses(repository.getCourses()));
        assertEquals(afterFirst, preferences.getString(coursesKeyOf(active), null));
        assertFalse("there is only ever one step back", repository.hasPreviousCourses());
    }

    @Test
    public void aSnapshotBelongsToTheSemesterThatTookTheImport() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        assertTrue(repository.replaceCourses(List.of(course("first term import"))));

        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        repository.upsertCourse(course("typed by hand"));

        assertFalse("another term's import is not this term's to undo",
                repository.hasPreviousCourses());
        List<String> before = descriptionsOf(repository.getCourses());
        assertFalse("refusing must not touch the data",
                repository.restorePreviousCourses());
        assertEquals(before, descriptionsOf(repository.getCourses()));

        repository.setActiveSemester(first);
        assertTrue("the term that imported keeps its own undo",
                repository.hasPreviousCourses());
    }

    @Test
    public void aSemesterThatHadNoPayloadGetsItsKeyBackAbsent() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        // A second term has no payload of its own: not an empty timetable, but
        // nothing at all, and undo has to put that state back.
        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        assertNull(preferences.getString(coursesKeyOf(second), null));

        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertTrue(repository.hasPreviousCourses());
        assertTrue(repository.restorePreviousCourses());

        assertNull("an absent key must come back absent, not as an empty array",
                preferences.getString(coursesKeyOf(second), null));
        assertTrue(repository.getCourses().isEmpty());
        assertTrue("and a reopen must not seed samples into a term that never had any",
                repositoryFor(preferences).getCourses().isEmpty());
    }

    @Test
    public void undoingWithNoSnapshotChangesNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<String> before = descriptionsOf(repository.getCourses());
        assertFalse(repository.hasPreviousCourses());

        assertFalse("there is nothing to undo, and that is not a success",
                repository.restorePreviousCourses());

        assertEquals(sorted(before), sorted(descriptionsOf(repository.getCourses())));
        assertEquals(sorted(before),
                sorted(descriptionsOf(repositoryFor(preferences).getCourses())));
    }

    @Test
    public void aRefusedImportWriteIsNotReportedAsSuccess() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        int before = repository.getCourses().size();
        preferences.failCommitNumber(1);

        assertFalse("a refused commit must not be reported as a completed import",
                repository.replaceCourses(List.of(course("imported"))));

        assertFalse("and it must not leave an undo entry claiming one happened",
                repository.hasPreviousCourses());
        assertEquals(before, repository.getCourses().size());
    }

    @Test
    public void aRefusedUndoWriteIsNotReportedAsSuccess() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        preferences.failCommitNumber(2);

        assertFalse("a refused undo must not be reported as done",
                repository.restorePreviousCourses());

        assertEquals("the imported timetable is still the stored one",
                1, repository.getCourses().size());
        assertTrue("and the import is still offered, because it was never undone",
                repository.hasPreviousCourses());
    }

    @Test
    public void deletingASemesterForgetsItsSnapshot() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertNotNull(preferences.getString(previousKeyOf(second), null));

        assertTrue(repository.deleteSemester(second));

        assertNull("a deleted term must not leave a snapshot pointing at it",
                preferences.getString(previousKeyOf(second), null));
        assertFalse(repository.hasPreviousCourses());
    }

    @Test
    public void copyingASemesterDoesNotCarryTheSourcesSnapshot() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        assertTrue(repository.replaceCourses(List.of(course("first term import"))));

        repository.copyCourses(first, second);

        assertNull("the source's undo state must not travel with the copy",
                preferences.getString(previousKeyOf(second), null));
        assertNotNull("the source keeps its own",
                preferences.getString(previousKeyOf(first), null));
    }

    @Test
    public void copyingOverASemesterForgetsTheImportItCouldHaveUndone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        Semester second = repository.addSemester(first.start().plusWeeks(20), "next");
        repository.setActiveSemester(second);
        assertTrue(repository.replaceCourses(List.of(course("second term import"))));
        assertTrue(repository.hasPreviousCourses());

        repository.copyCourses(first, second);

        assertFalse("the copy replaced the timetable the snapshot described",
                repository.hasPreviousCourses());
        assertNull(preferences.getString(previousKeyOf(second), null));
    }

    @Test
    public void aBackupRestoreLeavesNoStaleUndoEntry() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertTrue("the import must be undoable first", repository.hasPreviousCourses());

        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        coursesBySemester.put(Long.valueOf(active.id()),
                List.of(course("from the backup")));
        BackupCodec.Document document = BackupCodec.of(List.of(active), coursesBySemester,
                BellSchedules.presets(), active.id(), "2026-10-11T12:00:00+08:00", "1.0");

        RestoreReport report = repository.restoreFrom(document);

        assertTrue(report.toString(), report.isRestored());
        assertFalse("a restore replaces the term, so the old undo entry is a lie",
                repository.hasPreviousCourses());
        assertNull(preferences.getString(previousKeyOf(active), null));
        assertEquals(List.of("from the backup"), namesOfCourses(repository.getCourses()));
    }

    @Test
    public void undoingTwiceRestoresOnceAndThenRefuses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        String before = preferences.getString(coursesKeyOf(active), null);
        assertTrue(repository.replaceCourses(List.of(course("imported"))));

        assertTrue(repository.restorePreviousCourses());
        assertFalse("the same snapshot must not be applied a second time",
                repository.restorePreviousCourses());

        assertEquals(before, preferences.getString(coursesKeyOf(active), null));
        assertEquals(9, repository.getCourses().size());
    }

    // -------------------------------------------- 导入流程的数据安全保证

    /**
     * Reading and parsing a file must leave the store completely alone.
     *
     * <p>This is what makes cancelling the confirmation safe: up to the moment the
     * user presses "覆盖导入", the import path has only read. The store is compared
     * as a whole rather than field by field, so a stray write anywhere -- including
     * to the undo snapshot an earlier import left behind -- shows up as a failure.</p>
     */
    @Test
    public void parsingAFileWritesNothingAndLeavesTheUndoEntryAlone() throws Exception {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        assertTrue(repository.replaceCourses(List.of(course("imported"))));
        assertTrue(repository.hasPreviousCourses());
        Map<String, Object> before = preferences.snapshot();

        TimetableGrid grid = new TimetableGrid(4, 2);
        grid.put(0, 0, "\u8282\u6B21");
        grid.put(0, 1, "\u661F\u671F\u4E00");
        grid.put(1, 0, "1");
        grid.put(2, 0, "2");
        grid.put(1, 1, "X02018\u3010\u65B0\u8BFE\u3011\n2-5\u5468  \u5E08  \u5730");
        TimetableImportParser.Report report = TimetableImportParser.parseWithReport(grid);
        assertTrue("the file itself must be readable", report.isUsable());

        assertEquals("parsing must not touch the store at all",
                before, preferences.snapshot());
        assertEquals("and the timetable is still the imported one",
                1, repository.getCourses().size());
        assertTrue("cancelling must not consume the snapshot",
                repository.hasPreviousCourses());
        assertTrue("nor damage it", repository.restorePreviousCourses());
        assertEquals(9, repository.getCourses().size());
    }

    // ------------------------------------------------ 损坏数据副本的读取

    /**
     * The copy exists so it can leave the device; these tests are about reaching it
     * without disturbing anything.
     *
     * <p>The store is compared as a whole wherever the claim is "nothing changed",
     * so a stray write to any key -- the copy itself, the courses, the undo
     * snapshot -- fails the test rather than passing unnoticed.</p>
     */

    @Test
    public void aStoreWithNoDamagedCopyReportsNone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.getCourses();

        DamagedCourseCopy copy = repository.damagedCopy();

        assertEquals(DamagedCourseCopy.State.ABSENT, copy.state());
        assertFalse(copy.exists());
        assertFalse(copy.isExportable());
        assertNull(copy.raw());
    }

    @Test
    public void aStoredPayloadIsFoundAndReadBackWhole() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[1,2,3]";
        storeDamagedCopy(preferences, payload);

        DamagedCourseCopy copy = repositoryFor(preferences).damagedCopy();

        assertEquals(DamagedCourseCopy.State.PRESENT, copy.state());
        assertTrue(copy.isExportable());
        assertEquals(payload, copy.raw());
        assertEquals(payload.length(), copy.length());
    }

    @Test
    public void aPayloadIsFoundWithoutBeingParsed() {
        FakePreferences preferences = new FakePreferences();
        // Valid JSON, but not a course list: a reader would reject it. Whether it
        // can be understood must not decide whether it is there.
        String payload = "{\"not\":\"an array\"}";
        storeDamagedCopy(preferences, payload);

        DamagedCourseCopy copy = repositoryFor(preferences).damagedCopy();

        assertEquals(DamagedCourseCopy.State.PRESENT, copy.state());
        assertEquals(payload, copy.raw());
    }

    @Test
    public void anEmptyStoredCopyIsReportedAsEmptyRatherThanAbsent() {
        FakePreferences preferences = new FakePreferences();
        preferences.edit().putString(KEY_COURSES_BACKUP, "").apply();

        DamagedCourseCopy copy = repositoryFor(preferences).damagedCopy();

        assertEquals(DamagedCourseCopy.State.EMPTY, copy.state());
        assertTrue(copy.exists());
        assertFalse(copy.isExportable());
    }

    @Test
    public void aSlotHoldingSomethingOtherThanTextIsReportedAsUnreadable() {
        FakePreferences preferences = new FakePreferences();
        // Only reachable by hand-editing the store, but "there is none" would then
        // be a wrong answer rather than a harmless one.
        preferences.edit().putInt(KEY_COURSES_BACKUP, 3).apply();

        DamagedCourseCopy copy = repositoryFor(preferences).damagedCopy();

        assertEquals(DamagedCourseCopy.State.UNREADABLE, copy.state());
        assertTrue(copy.exists());
        assertFalse(copy.isExportable());
        assertNull(copy.raw());
    }

    @Test
    public void readingTheCopyChangesNothingInTheStore() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},42,{\"id\":2,\"name\":\"B\"}]";
        storeCourses(preferences, payload);
        CourseRepository repository = repositoryFor(preferences);
        List<Course> readable = repository.getCourses();
        assertEquals("the readable entries still load", 2, readable.size());
        Map<String, Object> before = preferences.snapshot();

        DamagedCourseCopy copy = repository.damagedCopy();
        copy.state();
        copy.raw();
        copy.length();
        copy.isExportable();

        assertEquals("reading the copy must not touch the store",
                before, preferences.snapshot());
        assertEquals("and the copy itself is untouched",
                payload, preferences.getString(KEY_COURSES_BACKUP, null));
    }

    @Test
    public void thePayloadTheReaderCouldNotUseIsTheOneTheCopyHolds() {
        FakePreferences preferences = new FakePreferences();
        String payload = "[{\"id\":1,\"name\":\"A\"},null,{\"id\":2,\"name\":\"B\"}]";
        storeCourses(preferences, payload);

        // Reading the damaged store is what produces the copy in the first place.
        assertEquals(2, repositoryFor(preferences).getCourses().size());

        DamagedCourseCopy copy = repositoryFor(preferences).damagedCopy();
        assertEquals("the copy is the payload itself, not a summary of it",
                payload, copy.raw());
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
                .putString(coursesKeyOf(semester), payload)
                .apply();
    }

    /**
     * Files a payload straight into the damaged-copy slot.
     *
     * <p>Used where the subject is reading the slot rather than how it gets filled;
     * the end-to-end path is covered by
     * {@link #thePayloadTheReaderCouldNotUseIsTheOneTheCopyHolds}.</p>
     */
    private static void storeDamagedCopy(FakePreferences preferences, String payload) {
        preferences.edit().putString(KEY_COURSES_BACKUP, payload).apply();
    }

    private static String coursesKeyOf(Semester semester) {
        return "courses:" + semester.id();
    }

    /** The undo snapshot key of one semester. */
    private static String previousKeyOf(Semester semester) {
        return "courses_previous:" + semester.id();
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
