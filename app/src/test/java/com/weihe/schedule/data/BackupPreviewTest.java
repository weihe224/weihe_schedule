package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * The restore preview: the numbers it shows, and the promise that showing them
 * changes nothing.
 *
 * <p>{@link BackupPreview} is built from a document that came out of a file, so
 * every figure here is checkable against the file that produced it. The
 * read-only tests at the end are the important ones: they run the whole pipeline
 * -- read the stream, check it, summarise it -- against a live repository and ask
 * the store whether anything moved.</p>
 */
public class BackupPreviewTest {

    private static final long SEMESTER_ID = 1757000000000L;
    private static final long OTHER_SEMESTER_ID = 1760000000000L;
    private static final String SEMESTER_NAME = "\u5927\u4E09\u4E0A";
    private static final String STAMP = "2026-10-09T21:32:10+08:00";

    // ====================================================== what the preview says

    @Test
    public void aValidBackupPreviewsTheCountsTheFileHolds() {
        BackupPreview preview = previewOf(sampleDocument());

        assertEquals(2, preview.semesterCount());
        assertEquals(3, preview.courseCount());
        assertEquals(3, preview.scheduleCount());
        assertEquals(BackupCodec.FORMAT_VERSION, preview.formatVersion());
    }

    @Test
    public void theCountsMatchWhatTheDocumentItselfHolds() {
        BackupCodec.Document document = sampleDocument();

        BackupPreview preview = previewOf(document);

        assertEquals(document.semesterCount(), preview.semesterCount());
        assertEquals(document.courseCount(), preview.courseCount());
        assertEquals(document.scheduleCount(), preview.scheduleCount());
    }

    @Test
    public void theSummarySentenceCarriesAllThreeCounts() {
        BackupPreview preview = previewOf(sampleDocument());

        assertEquals("2 \u4E2A\u5B66\u671F \u00B7 3 \u95E8\u8BFE\u7A0B \u00B7 3 \u5F20"
                + "\u4F5C\u606F\u8868", preview.summary());
    }

    @Test
    public void anEmptyTimetablePreviewsAsZeroCourses() {
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(),
                BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertEquals(1, preview.semesterCount());
        assertEquals(0, preview.courseCount());
        assertEquals(3, preview.scheduleCount());
        assertEquals(0, preview.parityCourseCount());
        assertEquals(1, preview.semesters().size());
        assertEquals(0, preview.semesters().get(0).courseCount());
    }

