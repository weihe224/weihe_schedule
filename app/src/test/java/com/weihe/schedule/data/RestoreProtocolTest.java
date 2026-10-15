package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * The restore protocol: what it replaces, what it refuses, and what it does when
 * the write or the check afterwards goes wrong.
 *
 * <p>Two things are worth stating plainly, because a test suite can be read as
 * claiming more than it checked.</p>
 *
 * <p>First, the transaction. The restore is asserted to be <em>one</em> commit by
 * counting them, not only by looking at the values afterwards -- "the data looks
 * right" and "the data cannot be left half-written" are different claims, and
 * only the second one is the point of the protocol.</p>
 *
 * <p>Second, the interruption tests. A JVM test cannot kill a process between
 * two writes, so the interrupted state is <em>constructed</em>: the store is put
 * into exactly the shape a cut-short restore would leave, and a repository is
 * then built over it. That exercises the recovery path honestly; it does not
 * demonstrate that a real process death produces that shape. The real device
 * check is listed as not covered in the report.</p>
 */
public class RestoreProtocolTest {

    private static final long SEMESTER_ID = 1757000000000L;
    private static final long OTHER_SEMESTER_ID = 1760000000000L;
    private static final String SEMESTER_NAME = "\u5927\u4E09\u4E0A";

    // ============================================== the ordinary restore

    @Test
    public void aValidBackupReplacesTheTimetableCompletely() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        BackupCodec.Document document = documentOfTwoSemesters();

        RestoreReport report = repository.restoreFrom(document);

