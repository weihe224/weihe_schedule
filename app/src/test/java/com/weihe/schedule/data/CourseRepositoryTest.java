package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;

/**
 * Tests for the storage layer itself.
 *
 * <p>This used to be the one untested corner of the project: the repository
 * needs a {@link Context} and a {@link SharedPreferences}. {@code Context} is a
 * class, not an interface, so it cannot be proxied and its methods are stubs on
 * the JVM; instead the repository is allocated without running its constructor
 * and handed a {@link FakePreferences} through the field the constructor would
 * have set. No Android method is ever invoked, so the suite still runs on a bare
 * JVM.</p>
 *
 * <p>What is being protected: the JSON round trip, the in-memory snapshot, the
 * backup of an unreadable payload, id assignment, schedule resolution per
 * course, and the rename/clear cascades that keep a course's schedule name
 * pointing at something real.</p>
 */
public class CourseRepositoryTest {

    private static final String KEY_COURSES = "courses";
    private static final String KEY_BACKUP = "courses_backup";
    private static final String KEY_SCHEDULES = "bell_schedules";
    private static final String KEY_START = "semester_start";
    private static final String KEY_SEMESTERS = "semesters";

    /**
     * A repository over {@code preferences}, with its constructor actually run.
     *
     * <p>{@link Context} is a class whose methods are all stubs on the JVM, so
     * the repository offers a seam for exactly this: hand the next instance the
     * store to use. Running the real constructor matters, because the storage
     * migration lives there -- an earlier version of these tests allocated the
     * object without it and silently tested an unmigrated repository.</p>
     */
    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    /** Position of a named schedule, so tests do not depend on preset order. */
    private static int indexOfSchedule(List<BellSchedule> schedules, String name) {
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("no schedule named " + name);
    }

    private static FakePreferences preferencesWith(String key, String value) {
        FakePreferences preferences = new FakePreferences();
        preferences.edit().putString(key, value).apply();
        return preferences;
    }

    @Test
    public void aFirstRunWritesTheSampleCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        List<Course> courses = repository.getCourses();

