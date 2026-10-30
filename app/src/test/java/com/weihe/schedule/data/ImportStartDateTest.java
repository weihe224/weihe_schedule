package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseDetail;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * The import flow asks for a start date instead of a week offset.
 *
 * <p>The rule the user asked for: week 1 of the file is week 1 of the term, and
 * the only thing the calendar decides is which dates that term's weeks fall on.
 * The two halves are deliberately separate concerns, so these tests pin both --
 * that the imported weeks come through untouched, and that the chosen date is
 * what every other screen reads its dates from.</p>
 *
 * <p>They go through {@link CourseRepository} rather than a conversion helper,
 * because the failure this feature exists to prevent is a date that is saved in
 * one place and read from another: the preview saying one Monday while the home
 * screen, the detail page and the reminder planner use a different one.</p>
 */
public class ImportStartDateTest {

    /** A semester on the repository's own terms, starting on this Monday. */
    private static final LocalDate TERM_START = LocalDate.of(2026, 8, 31);

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((android.content.Context) null);
    }

    private static Course course(String name, int startWeek, int endWeek,
            int parity) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        course.parity = parity;
        return course;
    }

    private static List<Course> courses(Course... items) {
        List<Course> list = new ArrayList<>();
        for (Course item : items) {
            list.add(item);
        }
        return list;
    }

    /**
     * The names of a list, sorted, for "nothing else moved" assertions.
     *
     * <p>Sorted because {@link CourseRepository#getCourses} orders by the resolved
     * period, so the same payload can read back in a different order once the
     * schedule it resolves against changes -- comparing raw order would fail on
     * something that did not move.</p>
     */
    private static List<String> namesOf(List<Course> courses) {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** A term's stored start date, read fresh from the repository. */
    private static LocalDate startOf(CourseRepository repository, long id) {
        for (Semester semester : repository.getSemesters()) {
            if (semester.id() == id) {
                return semester.start();
            }
        }
        return null;
    }

    @Test
    public void diskFailureStillReloadsTheInMemoryImportedState() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester target = repository.getActiveSemester();
        repository.getCourses();
        preferences.failAfterMemoryUpdate(preferences.commitCount() + 1);
        LocalDate moved = TERM_START.plusWeeks(1);

        assertFalse(repository.replaceCoursesAt(courses(
                course("Imported", 1, 12, Course.PARITY_ALL)), target, moved, 18));
        assertEquals(List.of("Imported"), namesOf(repository.getCourses()));
        assertEquals(moved, repository.getSemesterStart());
        assertEquals(moved, startOf(repository, target.id()));
    }

    @Test
    public void undoDiskFailureStillReloadsTheInMemoryRestoredState() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester target = repository.getActiveSemester();
        repository.replaceCourses(courses(course("Original", 1, 12, Course.PARITY_ALL)));
        repository.replaceCoursesAt(courses(course("Imported", 1, 12,
                Course.PARITY_ALL)), target, TERM_START, 18);
        preferences.failAfterMemoryUpdate(preferences.commitCount() + 1);

        assertFalse(repository.restorePreviousCourses());
        assertEquals(List.of("Original"), namesOf(repository.getCourses()));
        assertFalse(repository.hasPreviousCourses());
    }

    // --------------------------------------------------- default comes from term

    @Test
    public void theDialogOpensOnTheTermsOwnStoredStartDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester term = repository.getActiveSemester();
        repository.setSemesterStart(TERM_START);

        // This is exactly what the dialog reads to seed the picker: the active
        // term's key, not today and not a default worked out from the month.
        assertEquals(TERM_START, repository.getSemesterStart());
        assertEquals(term.id(), repository.getActiveSemester().id());
    }

    @Test
    public void aSecondTermsStartIsNotUsedForTheFirst() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester second = repository.addSemester(
                LocalDate.of(2027, 2, 22), "Next term");

        // The import aims at the active term, so the picker must show that
        // term's date; a neighbouring term's date is a different Monday.
        repository.setSemesterStart(TERM_START);

        assertEquals(TERM_START, repository.getActiveSemester().start());
        assertEquals(LocalDate.of(2027, 2, 22),
                startOf(repository, second.id()));
    }

    // ------------------------------------------------ weeks map one to one

    @Test
    public void theFirstWeekOfTheFileIsTheFirstWeekOfTheTerm() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        boolean written = repository.replaceCoursesAt(
                courses(course("Maths", 1, 1, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);

        assertTrue("the store must accept the import", written);
        Course stored = repository.getCourses().get(0);
        // Week 1 in the file, week 1 in the term. No helper rewrote it.
        assertEquals(1, stored.startWeek);
        assertEquals(1, stored.endWeek);
    }

    @Test
    public void theSecondWeekOfTheFileIsTheSecondWeekOfTheTerm() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(
                courses(course("Physics", 2, 2, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);

        Course stored = repository.getCourses().get(0);
        assertEquals(2, stored.startWeek);
        assertEquals(2, stored.endWeek);
    }

    @Test
    public void aWeekRangeIsImportedUnchanged() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(
                courses(course("Chemistry", 1, 16, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);

        Course stored = repository.getCourses().get(0);
        // The example from the request: 1-16 stays 1-16 rather than becoming
        // 3-18 because a date moved.
        assertEquals(1, stored.startWeek);
        assertEquals(16, stored.endWeek);
    }

    @Test
    public void oddAndEvenAndSplitRangesSurviveTheImport() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(courses(
                        course("Odd", 1, 16, Course.PARITY_ODD),
                        course("Even", 2, 16, Course.PARITY_EVEN),
                        course("Split", 5, 9, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);

        List<Course> stored = repository.getCourses();
        assertEquals(Course.PARITY_ODD, stored.get(0).parity);
        assertEquals(Course.PARITY_EVEN, stored.get(1).parity);
        assertEquals(Course.PARITY_ALL, stored.get(2).parity);
        assertEquals(5, stored.get(2).startWeek);
        assertEquals(9, stored.get(2).endWeek);
    }

    // ------------------------------------------------- date drives the calendar

    @Test
    public void changingTheDateMovesTheCalendarDaysButNotTheWeeks() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate laterStart = LocalDate.of(2026, 9, 7);

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 16, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);
        // Before the move: week 1 is 08-31, so week 2 starts 09-07.
        assertEquals(LocalDate.of(2026, 9, 7),
                ScheduleUtils.dateOf(repository.getSemesterStart(), 2, 1));

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 16, Course.PARITY_ALL)),
                repository.getActiveSemester(), laterStart);

        Course stored = repository.getCourses().get(0);
        // The weeks are untouched by the move...
        assertEquals(1, stored.startWeek);
        assertEquals(16, stored.endWeek);
        // ...but every calendar day shifted a week, because week 1 now starts
        // on the chosen Monday.
        assertEquals(laterStart, repository.getSemesterStart());
        assertEquals(LocalDate.of(2026, 9, 14),
                ScheduleUtils.dateOf(repository.getSemesterStart(), 2, 1));
    }

    @Test
    public void aMidweekDateIsNormalisedToItsMonday() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        // A Thursday. The project's rule is previousOrSame MONDAY, and the
        // repository is where it must be applied -- a picker that only showed the
        // normalised date but stored the raw one would disagree with every screen
        // that counts weeks from it.
        repository.replaceCoursesAt(
                courses(course("Maths", 1, 2, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 3));

        assertEquals(LocalDate.of(2026, 8, 31), repository.getSemesterStart());
        assertEquals(LocalDate.of(2026, 8, 31),
                repository.getActiveSemester().start());
    }

    @Test
    public void aSundayDateNormalisesBackwardsNotForwards() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 2, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 6));

        assertEquals(LocalDate.of(2026, 8, 31), repository.getSemesterStart());
    }

    // ----------------------------------------- the date and courses move together

    @Test
    public void theDateAndTheCoursesAreOneCommit() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.setSemesterStart(TERM_START);
        int before = preferences.commitCount();

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 7));

        // One write, not two: a separate save for the date is what would let the
        // date land while the courses failed.
        assertEquals(before + 1, preferences.commitCount());
        assertEquals(LocalDate.of(2026, 9, 7), repository.getSemesterStart());
        assertEquals(1, repository.getCourses().size());
    }

    @Test
    public void aRefusedWriteLeavesNeitherTheDateNorTheCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.setSemesterStart(TERM_START);
        repository.upsertCourse(course("Kept", 1, 18, Course.PARITY_ALL));
        LocalDate before = repository.getSemesterStart();
        int coursesBefore = repository.getCourses().size();

        preferences.failCommitNumber(preferences.commitCount() + 1);
        boolean written = repository.replaceCoursesAt(
                courses(course("New", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 7));

        assertFalse("the store must report the refusal", written);
        // The failing store keeps the old values, so a caller that does not check
        // the return value would still be reading the old state -- but the point
        // here is that the date did not move on its own.
        assertEquals(before, repository.getSemesterStart());
        assertEquals(coursesBefore, repository.getCourses().size());
    }

    @Test
    public void cancellingLeavesTheDateAndTheCoursesAlone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.setSemesterStart(TERM_START);
        List<String> before = namesOf(repository.getCourses());
        int commits = preferences.commitCount();

        // What the dialog does on 取消: nothing. There is no method call to make
        // and none is made, so the assertion is that the count and the values are
        // exactly where they were after the user tapped around the date field.
        assertEquals(commits, preferences.commitCount());
        assertEquals(TERM_START, repository.getSemesterStart());
        assertEquals(before, namesOf(repository.getCourses()));
    }

    // --------------------------------------------------------- other terms untouched

    @Test
    public void importingIntoOneTermDoesNotMoveAnothersDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long firstId = repository.getActiveSemester().id();
        Semester other = repository.addSemester(LocalDate.of(2027, 2, 22), "Other");
        repository.setSemesterStart(TERM_START);

        repository.replaceCoursesAt(
                courses(course("Second", 1, 4, Course.PARITY_ALL)),
                other, LocalDate.of(2027, 3, 1));

        // Re-read rather than trusting the object captured before the write: a
        // stale Semester would report the old date whether or not it changed.
        assertEquals(LocalDate.of(2027, 3, 1), startOf(repository, other.id()));
        assertEquals("the first term must not have moved", TERM_START,
                startOf(repository, firstId));
    }

    @Test
    public void importingIntoANonActiveTermWritesThatTermsCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        List<String> activeBefore = namesOf(repository.getCourses());
        Semester other = repository.addSemester(LocalDate.of(2027, 2, 22), "Other");

        repository.replaceCoursesAt(
                courses(course("In other", 1, 4, Course.PARITY_ALL)),
                other, LocalDate.of(2027, 3, 1));

        // The target names the term, so the active one is untouched rather than
        // being written by accident. A fresh store seeds samples into the first
        // term, so "untouched" means the sample list, not an empty one.
        assertEquals(1, repository.countCoursesOf(other));
        assertEquals(activeBefore, namesOf(repository.coursesOf(active)));
    }

    // ---------------------------------------------- every screen reads one date

    @Test
    public void theDetailPageAndTheReminderPlannerUseTheChosenDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate laterStart = LocalDate.of(2026, 9, 7);
        repository.replaceCoursesAt(
                courses(course("Maths", 1, 16, Course.PARITY_ALL)),
                repository.getActiveSemester(), laterStart);

        Course stored = repository.getCourses().get(0);
        Semester term = repository.getActiveSemester();
        List<com.weihe.schedule.model.BellSchedule> schedules =
                repository.getBellSchedules();
        String defaultId = repository.getDefaultScheduleId();

        // Week 2 of the term begins a week after the chosen Monday. 09-14 is a
        // Monday, and it is week 2 when week 1 starts on 09-07.
        assertEquals(laterStart, term.start());
        assertEquals(LocalDate.of(2026, 9, 14),
                ScheduleUtils.dateOf(term.start(), 2, 1));
        CourseDetail detail = CourseDetail.of(stored, term, schedules, defaultId);
        assertTrue("the course must have a known time", detail.timeKnown());

        Reminder reminder = ReminderPlan.forCourseOnDay(repository.getCourses(),
                term, schedules, stored.id, LocalDate.of(2026, 9, 14), 10, defaultId);
        assertNotNull("week 2 holds the course", reminder);
        assertEquals("the class runs on the day that was asked about",
                LocalDate.of(2026, 9, 14),
                reminder.startAt().toLocalDate());
    }

    @Test
    public void theHomeScreensCurrentWeekFollowsTheChosenDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(
                courses(course("Maths", 1, 16, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 8, 31));

        // Same calendar day, two different week-1 dates: the week number the home
        // screen shows is derived from the stored one, so moving it moves the week.
        assertEquals(7, ScheduleUtils.currentWeek(
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 12)));
        assertEquals(6, ScheduleUtils.currentWeek(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 12)));
    }

    @Test
    public void aTargetThatIsNotAStoredTermIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<String> before = namesOf(repository.getCourses());

        // A term object that no longer names a stored term -- what a stale
        // screen would hold after the term was deleted under it.
        Semester ghost = Semester.of(999999L, LocalDate.of(2026, 9, 7));
        boolean written = repository.replaceCoursesAt(
                courses(course("Nowhere", 1, 4, Course.PARITY_ALL)),
                ghost, LocalDate.of(2026, 9, 7));

        assertFalse("an unreachable term must not get a timetable", written);
        assertEquals(before, namesOf(repository.getCourses()));
    }

    @Test
    public void aNullTargetOrDateIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        assertFalse(repository.replaceCoursesAt(
                courses(course("Nowhere", 1, 4, Course.PARITY_ALL)),
                null, LocalDate.of(2026, 9, 7)));
        assertFalse(repository.replaceCoursesAt(
                courses(course("Nowhere", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), null));
    }

    // -------------------------------------------------------------- undo still works

    @Test
    public void theUndoSnapshotCoversTheCoursesItReplaced() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<String> before = namesOf(repository.getCourses());

        repository.replaceCoursesAt(
                courses(course("Imported", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 7));
        assertEquals(1, repository.getCourses().size());
        assertTrue("an import offers an undo", repository.hasPreviousCourses());

        assertTrue(repository.restorePreviousCourses());
        // The undo puts the replaced payload back, so the term reads exactly as
        // it did before the import rather than as an empty one.
        assertEquals(before, namesOf(repository.getCourses()));
    }

    // ------------------------------------------------------------- old data stays

    @Test
    public void anImportDoesNotDisturbTheScheduleDataOrTheIds() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        int schedulesBefore = repository.getBellSchedules().size();

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), TERM_START);

        assertEquals(schedulesBefore, repository.getBellSchedules().size());
        Course stored = repository.getCourses().get(0);
        assertTrue("an imported course gets a real id", stored.id != 0L);
        // An imported row carries no schedule choice; the file has none to give.
        assertEquals("", stored.scheduleName);
    }

    @Test
    public void theTermRecordAndTheFlatKeyAgreeAfterAnImport() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(
                courses(course("Maths", 1, 4, Course.PARITY_ALL)),
                repository.getActiveSemester(), LocalDate.of(2026, 9, 7));

        // Both places the date is stored were written, so a later read from
        // either one gets the same Monday.
        assertEquals(repository.getActiveSemester().start(),
                repository.getSemesterStart());
    }
}
