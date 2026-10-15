package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * Choosing which timetable an unmatched course falls back to.
 *
 * <p>The rule the user asked for: a venue still decides first, and only a course
 * whose venue matches nothing is read against the default. These tests pin all
 * three parts -- the precedence, the persistence of the choice, and what an
 * upgrade from a build that had no choice does -- against the repository rather
 * than against the resolution helper alone, because the failure this feature is
 * there to prevent is the home screen and the reminder planner disagreeing about
 * which one is in force.</p>
 */
public class DefaultScheduleTest {

    /** Three tables whose period 1 is a different minute, so they are tellable apart. */
    private static BellSchedule table(String name, int firstPeriodStart) {
        int periods = 15;
        int[] starts = new int[periods];
        int[] ends = new int[periods];
        for (int period = 0; period < periods; period++) {
            starts[period] = firstPeriodStart + period * 45;
            ends[period] = starts[period] + 40;
        }
        return new BellSchedule(name, starts, ends, new ArrayList<String>(), 40);
    }

    private static Course course(String name, String scheduleName, String location) {
        Course course = new Course();
        course.name = name;
        course.location = location;
        course.scheduleName = scheduleName;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 1;
        course.startWeek = 1;
        course.endWeek = 5;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((android.content.Context) null);
    }

    /** A store holding exactly these three tables, in this order. */
    private static CourseRepository repositoryWith(FakePreferences preferences,
            BellSchedule... schedules) {
        CourseRepository repository = repositoryFor(preferences);
        repository.saveBellSchedules(new ArrayList<>(Arrays.asList(schedules)));
        repository.reload();
        return repository;
    }

    private static BellSchedule byName(List<BellSchedule> schedules, String name) {
        BellSchedule found = BellSchedules.byName(schedules, name);
        assertNotNull(name + " must be in the list", found);
        return found;
    }

    // ============================================ 1 / 2. picking any row