        assertEquals(9, courses.size());
        assertNotNull(preferences.getString("courses:" + repository
                .getActiveSemester().id(), null));
        assertFalse(courses.get(0).timeRange.isEmpty());
    }

    @Test
    public void everyScheduleChoiceSurvivesAReopen() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository first = repositoryFor(preferences);
        Course course = first.getCourses().get(0);
        course.name = "\u6539\u8FC7\u7684\u540D\u5B57";
        course.scheduleName = BellSchedules.DE_NAME;
        first.upsertCourse(course);

        CourseRepository reopened = repositoryFor(preferences);
        Course stored = reopened.findCourse(course.id);

        assertEquals("\u6539\u8FC7\u7684\u540D\u5B57", stored.name);
        assertEquals(BellSchedules.DE_NAME, stored.scheduleName);
        assertEquals(course.color, stored.color);
        assertEquals(course.startWeek, stored.startWeek);
        assertEquals(BellSchedules.DE_NAME,
                reopened.scheduleFor(stored).name());
    }

    @Test
    public void aSchedulePastMidnightSurvivesAReopen() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> schedules = BellSchedules.copyOf(repository.getBellSchedules());
        int deIndex = indexOfSchedule(schedules, BellSchedules.DE_NAME);
        schedules.set(deIndex, schedules.get(deIndex).withTimes(
                new int[] {500, 555, 620, 675, 810, 865, 930, 985, 1110, 1165,
                        1230, 1280, 1330, 1380, 1430},
                new int[] {545, 600, 665, 720, 855, 910, 975, 1030, 1155, 1210,
                        1275, 1325, 1375, 1425, 1475}));
        repository.saveBellSchedules(schedules);

        CourseRepository reopened = repositoryFor(preferences);

        BellSchedule reopenedDe = reopened.getBellSchedules()
                .get(indexOfSchedule(reopened.getBellSchedules(), BellSchedules.DE_NAME));
        assertEquals("08:20 - 09:05", reopenedDe.timeRange(1, 1));
        assertEquals(BellSchedule.PERIODS, reopenedDe.periodCount());
    }

    @Test
    public void renamingAScheduleRepointsTheCoursesThatChoseIt() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = repository.getCourses().get(0);
        course.scheduleName = BellSchedules.DE_NAME;
        repository.upsertCourse(course);

        int updated = repository.renameSchedule(BellSchedules.DE_NAME,
                "\u6539\u540D\u540E\u7684\u4F5C\u606F");

        assertEquals(1, updated);
        assertEquals("\u6539\u540D\u540E\u7684\u4F5C\u606F",
                repository.findCourse(course.id).scheduleName);
    }

    // ------------------------------------------- saving and deleting by name

    /** A course whose schedule choice is pinned to {@code scheduleName}. */
    private static Course courseNaming(String scheduleName, String location) {
        Course course = new Course();
        course.name = "\u9AD8\u7B49\u6570\u5B66";
        course.teacher = "\u5F20\u8001\u5E08";
        course.location = location;
        course.dayOfWeek = 1;
        course.startPeriod = 3;
        course.endPeriod = 4;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        course.scheduleName = scheduleName;
        return course;
    }

    /** How many schedules carry this name. */
    private static int countNamed(List<BellSchedule> schedules, String name) {
        int found = 0;
        for (BellSchedule schedule : schedules) {
            if (schedule.name().equals(name)) {
                found++;
            }
        }
        return found;
    }

    /**
     * Renaming through the operation the editor actually performs.
     *
     * <p>{@code renameSchedule} was already correct and tested; the editor simply
     * never called it, so it wrote the renamed table back and left every course
     * that pinned the old name pointing at a name that no longer existed. Those
     * courses then fell back to venue resolution and could land on a different
     * clock -- the schedule silently changing under the user.</p>
     */
    @Test
    public void savingAScheduleUnderANewNameRepointsTheCoursesThatNamedIt() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = courseNaming(BellSchedules.DE_NAME, "\u6CA1\u6709\u5173\u952E\u8BCD\u7684\u5730\u70B9");
        repository.upsertCourse(course);

        repository.saveBellSchedule(BellSchedules.DE_NAME,
                BellSchedules.deBuilding().renamed("\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868"));

        List<BellSchedule> schedules = repository.getBellSchedules();
        assertEquals(3, schedules.size());
        assertEquals(0, countNamed(schedules, BellSchedules.DE_NAME));
        assertEquals(1, countNamed(schedules, "\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868"));
        assertEquals("\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868",
                repository.findCourse(course.id).scheduleName);

        // And the repointed name survives a reopen, which is what makes it a fix
        // rather than a session-long illusion.
        CourseRepository reopened = repositoryFor(preferences);
        assertEquals("\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868",
                reopened.findCourse(course.id).scheduleName);
        assertNotNull(BellSchedules.byName(reopened.getBellSchedules(),
                "\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868"));
    }

    @Test
    public void renamingAScheduleKeepsTheClockTimesItWasGiven() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellSchedule renamed = BellSchedules.deBuilding()
                .renamed("\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868");

        repository.saveBellSchedule(BellSchedules.DE_NAME, renamed);

        BellSchedule stored = BellSchedules.byName(repository.getBellSchedules(),
                "\u6539\u540D\u540E\u7684\u4F5C\u606F\u8868");
        assertNotNull(stored);
        assertEquals("10:15 - 10:55", stored.timeRange(3, 3));
        assertEquals(BellSchedule.PERIODS, stored.periodCount());
    }

    /**
     * The write lands on the schedule that was opened, not on whatever sits at
     * that position now.
     *
     * <p>The editor used to carry a list index for its whole life while holding a
     * repository that never reloads; anything another screen did in between made
     * that index name a different table.</p>
     */
    @Test
    public void savingAScheduleReplacesItByNameNotByPosition() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        // Reverse the stored order, so position and name disagree.
        List<BellSchedule> reordered = BellSchedules.copyOf(repository.getBellSchedules());
        java.util.Collections.reverse(reordered);
        repository.saveBellSchedules(reordered);

        repository.saveBellSchedule(BellSchedules.STANDARD_NAME,
                BellSchedules.standard().withTimes(
                        new int[] {500, 540, 600, 645, 685, 805, 845, 905,
                                945, 1000, 1040, 1095, 1135, 1180, 1225},
                        new int[] {540, 580, 640, 685, 725, 845, 885, 945,
                                985, 1040, 1080, 1135, 1175, 1220, 1265}));

        List<BellSchedule> stored = repository.getBellSchedules();
        assertEquals(3, stored.size());
        // The D/E and B/C tables are untouched...
        assertEquals("10:15 - 10:55",
                BellSchedules.byName(stored, BellSchedules.DE_NAME).timeRange(3, 3));
        assertEquals("10:35 - 11:15",
                BellSchedules.byName(stored, BellSchedules.BC_NAME).timeRange(4, 4));
        // ...and the edit went to the one that was named.
        assertEquals("08:20 - 09:00",
                BellSchedules.byName(stored, BellSchedules.STANDARD_NAME).timeRange(1, 1));
    }

    @Test
    public void addingANewScheduleLeavesTheOthersAlone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.saveBellSchedule(null, BellSchedules.standard()
                .renamed("\u5B9E\u9A8C\u697C\u4F5C\u606F")
                .withLocationKeywords(java.util.Arrays.asList("\u5B9E\u9A8C\u697C")));

        List<BellSchedule> stored = repository.getBellSchedules();
        assertEquals(4, stored.size());
        assertEquals(1, countNamed(stored, "\u5B9E\u9A8C\u697C\u4F5C\u606F"));
        assertEquals(1, countNamed(stored, BellSchedules.STANDARD_NAME));
        assertEquals(1, countNamed(stored, BellSchedules.DE_NAME));
        assertEquals(1, countNamed(stored, BellSchedules.BC_NAME));
    }

    @Test
    public void savingWithAnUnknownOriginalNameAppendsRatherThanReplacing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.saveBellSchedule("\u4E0D\u5B58\u5728\u7684\u4F5C\u606F\u8868",
                BellSchedules.standard().renamed("\u53E6\u4E00\u5F20\u8868"));

        assertEquals(4, repository.getBellSchedules().size());
    }

    /**
     * The last schedule may not be deleted.
     *
     * <p>An empty stored list is not "no schedules" to
     * {@link CourseRepository#getBellSchedules} -- it is "this install has not
     * been read yet", and the presets are written straight back. So deleting the
     * final one did not delete anything; it looked like the app had ignored the
     * user, and the table reappeared on the next screen that read.</p>
     */
    @Test
    public void theLastScheduleMayNotBeDeleted() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> only = new ArrayList<>();
        only.add(BellSchedules.deBuilding());
        repository.saveBellSchedules(only);

        assertFalse(repository.deleteBellSchedule(BellSchedules.DE_NAME));

        assertEquals(1, repository.getBellSchedules().size());
        assertEquals(BellSchedules.DE_NAME, repository.getBellSchedules().get(0).name());
        // Nothing was resurrected and nothing was written over: reopening still
        // shows the one table the user had, not the three presets.
        CourseRepository reopened = repositoryFor(preferences);
        assertEquals(1, reopened.getBellSchedules().size());
        assertEquals(BellSchedules.DE_NAME, reopened.getBellSchedules().get(0).name());
    }

    @Test
    public void deletingAScheduleClearsTheCoursesThatNamedIt() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = courseNaming(BellSchedules.DE_NAME, "\u6559\u5B66\u697C E302");
        repository.upsertCourse(course);

        assertTrue(repository.deleteBellSchedule(BellSchedules.DE_NAME));

        List<BellSchedule> stored = repository.getBellSchedules();
        assertEquals(2, stored.size());
        assertEquals(0, countNamed(stored, BellSchedules.DE_NAME));

        // The course is cleared rather than left holding a name nothing answers to,
        // and it falls back to venue resolution -- which is what the delete
        // confirmation promises: "these courses will switch to matching by venue".
        Course cleared = repository.findCourse(course.id);
        assertEquals("", cleared.scheduleName);
        // That fallback now lands on the B/C group, because the keyword that knew
        // about 教学楼 E lived on the table that was just deleted. Worth pinning:
        // deleting a schedule does not only stop it being chosen, it takes its
        // venue keywords with it, and the "other teaching places" rule picks up
        // whatever they used to claim.
        assertEquals(BellSchedules.BC_NAME, repository.scheduleFor(cleared).name());
    }

    @Test
    public void deletingAScheduleThatIsNotThereChangesNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        assertFalse(repository.deleteBellSchedule("\u4E0D\u5B58\u5728\u7684\u4F5C\u606F\u8868"));
        assertFalse(repository.deleteBellSchedule(null));
        assertFalse(repository.deleteBellSchedule("   "));

        assertEquals(3, repository.getBellSchedules().size());
    }

    @Test
    public void deletingAScheduleIsPersisted() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        assertTrue(repository.deleteBellSchedule(BellSchedules.BC_NAME));

        CourseRepository reopened = repositoryFor(preferences);
        assertEquals(2, reopened.getBellSchedules().size());
        assertNull(BellSchedules.byName(reopened.getBellSchedules(),
                BellSchedules.BC_NAME));
    }

    @Test
    public void everyCourseGetsItsClockTimesFromItsOwnSchedule() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        // The same two periods in three venues, three different clocks: this is
        // what per-venue resolution has to get right.
        assertEquals("09:55 - 11:20", timeRangeAt(repository, "\u6559\u5B66\u697CF\u697C F420"));
        assertEquals("10:15 - 11:35", timeRangeAt(repository, "\u6559\u5B66\u697CD\u697C D305"));
        assertEquals("09:55 - 11:15", timeRangeAt(repository, "\u6559\u5B66\u697C C203"));

        // And the schedule each of them resolved to is the group it belongs to.
        assertEquals(BellSchedules.STANDARD_NAME,
                scheduleNameAt(repository, "\u6559\u5B66\u697CF\u697C F420"));
        assertEquals(BellSchedules.DE_NAME,
                scheduleNameAt(repository, "\u6559\u5B66\u697CD\u697C D305"));
        assertEquals(BellSchedules.BC_NAME,
                scheduleNameAt(repository, "\u6559\u5B66\u697C C203"));

        // The stored snapshot carries the same value, so screens do not resolve it
        // again on every draw.
        Course course = new Course();
        course.location = "\u6559\u5B66\u697CD\u697C D305";
        course.startPeriod = 3;
        course.endPeriod = 4;
        repository.upsertCourse(course);
        assertEquals("10:15 - 11:35", repository.findCourse(course.id).timeRange);
    }

    /** The clock a course in {@code location} for periods 3-4 would ring. */
    private static String timeRangeAt(CourseRepository repository, String location) {
        Course course = new Course();
        course.location = location;
        course.startPeriod = 3;
        course.endPeriod = 4;
        repository.upsertCourse(course);
        return repository.timeRangeOf(repository.findCourse(course.id));
    }

    private static String scheduleNameAt(CourseRepository repository, String location) {
        Course course = new Course();
        course.location = location;
        course.startPeriod = 3;
        course.endPeriod = 4;
        repository.upsertCourse(course);
        return repository.scheduleFor(repository.findCourse(course.id)).name();
    }

    @Test
    public void clearingAScheduleReferenceFallsBackToTheVenue() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Course course = repository.getCourses().get(0);
        course.location = "\u6559\u5B66\u697CF\u697C F420";
        course.scheduleName = BellSchedules.DE_NAME;
        repository.upsertCourse(course);
        String pinnedTo = repository.scheduleFor(
                repository.findCourse(course.id)).name();

        int cleared = repository.clearScheduleReferences(BellSchedules.DE_NAME);

        assertEquals(1, cleared);
        Course stored = repository.findCourse(course.id);
        assertEquals("", stored.scheduleName);
        // The venue decides again, and F420 belongs to the A/F group, so the course
        // stops following the schedule it had been pinned to.
        assertEquals(BellSchedules.DE_NAME, pinnedTo);
        assertEquals(BellSchedules.STANDARD_NAME,
                repository.scheduleFor(stored).name());
    }

    // -------------------------------------------------------------- semesters

    @Test
    public void aFreshInstallStartsWithOneSemester() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        List<Semester> semesters = repository.getSemesters();

        assertEquals(1, semesters.size());
        assertEquals(repository.getActiveSemester().id(), semesters.get(0).id());
        assertEquals(repository.getSemesterStart(), semesters.get(0).start());
        assertNotNull(preferences.getString(KEY_SEMESTERS, null));
    }

    @Test
    public void anExistingInstallIsMigratedWithItsCoursesAndStartDate() {
        FakePreferences preferences = new FakePreferences();
        preferences.edit()
                .putString(KEY_START, "2026-02-23")
                .putString(KEY_COURSES, "[{\"id\":1,\"name\":\"\u65E7\u8BFE\","
                        + "\"day\":1,\"startPeriod\":1,\"endPeriod\":2}]")
                .apply();

        CourseRepository repository = repositoryFor(preferences);

        List<Semester> semesters = repository.getSemesters();
        assertEquals(1, semesters.size());
        assertEquals(LocalDate.of(2026, 2, 23), semesters.get(0).start());
        assertEquals("\u65E7\u8BFE", repository.getCourses().get(0).name);
        assertEquals(LocalDate.of(2026, 2, 23), repository.getSemesterStart());
        // The legacy keys are retired so a later fresh install cannot read them.
        assertNull(preferences.getString(KEY_COURSES, null));
        assertNull(preferences.getString(KEY_START, null));
        assertNotNull(preferences.getString(KEY_SEMESTERS, null));
    }

    @Test
    public void migrationHappensOnlyOnce() {
        FakePreferences preferences = new FakePreferences();
        preferences.edit()
                .putString(KEY_COURSES, "[{\"id\":1,\"name\":\"\u65E7\u8BFE\"}]")
                .apply();
        CourseRepository first = repositoryFor(preferences);
        long id = first.getActiveSemester().id();

        // A second repository over the same store must not create a second
        // semester nor re-import anything.
        CourseRepository second = repositoryFor(preferences);

        assertEquals(1, second.getSemesters().size());
        assertEquals(id, second.getActiveSemester().id());
        assertEquals(1, second.getCourses().size());
    }

    @Test
    public void eachSemesterKeepsItsOwnCourses() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        assertEquals(9, repository.getCourses().size());

        Semester spring = repository.addSemester(LocalDate.of(2027, 2, 22));
        repository.setActiveSemester(spring);

        // A semester the user created starts empty: the sample courses are a
        // first-run thing and must not be inherited.
        assertEquals(0, repository.getCourses().size());
        Course added = new Course();
        added.name = "\u6625\u5B63\u8BFE";
        repository.upsertCourse(added);
        assertEquals(1, repository.getCourses().size());
        assertEquals("\u6625\u5B63\u8BFE", repository.getCourses().get(0).name);

        // The other semester is untouched: this is not a filtered view.
        Semester previous = repository.getSemesters().get(0);
        assertEquals(9, repository.countCoursesOf(previous));
        assertEquals(1, repository.countCoursesOf(spring));
    }

    @Test
    public void eachSemesterKeepsItsOwnStartDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate firstStart = repository.getSemesterStart();

        Semester second = repository.addSemester(firstStart.plusWeeks(20));
        repository.setActiveSemester(second);
        repository.setSemesterStart(LocalDate.of(2027, 2, 22));

        assertEquals(LocalDate.of(2027, 2, 22), repository.getSemesterStart());
        repository.setActiveSemester(repository.getSemesters().get(0));
        assertEquals(firstStart, repository.getSemesterStart());
    }

    @Test
    public void switchingSemestersSurvivesAReopen() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester second = repository.addSemester(LocalDate.of(2027, 2, 22));
        repository.setActiveSemester(second);

        CourseRepository reopened = repositoryFor(preferences);

        assertEquals(second.id(), reopened.getActiveSemester().id());
        assertEquals(LocalDate.of(2027, 2, 22),
                reopened.getActiveSemester().start());
    }

    @Test
    public void addingASemesterThatAlreadyExistsReturnsTheStoredOne() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate start = repository.getSemesterStart();

        Semester same = repository.addSemester(start);
        Semester sameWeekday = repository.addSemester(start.plusDays(2));

        assertEquals(1, repository.getSemesters().size());
        assertEquals(start, same.start());
        // Any day of the same week means the same semester.
        assertEquals(same.id(), sameWeekday.id());
    }

    @Test
    public void aTypedNameIsUsedAndAnEmptyOneFallsBackToTheDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate start = LocalDate.of(2027, 2, 22);

        Semester named = repository.addSemester(start, "\u5927\u4E09\u4E0B");
        Semester derived = repository.addSemester(start.plusWeeks(30), "   ");

        // Nothing replaces what the user typed...
        assertEquals("\u5927\u4E09\u4E0B", named.label());
        assertTrue(named.hasCustomName());
        assertEquals(start, named.start());
        // ...and nothing is invented when they typed nothing.
        assertFalse(derived.hasCustomName());
        assertEquals("2027 \u79CB\u5B63\u5B66\u671F", derived.label());
    }

    @Test
    public void aSemesterCannotBeCreatedWithoutAStartDate() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        boolean threw = false;
        try {
            repository.addSemester(null, "\u65E0\u65E5\u671F");
        } catch (IllegalArgumentException expected) {
            threw = true;
        }

        assertTrue("a semester must not be created without a date", threw);
        assertEquals(1, repository.getSemesters().size());
    }

    @Test
    public void aNewSemesterIsEmptyUnlessCoursesAreCarriedOver() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester source = repository.getActiveSemester();
        assertEquals(9, repository.countCoursesOf(source));

        Semester empty = repository.addSemester(LocalDate.of(2027, 2, 22), "");
        assertEquals(0, repository.countCoursesOf(empty));

        Semester carried = repository.addSemester(LocalDate.of(2027, 8, 30), "");
        int copied = repository.copyCourses(source, carried);

        assertEquals(9, copied);
        assertEquals(9, repository.countCoursesOf(carried));
        // The source is untouched, and the copies are separate objects with
        // their own ids.
        assertEquals(9, repository.countCoursesOf(source));
        List<Course> originals = repository.coursesOf(source);
        List<Course> copies = repository.coursesOf(carried);
        assertTrue(originals.get(0).id != copies.get(0).id);
        copies.get(0).name = "\u6539\u8FC7\u7684\u526F\u672C";
        assertEquals(originals.get(0).name, repository.coursesOf(source).get(0).name);
    }

    @Test
    public void carryingCoursesOverDoesNotChangeTheActiveSemester() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester source = repository.getActiveSemester();
        Semester target = repository.addSemester(LocalDate.of(2027, 2, 22), "");

        repository.copyCourses(source, target);

        assertEquals(source.id(), repository.getActiveSemester().id());
        assertEquals(9, repository.getCourses().size());
        assertEquals(9, repository.countCoursesOf(target));
    }

    @Test
    public void copyingASemesterOntoItselfDoesNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester source = repository.getActiveSemester();

        assertEquals(0, repository.copyCourses(source, source));
        assertEquals(9, repository.countCoursesOf(source));
    }

    @Test
    public void deletingASemesterTakesItsCoursesWithIt() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester previous = repository.getActiveSemester();
        assertEquals(9, repository.countCoursesOf(previous));

        Semester extra = repository.addSemester(LocalDate.of(2027, 2, 22));
        repository.setActiveSemester(extra);
        Course added = new Course();
        added.name = "\u4E34\u65F6\u8BFE";
        repository.upsertCourse(added);
        assertEquals(1, repository.countCoursesOf(extra));

        boolean removed = repository.deleteSemester(extra);

        assertTrue(removed);
        assertEquals(1, repository.getSemesters().size());
        assertEquals(0, repository.countCoursesOf(extra));
        // Deleting one term leaves the other one whole.
        assertEquals(9, repository.countCoursesOf(previous));
    }

    @Test
    public void theLastSemesterMayNotBeDeleted() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester only = repository.getActiveSemester();

        assertFalse(repository.deleteSemester(only));
        assertEquals(1, repository.getSemesters().size());
    }

    @Test
    public void deletingTheActiveSemesterFallsBackToAnotherOne() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester extra = repository.addSemester(LocalDate.of(2027, 2, 22));
        repository.setActiveSemester(extra);

        repository.deleteSemester(extra);

        Semester active = repository.getActiveSemester();
        assertTrue(active.id() != extra.id());
        assertEquals(repository.getSemesters().get(0).id(), active.id());
        // And the fallback is what got stored, not just what is in memory.
        assertEquals(active.id(), repositoryFor(preferences).getActiveSemester().id());
    }

    @Test
    public void movingTheStartDateAlsoMovesTheSemesterRecord() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        repository.setSemesterStart(LocalDate.of(2026, 9, 7));

        Semester stored = repository.getActiveSemester();
        assertEquals(LocalDate.of(2026, 9, 7), stored.start());
        assertEquals(repository.getSemesterStart(), stored.start());
    }

    @Test
    public void renamingASemesterIsPersisted() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester active = repository.getActiveSemester();

        repository.updateSemester(active.renamed("\u5927\u4E09\u4E0A"));

        assertEquals("\u5927\u4E09\u4E0A", repository.getActiveSemester().label());
        assertEquals("\u5927\u4E09\u4E0A",
                repositoryFor(preferences).getActiveSemester().label());
    }

    /**
     * Two repository instances over one store: the snapshot has to be droppable.
     *
     * <p>This is the shape of the real bug. Each Activity builds its own
     * repository, and the snapshot is only invalidated by writes through the same
     * instance. Importing from settings therefore left the home screen serving
     * what it had loaded at startup, while the overview -- a different instance --
     * showed the imported courses. The two screens disagreed because they were
     * literally holding different data.</p>
     */
    @Test
    public void anotherInstancesWriteIsInvisibleUntilThisOneReloads() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository home = repositoryFor(preferences);
        CourseRepository settings = repositoryFor(preferences);

        int before = home.getCourses().size();

        List<Course> imported = new ArrayList<>();
        Course course = new Course();
        course.name = "\u5BFC\u5165\u7684\u8BFE";
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        course.location = "\u6559\u5B66\u697C A101";
        imported.add(course);
        settings.replaceCourses(imported);

        // Without a reload the first instance keeps its own snapshot: exactly the
        // stale home screen, still showing the courses it loaded at startup.
        List<Course> stale = home.getCourses();
        assertEquals(before, stale.size());
        for (Course existing : stale) {
            assertTrue("the imported course must not be visible yet",
                    !"\u5BFC\u5165\u7684\u8BFE".equals(existing.name));
        }

        home.reload();

        List<Course> afterReload = home.getCourses();
        assertEquals(1, afterReload.size());
        assertEquals("\u5BFC\u5165\u7684\u8BFE", afterReload.get(0).name);
    }

    @Test
    public void reloadAlsoPicksUpAnotherInstancesSemesterChanges() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository home = repositoryFor(preferences);
        CourseRepository settings = repositoryFor(preferences);

        assertEquals(1, home.getSemesters().size());

        settings.addSemester(LocalDate.of(2027, 2, 22), "\u65B0\u5B66\u671F");

        // Stale until asked to re-read.
        assertEquals(1, home.getSemesters().size());

        home.reload();

        assertEquals(2, home.getSemesters().size());
    }
}