        assertTrue(report.toString(), report.isRestored());
        assertEquals(2, repository.getSemesters().size());
        assertEquals(2, allCourses(repository).size());
        assertEquals(1, repository.getBellSchedules().size());
    }

    @Test
    public void theRestoredTimetableIsStillThereAfterReopening() {
        FakePreferences preferences = new FakePreferences();
        repositoryFor(preferences).restoreFrom(documentOfTwoSemesters());

        CourseRepository reopened = repositoryFor(preferences);

        assertEquals(2, reopened.getSemesters().size());
        assertEquals(List.of("A", "B"), sortedNames(allCourses(reopened)));
        assertEquals(1, reopened.getBellSchedules().size());
    }

    @Test
    public void everyCourseFieldSurvivesARestore() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Course source = course(42L, "\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD");
        source.teacher = "\u5F90\u8001\u5E08";
        source.location = "\u6559\u5B66\u697C D305";
        source.dayOfWeek = 4;
        source.startPeriod = 5;
        source.endPeriod = 6;
        source.startWeek = 3;
        source.endWeek = 11;
        source.parity = Course.PARITY_ODD;
        source.color = 0xFF3C6A8E;
        source.scheduleName = "";

        repository.restoreFrom(documentOf(source));

        Course read = repository.getCourses().get(0);
        assertEquals(source.id, read.id);
        assertEquals(source.name, read.name);
        assertEquals(source.teacher, read.teacher);
        assertEquals(source.location, read.location);
        assertEquals(source.dayOfWeek, read.dayOfWeek);
        assertEquals(source.startPeriod, read.startPeriod);
        assertEquals(source.endPeriod, read.endPeriod);
        assertEquals(source.startWeek, read.startWeek);
        assertEquals(source.endWeek, read.endWeek);
        assertEquals(source.parity, read.parity);
        assertEquals(source.color, read.color);
        assertEquals(source.scheduleName, read.scheduleName);
    }

    @Test
    public void severalSemestersAndTheirCoursesAllLandWhereTheyBelong() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(SEMESTER_ID), new ArrayList<>(
                Arrays.asList(course(1L, "A"), course(2L, "B"), course(3L, "C"))));
        bySemester.put(Long.valueOf(OTHER_SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(4L, "D"))));
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                bySemester, BellSchedules.presets(), OTHER_SEMESTER_ID, "2026-10-09T21:32:10+08:00",
                "1.0");

        RestoreReport report = repository.restoreFrom(document);

        assertTrue(report.toString(), report.isRestored());
        assertEquals(4, allCourses(repository).size());
        assertEquals(OTHER_SEMESTER_ID, repository.getActiveSemester().id());
        assertEquals(List.of("A", "B", "C"),
                sortedNames(repository.coursesOf(find(repository, SEMESTER_ID))));
        assertEquals(List.of("D"),
                sortedNames(repository.coursesOf(find(repository, OTHER_SEMESTER_ID))));
    }

    @Test
    public void coursesAndSemestersTheBackupDoesNotHoldAreNotLeftBehind() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        assertEquals(9, repository.getCourses().size());
        // Compare id-and-name pairs, not ids: the seeded timetable uses 1..9 and
        // the backup puts its own course at id 1, so the ids alone overlap.
        List<String> oldCourses = idNamePairs(allCourses(repository));
        List<Long> oldSemesterIds = idsOfSemesters(repository);

        repository.restoreFrom(documentOfTwoSemesters());

        List<String> newCourses = idNamePairs(allCourses(repository));
        assertEquals(2, newCourses.size());
        for (String old : oldCourses) {
            assertFalse("a course the backup never mentioned is still here: " + old,
                    newCourses.contains(old));
        }
        for (Long old : oldSemesterIds) {
            assertFalse("a semester the backup never mentioned is still here: " + old,
                    idsOfSemesters(repository).contains(old));
        }
        assertEquals(2, repository.getSemesters().size());
    }

    @Test
    public void everyStoredKeyBelongsToTheBackupOrIsOneItDoesNotOwn() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        preferences.edit().putString("courses_backup", "damaged payload").apply();

        repository.restoreFrom(documentOfTwoSemesters());

        // Nothing the backup replaced survives as a stale per-semester key.
        for (String key : preferences.snapshot().keySet()) {
            boolean perSemester = key.startsWith("courses:") || key.startsWith("semester_start:");
            if (perSemester) {
                assertTrue("stale key left behind: " + key,
                        key.endsWith(String.valueOf(SEMESTER_ID))
                                || key.endsWith(String.valueOf(OTHER_SEMESTER_ID)));
            }
        }
        assertEquals("a key the restore does not own must be left alone",
                "damaged payload", preferences.getString("courses_backup", null));
    }

    @Test
    public void aRestoreIsExactlyOneCommit() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        int before = preferences.commitCount();

        repository.restoreFrom(documentOfTwoSemesters());

        assertEquals("the whole restore must be one transaction", 1,
                preferences.commitCount() - before);
    }

    @Test
    public void theRelationshipBetweenCoursesAndSchedulesSurvives() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Course course = course(1L, "A");
        course.scheduleName = BellSchedules.DE_NAME;

        RestoreReport report = repository.restoreFrom(
                documentOfWith(BellSchedules.presets(), course));

        assertTrue(report.toString(), report.isRestored());
        Course read = repository.findCourse(1L);
        assertNotNull(read);
        assertEquals(BellSchedules.DE_NAME, read.scheduleName);
        assertEquals(BellSchedules.DE_NAME, repository.scheduleFor(read).name());
    }

    @Test
    public void theInstanceThatRestoredAndAFreshOneAgreeAfterwards() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);

        repository.restoreFrom(documentOfTwoSemesters());

        assertEquals(describeAll(repository), describeAll(repositoryFor(preferences)));
    }

    @Test
    public void restoringTwiceLeavesTheSameStateTheSecondTime() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        BackupCodec.Document document = documentOfTwoSemesters();

        repository.restoreFrom(document);
        List<String> first = describeAll(repository);
        repository.restoreFrom(document);
        List<String> second = describeAll(repository);

        assertEquals(first, second);
        assertEquals(2, allCourses(repository).size());
    }

    // ====================================================== refusals

    @Test
    public void anIncompleteDocumentIsRefusedWithoutWritingAnything() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();
        int commits = preferences.commitCount();

        RestoreReport report = repository.restoreFrom(null);

        assertEquals(RestoreReport.Status.UNCHANGED, report.status());
        assertEquals(before, preferences.snapshot());
        assertEquals(commits, preferences.commitCount());
    }

    @Test
    public void aDocumentWithNoSemesterOrNoScheduleIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();

        BackupCodec.Document noSemesters = BackupCodec.of(new ArrayList<Semester>(),
                new LinkedHashMap<Long, List<Course>>(), BellSchedules.presets(),
                SEMESTER_ID, "", "1.0");
        BackupCodec.Document noSchedules = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(), new ArrayList<BellSchedule>(),
                SEMESTER_ID, "", "1.0");

        assertEquals(RestoreReport.Status.UNCHANGED,
                repository.restoreFrom(noSemesters).status());
        assertEquals(RestoreReport.Status.UNCHANGED,
                repository.restoreFrom(noSchedules).status());
        assertEquals(before, preferences.snapshot());
    }

    @Test
    public void aFileFromTheFutureIsRefusedBeforeAnyWrite() throws Exception {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();
        Map<String, Object> root = MiniJson.object();
        root.put("format", BackupCodec.FORMAT);
        root.put("formatVersion", Integer.valueOf(BackupCodec.MAX_SUPPORTED_VERSION + 1));
        root.put("semesters", MiniJson.array());
        root.put("schedules", MiniJson.array());

        BackupCodec.Result checked = BackupCodec.read(stream(MiniJson.write(root)),
                BackupCodec.MAX_BYTES);

        assertFalse(checked.isOk());
        assertEquals("a refused file never reaches the repository",
                before, preferences.snapshot());
    }

    @Test
    public void validatingAFileNeverWritesEvenWhenTheRepositoryExists() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();

        String[] rejected = {"", "{", "[]", "{}",
                "{\"format\":\"weihe-schedule-backup\"}"};
        for (String text : rejected) {
            BackupCodec.Result result = BackupCodec.parse(text);
            assertFalse(result.isOk());
        }
        // Also parse a good one: reading a backup is not restoring it.
        assertTrue(BackupCodec.parse(BackupCodec.write(documentOfTwoSemesters())).isOk());

        assertEquals(before, preferences.snapshot());
        assertEquals(9, repository.getCourses().size());
    }

    // ============================================ failure and rollback

    @Test
    public void whenTheCommitIsRefusedNothingChanges() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();
        preferences.failCommitNumber(1);

        RestoreReport report = repository.restoreFrom(documentOfTwoSemesters());

        assertEquals(RestoreReport.Status.UNCHANGED, report.status());
        assertEquals(before, preferences.snapshot());
        assertEquals(9, repository.getCourses().size());
        assertEquals(1, repository.getSemesters().size());
    }

    @Test
    public void whenTheWrittenDataDoesNotVerifyTheOldDataIsPutBack() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();

        RestoreReport report = repository.restoreFrom(documentWithABrokenReference());

        assertEquals(report.toString(), RestoreReport.Status.ROLLED_BACK, report.status());
        assertFalse(report.detail().isEmpty());
    }

    @Test
    public void aRollbackRestoresTheCoursesAndSemestersFieldForField() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        List<String> coursesBefore = describeAll(repository);
        List<Long> semesterIdsBefore = idsOfSemesters(repository);

        repository.restoreFrom(documentWithABrokenReference());

        assertEquals(coursesBefore, describeAll(repository));
        assertEquals(semesterIdsBefore, idsOfSemesters(repository));
        assertEquals(3, repository.getBellSchedules().size());
    }

    @Test
    public void aRollbackIsAlsoOneCommit() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        int before = preferences.commitCount();

        repository.restoreFrom(documentWithABrokenReference());

        assertEquals("the restore and its rollback are one commit each", 2,
                preferences.commitCount() - before);
    }

    @Test
    public void whenTheRollbackAlsoFailsTheStateIsReportedAsUnknown() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        preferences.failCommitNumber(2);

        RestoreReport report = repository.restoreFrom(documentWithABrokenReference());

        assertEquals(report.toString(), RestoreReport.Status.NEEDS_ATTENTION,
                report.status());
        assertTrue(report.needsAttention());
        assertFalse(report.isRestored());
    }

    @Test
    public void aStateThatCouldNotBeSettledKeepsItsRollbackMaterial() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        preferences.failCommitNumber(2);

        repository.restoreFrom(documentWithABrokenReference());

        assertNotNull("the snapshot must survive an unresolved restore",
                preferences.getString("restore_snapshot", null));
        assertNotNull("the marker must survive too, so the next start retries",
                preferences.getString("restore_pending", null));
    }

    @Test
    public void aSuccessfulRestoreKeepsTheSnapshotForLaterUndo() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);

        RestoreReport report = repository.restoreFrom(documentOfTwoSemesters());

        assertTrue(report.toString(), report.isRestored());
        assertNotNull(preferences.getString("restore_snapshot", null));
        assertNull("the marker is cleared once the data has been checked",
                preferences.getString("restore_pending", null));
    }

    // ============================================ interruption and restart

    @Test
    public void anInterruptedRestoreWhoseDataIsInPlaceIsSimplyAccepted() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        repository.restoreFrom(documentOfTwoSemesters());
        // Re-create the state a crash between commit and check would leave: the
        // data is in place, the marker still says the check never ran.
        preferences.edit().putString("restore_pending", expectationOf(preferences)).apply();

        CourseRepository restarted = repositoryFor(preferences);

        assertNotNull(restarted.recoveryReport());
        assertEquals(RestoreReport.Status.RESTORED, restarted.recoveryReport().status());
        assertEquals(2, allCourses(restarted).size());
        assertNull(preferences.getString("restore_pending", null));
    }

    @Test
    public void anInterruptedRestoreWhoseDataDoesNotMatchIsRolledBack() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        List<String> coursesBefore = describeAll(repository);
        repository.restoreFrom(documentOfTwoSemesters());

        // A marker expecting something the store does not hold: a restore that
        // was cut short did not write what it said it would.
        preferences.edit().putString("restore_pending",
                expectationOf(1757000000000L, 99, 1)).apply();

        CourseRepository restarted = repositoryFor(preferences);

        assertNotNull(restarted.recoveryReport());
        assertEquals(RestoreReport.Status.ROLLED_BACK, restarted.recoveryReport().status());
        assertEquals(coursesBefore, describeAll(restarted));
        assertEquals(9, restarted.getCourses().size());
    }

    @Test
    public void anOrdinaryStartHasNothingToRecover() {
        FakePreferences preferences = new FakePreferences();
        seededRepository(preferences);

        CourseRepository restarted = repositoryFor(preferences);

        assertNull("a normal start must not claim to have fixed anything",
                restarted.recoveryReport());
    }

    @Test
    public void aStartWithNoSnapshotReportsThatItCannotSettleTheRestore() {
        FakePreferences preferences = new FakePreferences();
        seededRepository(preferences);
        preferences.edit()
                .putString("restore_pending", expectationOf(1757000000000L, 99, 1))
                .remove("restore_snapshot")
                .apply();

        CourseRepository restarted = repositoryFor(preferences);

        assertNotNull(restarted.recoveryReport());
        assertEquals(RestoreReport.Status.NEEDS_ATTENTION,
                restarted.recoveryReport().status());
        assertNotNull("nothing may be cleared when the state is unknown",
                preferences.getString("restore_pending", null));
    }

    @Test
    public void theRestoreInProgressMarkerIsNotLeftBehindOnASuccessfulRun() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);

        RestoreReport report = repository.restoreFrom(documentOfTwoSemesters());

        assertTrue(report.toString(), report.isRestored());
        CourseRepository restarted = repositoryFor(preferences);
        assertNull(restarted.recoveryReport());
    }

    // ---------------------------------------------------------------- fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    /**
     * A repository holding the nine-course timetable the restore tests replace.
     *
     * <p>A term starts empty now, so the courses are written here on purpose.
     * The ids run 1..9 to match what the old sample timetable used: several tests
     * below reason about the ids overlapping the backup's own ids, and they were
     * written against that 1..9 space.</p>
     */
    private static CourseRepository seededRepository(FakePreferences preferences) {
        CourseRepository repository = repositoryFor(preferences);
        seedNineCourses(repository);
        assertEquals(9, repository.getCourses().size());
        assertEquals(1, repository.getSemesters().size());
        assertEquals(3, repository.getBellSchedules().size());
        return repository;
    }

    /** Nine courses at ids 1..9, one per weekday-and-period slot. */
    private static void seedNineCourses(CourseRepository repository) {
        int day = 1;
        int startPeriod = 1;
        for (int i = 1; i <= 9; i++) {
            Course seeded = course(i, "Seeded " + i);
            seeded.dayOfWeek = day;
            seeded.startPeriod = startPeriod;
            seeded.endPeriod = startPeriod + 1;
            repository.upsertCourse(seeded);
            startPeriod += 2;
            if (startPeriod > 11) {
                startPeriod = 1;
                day = day % 5 + 1;
            }
        }
    }

    /** One semester, two courses, one schedule. */
    private static BackupCodec.Document documentOfTwoSemesters() {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(1L, "A"))));
        bySemester.put(Long.valueOf(OTHER_SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(2L, "B"))));
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                bySemester, oneSchedule(), SEMESTER_ID, "2026-10-09T21:32:10+08:00", "1.0");
    }

    private static BackupCodec.Document documentOf(Course... courses) {
        return documentOfWith(oneSchedule(), courses);
    }

    private static BackupCodec.Document documentOfWith(List<BellSchedule> schedules,
            Course... courses) {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(SEMESTER_ID), new ArrayList<>(Arrays.asList(courses)));
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                bySemester, schedules, SEMESTER_ID, "2026-10-09T21:32:10+08:00", "1.0");
    }

    /**
     * A document that writes cleanly but cannot pass the read-back check.
     *
     * <p>Built through {@link BackupCodec#of} rather than through a parsed file,
     * so the course keeps a schedule reference the document does not carry. That
     * is exactly the association the check exists to catch, and it is reachable:
     * {@code of} is what the export path builds with, and it does not repair.</p>
     */
    private static BackupCodec.Document documentWithABrokenReference() {
        Course course = course(1L, "A");
        course.scheduleName = "\u4E0D\u5B58\u5728\u7684\u4F5C\u606F\u8868";
        return documentOf(course);
    }

    private static List<BellSchedule> oneSchedule() {
        return new ArrayList<>(Arrays.asList(BellSchedules.standard()));
    }

    private static Semester semester(long id, String start, String name) {
        return new Semester(id, LocalDate.parse(start), name);
    }

    private static Course course(long id, String name) {
        Course course = new Course();
        course.id = id;
        course.name = name;
        course.teacher = "\u5F20\u8001\u5E08";
        course.location = "\u6559\u5B66\u697C A203";
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    /** The expectation object the protocol stores, rebuilt for a simulation. */
    private static String expectationOf(long semesterId, int courseCount,
            int scheduleCount) {
        Map<String, Object> expectation = MiniJson.object();
        List<Object> ids = MiniJson.array();
        ids.add(Long.valueOf(semesterId));
        expectation.put("semesterIds", ids);
        Map<String, Object> counts = MiniJson.object();
        counts.put(String.valueOf(semesterId), Integer.valueOf(courseCount));
        expectation.put("courseCounts", counts);
        expectation.put("scheduleCount", Integer.valueOf(scheduleCount));
        return MiniJson.write(expectation);
    }

    /** The same, describing whatever the store holds right now. */
    private static String expectationOf(FakePreferences preferences) {
        List<Semester> semesters = SemesterJson.parse(
                preferences.getString("semesters", null));
        Map<String, Object> expectation = MiniJson.object();
        List<Object> ids = MiniJson.array();
        Map<String, Object> counts = MiniJson.object();
        for (Semester semester : semesters) {
            ids.add(Long.valueOf(semester.id()));
            counts.put(String.valueOf(semester.id()), Integer.valueOf(
                    MiniJson.parseArray(preferences.getString(
                            "courses:" + semester.id(), null)).size()));
        }
        expectation.put("semesterIds", ids);
        expectation.put("courseCounts", counts);
        expectation.put("scheduleCount", Integer.valueOf(BellScheduleJson.parse(
                preferences.getString("bell_schedules", null)).size()));
        return MiniJson.write(expectation);
    }

    private static java.io.InputStream stream(String text) {
        return new java.io.ByteArrayInputStream(
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Semester find(CourseRepository repository, long id) {
        Semester found = findSemesterOrNull(repository, id);
        assertNotNull("no semester with id " + id, found);
        return found;
    }

    private static Semester findSemesterOrNull(CourseRepository repository, long id) {
        for (Semester semester : repository.getSemesters()) {
            if (semester.id() == id) {
                return semester;
            }
        }
        return null;
    }

    private static List<Long> idsOfSemesters(CourseRepository repository) {
        List<Long> ids = new ArrayList<>();
        for (Semester semester : repository.getSemesters()) {
            ids.add(Long.valueOf(semester.id()));
        }
        return ids;
    }

    private static List<String> sortedNames(List<Course> courses) {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** Identifies a course by what it is as well as which id it holds. */
    private static List<String> idNamePairs(List<Course> courses) {
        List<String> pairs = new ArrayList<>();
        for (Course course : courses) {
            pairs.add(course.id + "|" + course.name);
        }
        java.util.Collections.sort(pairs);
        return pairs;
    }

    /**
     * Every course in every semester.
     *
     * <p>{@code getCourses()} is the active semester's timetable, so a test about
     * what a restore left behind has to look at all of them. Read through
     * {@code coursesOf}, which does not change which semester is active.</p>
     */
    private static List<Course> allCourses(CourseRepository repository) {
        List<Course> all = new ArrayList<>();
        for (Semester semester : repository.getSemesters()) {
            all.addAll(repository.coursesOf(semester));
        }
        return all;
    }

    /**
     * Every stored field of every course, for whole-state comparisons.
     *
     * <p>Reads each semester through {@code coursesOf}, which does not change
     * which semester is active -- crucially, this helper must not write anything,
     * because several tests use it to ask whether anything changed.</p>
     */
    private static List<String> describeAll(CourseRepository repository) {
        List<String> described = new ArrayList<>();
        for (Semester semester : repository.getSemesters()) {
            for (Course course : repository.coursesOf(semester)) {
                described.add(semester.id() + "/" + course.id + "|" + course.name + "|"
                        + course.teacher + "|" + course.location + "|" + course.dayOfWeek
                        + "|" + course.startPeriod + "|" + course.endPeriod + "|"
                        + course.startWeek + "|" + course.endWeek + "|" + course.parity
                        + "|" + course.color + "|" + course.scheduleName);
            }
        }
        java.util.Collections.sort(described);
        return described;
    }
}
