package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.CourseDetail;
import com.weihe.schedule.rule.ReminderPlan;
import com.weihe.schedule.rule.SemesterWeeks;

/**
 * The term length is per term, and the import can set it.
 *
 * <p>Two things are being pinned here. First, that the length lives on the term
 * rather than being a constant every screen reads -- so one term can run sixteen
 * weeks while another runs eighteen, and shortening a term re-derives its last
 * day instead of leaving a range that disagrees with its own week count. Second,
 * that the import writes it through the same commit as the courses, so there is
 * no state in which the term claims a length the timetable does not match.</p>
 *
 * <p>They go through {@link CourseRepository} for the same reason the start-date
 * tests do: the failure worth guarding against is a value saved in one place and
 * read from another.</p>
 */
public class SemesterWeeksTest {

    /** A semester on the repository's own terms, starting on this Monday. */
    private static final LocalDate TERM_START = LocalDate.of(2026, 8, 31);

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((android.content.Context) null);
    }

    private static Course course(String name, int startWeek, int endWeek) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    private static List<Course> courses(Course... items) {
        List<Course> list = new ArrayList<>();
        for (Course item : items) {
            list.add(item);
        }
        return list;
    }

    private static List<String> namesOf(List<Course> courses) {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            names.add(course.name);
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** A term read fresh from the repository, so a stale object cannot mask a write. */
    private static Semester termOf(CourseRepository repository, long id) {
        for (Semester semester : repository.getSemesters()) {
            if (semester.id() == id) {
                return semester;
            }
        }
        return null;
    }

    private static int weeksOf(CourseRepository repository, long id) {
        Semester term = termOf(repository, id);
        return term == null ? -1 : term.weeks();
    }

    // ------------------------------------------------- default comes from the term

    @Test
    public void theDialogOpensOnTheTermsOwnStoredWeekCount() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        // Seed a non-default length, then re-open the store the way a dialog would.
        repository.getActiveSemester();
        long id = repository.getActiveSemester().id();
        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 16);

        // Exactly what the stepper reads to seed itself.
        assertEquals(16, SemesterWeeks.of(repository.getActiveSemester()));
        assertEquals(16, weeksOf(repository, id));
    }

    @Test
    public void aTermWithNoStoredCountFallsBackToTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        // A fresh store has never had a length chosen for it, so the count is the
        // default rather than something invented from today's date.
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                SemesterWeeks.of(repository.getActiveSemester()));
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                repository.getActiveSemester().weeks());
    }

    @Test
    public void twoTermsKeepTheirOwnWeekCounts() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long firstId = repository.getActiveSemester().id();
        Semester second = repository.addSemester(LocalDate.of(2027, 2, 22), "Next");

        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 16);
        repository.replaceCoursesAt(courses(course("Physics", 1, 18)),
                second, LocalDate.of(2027, 2, 22), 17);

        // Each term carries its own number; neither borrowed the other's.
        assertEquals(16, weeksOf(repository, firstId));
        assertEquals(17, weeksOf(repository, second.id()));
    }

    // ------------------------------------------------------- the range is derived

    @Test
    public void theLastDayFollowsTheWeekCount() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long id = repository.getActiveSemester().id();

        repository.replaceCoursesAt(courses(course("Maths", 1, 16)),
                repository.getActiveSemester(), TERM_START, 16);
        // Sixteen whole weeks from the Monday of week 1, minus the day the term
        // stops just before.
        assertEquals(TERM_START.plusWeeks(16).minusDays(1),
                termOf(repository, id).lastDay());

        repository.replaceCoursesAt(courses(course("Maths", 1, 18)),
                repository.getActiveSemester(), TERM_START, 18);
        assertEquals(TERM_START.plusWeeks(18).minusDays(1),
                termOf(repository, id).lastDay());
    }

    @Test
    public void theDerivedLastDayAgreesWithTheSharedArithmetic() {
        // The dialog previews a range before anything is written, so its helper and
        // the stored term must not be two implementations that can drift.
        for (int weeks = Semester.MIN_WEEKS; weeks <= Semester.MAX_WEEKS; weeks++) {
            assertEquals("weeks=" + weeks,
                    Semester.of(1L, TERM_START).withWeeks(weeks).lastDay(),
                    SemesterWeeks.lastDay(TERM_START, weeks));
        }
    }

    @Test
    public void aOneWeekTermEndsSixDaysAfterItStarts() {
        assertEquals(TERM_START.plusDays(6),
                SemesterWeeks.lastDay(TERM_START, Semester.MIN_WEEKS));
    }

    // ------------------------------------------------- counts are range-checked

    @Test
    public void weekCountsOutsideTheAllowedRangeAreRejected() {
        assertFalse(SemesterWeeks.isValid(0));
        assertFalse(SemesterWeeks.isValid(-1));
        assertFalse(SemesterWeeks.isValid(Semester.MAX_WEEKS + 1));
        assertTrue(SemesterWeeks.isValid(Semester.MIN_WEEKS));
        assertTrue(SemesterWeeks.isValid(Semester.MAX_WEEKS));
        assertTrue(SemesterWeeks.isValid(18));
    }

    @Test
    public void aStoredCountOutsideTheRangeIsRepairedRatherThanKept() {
        // A hand-edited or damaged payload must not produce a term no screen can
        // render; it reads back as the default.
        List<Semester> parsed = SemesterJson.parse(
                "[{\"id\":7,\"start\":\"2026-08-31\",\"name\":\"\",\"weeks\":900}]");
        assertEquals(1, parsed.size());
        assertEquals(ScheduleUtils.SEMESTER_WEEKS, parsed.get(0).weeks());

        List<Semester> zero = SemesterJson.parse(
                "[{\"id\":7,\"start\":\"2026-08-31\",\"name\":\"\",\"weeks\":0}]");
        assertEquals(ScheduleUtils.SEMESTER_WEEKS, zero.get(0).weeks());
    }

    @Test
    public void aTermObjectClampsACountItWasHanded() {
        // The constructor is the last line of defence: even a caller that skips the
        // rule class cannot store a term outside the renderable range.
        assertEquals(Semester.MIN_WEEKS,
                Semester.of(1L, TERM_START).withWeeks(0).weeks());
        assertEquals(Semester.MAX_WEEKS,
                Semester.of(1L, TERM_START).withWeeks(999).weeks());
    }

    // --------------------------------------------- courses are never rewritten

    @Test
    public void theWeekCountNeverRewritesACoursesWeeks() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        // A term of 16 weeks holding a course that runs to 18: both facts are
        // kept. Whether that pairing is allowed is the screen's question, not a
        // reason for the store to edit the course.
        repository.replaceCoursesAt(
                courses(course("Maths", 1, 18)), repository.getActiveSemester(),
                TERM_START, 16);

        Course stored = repository.getCourses().get(0);
        assertEquals(1, stored.startWeek);
        assertEquals(18, stored.endWeek);
        assertEquals(16, repository.getActiveSemester().weeks());
    }

    @Test
    public void shorteningATermIsReportedAsStrandingCourses() {
        List<Course> existing = courses(
                course("Early", 1, 10), course("Late", 1, 18));

        assertEquals(1, SemesterWeeks.countBeyond(existing, 16));
        assertTrue(SemesterWeeks.wouldStrandCourses(existing, 16));
        // A count that still covers everything strands nothing.
        assertFalse(SemesterWeeks.wouldStrandCourses(existing, 18));
        assertEquals(0, SemesterWeeks.countBeyond(existing, 18));
    }

    @Test
    public void anEmptyTermStrandsNothingAndReportsNoMaxWeek() {
        assertEquals(0, SemesterWeeks.maxWeekOf(new ArrayList<>()));
        assertEquals(0, SemesterWeeks.maxWeekOf(null));
        assertFalse(SemesterWeeks.wouldStrandCourses(new ArrayList<>(), 1));
    }

    @Test
    public void theMaxWeekIsTheFurthestAnyCourseReaches() {
        assertEquals(20, SemesterWeeks.maxWeekOf(
                courses(course("A", 1, 20), course("B", 1, 5))));
    }

    // ------------------------------------------------------ the store round-trip

    @Test
    public void aNonDefaultCountSurvivesARestart() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 16);
        long id = repository.getActiveSemester().id();

        // A second repository over the same payload is the app restarting.
        CourseRepository reopened = repositoryFor(preferences);
        assertEquals(16, weeksOf(reopened, id));
    }

    @Test
    public void theCountIsWrittenOnlyWhenItSaysSomethingNew() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 16);

        String stored = preferences.getString("semesters", "");
        assertTrue("a non-default count must be stored", stored.contains("weeks"));

        // A term left at the default keeps the shape an older build wrote, so the
        // payload an old version reads is the one it always saw.
        FakePreferences plain = new FakePreferences();
        CourseRepository defaulted = repositoryFor(plain);
        defaulted.replaceCoursesAt(courses(course("Maths", 1, 10)),
                defaulted.getActiveSemester(), TERM_START,
                ScheduleUtils.SEMESTER_WEEKS);
        assertFalse(plain.getString("semesters", "").contains("weeks"));
    }

    @Test
    public void anOlderPayloadWithoutTheKeyStillReads() {
        List<Semester> parsed = SemesterJson.parse(
                "[{\"id\":7,\"start\":\"2026-08-31\",\"name\":\"\"}]");
        assertEquals(1, parsed.size());
        assertEquals(ScheduleUtils.SEMESTER_WEEKS, parsed.get(0).weeks());
    }

    @Test
    public void theCountAndTheCoursesAreOneCommit() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        int before = preferences.commitCount();

        boolean written = repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 16);

        assertTrue(written);
        // One commit carries the courses, the date and the length together.
        assertEquals(before + 1, preferences.commitCount());
    }

    @Test
    public void aRefusedWriteSavesNeitherTheCountNorTheCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 18);
        int weeksBefore = repository.getActiveSemester().weeks();
        List<String> namesBefore = namesOf(repository.getCourses());

        preferences.failCommitNumber(preferences.commitCount() + 1);
        boolean written = repository.replaceCoursesAt(courses(course("Physics", 1, 8)),
                repository.getActiveSemester(), TERM_START, 12);

        assertFalse("the store must refuse the write", written);
        assertEquals("the length must not have moved", weeksBefore,
                repository.getActiveSemester().weeks());
        assertEquals(namesBefore, namesOf(repository.getCourses()));
    }

    @Test
    public void cancellingLeavesTheCountAndTheCoursesAlone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 17);
        int commits = preferences.commitCount();
        int weeks = repository.getActiveSemester().weeks();

        // Cancelling writes nothing at all: the dialog's own state is the only
        // place a cancelled choice ever lived.
        assertEquals(weeks, repository.getActiveSemester().weeks());
        assertEquals(commits, preferences.commitCount());
    }

    @Test
    public void importingIntoOneTermDoesNotMoveAnothersCount() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        long firstId = repository.getActiveSemester().id();
        Semester second = repository.addSemester(LocalDate.of(2027, 2, 22), "Next");
        repository.replaceCoursesAt(courses(course("Other", 1, 12)),
                second, LocalDate.of(2027, 2, 22), 16);

        repository.replaceCoursesAt(courses(course("Maths", 1, 18)),
                repository.getActiveSemester(), TERM_START, 18);

        assertEquals(18, weeksOf(repository, firstId));
        assertEquals("the other term keeps its own length", 16,
                weeksOf(repository, second.id()));
    }

    @Test
    public void importingIntoANonActiveTermWritesThatTermsCount() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();
        Semester other = repository.addSemester(LocalDate.of(2027, 2, 22), "Next");
        int activeWeeks = repository.getActiveSemester().weeks();

        boolean written = repository.replaceCoursesAt(
                courses(course("Other", 1, 14)), other,
                LocalDate.of(2027, 2, 22), 14);

        assertTrue(written);
        assertEquals(14, weeksOf(repository, other.id()));
        assertEquals("the active term must be untouched", activeWeeks,
                weeksOf(repository, active.id()));
        assertNotEquals(active.id(), other.id());
    }

    @Test
    public void aTargetThatIsNotAStoredTermIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<String> namesBefore = namesOf(repository.getCourses());
        int weeksBefore = repository.getActiveSemester().weeks();

        boolean written = repository.replaceCoursesAt(courses(course("Ghost", 1, 5)),
                Semester.of(999999L, TERM_START), TERM_START, 12);

        assertFalse(written);
        assertEquals(namesBefore, namesOf(repository.getCourses()));
        assertEquals(weeksBefore, repository.getActiveSemester().weeks());
    }

    @Test
    public void aNullTargetOrDateIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        assertFalse(repository.replaceCoursesAt(courses(course("Maths", 1, 5)),
                null, TERM_START, 16));
        assertFalse(repository.replaceCoursesAt(courses(course("Maths", 1, 5)),
                repository.getActiveSemester(), null, 16));
    }

    @Test
    public void aCountHandedToTheImportIsClampedNotStored() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(courses(course("Maths", 1, 5)),
                repository.getActiveSemester(), TERM_START, 999);

        assertEquals(Semester.MAX_WEEKS, repository.getActiveSemester().weeks());
    }

    // --------------------------------------------------------------- no regression

    @Test
    public void weekOneStillMeansWeekOneAfterALengthChange() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.replaceCoursesAt(courses(course("Maths", 1, 16)),
                repository.getActiveSemester(), TERM_START, 16);

        Course stored = repository.getCourses().get(0);
        // A length setting is not a week offset: 1-16 stays 1-16.
        assertEquals(1, stored.startWeek);
        assertEquals(16, stored.endWeek);
        assertEquals(TERM_START, ScheduleUtils.weekStart(
                repository.getSemesterStart(), 1));
    }

    @Test
    public void theDetailPageAndTheReminderPlannerStillAgreeAfterALengthChange() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.replaceCoursesAt(courses(course("Maths", 1, 16)),
                repository.getActiveSemester(), TERM_START, 16);
        Semester term = repository.getActiveSemester();
        List<Course> stored = repository.getCourses();
        String defaultId = repository.getDefaultScheduleId();

        Course first = stored.get(0);
        assertTrue(CourseDetail.of(first, term, repository.getBellSchedules(),
                defaultId).timeKnown());

        // Week 2 of an 08-31 term is 09-07; the reminder must land there.
        var reminder = ReminderPlan.forCourseOnDay(stored, term,
                repository.getBellSchedules(), first.id, LocalDate.of(2026, 9, 7),
                10, defaultId);
        assertEquals(LocalDate.of(2026, 9, 7), reminder.startAt().toLocalDate());
    }

    @Test
    public void theStoredStartDateIsUnchangedByALengthChange() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        repository.setSemesterStart(TERM_START);

        repository.replaceCoursesAt(courses(course("Maths", 1, 10)),
                repository.getActiveSemester(), TERM_START, 14);

        // The date and the length are separate answers: setting one must not move
        // the other.
        assertEquals(TERM_START, repository.getSemesterStart());
        assertEquals(14, repository.getActiveSemester().weeks());
        assertNull(termOf(repository, 999999L));
    }
}
