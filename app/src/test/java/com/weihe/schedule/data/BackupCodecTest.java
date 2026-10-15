package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * The backup file format: what it carries out, what it accepts back, and what it
 * refuses.
 *
 * <p>{@link BackupCodec} has no Android dependencies at all, so everything here
 * runs on a plain JVM. That is the point of keeping the format in its own class:
 * the export path is otherwise only reachable through a file picker and a real
 * device.</p>
 *
 * <p>The two halves are deliberately independent. The "accepts" tests build their
 * documents through {@link BackupCodec#of} and {@link BackupCodec#write}; the
 * "refuses" tests build their JSON by hand out of {@link MiniJson}, so a bug in
 * the writer cannot hide a bug in the reader.</p>
 */
public class BackupCodecTest {

    private static final long SEMESTER_ID = 1757000000000L;
    private static final long OTHER_SEMESTER_ID = 1760000000000L;
    private static final String SEMESTER_NAME = "\u5927\u4E09\u4E0A";
    private static final String STAMP = "2026-10-09T21:32:10+08:00";
    private static final String VERSION_NAME = "1.0";

    // ==================================================== writing and reading

    @Test
    public void aDocumentRoundTripsThroughTheFile() {
        BackupCodec.Document written = sampleDocument();

        BackupCodec.Result result = BackupCodec.parse(BackupCodec.write(written));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(2, result.document().semesterCount());
        assertEquals(3, result.document().courseCount());
        assertEquals(3, result.document().scheduleCount());
        assertEquals(written.activeSemesterId(), result.document().activeSemesterId());
        assertEquals(STAMP, result.document().exportedAt());
        assertEquals(VERSION_NAME, result.document().appVersionName());
        assertTrue("a clean file needs no repairs: " + result.document().notes(),
                result.document().notes().isEmpty());
    }

    @Test
    public void everyCourseFieldSurvivesTheRoundTrip() {
        Course source = new Course();
        source.id = 42L;
        source.name = "\u5D4C\u5165\u5F0F\u4EBA\u5DE5\u667A\u80FD";
        source.teacher = "\u5F90\u8001\u5E08";
        source.location = "\u6559\u5B66\u697C D305";
        source.dayOfWeek = 4;
        source.startPeriod = 5;
        source.endPeriod = 6;
        source.startWeek = 3;
        source.endWeek = 11;
        source.parity = Course.PARITY_ODD;
        source.color = 0xFF3C6A8E;
        source.scheduleName = "D/E \u697C\u4F5C\u606F";

        Course read = roundTrip(singleCourseDocument(source))
                .coursesOf(SEMESTER_ID).get(0);

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
    public void theColourIsWrittenUnsignedSoItIsNotDistorted() {
        Course source = course(1L, "\u9AD8\u7B49\u6570\u5B66");
        source.color = 0xFF3C6A8E;

        Course read = roundTrip(singleCourseDocument(source))
                .coursesOf(SEMESTER_ID).get(0);

        assertEquals(0xFF3C6A8E, read.color);
        assertTrue("written unsigned, the same convention the store uses",
                BackupCodec.write(singleCourseDocument(source)).contains("4282149518"));
    }

    @Test
    public void eachSemesterKeepsItsOwnCourses() {
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                coursesBy(SEMESTER_ID, course(1L, "A"), course(2L, "B")),
                BellSchedules.presets(), SEMESTER_ID, STAMP, VERSION_NAME);

        BackupCodec.Document read = roundTrip(document);

        assertEquals(2, read.coursesOf(SEMESTER_ID).size());
        assertEquals(0, read.coursesOf(OTHER_SEMESTER_ID).size());
        assertEquals("A", read.coursesOf(SEMESTER_ID).get(0).name);
        assertEquals("\u5927\u4E09\u4E0B", read.semesters().get(1).name());
    }

    @Test
    public void everyParitySurvivesTheRoundTrip() {
        List<Course> read = roundTrip(documentWithParities()).coursesOf(SEMESTER_ID);

        assertEquals(3, read.size());
        assertEquals(Course.PARITY_ALL, read.get(0).parity);
        assertEquals(Course.PARITY_ODD, read.get(1).parity);
        assertEquals(Course.PARITY_EVEN, read.get(2).parity);
    }

    @Test
    public void anEmptyTimetableIsStillAValidBackup() {
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(),
                BellSchedules.presets(), SEMESTER_ID, STAMP, VERSION_NAME);

        BackupCodec.Result result = BackupCodec.parse(BackupCodec.write(document));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(1, result.document().semesterCount());
        assertEquals(0, result.document().courseCount());
        assertEquals(3, result.document().scheduleCount());
        assertTrue(result.document().notes().toString(),
                result.document().notes().isEmpty());
    }

    @Test
    public void aCustomScheduleSurvivesTheRoundTrip() {
        BellSchedule mine = BellSchedules.standard()
                .renamed("\u6211\u7684\u4F5C\u606F\u8868")
                .withLocationKeywords(Arrays.asList("\u6559\u5B66\u697C", "\u5B9E\u9A8C\u697C"));
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(),
                Arrays.asList(mine), SEMESTER_ID, STAMP, VERSION_NAME);

        BellSchedule read = roundTrip(document).schedules().get(0);

        assertEquals("\u6211\u7684\u4F5C\u606F\u8868", read.name());
        assertEquals(Arrays.asList("\u6559\u5B66\u697C", "\u5B9E\u9A8C\u697C"),
                read.locationKeywords());
        assertEquals(BellSchedule.PERIODS, read.periodCount());
        for (int period = 1; period <= BellSchedule.PERIODS; period++) {
            assertEquals(mine.startMinute(period), read.startMinute(period));
            assertEquals(mine.endMinute(period), read.endMinute(period));
        }
    }

    @Test
    public void aCourseKeepsItsLinkToAScheduleInTheFile() {
        Course course = course(1L, "\u9AD8\u7B49\u6570\u5B66");
        course.scheduleName = BellSchedules.DE_NAME;

        BackupCodec.Document read = roundTrip(singleCourseDocument(course));

        assertEquals(BellSchedules.DE_NAME,
                read.coursesOf(SEMESTER_ID).get(0).scheduleName);
        assertTrue("a resolvable link must not produce a note: " + read.notes(),
                read.notes().isEmpty());
    }

    @Test
    public void theHeaderCarriesTheFormatAndTheMetadata() {
        String json = BackupCodec.write(sampleDocument());
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(json);

        assertEquals(BackupCodec.FORMAT, root.get("format"));
        assertEquals(BackupCodec.FORMAT_VERSION,
                MiniJson.optInt(root, "formatVersion", -1));
        assertEquals(STAMP, root.get("exportedAt"));
        assertEquals(VERSION_NAME, root.get("appVersionName"));
        assertEquals(SEMESTER_ID, MiniJson.optLong(root, "activeSemesterId", 0L));
        assertEquals("2026-08-31", firstSemester(root).get("start"));
    }

    @Test
    public void writingTheSameDocumentTwiceGivesTheSameBytes() {
        BackupCodec.Document document = sampleDocument();

        assertEquals(BackupCodec.write(document), BackupCodec.write(document));
    }

    @Test
    public void theBackupLeavesOutTheDerivedTimeRange() {
        Course course = course(1L, "\u9AD8\u7B49\u6570\u5B66");
        course.timeRange = "08:15 - 09:35";

        String json = BackupCodec.write(singleCourseDocument(course));

        assertFalse("timeRange is derived and must not be stored",
                json.contains("timeRange"));
        assertFalse(json.contains("08:15"));
    }

    @Test
    public void theBackupLeavesOutInternalStorageArtefacts() {
        String json = BackupCodec.write(sampleDocument());

        assertFalse("the damaged-payload copy is internal", json.contains("courses_backup"));
        assertFalse("per-semester keys are a storage detail", json.contains("semester_start"));
        assertFalse(json.contains("\"courses:"));
    }

    @Test
    public void aDamagedCopyInTheStoreNeverReachesTheBackup() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        preferences.edit()
                .putString("courses_backup", "<html>not a timetable</html>")
                .apply();
        assertTrue("the copy must exist, or the test proves nothing",
                repository.damagedCopy().isExportable());

        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        coursesBySemester.put(Long.valueOf(active.id()), repository.getCourses());
        String json = BackupCodec.write(BackupCodec.of(List.of(active), coursesBySemester,
                repository.getBellSchedules(), active.id(), STAMP, VERSION_NAME));

        assertFalse("the damaged copy is internal", json.contains("courses_backup"));
        assertFalse("and none of its content may leak into a user's backup either",
                json.contains("not a timetable"));
    }

    @Test
    public void theBackupLeavesOutTheImportUndoSnapshot() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        repository.replaceCourses(List.of(course(1L, "\u5BFC\u5165\u7684\u8BFE")));
        assertNotNull("the import must have written a snapshot to leave out",
                preferences.getString("courses_previous:" + active.id(), null));

        // Built the way the settings screen builds one: from what the repository
        // hands out, not from a hand-made document.
        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        coursesBySemester.put(Long.valueOf(active.id()), repository.getCourses());
        String json = BackupCodec.write(BackupCodec.of(List.of(active), coursesBySemester,
                repository.getBellSchedules(), active.id(), STAMP, VERSION_NAME));

        assertFalse("the undo snapshot is internal", json.contains("courses_previous"));
        assertEquals(1, BackupCodec.parse(json).document().courseCount());
    }

    @Test
    public void quotesNewlinesBackslashesAndWideCharactersSurvive() {
        Course course = course(1L, "\u9AD8\u7B49\u6570\u5B66");
        course.teacher = "\u5F20\u201C\u5F15\u53F7\u201D\u8001\u5E08";
        course.location = "\u6559\u5B66\u697C A203\n\u7B2C\u4E8C\u884C\\\u53CD\u659C\u6760"
                + " \uD83D\uDE00";
        course.name = "\u5168\u89D2\uFF1A\u4F5C\u606F\uFF08\u4E00\uFF09";

        Course read = roundTrip(singleCourseDocument(course))
                .coursesOf(SEMESTER_ID).get(0);

        assertEquals(course.name, read.name);
        assertEquals(course.teacher, read.teacher);
        assertEquals(course.location, read.location);
    }

    /**
     * The backup's course fields must stay identical to the ones the store
     * writes, or the two drift apart silently as {@code Course} grows.
     */
    @Test
    public void theCourseFieldsMatchWhatTheStoreWritesExactly() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        // A term starts empty, so the payload to compare against has to be put
        // there deliberately -- one course is all this test needs.
        repository.upsertCourse(course(1L, "\u9AD8\u7B49\u6570\u5B66"));
        assertFalse(repository.getCourses().isEmpty());
        String stored = preferences.getString(
                "courses:" + repository.getActiveSemester().id(), null);
        assertNotNull(stored);

        Set<String> storedKeys = keysOfFirstEntry(MiniJson.parseArray(stored));
        Set<String> backupKeys = keysOfFirstEntry(coursesOf(sampleDocument()));

        assertEquals("the backup must carry exactly the stored course fields",
                storedKeys, backupKeys);
    }

    // ============================================================== refusals

    @Test
    public void anEmptyFileIsRefused() {
        assertRefused("", "\u7A7A");
    }

    @Test
    public void aFileOfNothingButWhitespaceIsRefused() {
        assertRefused("   \n\t  ", "\u7A7A");
    }

    @Test
    public void aNullTextIsRefused() {
        BackupCodec.Result result = BackupCodec.parse(null);
        assertFalse(result.isOk());
        assertNull(result.document());
    }

    @Test
    public void brokenJsonIsRefused() {
        assertRefused("{\"format\":\"weihe-schedule-backup\",", "JSON");
    }

    @Test
    public void aJsonArrayAtTheTopLevelIsRefused() {
        assertRefused("[1,2,3]", "\u9876\u5C42");
    }

    @Test
    public void anotherAppsJsonIsRefused() {
        assertRefused("{\"hello\":\"world\"}", "\u62FE\u8BFE");
    }

    @Test
    public void aNewerFormatVersionIsRefusedAndSaysSo() {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Integer.valueOf(BackupCodec.MAX_SUPPORTED_VERSION + 1));

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertFalse(result.isOk());
        assertTrue(result.problem(), result.problem().contains("\u7248\u672C"));
        assertTrue(result.problem(), result.problem().contains(
                String.valueOf(BackupCodec.MAX_SUPPORTED_VERSION + 1)));
    }

    @Test
    public void aFormatVersionThatIsNotANumberIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", "not-a-version");

        assertRefused(json(root), "\u7248\u672C");
    }

    @Test
    public void aMissingFormatVersionIsAcceptedWithANote() {
        Map<String, Object> root = validRoot();
        root.remove("formatVersion");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(result.document().notes().toString(), 1, result.document().notes().size());
        assertTrue(result.document().notes().get(0),
                result.document().notes().get(0).contains("\u672A\u6807\u6CE8"));
    }

    @Test
    public void missingSemestersIsRefused() {
        Map<String, Object> root = validRoot();
        root.remove("semesters");

        assertRefused(json(root), "semesters");
    }

    @Test
    public void anEmptySemesterListIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("semesters", MiniJson.array());

        assertRefused(json(root), "\u5B66\u671F");
    }

    @Test
    public void missingSchedulesIsRefused() {
        Map<String, Object> root = validRoot();
        root.remove("schedules");

        assertRefused(json(root), "schedules");
    }

    @Test
    public void anEmptyScheduleListIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("schedules", MiniJson.array());

        assertRefused(json(root), "\u4F5C\u606F\u8868");
    }

    @Test
    public void aSemesterThatIsNotAnObjectIsRefused() {
        Map<String, Object> root = validRoot();
        List<Object> semesters = MiniJson.array();
        semesters.add("not a semester");
        root.put("semesters", semesters);

        assertRefused(json(root), "\u5BF9\u8C61");
    }

    @Test
    public void aSemesterWithoutAUsableIdIsRefused() {
        Map<String, Object> root = validRoot();
        firstSemester(root).put("id", Integer.valueOf(0));

        assertRefused(json(root), "id");
    }

    @Test
    public void aSemesterWithAnUnparseableStartIsRefused() {
        Map<String, Object> root = validRoot();
        firstSemester(root).put("start", "not a date");

        assertRefused(json(root), "\u65E5\u671F");
    }

    @Test
    public void aSemesterWithoutAStartFieldIsRefused() {
        Map<String, Object> root = validRoot();
        firstSemester(root).remove("start");

        assertRefused(json(root), "\u65E5\u671F");
    }

    @Test
    public void duplicateSemesterIdsAreRefused() {
        Map<String, Object> root = validRoot();
        listOf(root, "semesters").add(semesterEntry(SEMESTER_ID, SEMESTER_NAME));

        assertRefused(json(root), "\u91CD\u590D");
    }

    @Test
    public void aScheduleWithoutANameIsRefused() {
        Map<String, Object> root = validRoot();
        firstSchedule(root).put("name", "   ");

        assertRefused(json(root), "\u540D\u5B57");
    }

    @Test
    public void duplicateScheduleNamesAreRefused() {
        Map<String, Object> root = validRoot();
        listOf(root, "schedules").add(scheduleEntry("A\u697C\u4F5C\u606F"));

        assertRefused(json(root), "\u540C\u540D");
    }

    @Test
    public void aScheduleWithADifferentPeriodCountIsAccepted() {
        // The number of periods is the length of the arrays, not a fixed fifteen.
        // A table of twenty periods is a valid file now that a schedule can have
        // more than the default, and refusing it would make this build unable to
        // read a backup it had just written.
        Map<String, Object> root = validRoot();
        Map<String, Object> schedule = firstSchedule(root);
        schedule.put("starts", ints(20));
        schedule.put("ends", ints(20));

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue("a twenty-period table must load: " + result.problem(),
                result.isOk());
        assertEquals(20, result.document().schedules().get(0).periodCount());
    }

    @Test
    public void aScheduleWithFewerPeriodsThanTheMinimumIsRefused() {
        Map<String, Object> root = validRoot();
        Map<String, Object> schedule = firstSchedule(root);
        schedule.put("starts", ints(BellSchedule.MIN_PERIODS - 1));
        schedule.put("ends", ints(BellSchedule.MIN_PERIODS - 1));

        assertRefused(json(root), String.valueOf(BellSchedule.MIN_PERIODS));
    }

    @Test
    public void aScheduleWithMorePeriodsThanTheCeilingIsRefused() {
        // Past the ceiling the file is refused and says how many periods it held.
        // The minutes stay inside the day so that the count, not a time, is what
        // trips -- otherwise this would pass for the wrong reason.
        Map<String, Object> root = validRoot();
        Map<String, Object> schedule = firstSchedule(root);
        // Every entry is a legal minute, so what the refusal is about is the count.
        // Times are not required to ascend, only to be times.
        List<Object> tooManyStarts = MiniJson.array();
        List<Object> tooManyEnds = MiniJson.array();
        for (int i = 0; i < ScheduleUtils.MAX_PERIODS + 1; i++) {
            tooManyStarts.add(Integer.valueOf(60));
            tooManyEnds.add(Integer.valueOf(120));
        }
        schedule.put("starts", tooManyStarts);
        schedule.put("ends", tooManyEnds);

        assertRefused(json(root), String.valueOf(ScheduleUtils.MAX_PERIODS));
    }

    @Test
    public void startsAndEndsOfDifferentLengthsAreRefused() {
        Map<String, Object> root = validRoot();
        Map<String, Object> schedule = firstSchedule(root);
        schedule.put("starts", ints(BellSchedule.PERIODS));
        schedule.put("ends", ints(BellSchedule.PERIODS - 1));

        assertRefused(json(root), "\u6570\u91CF\u4E0D\u4E00\u81F4");
    }

    @Test
    public void aMinuteOutsideTheAllowedRangeIsRefused() {
        Map<String, Object> root = validRoot();
        List<Object> starts = ints(BellSchedule.PERIODS);
        starts.set(0, Integer.valueOf(BellSchedule.MAX_MINUTE + 1));
        firstSchedule(root).put("starts", starts);

        assertRefused(json(root), "\u65F6\u95F4");
    }

    @Test
    public void aCourseThatIsNotAnObjectIsRefused() {
        Map<String, Object> root = validRoot();
        listOf(firstSemester(root), "courses").add("not a course");

        assertRefused(json(root), "\u5BF9\u8C61");
    }

    @Test
    public void aCourseWithAnOutOfRangeDayIsRefused() {
        assertCourseFieldRefused("day", Integer.valueOf(9));
    }

    @Test
    public void aCourseWithAZeroDayIsRefused() {
        assertCourseFieldRefused("day", Integer.valueOf(0));
    }

    @Test
    public void aCourseWithAnOutOfRangePeriodIsRefused() {
        // Period sixteen is inside a schedule now that the count is settable, so the
        // value that is out of range has to be past the app's ceiling rather than
        // past the default fifteen.
        assertCourseFieldRefused("startPeriod",
                Integer.valueOf(ScheduleUtils.MAX_PERIODS + 1));
    }

    @Test
    public void aCourseOnAPeriodBeyondTheDefaultIsAccepted() {
        // A course on period sixteen is an ordinary course once a schedule can hold
        // sixteen periods; refusing it here would reject a file this build wrote.
        Map<String, Object> root = validRoot();
        Map<String, Object> course = firstCourse(root);
        course.put("startPeriod", Integer.valueOf(BellSchedule.PERIODS + 1));
        course.put("endPeriod", Integer.valueOf(BellSchedule.PERIODS + 1));

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue("a course on period 16 must load: " + result.problem(),
                result.isOk());
    }

    @Test
    public void aCourseWhosePeriodsRunBackwardsIsRefused() {
        Map<String, Object> root = validRoot();
        Map<String, Object> course = firstCourse(root);
        course.put("startPeriod", Integer.valueOf(6));
        course.put("endPeriod", Integer.valueOf(2));

        assertRefused(json(root), "endPeriod");
    }

    @Test
    public void aCourseWhoseWeeksRunBackwardsIsRefused() {
        Map<String, Object> root = validRoot();
        Map<String, Object> course = firstCourse(root);
        course.put("startWeek", Integer.valueOf(10));
        course.put("endWeek", Integer.valueOf(3));

        assertRefused(json(root), "endWeek");
    }

    @Test
    public void aCourseWithAWeekBeyondTheSemesterIsRefused() {
        assertCourseFieldRefused("endWeek",
                Integer.valueOf(ScheduleUtils.SEMESTER_WEEKS + 1));
    }

    @Test
    public void aCourseWithAnUnknownParityIsRefused() {
        assertCourseFieldRefused("parity", Integer.valueOf(7));
    }

    @Test
    public void aCourseWithATextValueWhereANumberBelongsIsRefused() {
        Map<String, Object> root = validRoot();
        firstCourse(root).put("day", "\u661F\u671F\u4E00");

        assertRefused(json(root), "day");
    }

    @Test
    public void aCourseListThatIsNotAListIsRefused() {
        Map<String, Object> root = validRoot();
        firstSemester(root).put("courses", "not a list");

        assertRefused(json(root), "courses");
    }

    @Test
    public void anOversizedFileIsRefusedWithoutBeingParsed() {
        assertRefused(repeat('x', BackupCodec.MAX_BYTES + 8), "\u8FC7\u5927");
    }

    // ================================================ repairs and side effects

    @Test
    public void aScheduleReferenceTheFileDoesNotCarryIsClearedAndNoted() {
        Map<String, Object> root = validRoot();
        firstCourse(root).put("scheduleName", "\u4E0D\u5B58\u5728\u7684\u4F5C\u606F\u8868");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals("", result.document().coursesOf(SEMESTER_ID).get(0).scheduleName);
        assertEquals(result.document().notes().toString(),
                1, result.document().notes().size());
        assertTrue(result.document().notes().get(0),
                result.document().notes().get(0).contains("\u4F5C\u606F\u8868"));
    }

    @Test
    public void aCourseWithoutAnIdIsGivenOneAndNoted() {
        Map<String, Object> root = validRoot();
        firstCourse(root).remove("id");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertNotEquals(0L, result.document().coursesOf(SEMESTER_ID).get(0).id);
        assertEquals(result.document().notes().toString(),
                1, result.document().notes().size());
        assertTrue(result.document().notes().get(0),
                result.document().notes().get(0).contains("\u6807\u8BC6"));
    }

    @Test
    public void twoCoursesSharingAnIdAreSeparatedAndNeitherIsDropped() {
        Map<String, Object> root = validRoot();
        List<Object> courses = listOf(firstSemester(root), "courses");
        courses.add(courseEntry(7L));
        courses.add(courseEntry(7L));

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        List<Course> read = result.document().coursesOf(SEMESTER_ID);
        assertEquals("the seed course plus the two duplicates", 3, read.size());
        assertEquals("every course must end up addressable", 3,
                distinctIds(read).size());
    }

    @Test
    public void anActiveSemesterThatIsNotInTheFileFallsBackToTheFirstOne() {
        Map<String, Object> root = validRoot();
        root.put("activeSemesterId", Long.valueOf(999L));

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(result.document().semesters().get(0).id(),
                result.document().activeSemesterId());
        assertEquals(1, result.document().notes().size());
    }

    @Test
    public void aMissingActiveSemesterSimplyPicksTheFirstOne() {
        Map<String, Object> root = validRoot();
        root.remove("activeSemesterId");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(result.document().semesters().get(0).id(),
                result.document().activeSemesterId());
        assertTrue("nothing was wrong, so nothing to report: " + result.document().notes(),
                result.document().notes().isEmpty());
    }

    @Test
    public void optionalFieldsMayBeAbsent() {
        Map<String, Object> root = validRoot();
        Map<String, Object> course = firstCourse(root);
        course.remove("teacher");
        course.remove("location");
        course.remove("scheduleName");
        course.remove("color");
        firstSchedule(root).remove("keywords");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        Course read = result.document().coursesOf(SEMESTER_ID).get(0);
        assertEquals("", read.teacher);
        assertEquals("", read.location);
        assertEquals("", read.scheduleName);
        assertEquals(Course.DEFAULT_COLOR, read.color);
        assertEquals(0, result.document().schedules().get(0).locationKeywords().size());
    }

    @Test
    public void aSemesterWithoutACustomNameIsNoted() {
        Map<String, Object> root = validRoot();
        firstSemester(root).put("name", "");

        BackupCodec.Result result = BackupCodec.parse(json(root));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(1, result.document().notes().size());
        assertTrue(result.document().notes().get(0),
                result.document().notes().get(0).contains("\u81EA\u52A8\u547D\u540D"));
    }

    @Test
    public void checkingABadFileChangesNothingInStorage() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        // The file being checked here came from a filled-in term, so fill this
        // one in first: nine courses, so the count below has something to prove.
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
        Map<String, Object> before = preferences.snapshot();

        String good = BackupCodec.write(sampleDocument());
        String[] inputs = {
                null, "", "   ", "{", "[]", "{}",
                "{\"format\":\"weihe-schedule-backup\"}",
                json(withVersion(BackupCodec.MAX_SUPPORTED_VERSION + 1)),
                json(withEmptySemesters()),
                good.substring(0, Math.max(1, good.length() / 2)),
        };
        for (String input : inputs) {
            BackupCodec.parse(input);
        }

        Map<String, Object> after = preferences.snapshot();
        assertEquals("validating must not touch storage at all", before, after);
        assertFalse(after.containsKey("restore_snapshot"));
        assertFalse(after.containsKey("courses_backup"));

        // And the timetable the file was taken from is still intact.
        assertEquals(9, repository.getCourses().size());
        assertEquals(3, repository.getBellSchedules().size());
        assertEquals(1, repository.getSemesters().size());
    }

    @Test
    public void theDocumentCopiesWhateverTheCallerHandsIt() {
        List<Course> mine = new ArrayList<>();
        mine.add(course(1L, "original"));
        Map<Long, List<Course>> bySemester = new LinkedHashMap<Long, List<Course>>();
        bySemester.put(Long.valueOf(SEMESTER_ID), mine);
        List<BellSchedule> schedules = new ArrayList<>(BellSchedules.presets());

        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                bySemester, schedules, SEMESTER_ID, STAMP, VERSION_NAME);

        mine.clear();
        schedules.clear();

        assertEquals("the document must not alias the caller's lists",
                1, document.courseCount());
        assertEquals(3, document.scheduleCount());
    }

    @Test
    public void theDocumentHandsOutListsThatCannotBeMutated() {
        BackupCodec.Document document = sampleDocument();

        try {
            document.semesters().clear();
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            document.coursesOf(SEMESTER_ID).clear();
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            document.schedules().clear();
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    // ===================================================== writing to a stream

    @Test
    public void writingToAStreamProducesTheSameTextAsWritingToAString() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BackupCodec.writeTo(sampleDocument(), out);

        assertEquals(BackupCodec.write(sampleDocument()),
                new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void theWrittenBytesCarryNoByteOrderMarkAndReadStraightBack() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BackupCodec.writeTo(sampleDocument(), out);

        byte[] bytes = out.toByteArray();
        assertEquals('{', bytes[0]);
        assertFalse("a byte-order mark would upset other readers",
                bytes.length > 3 && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF);
        assertTrue(BackupCodec.parse(new String(bytes, StandardCharsets.UTF_8)).isOk());
    }

    @Test
    public void aStreamThatFailsToWriteReportsTheFailure() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("no space left on device");
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("no space left on device");
            }
        };

        try {
            BackupCodec.writeTo(sampleDocument(), broken);
            fail("expected the failure to reach the caller");
        } catch (IOException expected) {
            assertEquals("no space left on device", expected.getMessage());
        }
    }

    @Test
    public void whatWasWrittenCanBeReadBackFromTheSameBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupCodec.writeTo(sampleDocument(), out);

        BackupCodec.Result result = BackupCodec.parse(
                new String(out.toByteArray(), StandardCharsets.UTF_8));

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(3, result.document().courseCount());
        assertEquals(2, result.document().semesterCount());
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static BackupCodec.Document roundTrip(BackupCodec.Document document) {
        BackupCodec.Result result = BackupCodec.parse(BackupCodec.write(document));
        assertTrue(result.problems().toString(), result.isOk());
        return result.document();
    }

    private static BackupCodec.Document sampleDocument() {
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                coursesBy(SEMESTER_ID, course(1L, "\u9AD8\u7B49\u6570\u5B66"),
                        course(2L, "\u5927\u5B66\u82F1\u8BED"),
                        course(5L, "\u7A0B\u5E8F\u8BBE\u8BA1\u5B9E\u9A8C")),
                BellSchedules.presets(),
                SEMESTER_ID, STAMP, VERSION_NAME);
    }

    private static BackupCodec.Document singleCourseDocument(Course course) {
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                coursesBy(SEMESTER_ID, course), BellSchedules.presets(),
                SEMESTER_ID, STAMP, VERSION_NAME);
    }

    private static BackupCodec.Document documentWithParities() {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        List<Course> courses = new ArrayList<>();
        Course all = course(1L, "all");
        all.parity = Course.PARITY_ALL;
        Course odd = course(2L, "odd");
        odd.parity = Course.PARITY_ODD;
        Course even = course(3L, "even");
        even.parity = Course.PARITY_EVEN;
        courses.add(all);
        courses.add(odd);
        courses.add(even);
        bySemester.put(Long.valueOf(SEMESTER_ID), courses);
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                bySemester, BellSchedules.presets(), SEMESTER_ID, STAMP, VERSION_NAME);
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

    private static Map<Long, List<Course>> coursesBy(long semesterId, Course... courses) {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(semesterId), new ArrayList<>(Arrays.asList(courses)));
        return bySemester;
    }

    private static Set<Long> distinctIds(List<Course> courses) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Course course : courses) {
            ids.add(Long.valueOf(course.id));
        }
        return ids;
    }

    private static String repeat(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    // --------------------------------------------- hand-built JSON for refusals

    /** A valid document, built without going through the writer. */
    private static Map<String, Object> validRoot() {
        Map<String, Object> root = MiniJson.object();
        root.put("format", BackupCodec.FORMAT);
        root.put("formatVersion", Integer.valueOf(BackupCodec.FORMAT_VERSION));
        root.put("exportedAt", STAMP);
        root.put("appVersionName", VERSION_NAME);
        root.put("activeSemesterId", Long.valueOf(SEMESTER_ID));
        List<Object> semesters = MiniJson.array();
        semesters.add(semesterEntry(SEMESTER_ID, SEMESTER_NAME));
        root.put("semesters", semesters);
        List<Object> schedules = MiniJson.array();
        schedules.add(scheduleEntry("A\u697C\u4F5C\u606F"));
        schedules.add(scheduleEntry("B\u697C\u4F5C\u606F"));
        root.put("schedules", schedules);
        return root;
    }

    private static Map<String, Object> semesterEntry(long id, String name) {
        Map<String, Object> item = MiniJson.object();
        item.put("id", Long.valueOf(id));
        item.put("start", "2026-08-31");
        item.put("name", name);
        List<Object> courses = MiniJson.array();
        courses.add(courseEntry(1L));
        item.put("courses", courses);
        return item;
    }

    private static Map<String, Object> courseEntry(long id) {
        Map<String, Object> item = MiniJson.object();
        item.put("id", Long.valueOf(id));
        item.put("name", "\u9AD8\u7B49\u6570\u5B66");
        item.put("teacher", "\u5F20\u8001\u5E08");
        item.put("location", "\u6559\u5B66\u697C A203");
        item.put("day", Integer.valueOf(1));
        item.put("startPeriod", Integer.valueOf(1));
        item.put("endPeriod", Integer.valueOf(2));
        item.put("startWeek", Integer.valueOf(1));
        item.put("endWeek", Integer.valueOf(16));
        item.put("parity", Integer.valueOf(Course.PARITY_ALL));
        item.put("color", Long.valueOf(0xFF3C6A8EL));
        item.put("scheduleName", "");
        return item;
    }

    private static Map<String, Object> scheduleEntry(String name) {
        Map<String, Object> item = MiniJson.object();
        item.put("name", name);
        List<Object> keywords = MiniJson.array();
        keywords.add("A\u697C");
        item.put("keywords", keywords);
        item.put("starts", ints(BellSchedule.PERIODS));
        item.put("ends", ints(BellSchedule.PERIODS));
        return item;
    }

    private static List<Object> ints(int count) {
        List<Object> values = MiniJson.array();
        for (int i = 0; i < count; i++) {
            values.add(Integer.valueOf(480 + i * 45));
        }
        return values;
    }

    private static Map<String, Object> withVersion(int version) {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Integer.valueOf(version));
        return root;
    }

    private static Map<String, Object> withEmptySemesters() {
        Map<String, Object> root = validRoot();
        root.put("semesters", MiniJson.array());
        return root;
    }

    private static String json(Map<String, Object> root) {
        return MiniJson.write(root);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Map<String, Object> holder, String key) {
        return (List<Object>) holder.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSemester(Map<String, Object> root) {
        return (Map<String, Object>) listOf(root, "semesters").get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSchedule(Map<String, Object> root) {
        return (Map<String, Object>) listOf(root, "schedules").get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCourse(Map<String, Object> root) {
        return (Map<String, Object>) listOf(firstSemester(root), "courses").get(0);
    }

    /** The course objects a document writes, for key-set comparisons. */
    @SuppressWarnings("unchecked")
    private static List<Object> coursesOf(BackupCodec.Document document) {
        Map<String, Object> root =
                (Map<String, Object>) MiniJson.parse(BackupCodec.write(document));
        return listOf(firstSemester(root), "courses");
    }

    private static void assertCourseFieldRefused(String field, Object value) {
        Map<String, Object> root = validRoot();
        firstCourse(root).put(field, value);
        assertRefused(json(root), field);
    }

    private static void assertRefused(String text, String expectedFragment) {
        BackupCodec.Result result = BackupCodec.parse(text);
        assertFalse("expected a refusal for: " + abbreviate(text), result.isOk());
        assertNull(result.document());
        assertEquals(1, result.problems().size());
        assertTrue("problem was: " + result.problem(),
                result.problem().contains(expectedFragment));
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }

    private static Set<String> keysOfFirstEntry(List<Object> array) {
        assertFalse("expected at least one entry", array.isEmpty());
        Map<String, Object> first = MiniJson.objectAt(array, 0);
        assertNotNull(first);
        return new LinkedHashSet<>(first.keySet());
    }
    // ============================================ the version field, strictly
    //
    // The version decides whether the rest of the file may be read at all, so it is
    // the one field read strictly: a whole JSON number, judged before it is narrowed.
    // 4294967297 is the value that used to slip through -- intValue() turns it into
    // 1, the oldest version there is, so a file from the future was read as one.

    @Test
    public void theLowestSupportedVersionIsStillAccepted() {
        assertNotNull("version 1 must keep parsing",
                BackupCodec.parse(json(withVersion(1))).document());
    }

    @Test
    public void theHighestSupportedVersionIsStillAccepted() {
        assertNotNull(BackupCodec.parse(
                json(withVersion(BackupCodec.MAX_SUPPORTED_VERSION))).document());
    }

    @Test
    public void oneVersionPastTheTopIsRefused() {
        assertRefused(json(withVersion(BackupCodec.MAX_SUPPORTED_VERSION + 1)),
                String.valueOf(BackupCodec.MAX_SUPPORTED_VERSION + 1));
    }

    @Test
    public void anIntegerThatNarrowsToAValidVersionIsRefusedAnyway() {
        // 4294967297 = 2^32 + 1. As an int it is 1, and 1 is a version this build
        // supports, which is exactly why the check has to happen before the cast.
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Double.valueOf(4294967297.0));
        assertRefused(json(root), "4294967297");
    }

    @Test
    public void otherValuesThatNarrowIntoRangeAreRefusedToo() {
        for (double value : new double[] {4294967296.0, 4294967298.0, 8589934593.0}) {
            Map<String, Object> root = validRoot();
            root.put("formatVersion", Double.valueOf(value));
            assertRefused(json(root), String.valueOf((long) value));
        }
    }

    @Test
    public void anIntegerBeyondLongRangeIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Double.valueOf(1.0E30));
        assertRefused(json(root), "\u683C\u5F0F\u7248\u672C");
    }

    @Test
    public void aNegativeVersionIsRefusedRatherThanReadAsVersionOne() {
        // It used to be noted and read as version 1. A version of -1 is not an older
        // file, it is a file whose version means nothing.
        assertRefused(json(withVersion(-1)), "\u4E0D\u5408\u6CD5");
        assertRefused(json(withVersion(0)), "\u4E0D\u5408\u6CD5");
    }

    @Test
    public void aFractionalVersionIsRefusedRatherThanTruncated() {
        // 1.5 used to become 1 and be accepted.
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Double.valueOf(1.5));
        assertRefused(json(root), "\u65E0\u6CD5\u8BC6\u522B");
    }

    @Test
    public void aVersionWrittenAsAStringIsRefused() {
        // The format writes a number. A string is a different file, not a lenient
        // reading of this one, and is not converted.
        assertRefused(json(withVersionString("1")), "\u65E0\u6CD5\u8BC6\u522B");
        assertRefused(json(withVersionString("2")), "\u65E0\u6CD5\u8BC6\u522B");
    }

    @Test
    public void aNullVersionIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", null);
        assertRefused(json(root), "\u65E0\u6CD5\u8BC6\u522B");
    }

    @Test
    public void aBooleanVersionIsRefused() {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Boolean.TRUE);
        assertRefused(json(root), "\u65E0\u6CD5\u8BC6\u522B");
    }

    @Test
    public void aFutureVersionNeverReachesTheRestOfTheFile() {
        // Refused means refused: no document comes back, so nothing downstream has
        // anything to decode, store or restore.
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Double.valueOf(4294967297.0));
        BackupCodec.Result result = BackupCodec.parse(json(root));
        assertNull("a future version must not produce a document", result.document());
        assertFalse("and must say why", result.problem().isEmpty());
    }

    private static Map<String, Object> withVersionString(String version) {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", version);
        return root;
    }
}