    @Test
    public void theFirstSecondAndThirdTableCanEachBeMadeTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));

        List<BellSchedule> schedules = repository.getBellSchedules();
        for (BellSchedule wanted : schedules) {
            assertTrue("setting " + wanted.name() + " must succeed",
                    repository.setDefaultSchedule(wanted.id()));
            assertEquals("the stored choice must be read back by id",
                    wanted.id(), repository.getDefaultScheduleId());
            assertEquals(wanted.name(), repository.getDefaultSchedule().name());
        }
    }

    @Test
    public void exactlyOneTableIsTheDefaultAtEveryMoment() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));

        String[] order = {"First", "Third", "Second", "First"};
        for (String wanted : order) {
            BellSchedule target = byName(repository.getBellSchedules(), wanted);
            assertTrue(repository.setDefaultSchedule(target.id()));

            List<BellSchedule> schedules = repository.getBellSchedules();
            int defaults = 0;
            for (BellSchedule schedule : schedules) {
                if (schedule.id().equals(repository.getDefaultScheduleId())) {
                    defaults++;
                }
            }
            assertEquals("after choosing " + wanted + " there must be one default",
                    1, defaults);
            assertEquals(wanted, repository.getDefaultSchedule().name());
        }
    }

    // ============================================ 3. reordering

    @Test
    public void reorderingTheListDoesNotMoveTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(repository.getBellSchedules(), "Third");
        assertTrue(repository.setDefaultSchedule(third.id()));

        // The same three tables, offered in the opposite order -- which is what any
        // future "sort" or manual reorder would store.
        repository.saveBellSchedules(new ArrayList<>(Arrays.asList(
                table("Third", 10 * 60), table("Second", 9 * 60), table("First", 8 * 60))));
        repository.reload();

        List<BellSchedule> schedules = repository.getBellSchedules();
        assertEquals("Third", schedules.get(0).name());
        assertEquals("the default must still be Third, by id",
                "Third", repository.getDefaultSchedule().name());
        assertEquals(schedules.get(0).id(), repository.getDefaultScheduleId());
    }

    // ============================================ 4 / 5 / 6. precedence

    @Test
    public void aVenueMatchStillBeatsTheDefault() {
        // The default is deliberately set to a table whose keywords do NOT cover the
        // venue, so "the venue won" cannot be an accident of picking the same table.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellSchedule aAndF = BellSchedules.standard();
        BellSchedule dAndE = BellSchedules.deBuilding();
        BellSchedule bAndC = BellSchedules.bcBuilding();
        repository.saveBellSchedules(
                new ArrayList<>(Arrays.asList(aAndF, dAndE, bAndC)));
        repository.reload();
        assertTrue(repository.setDefaultSchedule(bAndC.id()));

        BellSchedule forD = repository.scheduleFor(
                course("Lab", "", "\u6559\u5B66\u697C D305"));
        assertEquals(BellSchedules.DE_NAME, forD.name());

        // And the explicit course choice still beats the venue, as it always did.
        BellSchedule named = repository.scheduleFor(
                course("Named", BellSchedules.STANDARD_NAME, "\u6559\u5B66\u697C D305"));
        assertEquals(BellSchedules.STANDARD_NAME, named.name());
    }

    @Test
    public void anEmptyVenueUsesTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule second = byName(repository.getBellSchedules(), "Second");
        assertTrue(repository.setDefaultSchedule(second.id()));

        BellSchedule chosen = repository.scheduleFor(course("No venue", "", ""));

        assertEquals("an empty venue must land on the default, not the first row",
                "Second", chosen.name());
        assertEquals(9 * 60, chosen.startMinute(1));
    }

    @Test
    public void anUnmatchableVenueUsesTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(repository.getBellSchedules(), "Third");
        assertTrue(repository.setDefaultSchedule(third.id()));

        BellSchedule chosen = repository.scheduleFor(
                course("Gym", "", "\u4E1C\u64CD\u573A"));

        assertEquals("a venue that matches no keyword must land on the default",
                "Third", chosen.name());
        assertEquals(10 * 60, chosen.startMinute(1));
    }

    @Test
    public void theDefaultInAnyPositionIsUsed() {
        // The user's requirement spelled out: first, second and third all work. The
        // failure mode being guarded is "position one is special", so each position
        // is asked for separately with a venue that matches nothing.
        for (int position = 0; position < 3; position++) {
            FakePreferences preferences = new FakePreferences();
            CourseRepository repository = repositoryWith(preferences,
                    table("First", 8 * 60), table("Second", 9 * 60),
                    table("Third", 10 * 60));
            List<BellSchedule> schedules = repository.getBellSchedules();
            BellSchedule wanted = schedules.get(position);
            assertTrue(repository.setDefaultSchedule(wanted.id()));

            BellSchedule chosen = repository.scheduleFor(course("Nowhere", "", "\u64CD\u573A"));

            assertEquals("position " + position + " must be usable as the default",
                    wanted.name(), chosen.name());
        }
    }

    // ============================================ 7. one resolution everywhere

    @Test
    public void timeCalculationDetailAndRemindersAllUseTheDefault() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(repository.getBellSchedules(), "Third");
        assertTrue(repository.setDefaultSchedule(third.id()));

        Course stored = course("Gym", "", "\u4E1C\u64CD\u573A");
        repository.upsertCourse(stored);
        repository.reload();
        Course saved = named(repository, "Gym");
        assertNotNull(saved);

        // Every path that answers "when is this course" must land on 10:00, the
        // third table's period 1 -- not 08:00, the first table's.
        assertEquals("the stored time range",
                "10:00 - 10:40", repository.timeRangeOf(saved));
        BellSchedule resolved = repository.scheduleFor(saved);
        assertEquals("Third", resolved.name());
        assertEquals(10 * 60, resolved.startMinute(1));

        // A Monday inside the course's own week range -- the seeded semester starts
        // 2026-08-31, so 09-07 is the Monday of week 2.
        LocalDate classDay = LocalDate.of(2026, 9, 7);
        LocalDateTime reminderStart = ReminderPlan.startOf(saved,
                Arrays.asList(resolved), classDay, repository.getDefaultScheduleId());
        assertNotNull(reminderStart);
        assertEquals("the reminder planner must agree with the card",
                10 * 60, reminderStart.getHour() * 60 + reminderStart.getMinute());
        assertEquals(classDay, reminderStart.toLocalDate());

        // And the planner's own entry point, called the way the scheduler calls it,
        // must build the notification on the same minute. This is the check that
        // would have caught the detail page resolving through the legacy fallback
        // while the card resolved through the user's choice.
        Reminder reminder = ReminderPlan.forCourseOnDay(
                Arrays.asList(saved), repository.getActiveSemester(),
                Arrays.asList(resolved), saved.id, classDay, 10,
                repository.getDefaultScheduleId());
        assertNotNull(reminder);
        assertEquals("Third", reminder.scheduleName());
        assertEquals(10 * 60,
                reminder.startAt().getHour() * 60 + reminder.startAt().getMinute());
    }

    @Test
    public void theDetailDescriptionUsesTheSameDefaultAsTheCard() {
        // The defect this exists to prevent, found on the device: the detail page
        // resolved through its own path and showed the legacy fallback while the
        // card beside it showed the user's choice -- two different times for one
        // lesson. Both now take the id, so both must agree.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(repository.getBellSchedules(), "Third");
        assertTrue(repository.setDefaultSchedule(third.id()));

        Course gym = course("Gym", "", "\u4E1C\u64CD\u573A");
        com.weihe.schedule.rule.CourseDetail detail = com.weihe.schedule.rule.CourseDetail
                .of(gym, repository.getActiveSemester(), repository.getBellSchedules(),
                        repository.getDefaultScheduleId());

        assertTrue(detail.timeKnown());
        assertEquals("Third", detail.scheduleName());
        assertEquals("10:00 - 10:40", detail.timeText());
        // And the repository, which is what the card is drawn from, agrees.
        assertEquals(detail.timeText(), repository.timeRangeOf(gym));
    }

    @Test
    public void changingTheDefaultChangesTheTimeTheSameCourseResolvesTo() {        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        Course gym = course("Gym", "", "\u4E1C\u64CD\u573A");

        assertTrue(repository.setDefaultSchedule(
                byName(repository.getBellSchedules(), "First").id()));
        assertEquals("08:00 - 08:40", repository.timeRangeOf(gym));

        assertTrue(repository.setDefaultSchedule(
                byName(repository.getBellSchedules(), "Third").id()));
        assertEquals("10:00 - 10:40", repository.timeRangeOf(gym));

        // The course itself is untouched -- only which clock it is read against.
        assertEquals("", gym.scheduleName);
        assertEquals("\u4E1C\u64CD\u573A", gym.location);
    }

    // ============================================ 8 / 9. deleting

    @Test
    public void deletingANonDefaultTableLeavesTheDefaultAlone() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(repository.getBellSchedules(), "Third");
        assertTrue(repository.setDefaultSchedule(third.id()));

        assertTrue(repository.deleteBellSchedule("Second"));

        assertEquals("the default must be untouched",
                third.id(), repository.getDefaultScheduleId());
        assertEquals("Third", repository.getDefaultSchedule().name());
        assertNull("the deleted table must be gone",
                BellSchedules.byName(repository.getBellSchedules(), "Second"));
    }

    @Test
    public void deletingTheDefaultLeavesNoPointerAtNothing() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule second = byName(repository.getBellSchedules(), "Second");
        assertTrue(repository.setDefaultSchedule(second.id()));

        assertTrue(repository.deleteBellSchedule("Second"));
        repository.reload();

        // Deleting the default moves the choice to the list's first remaining table,
        // which is the confirmed behaviour: the user is left with a working fallback
        // rather than a pointer that names nothing.
        String afterDelete = repository.getDefaultScheduleId();
        assertNotNull("a working default must remain", afterDelete);
        assertNotNull("and it must name a table that is still stored",
                BellSchedules.defaultOf(repository.getBellSchedules(), afterDelete));
        assertEquals("First", repository.getDefaultSchedule().name());
        List<BellSchedule> remaining = repository.getBellSchedules();
        assertEquals(2, remaining.size());
        for (BellSchedule schedule : remaining) {
            assertFalse("the deleted table must be gone from the list",
                    "Second".equals(schedule.name()));
        }
    }

    @Test
    public void deletingTheDefaultWhenItWasTheOnlyRemainingNameCannotHappen() {
        // The list is never emptied: the last table cannot be deleted at all, so
        // there is no state where a pointer would have nothing to point at.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences, table("Only", 8 * 60));

        assertFalse("the last table must not be deletable",
                repository.deleteBellSchedule("Only"));
        assertEquals(1, repository.getBellSchedules().size());
        assertNotNull(repository.getDefaultSchedule());
        assertEquals("Only", repository.getDefaultSchedule().name());
    }

    @Test
    public void deletingTheDefaultAndThenRestartingStillResolvesToTheSuccessor() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        assertTrue(repository.setDefaultSchedule(
                byName(repository.getBellSchedules(), "Second").id()));
        repository.deleteBellSchedule("Second");

        CourseRepository restarted = repositoryFor(preferences);

        BellSchedule chosen = restarted.scheduleFor(course("Gym", "", ""));
        assertNotNull("an unmatched course must still resolve after a restart", chosen);
        assertEquals("First", chosen.name());
    }

    // ============================================ 10. upgrading

    @Test
    public void anInstallWithNoChoiceGetsTheTableItUsedToFallBackTo() {
        // What an install upgraded from the previous build looks like: the three
        // shipped tables, no pointer at all. Before this feature an unmatched venue
        // ran to the B/C group; that is the group the choice must adopt, or every
        // course in "other teaching places" would move onto the A/F clock, twenty
        // minutes out in periods 3 to 5.
        FakePreferences preferences = new FakePreferences();
        preferences.edit()
                .putString("bell_schedules",
                        BellScheduleJson.write(BellSchedules.presets()))
                .commit();

        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> schedules = repository.getBellSchedules();

        String adopted = repository.getDefaultScheduleId();
        assertNotNull("a choice must have been adopted", adopted);
        assertEquals("and it must be the B/C group, which is what the fallback was",
                BellSchedules.BC_NAME, repository.getDefaultSchedule().name());

        // And it is written down, not merely returned for this read.
        CourseRepository restarted = repositoryFor(preferences);
        assertEquals(adopted, restarted.getDefaultScheduleId());
        assertEquals(BellSchedules.BC_NAME, restarted.getDefaultSchedule().name());
        assertEquals(3, schedules.size());
    }

    @Test
    public void tablesStoredWithoutIdsAreGivenIdsAndKeepTheirTimes() {
        // The same upgrade, one step earlier: a store written before ids existed.
        FakePreferences preferences = new FakePreferences();
        preferences.edit()
                .putString("bell_schedules",
                        "[{\"name\":\"A/F \u697C\u4F5C\u606F\""
                                + ",\"starts\":[495,535],\"ends\":[535,575]"
                                + ",\"keywords\":[\"a\u697C\"]}"
                                + ",{\"name\":\"B/C \u697C\u4F5C\u606F\""
                                + ",\"starts\":[495,535],\"ends\":[535,575]"
                                + ",\"keywords\":[\"b\u697C\"]}]")
                .commit();

        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> schedules = repository.getBellSchedules();

        for (BellSchedule schedule : schedules) {
            assertNotNull("every table must come back with an id", schedule.id());
        }
        // The times are the stored ones, untouched by the id assignment.
        assertEquals(495, byName(schedules, BellSchedules.STANDARD_NAME).startMinute(1));
        assertEquals(575, byName(schedules, BellSchedules.STANDARD_NAME).endMinute(2));
        assertEquals(BellSchedules.BC_NAME, repository.getDefaultSchedule().name());

        // The invented ids are on disk, so the pointer survives the next restart.
        CourseRepository restarted = repositoryFor(preferences);
        for (BellSchedule schedule : restarted.getBellSchedules()) {
            assertNotNull(schedule.id());
        }
        assertEquals(BellSchedules.BC_NAME, restarted.getDefaultSchedule().name());
    }

    @Test
    public void noCourseOrSemesterDataIsDisturbedByTheUpgrade() {
        // The upgrade must not reset courses, keywords, times or period counts.
        FakePreferences preferences = new FakePreferences();
        CourseRepository first = repositoryFor(preferences);
        first.upsertCourse(course("Kept", "", "\u6559\u5B66\u697C A203"));
        int semesters = first.getSemesters().size();
        int courses = first.getCourses().size();
        List<BellSchedule> before = first.getBellSchedules();

        CourseRepository upgraded = repositoryFor(preferences);

        assertEquals("the semesters are the same", semesters,
                upgraded.getSemesters().size());
        assertEquals("no course was lost or added", courses,
                upgraded.getCourses().size());
        assertNotNull(named(upgraded, "Kept"));
        // Every table kept its period count and its first start.
        List<BellSchedule> after = upgraded.getBellSchedules();
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i).name(), after.get(i).name());
            assertEquals(before.get(i).periodCount(), after.get(i).periodCount());
            assertEquals(before.get(i).startMinute(1), after.get(i).startMinute(1));
            assertEquals(before.get(i).durationMinutes(),
                    after.get(i).durationMinutes());
        }
    }

    // ============================================ 11. restarting

    @Test
    public void theChoiceSurvivesAnAppRestart() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule second = byName(repository.getBellSchedules(), "Second");
        assertTrue(repository.setDefaultSchedule(second.id()));

        CourseRepository restarted = repositoryFor(preferences);

        assertEquals(second.id(), restarted.getDefaultScheduleId());
        assertEquals("Second", restarted.getDefaultSchedule().name());
        assertEquals("Second",
                restarted.scheduleFor(course("Gym", "", "")).name());
    }

    @Test
    public void renamingTheDefaultTableKeepsItTheDefault() {
        // The reason the choice is stored by id and not by name.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule second = byName(repository.getBellSchedules(), "Second");
        assertTrue(repository.setDefaultSchedule(second.id()));

        assertNotNull(repository.saveBellSchedule("Second",
                second.renamed("\u6539\u540D\u540E\u7684\u4F5C\u606F")));
        repository.reload();

        BellSchedule renamed = repository.getDefaultSchedule();
        assertNotNull(renamed);
        assertEquals("\u6539\u540D\u540E\u7684\u4F5C\u606F", renamed.name());
        assertEquals("a rename must carry the id, and the choice with it",
                second.id(), renamed.id());
        assertEquals(9 * 60, renamed.startMinute(1));

        CourseRepository restarted = repositoryFor(preferences);
        assertEquals("\u6539\u540D\u540E\u7684\u4F5C\u606F",
                restarted.getDefaultSchedule().name());
    }

    @Test
    public void aStoredChoiceThatNamesNothingIsNotTreatedAsAChoice() {
        // A half-written or hand-edited store: the pointer is there but names no
        // table. It must be reported as "no choice" so the reader falls back rather
        // than crashing or resolving to nothing.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60));
        preferences.edit().putString("default_schedule_id", "no-such-id").commit();
        repository.reload();

        assertNull("an unusable pointer must read as no choice",
                repository.getDefaultScheduleId());
        assertNotNull("and a course must still resolve to a real table",
                repository.scheduleFor(course("Gym", "", "")));
    }

    @Test
    public void settingADefaultThatNamesNothingIsRefused() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryWith(preferences,
                table("First", 8 * 60), table("Second", 9 * 60));

        assertFalse(repository.setDefaultSchedule("no-such-id"));
        assertFalse("and a blank id is not a choice either",
                repository.setDefaultSchedule(""));

        // The refusal must not have moved whatever was already chosen.
        assertNotNull(repository.getDefaultSchedule());
    }

    // ============================================ 12. backups

    @Test
    public void aNewBackupCarriesTheChoiceAndRestoresIt() {
        FakePreferences sourceStore = new FakePreferences();
        CourseRepository source = repositoryWith(sourceStore,
                table("First", 8 * 60), table("Second", 9 * 60), table("Third", 10 * 60));
        BellSchedule third = byName(source.getBellSchedules(), "Third");
        assertTrue(source.setDefaultSchedule(third.id()));
        source.upsertCourse(course("Kept", "", "\u4E1C\u64CD\u573A"));

        BackupCodec.Document document = BackupCodec.of(
                source.getSemesters(), bySemesterOf(source),
                BellSchedules.copyOf(source.getBellSchedules()),
                source.getActiveSemester().id(), "2026-10-12T10:00:00+08:00", "1.0",
                source.getDefaultScheduleId());
        assertEquals("the document must record the choice",
                third.id(), document.defaultScheduleId());

        FakePreferences targetStore = new FakePreferences();
        CourseRepository target = repositoryFor(targetStore);
        target.restoreFrom(document);

        CourseRepository restarted = repositoryFor(targetStore);
        BellSchedule restored = restarted.getDefaultSchedule();
        assertNotNull(restored);
        assertEquals("Third", restored.name());
        assertEquals("and an unmatched course must use it",
                "Third", restarted.scheduleFor(course("Gym", "", "")).name());
        assertNotNull(BellSchedules.byName(restarted.getBellSchedules(), "First"));
        assertNotNull("the course in the backup must be there too",
                named(restarted, "Kept"));
    }

    @Test
    public void aBackupWithoutAChoiceStillRestoresAndAdoptsTheOldFallback() {
        // An old file: written before the field existed. It must load unchanged,
        // and the choice must come out as the group the old build fell back to.
        FakePreferences sourceStore = new FakePreferences();
        CourseRepository source = repositoryWith(sourceStore,
                BellSchedules.standard(), BellSchedules.deBuilding(),
                BellSchedules.bcBuilding());

        BackupCodec.Document old = BackupCodec.of(source.getSemesters(),
                bySemesterOf(source), BellSchedules.copyOf(source.getBellSchedules()),
                source.getActiveSemester().id(), "2026-10-01T10:00:00+08:00", "1.0");
        assertNull("the old document has no choice", old.defaultScheduleId());

        // Round trip it through the file format so the missing key is what the
        // reader sees, not a Java null that never went through write().
        String json = BackupCodec.write(old);
        assertFalse("an absent choice must not be written as a key",
                json.contains("defaultScheduleId"));
        BackupCodec.Result reread = BackupCodec.parse(json);
        assertTrue(reread.problem(), reread.isOk());

        FakePreferences targetStore = new FakePreferences();
        CourseRepository target = repositoryFor(targetStore);
        target.restoreFrom(reread.document());

        CourseRepository restarted = repositoryFor(targetStore);
        assertNotNull("an old backup must still restore", restarted.getDefaultSchedule());
        assertEquals("and land on the group the old build used",
                BellSchedules.BC_NAME, restarted.getDefaultSchedule().name());
        assertEquals(3, restarted.getBellSchedules().size());
    }

    @Test
    public void aBackupNamingAScheduleItDoesNotCarryRestoresWithoutThatChoice() {
        // Hand-made file whose pointer names a table that is not in its own list.
        // The data must still restore; the preference is dropped, not invented.
        String json = "{\"format\":\"weihe-schedule-backup\",\"formatVersion\":1,"
                + "\"activeSemesterId\":1,"
                + "\"semesters\":[{\"id\":1,\"start\":\"2026-09-01\",\"name\":\"\","
                + "\"courses\":[]}],"
                + "\"schedules\":[{\"name\":\"Only\",\"id\":\"schedule-only\","
                + "\"keywords\":[],\"starts\":[495,535],\"ends\":[535,575]}],"
                + "\"defaultScheduleId\":\"schedule-missing\"}";

        BackupCodec.Result result = BackupCodec.parse(json);

        assertTrue(result.problem(), result.isOk());
        assertNull("a pointer at nothing must be dropped on read",
                result.document().defaultScheduleId());
        assertEquals("the table itself must survive",
                "schedule-only", result.document().schedules().get(0).id());
        assertFalse("and the reader must say what it did",
                result.document().notes().isEmpty());
    }

    @Test
    public void aRestoreThatReplacesTheSchedulesLeavesNoPointerAtAnOldTable() {
        // The default in the target store names a table the backup does not carry.
        // After the restore the pointer must name something that exists, or nothing.
        FakePreferences sourceStore = new FakePreferences();
        CourseRepository source = repositoryWith(sourceStore,
                table("BackupTable", 11 * 60));
        BackupCodec.Document document = BackupCodec.of(source.getSemesters(),
                bySemesterOf(source), BellSchedules.copyOf(source.getBellSchedules()),
                source.getActiveSemester().id(), "2026-10-12T10:00:00+08:00", "1.0",
                source.getDefaultScheduleId());

        FakePreferences targetStore = new FakePreferences();
        CourseRepository target = repositoryWith(targetStore,
                table("First", 8 * 60), table("Second", 9 * 60));
        assertTrue(target.setDefaultSchedule(
                byName(target.getBellSchedules(), "Second").id()));

        target.restoreFrom(document);

        CourseRepository restarted = repositoryFor(targetStore);
        List<BellSchedule> schedules = restarted.getBellSchedules();
        assertEquals(1, schedules.size());
        assertEquals("BackupTable", schedules.get(0).name());

        String pointer = restarted.getDefaultScheduleId();
        if (pointer != null) {
            assertEquals("a surviving pointer must name the restored table",
                    schedules.get(0).id(), pointer);
        }
        BellSchedule chosen = restarted.getDefaultSchedule();
        assertNotNull(chosen);
        assertEquals("BackupTable", chosen.name());
    }

    @Test
    public void theBackupFileFormatStillReadsAFullDocumentWithIds() {
        // Compatibility in both directions: a file this build writes with ids and a
        // choice must round trip to the same ids and the same choice.
        BellSchedule one = table("One", 8 * 60).withId("id-one");
        BellSchedule two = table("Two", 9 * 60).withId("id-two");
        BackupCodec.Document written = BackupCodec.of(
                List.of(new com.weihe.schedule.model.Semester(1L,
                        LocalDate.of(2026, 9, 1), "")),
                java.util.Collections.emptyMap(),
                Arrays.asList(one, two), 1L, "2026-10-12T10:00:00+08:00", "1.0",
                "id-two");

        BackupCodec.Result read = BackupCodec.parse(BackupCodec.write(written));

        assertTrue(read.problem(), read.isOk());
        assertEquals(2, read.document().schedules().size());
        assertEquals("id-one", read.document().schedules().get(0).id());
        assertEquals("id-two", read.document().schedules().get(1).id());
        assertEquals("id-two", read.document().defaultScheduleId());
    }

    @Test
    public void everyScheduleGetsAnIdOnTheWayIntoABackup() {
        // A schedule stored before ids existed must not leave an id-less row in a
        // file, because a pointer in that file could not name it.
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        List<BellSchedule> schedules = new ArrayList<>();
        schedules.add(table("Alpha", 8 * 60));
        schedules.add(table("Beta", 9 * 60));
        repository.saveBellSchedules(schedules);

        for (BellSchedule stored : repository.getBellSchedules()) {
            assertNotNull(stored.name() + " must have an id", stored.id());
        }
    }

    // ============================================ helpers

    private static Course named(CourseRepository repository, String name) {
        for (Course each : repository.getCourses()) {
            if (name.equals(each.name)) {
                return each;
            }
        }
        return null;
    }

    /**
     * Every semester's courses, in the shape a backup document wants.
     *
     * <p>Built here rather than read off the repository, because the repository
     * hands out one term's list at a time and the document needs them keyed by
     * semester id -- the same shape {@code SettingsActivity.buildBackup} builds.</p>
     */
    private static java.util.Map<Long, List<Course>> bySemesterOf(
            CourseRepository repository) {
        java.util.Map<Long, List<Course>> bySemester = new java.util.LinkedHashMap<>();
        for (com.weihe.schedule.model.Semester semester : repository.getSemesters()) {
            bySemester.put(Long.valueOf(semester.id()),
                    new ArrayList<>(repository.coursesOf(semester)));
        }
        return bySemester;
    }
}