    @Test
    public void everySemesterIsListedWithItsOwnCourseCount() {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(1L, "A"), course(2L, "B"))));
        bySemester.put(Long.valueOf(OTHER_SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(3L, "C"))));
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                bySemester, BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertEquals(2, preview.semesters().size());
        assertEquals(SEMESTER_NAME, preview.semesters().get(0).label());
        assertEquals(2, preview.semesters().get(0).courseCount());
        assertEquals("\u5927\u4E09\u4E0B", preview.semesters().get(1).label());
        assertEquals(1, preview.semesters().get(1).courseCount());
        assertEquals("the file holds three courses in total", 3, preview.courseCount());
    }

    @Test
    public void aSemesterWithoutACustomNameShowsTheDerivedLabel() {
        Semester plain = semester(SEMESTER_ID, "2026-08-31", "");
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(plain), new LinkedHashMap<Long, List<Course>>(),
                BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertFalse(plain.hasCustomName());
        assertEquals(plain.label(), preview.semesters().get(0).label());
        assertTrue("the derived label names the year",
                preview.semesters().get(0).label().startsWith("2026 "));
        assertEquals("both semesters in the sample carry a typed name", 2,
                previewOf(sampleDocument()).namedSemesterCount());
    }

    @Test
    public void aSemesterRowSpansWeekOneToTheLastDay() {
        Semester alone = semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME);
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(alone), new LinkedHashMap<Long, List<Course>>(),
                BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertEquals(ScheduleUtils.formatDate(alone.start()) + " - "
                + ScheduleUtils.formatDate(alone.lastDay()),
                preview.semesters().get(0).dateRange());
    }

    @Test
    public void oddAndEvenWeekCoursesAreCountedSeparately() {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        Course all = course(1L, "all");
        Course odd = course(2L, "odd");
        odd.parity = Course.PARITY_ODD;
        Course even = course(3L, "even");
        even.parity = Course.PARITY_EVEN;
        bySemester.put(Long.valueOf(SEMESTER_ID),
                new ArrayList<>(Arrays.asList(all, odd, even)));
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                bySemester, BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertEquals(3, preview.courseCount());
        assertEquals(2, preview.parityCourseCount());
    }

    @Test
    public void aCustomScheduleSurvivesIntoThePreview() {
        BellSchedule mine = BellSchedules.standard()
                .renamed("\u6211\u7684\u4F5C\u606F\u8868")
                .withLocationKeywords(Arrays.asList("\u6559\u5B66\u697C", "\u5B9E\u9A8C\u697C"));
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(),
                Arrays.asList(mine), SEMESTER_ID, STAMP, "1.0");

        BackupPreview preview = previewOf(document);

        assertEquals(1, preview.scheduleCount());
        assertEquals(2, preview.keywordCount());
        assertEquals(0, preview.parityCourseCount());
    }

    @Test
    public void theFileNameAndVersionComeFromTheInput() {
        BackupPreview preview = BackupPreview.of("weihe-backup-20261009-2132.json",
                sampleDocument());

        assertEquals("weihe-backup-20261009-2132.json", preview.fileName());
        assertEquals(BackupCodec.FORMAT_VERSION, preview.formatVersion());
    }

    @Test
    public void aFileWithAnExportTimeReportsIt() {
        BackupPreview preview = previewOf(sampleDocument());

        assertTrue(preview.hasExportedAt());
        assertEquals(STAMP, preview.exportedAt());
        assertTrue(preview.hasAppVersionName());
        assertEquals("1.0", preview.appVersionName());
    }

    @Test
    public void aFileWithoutAnExportTimeReportsItAsUnknownRatherThanGuessing() {
        BackupCodec.Document document = BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME)),
                new LinkedHashMap<Long, List<Course>>(),
                BellSchedules.presets(), SEMESTER_ID, "", "");

        BackupPreview preview = previewOf(document);

        assertFalse("an absent time must be reported as unknown", preview.hasExportedAt());
        assertEquals("", preview.exportedAt());
        assertFalse(preview.hasAppVersionName());
        assertEquals("", preview.appVersionName());
    }

    @Test
    public void aCleanFileNeedsNoRepair() {
        BackupPreview preview = previewOf(sampleDocument());

        assertFalse(preview.needsRepair());
        assertTrue(preview.notes().isEmpty());
    }

    @Test
    public void whateverTheReaderHadToRepairIsCarriedToThePreview() {
        Map<String, Object> root = validRoot();
        firstCourse(root).put("scheduleName", "\u4E0D\u5B58\u5728\u7684\u4F5C\u606F\u8868");
        BackupCodec.Result result = BackupCodec.parse(MiniJson.write(root));
        assertTrue(result.problems().toString(), result.isOk());

        BackupPreview preview = BackupPreview.of("x.json", result.document());

        assertTrue(preview.needsRepair());
        assertEquals(1, preview.notes().size());
        assertEquals(result.document().notes().get(0), preview.notes().get(0));
    }

    @Test
    public void thePreviewHandsOutListsThatCannotBeMutated() {
        BackupPreview preview = previewOf(sampleDocument());

        try {
            preview.semesters().clear();
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
        try {
            preview.notes().clear();
            fail("expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    // ================================================= reading from a stream

    @Test
    public void aBackupReadFromAStreamPreviewsTheSameWay() throws Exception {
        BackupCodec.Document document = sampleDocument();

        BackupCodec.Result result = BackupCodec.read(stream(BackupCodec.write(document)),
                BackupCodec.MAX_BYTES);

        assertTrue(result.problems().toString(), result.isOk());
        assertEquals(previewOf(document).summary(),
                BackupPreview.of("x.json", result.document()).summary());
    }

    @Test
    public void anEmptyStreamIsRefused() throws Exception {
        BackupCodec.Result result =
                BackupCodec.read(stream(""), BackupCodec.MAX_BYTES);
        assertFalse(result.isOk());
        assertTrue(result.problem(), result.problem().contains("\u7A7A"));
    }

    @Test
    public void aStreamOfBrokenJsonIsRefused() throws Exception {
        BackupCodec.Result result =
                BackupCodec.read(stream("{\"format\":"), BackupCodec.MAX_BYTES);
        assertFalse(result.isOk());
        assertTrue(result.problem(), result.problem().contains("JSON"));
    }

    @Test
    public void aStreamWhoseVersionIsTooNewIsRefused() throws Exception {
        Map<String, Object> root = validRoot();
        root.put("formatVersion", Integer.valueOf(BackupCodec.MAX_SUPPORTED_VERSION + 1));

        BackupCodec.Result result =
                BackupCodec.read(stream(MiniJson.write(root)), BackupCodec.MAX_BYTES);

        assertFalse(result.isOk());
        assertEquals(1, result.problems().size());
        assertTrue(result.problem(), result.problem().contains("\u7248\u672C"));
    }

    @Test
    public void aStreamMissingARequiredSectionIsRefused() throws Exception {
        Map<String, Object> root = validRoot();
        root.remove("semesters");

        BackupCodec.Result result =
                BackupCodec.read(stream(MiniJson.write(root)), BackupCodec.MAX_BYTES);

        assertFalse(result.isOk());
        assertTrue(result.problem(), result.problem().contains("semesters"));
    }

    @Test
    public void anOversizedStreamIsRefusedWithAReadableMessage() {
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= 4096) {
            huge.append('x');
        }

        try {
            BackupCodec.read(stream(huge.toString()), 64);
            fail("expected a stream past the limit to be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("\u8FC7\u5927"));
        }
    }

    @Test
    public void aNullStreamIsRefusedWithAReadableMessage() {
        try {
            BackupCodec.read(null, BackupCodec.MAX_BYTES);
            fail("expected a null stream to be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("\u65E0\u6CD5\u6253\u5F00"));
        }
    }

    @Test
    public void aStreamThatFailsWhileReadingSurfacesTheFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("the document is gone");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("the document is gone");
            }
        };

        try {
            BackupCodec.read(broken, BackupCodec.MAX_BYTES);
            fail("expected the read failure to reach the caller");
        } catch (IOException expected) {
            assertEquals("the document is gone", expected.getMessage());
        }
    }

    // ================================================ previewing writes nothing

    @Test
    public void previewingAValidBackupLeavesTheStoredDataUntouched() throws Exception {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();
        String payload = BackupCodec.write(sampleDocument());

        BackupCodec.Result result =
                BackupCodec.read(stream(payload), BackupCodec.MAX_BYTES);
        assertTrue(result.problems().toString(), result.isOk());
        BackupPreview preview = BackupPreview.of("x.json", result.document());
        assertEquals(3, preview.courseCount());

        assertEquals("previewing must not write anything", before,
                preferences.snapshot());
        assertUnchanged(repository, before);
    }

    @Test
    public void previewingABrokenBackupLeavesTheStoredDataUntouched() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();

        String[] rejected = {"", "   ", "{", "[]", "{}",
                "{\"format\":\"weihe-schedule-backup\"}"};
        for (String text : rejected) {
            try {
                BackupCodec.Result result =
                        BackupCodec.read(stream(text), BackupCodec.MAX_BYTES);
                assertFalse("expected a refusal for: " + text, result.isOk());
            } catch (IOException unexpected) {
                fail("reading a small stream must not fail: " + unexpected);
            }
        }

        assertEquals(before, preferences.snapshot());
        assertUnchanged(repository, before);
    }

    @Test
    public void previewingTheSameBackupRepeatedlyChangesNothingEachTime() throws Exception {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        Map<String, Object> before = preferences.snapshot();
        String payload = BackupCodec.write(sampleDocument());

        List<String> summaries = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            BackupCodec.Result result =
                    BackupCodec.read(stream(payload), BackupCodec.MAX_BYTES);
            assertTrue(result.problems().toString(), result.isOk());
            summaries.add(BackupPreview.of("x.json", result.document()).summary());
            assertEquals("round " + round + " must not write", before,
                    preferences.snapshot());
        }

        assertEquals("the same file must summarise the same way every time", 1,
                new java.util.LinkedHashSet<>(summaries).size());
        assertEquals(9, repository.getCourses().size());
        assertEquals(1, repository.getSemesters().size());
        assertEquals(3, repository.getBellSchedules().size());
    }

    @Test
    public void previewingDoesNotDisturbTheRepositorysOwnViewEither() throws Exception {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = seededRepository(preferences);
        List<String> coursesBefore = describeAll(repository.getCourses());
        String semestersBefore = repository.getSemesters().get(0).label();
        int schedulesBefore = repository.getBellSchedules().size();

        BackupCodec.Result result = BackupCodec.read(
                stream(BackupCodec.write(sampleDocument())), BackupCodec.MAX_BYTES);
        BackupPreview.of("x.json", result.document());

        assertEquals(coursesBefore, describeAll(repository.getCourses()));
        assertEquals(semestersBefore, repository.getSemesters().get(0).label());
        assertEquals(schedulesBefore, repository.getBellSchedules().size());
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    /**
     * A repository whose store has already been written once.
     *
     * <p>Reading is what seeds the sample timetable, so the store has to be read
     * before the fingerprint is taken -- otherwise the comparison would be
     * measuring the first read rather than the preview.</p>
     */
    private static CourseRepository seededRepository(FakePreferences preferences) {
        CourseRepository repository = repositoryFor(preferences);
        assertFalse(repository.getCourses().isEmpty());
        assertFalse(repository.getBellSchedules().isEmpty());
        assertFalse(repository.getSemesters().isEmpty());
        return repository;
    }

    private static void assertUnchanged(CourseRepository repository,
            Map<String, Object> before) {
        assertFalse(before.containsKey("restore_snapshot"));
        assertEquals(9, repository.getCourses().size());
        assertEquals(1, repository.getSemesters().size());
        assertEquals(3, repository.getBellSchedules().size());
    }

    private static List<String> describeAll(List<Course> courses) {
        List<String> described = new ArrayList<>();
        for (Course course : courses) {
            described.add(course.id + "|" + course.name + "|" + course.teacher + "|"
                    + course.location + "|" + course.dayOfWeek + "|"
                    + course.startPeriod + "|" + course.endPeriod + "|"
                    + course.startWeek + "|" + course.endWeek + "|" + course.parity
                    + "|" + course.color + "|" + course.scheduleName);
        }
        return described;
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static BackupPreview previewOf(BackupCodec.Document document) {
        return BackupPreview.of("weihe-backup.json", document);
    }

    private static BackupCodec.Document sampleDocument() {
        Map<Long, List<Course>> bySemester = new LinkedHashMap<>();
        bySemester.put(Long.valueOf(SEMESTER_ID),
                new ArrayList<>(Arrays.asList(course(1L, "\u9AD8\u7B49\u6570\u5B66"),
                        course(2L, "\u5927\u5B66\u82F1\u8BED"),
                        course(5L, "\u7A0B\u5E8F\u8BBE\u8BA1\u5B9E\u9A8C"))));
        bySemester.put(Long.valueOf(OTHER_SEMESTER_ID), new ArrayList<Course>());
        return BackupCodec.of(
                Arrays.asList(semester(SEMESTER_ID, "2026-08-31", SEMESTER_NAME),
                        semester(OTHER_SEMESTER_ID, "2027-02-22", "\u5927\u4E09\u4E0B")),
                bySemester, BellSchedules.presets(), SEMESTER_ID, STAMP, "1.0");
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

    /** A valid document built by hand, so the reader is tested on its own. */
    private static Map<String, Object> validRoot() {
        Map<String, Object> root = MiniJson.object();
        root.put("format", BackupCodec.FORMAT);
        root.put("formatVersion", Integer.valueOf(BackupCodec.FORMAT_VERSION));
        root.put("exportedAt", STAMP);
        root.put("activeSemesterId", Long.valueOf(SEMESTER_ID));
        List<Object> semesters = MiniJson.array();
        Map<String, Object> semesterEntry = MiniJson.object();
        semesterEntry.put("id", Long.valueOf(SEMESTER_ID));
        semesterEntry.put("start", "2026-08-31");
        semesterEntry.put("name", SEMESTER_NAME);
        List<Object> courses = MiniJson.array();
        courses.add(courseEntry(1L));
        semesterEntry.put("courses", courses);
        semesters.add(semesterEntry);
        root.put("semesters", semesters);
        List<Object> schedules = MiniJson.array();
        schedules.add(scheduleEntry("A\u697C\u4F5C\u606F"));
        root.put("schedules", schedules);
        return root;
    }

    private static Map<String, Object> courseEntry(long id) {
        Map<String, Object> item = MiniJson.object();
        item.put("id", Long.valueOf(id));
        item.put("name", "\u9AD8\u7B49\u6570\u5B66");
        item.put("day", Integer.valueOf(1));
        item.put("startPeriod", Integer.valueOf(1));
        item.put("endPeriod", Integer.valueOf(2));
        item.put("startWeek", Integer.valueOf(1));
        item.put("endWeek", Integer.valueOf(16));
        item.put("parity", Integer.valueOf(Course.PARITY_ALL));
        item.put("scheduleName", "");
        return item;
    }

    private static Map<String, Object> scheduleEntry(String name) {
        Map<String, Object> item = MiniJson.object();
        item.put("name", name);
        item.put("keywords", MiniJson.array());
        List<Object> starts = MiniJson.array();
        List<Object> ends = MiniJson.array();
        for (int period = 0; period < BellSchedule.PERIODS; period++) {
            starts.add(Integer.valueOf(480 + period * 45));
            ends.add(Integer.valueOf(520 + period * 45));
        }
        item.put("starts", starts);
        item.put("ends", ends);
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCourse(Map<String, Object> root) {
        List<Object> semesters = (List<Object>) root.get("semesters");
        Map<String, Object> semester = (Map<String, Object>) semesters.get(0);
        List<Object> courses = (List<Object>) semester.get("courses");
        return (Map<String, Object>) courses.get(0);
    }
}
