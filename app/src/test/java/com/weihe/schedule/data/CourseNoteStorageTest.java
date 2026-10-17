package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * Course notes through the store and through a backup.
 *
 * <p>{@code CourseNotesTest} covers the resolution rule. What is checked here is
 * what actually reaches storage: that one save writes the note to every
 * arrangement of the course and to no other, that it survives a reload and a
 * restart, that editing anything else leaves it alone, and that a backup carries
 * it back.</p>
 *
 * <p>Every course here is given a name of its own, because a name is what makes two
 * records the same course -- a shared name would silently join records a test meant
 * to keep apart.</p>
 */
public class CourseNoteStorageTest {

    private static final String COURSE = "ZZ \u5907\u6CE8\u8BFE";
    private static final String OTHER = "ZZ \u53E6\u4E00\u95E8";

    // ================================================= one note, many arrangements

    @Test
    public void aNoteSavedFromOneArrangementIsReadFromTheOther() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        Course monday = add(repository, COURSE, 1, 1);
        Course thursday = add(repository, COURSE, 4, 5);

        repository.saveCourse(repository.findCourse(monday.id),
                "\u5E26\u8BA1\u7B97\u5668");
        repository.reload();

        assertEquals("the second arrangement reads what the first wrote",
                "\u5E26\u8BA1\u7B97\u5668",
                repository.noteOf(repository.findCourse(thursday.id)));
        assertEquals("and both records hold it", "\u5E26\u8BA1\u7B97\u5668",
                repository.findCourse(monday.id).note);
        assertEquals("\u5E26\u8BA1\u7B97\u5668",
                repository.findCourse(thursday.id).note);
    }

    @Test
    public void savingFromTheSecondArrangementUpdatesTheFirst() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, COURSE, 1, 1);
        Course thursday = add(repository, COURSE, 4, 5);
        repository.saveCourse(repository.findCourse(monday.id), "\u7B2C\u4E00\u7248");

        repository.saveCourse(repository.findCourse(thursday.id), "\u7B2C\u4E8C\u7248");
        repository.reload();

        assertEquals("\u7B2C\u4E8C\u7248", repository.findCourse(monday.id).note);
        assertEquals("\u7B2C\u4E8C\u7248", repository.findCourse(thursday.id).note);
    }

    @Test
    public void clearingTheNoteFromOneArrangementClearsTheCourse() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, COURSE, 1, 1);
        Course thursday = add(repository, COURSE, 4, 5);
        repository.saveCourse(repository.findCourse(monday.id), "\u8981\u6E05\u6389");

        repository.saveCourse(repository.findCourse(thursday.id), "");
        repository.reload();

        assertEquals("", repository.findCourse(monday.id).note);
        assertEquals("", repository.findCourse(thursday.id).note);
        assertEquals("", repository.noteOf(repository.findCourse(monday.id)));
    }

    @Test
    public void aNewArrangementJoinsTheNoteItNeverSaw() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(monday.id), "\u5DF2\u6709\u7684\u5907\u6CE8");

        // A third arrangement, created the way the editor creates one: the note
        // field was empty because the course's note was written on another record.
        Course wednesday = new Course();
        wednesday.name = COURSE;
        wednesday.dayOfWeek = 3;
        wednesday.startPeriod = 2;
        wednesday.endPeriod = 3;
        repository.saveCourse(wednesday, "");
        repository.reload();

        assertEquals("the new arrangement did not erase the note",
                "\u5DF2\u6709\u7684\u5907\u6CE8", repository.findCourse(monday.id).note);
        assertEquals("and joined it", "\u5DF2\u6709\u7684\u5907\u6CE8",
                repository.findCourse(wednesday.id).note);
    }

    // ================================================================ isolation

    @Test
    public void anotherCourseKeepsItsOwnNote() {
        CourseRepository repository = repository(new FakePreferences());
        Course first = add(repository, COURSE, 1, 1);
        Course second = add(repository, OTHER, 2, 1);

        repository.saveCourse(repository.findCourse(first.id), "\u7B2C\u4E00\u95E8");
        repository.saveCourse(repository.findCourse(second.id), "\u7B2C\u4E8C\u95E8");
        repository.reload();

        assertEquals("\u7B2C\u4E00\u95E8", repository.findCourse(first.id).note);
        assertEquals("\u7B2C\u4E8C\u95E8", repository.findCourse(second.id).note);
    }

    @Test
    public void theSameNameInAnotherTermIsAnotherCourse() {
        CourseRepository repository = repository(new FakePreferences());
        Course inFirstTerm = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(inFirstTerm.id), "\u4E0A\u5B66\u671F");

        Semester first = repository.getActiveSemester();
        // Adding a term does not switch to it, so the second course has to be
        // written after making that term the active one.
        Semester second = repository.addSemester(LocalDate.of(2027, 2, 22));
        repository.setActiveSemester(second);
        Course inSecondTerm = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(inSecondTerm.id), "\u4E0B\u5B66\u671F");

        assertEquals("\u4E0B\u5B66\u671F", repository.findCourse(inSecondTerm.id).note);
        repository.setActiveSemester(first);
        repository.reload();
        assertEquals("the other term's note was not touched", "\u4E0A\u5B66\u671F",
                repository.findCourse(inFirstTerm.id).note);
    }

    // ============================================================== persistence

    @Test
    public void theNoteSurvivesARestart() {
        FakePreferences store = new FakePreferences();
        CourseRepository first = repository(store);
        Course course = add(first, COURSE, 1, 1);
        String note = "\u4F5C\u4E1A\uFF1A\u7B2C3\u7AE0\n"
                + "\u8003\u8BD5\uFF1A11\u6708 20 \u65E5\n"
                + "deadline: 2026-12-01 (no late work!)";
        first.saveCourse(first.findCourse(course.id), note);

        // A second repository over the same store is what a restart looks like from
        // here: nothing is shared between the two but the bytes.
        CourseRepository restarted = repository(store);

        assertEquals(note, restarted.findCourse(course.id).note);
    }

    @Test
    public void chineseNewlinesAndPunctuationComeBackExactly() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        Course course = add(repository, COURSE, 1, 1);
        String note = "\u5B66\u671F\u4EFB\u52A1\uFF1A\n"
                + "1. \u9884\u4E60\uFF0C\u5E26\u4E66\u3002\n"
                + "2. \u5C0F\u7EC4\u4F5C\u4E1A\uFF08\u4E09\u4EBA\uFF09\u2014\u2014"
                + "\u4E0B\u5468\u4E94\u4EA4\n"
                + "\u7279\u6B8A\u5B57\u7B26\uFF1A\u201C\u5F15\u53F7\u201D\u3001\u3010"
                + "\u62EC\u53F7\u3011 & < > \\ \" '";
        repository.saveCourse(repository.findCourse(course.id), note);

        assertEquals(note, repository(store).findCourse(course.id).note);
    }

    // ========================================================= arrangements change

    @Test
    public void movingTheLessonKeepsTheNote() {
        CourseRepository repository = repository(new FakePreferences());
        Course course = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(course.id), "\u4E0D\u80FD\u4E22");

        Course edited = repository.findCourse(course.id);
        edited.dayOfWeek = 5;
        edited.startPeriod = 8;
        edited.endPeriod = 9;
        edited.location = "\u6559\u5B66\u697C B203";
        edited.teacher = "\u674E\u8001\u5E08";
        edited.startWeek = 3;
        edited.endWeek = 12;
        edited.parity = Course.PARITY_ODD;
        // What the editor does: the field was pre-filled with the course's note, so
        // it hands the same text back.
        repository.saveCourse(edited, repository.noteOf(edited));
        repository.reload();

        Course stored = repository.findCourse(course.id);
        assertEquals("\u4E0D\u80FD\u4E22", stored.note);
        assertEquals("and the edit itself landed", 5, stored.dayOfWeek);
        assertEquals("\u6559\u5B66\u697C B203", stored.location);
        assertEquals(Course.PARITY_ODD, stored.parity);
    }

    @Test
    public void deletingOneArrangementKeepsTheNoteOnTheRest() {
        CourseRepository repository = repository(new FakePreferences());
        Course monday = add(repository, COURSE, 1, 1);
        Course thursday = add(repository, COURSE, 4, 5);
        repository.saveCourse(repository.findCourse(monday.id), "\u4FDD\u7559\u7740");

        repository.deleteCourse(monday.id);
        repository.reload();

        assertEquals("the surviving arrangement still has it", "\u4FDD\u7559\u7740",
                repository.findCourse(thursday.id).note);
    }

    @Test
    public void deletingTheLastArrangementTakesTheNoteWithIt() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        Course only = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(only.id), "\u968F\u8BFE\u7A0B\u4E00\u8D77\u6D88\u5931");

        repository.deleteCourse(only.id);
        repository.reload();

        assertTrue("nothing of the course is left", repository.getCourses().isEmpty());
        // Nothing is left over in the payload either: the note lived on the record,
        // so removing the record removes it.
        assertEquals("[]", store.getString("courses:" + repository.getActiveSemester().id(), ""));
    }

    // =============================================================== compatibility

    @Test
    public void aCourseStoredBeforeNotesExistedReadsAsHavingNone() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        long semesterId = repository.getActiveSemester().id();
        store.edit().putString("courses:" + semesterId,
                "[{\"id\":7,\"name\":\"\u65E7\u8BFE\",\"teacher\":\"\u5F20\u8001\u5E08\","
                        + "\"location\":\"A101\",\"day\":2,\"startPeriod\":1,"
                        + "\"endPeriod\":2,\"startWeek\":1,\"endWeek\":16,"
                        + "\"parity\":0,\"color\":2655366325,\"scheduleName\":\"\"}]")
                .apply();

        Course loaded = repository(store).findCourse(7L);

        assertNotNull(loaded);
        assertEquals("", loaded.note);
        assertEquals("\u65E7\u8BFE", loaded.name);
        assertEquals("\u5F20\u8001\u5E08", loaded.teacher);
    }

    @Test
    public void aCourseWithoutANoteStoresExactlyWhatItStoredBefore() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        add(repository, COURSE, 1, 1);

        String raw = store.getString("courses:" + repository.getActiveSemester().id(), "");

        assertTrue("no empty note key is written", !raw.contains("\"note\""));
    }

    @Test
    public void aBackupCarriesTheNoteBack() {
        FakePreferences store = new FakePreferences();
        CourseRepository repository = repository(store);
        Course monday = add(repository, COURSE, 1, 1);
        add(repository, COURSE, 4, 5);
        String note = "\u5E26\u8D70\u7684\u5907\u6CE8\n\u7B2C\u4E8C\u884C";
        repository.saveCourse(repository.findCourse(monday.id), note);

        String json = BackupCodec.write(documentOf(repository));

        // Restore into a store that knows nothing about these courses.
        CourseRepository target = repository(new FakePreferences());
        RestoreReport report = target.restoreFrom(BackupCodec.parse(json).document());
        assertTrue(report.toString(), report.isRestored());
        target.reload();

        List<Course> restored = new ArrayList<>();
        for (Course course : target.getCourses()) {
            if (COURSE.equals(course.name)) {
                restored.add(course);
            }
        }
        assertEquals("both arrangements came back", 2, restored.size());
        for (Course course : restored) {
            assertEquals("and each carries the note", note, course.note);
        }
        assertEquals("so either of them reads it", note,
                target.noteOf(restored.get(0)));
    }

    @Test
    public void aBackupTakenBeforeNotesExistedStillRestores() {
        CourseRepository repository = repository(new FakePreferences());
        Course course = add(repository, COURSE, 1, 1);
        repository.saveCourse(repository.findCourse(course.id), "\u4E0D\u8BE5\u51FA\u73B0");

        // A real backup with every note key stripped out: exactly the file an older
        // build produced, rather than a hand-written guess at the schema.
        String older = BackupCodec.write(documentOf(repository))
                .replaceAll(",\"note\":\"[^\"]*\"", "");
        assertTrue("the fixture really has no note keys", !older.contains("\"note\""));

        CourseRepository target = repository(new FakePreferences());
        RestoreReport report = target.restoreFrom(BackupCodec.parse(older).document());

        assertTrue("an old backup is not refused", report.isRestored());
        target.reload();
        Course restored = null;
        for (Course candidate : target.getCourses()) {
            if (COURSE.equals(candidate.name)) {
                restored = candidate;
            }
        }
        assertNotNull(restored);
        assertEquals("", restored.note);
        assertEquals(1, restored.dayOfWeek);
    }

    // ------------------------------------------------------------------- helpers

    private static CourseRepository repository(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static Course add(CourseRepository repository, String name,
            int day, int startPeriod) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = day;
        course.startPeriod = startPeriod;
        course.endPeriod = startPeriod + 1;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        repository.upsertCourse(course);
        return course;
    }

    /** The active term and its courses, in the shape a backup carries. */
    private static BackupCodec.Document documentOf(CourseRepository repository) {
        Semester semester = repository.getActiveSemester();
        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        coursesBySemester.put(Long.valueOf(semester.id()),
                repository.getCourses());
        return BackupCodec.of(List.of(semester), coursesBySemester,
                BellSchedules.presets(), semester.id(),
                "2026-10-17T12:00:00+08:00", "1.0");
    }
}
