package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.weihe.schedule.rule.ReminderPlan;

/**
 * The rest of the app when a day is not fifteen periods of forty minutes.
 *
 * <p>The schedule screen is only the place the setting is typed. What matters is
 * everything downstream: a course on period eighteen has to be storable, findable,
 * timeable and remindable, and a table that says fifty minutes has to produce fifty
 * minute times everywhere rather than the forty it used to assume. These tests take
 * one non-default schedule -- twenty periods, fifty minutes -- through those paths
 * so that "the whole app uses the new configuration" is checked rather than
 * asserted.</p>
 */
public class NonDefaultScheduleFlowTest {

    /** Twenty periods from 08:00, fifty minutes each. */
    private static BellSchedule longDay() {
        int[] starts = new int[20];
        int[] ends = new int[20];
        for (int period = 1; period <= 20; period++) {
            starts[period - 1] = 8 * 60 + (period - 1) * 55;
            ends[period - 1] = starts[period - 1] + 50;
        }
        return new BellSchedule("Long day", starts, ends,
                new ArrayList<String>(), 50);
    }

    private static Course course(String name, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.name = name;
        course.teacher = "T";
        course.location = "A101";
        course.dayOfWeek = 1;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = 5;
        course.parity = Course.PARITY_ALL;
        course.scheduleName = "Long day";
        return course;
    }

    private static CourseRepository repositoryWithLive(BellSchedule schedule) {
        CourseRepository.useStoreForNextInstance(new FakePreferences());
        CourseRepository repository = new CourseRepository((android.content.Context) null);
        repository.saveBellSchedules(new ArrayList<>(Arrays.asList(schedule)));
        return repository;
    }

    // ============================================ storing and finding

    @Test
    public void aCoursePastPeriodFifteenStoresAndComesBack() {
        CourseRepository repository = repositoryWithLive(longDay());

        repository.upsertCourse(course("Late lab", 17, 18));
        repository.reload();

        // Found by name rather than by position, because a fresh install already
        // holds the sample timetable and this is about one course, not a count.
        Course saved = named(repository, "Late lab");
        assertNotNull("the course must have been stored", saved);
        assertEquals(17, saved.startPeriod);
        assertEquals(18, saved.endPeriod);

        // The table it belongs to came back with its length and its count.
        BellSchedule stored = repository.scheduleFor(saved);
        assertNotNull(stored);
        assertEquals(50, stored.durationMinutes());
        assertEquals(20, stored.periodCount());
    }

    @Test
    public void theDefaultFifteenStillStoresAndSorts() {
        // The compatibility half: an existing user's courses must be unchanged.
        CourseRepository.useStoreForNextInstance(new FakePreferences());
        CourseRepository repository = new CourseRepository((android.content.Context) null);

        repository.upsertCourse(course("Early", 1, 2));
        repository.upsertCourse(course("Late", 10, 11));
        repository.reload();

        // Every course the repository holds -- the seeded ones and these two -- must
        // be inside the default day, which is what an existing user has.
        for (Course each : repository.getCourses()) {
            assertTrue(each.name + " is past the default day",
                    each.endPeriod <= ScheduleUtils.PERIODS_PER_DAY);
        }
        assertNotNull(named(repository, "Early"));
        assertNotNull(named(repository, "Late"));
    }

    // ============================================ time calculation

    @Test
    public void courseTimesComeFromTheSchedulesOwnLength() {
        BellSchedule schedule = longDay();
        Course late = course("Late lab", 17, 18);

        // Period 17 is the seventeenth hour-and-a-bit the table lists: 08:00 plus
        // sixteen fifty-five minute gaps to its start, then fifty minutes of itself.
        int period17Start = 8 * 60 + 16 * 55;
        assertEquals(period17Start, schedule.startMinute(17));
        assertEquals(period17Start + 50, schedule.endMinute(17));

        LocalDateTime start = ReminderPlan.startOf(late, Arrays.asList(schedule),
                LocalDate.of(2026, 10, 12));
        assertNotNull("a course on period 17 must be timeable", start);
        assertEquals("22:40", BellSchedule.label(period17Start));
        assertEquals(period17Start, start.getHour() * 60 + start.getMinute());
        // Fifty minutes, not the forty the old constant would have produced.
        assertEquals(50, schedule.endMinute(17) - schedule.startMinute(17));
    }

    @Test
    public void aDifferentLengthMovesTheTimesRatherThanTheStarts() {
        // A table at forty minutes, built from the same starts -- which is what the
        // editor produces when the length is changed, and what a test needs in order
        // to ask whether the starts moved.
        BellSchedule fifty = longDay();
        int[] starts = new int[20];
        for (int period = 1; period <= 20; period++) {
            starts[period - 1] = fifty.startMinute(period);
        }
        BellSchedule forty = fifty.withTimes(starts, endsAfter(starts, 40))
                .withDuration(40);

        for (int period = 1; period <= 20; period++) {
            assertEquals("start " + period + " must not move with the length",
                    fifty.startMinute(period), forty.startMinute(period));
            assertEquals("end " + period + " must follow the length",
                    fifty.startMinute(period) + 40, forty.endMinute(period));
        }
        assertEquals(50, fifty.durationMinutes());
        assertEquals(40, forty.durationMinutes());
    }

    @Test
    public void theStoredEndsAgreeWithTheLengthOnEveryFreshTable() {
        // The invariant that makes reading endMinute() safe: a table the editor built
        // has ends equal to start plus length, so the stored end and the derived one
        // are the same number and nothing can drift.
        BellSchedule schedule = longDay();
        for (int period = 1; period <= schedule.periodCount(); period++) {
            assertEquals("period " + period,
                    schedule.endMinute(period), schedule.computedEndMinute(period));
        }
    }

    /** The stored course with this name, or null. */
    private static Course named(CourseRepository repository, String name) {
        for (Course each : repository.getCourses()) {
            if (name.equals(each.name)) {
                return each;
            }
        }
        return null;
    }

    private static int[] endsAfter(int[] starts, int length) {
        int[] ends = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            ends[i] = starts[i] + length;
        }
        return ends;
    }

    // ============================================ reminders

    @Test
    public void aReminderIsPlannedForAPeriodPastTheOldDefault() {
        BellSchedule schedule = longDay();
        Course late = course("Late lab", 17, 18);

        assertTrue("a period-17 course must not be refused as out of range",
                ReminderPlan.problemWith(late, Arrays.asList(schedule)) == null);

        LocalDateTime start = ReminderPlan.startOf(late, Arrays.asList(schedule),
                LocalDate.of(2026, 10, 12));
        assertNotNull(start);
        assertEquals("the time is the table's, not a hard-coded forty minutes",
                22 * 60 + 40, start.getHour() * 60 + start.getMinute());
    }

    @Test
    public void aCoursePastWhatItsScheduleReachesIsStillRefused() {
        // The ceiling moved; the honest refusal did not. A course on period 25 of a
        // twenty-period table has no time, and saying so is better than clamping it
        // onto period 20 and reminding at the wrong moment.
        BellSchedule schedule = longDay();
        Course nowhere = course("Nowhere", 25, 25);

        assertEquals(ReminderPlan.Skipped.INVALID_PERIOD,
                ReminderPlan.problemWith(nowhere, Arrays.asList(schedule)));
    }

    @Test
    public void theDefaultReminderLeadIsStillFifteenMinutesOrLess() {
        // The lead is a user setting in minutes and has nothing to do with how long a
        // period is; the count change must not have reached it.
        assertEquals(5, ReminderPlan.MIN_LEAD_MINUTES);
        assertEquals(15, ReminderPlan.MAX_LEAD_MINUTES);
        assertTrue(ReminderPlan.supportsLeadMinutes(15));
        assertFalse(ReminderPlan.supportsLeadMinutes(20));
    }

    // ============================================ old data

    @Test
    public void aScheduleWithoutALengthReadsAsForty() {
        // What every schedule stored before this round looks like: starts and ends,
        // no length. It must open as the table it was, not be reset to a default.
        String json = "[{\"name\":\"Old\",\"starts\":[495,535],\"ends\":[535,575],"
                + "\"keywords\":[\"A\"]}]";

        List<BellSchedule> loaded = BellScheduleJson.parse(json);

        assertEquals(1, loaded.size());
        assertEquals("a missing length is forty",
                40, loaded.get(0).durationMinutes());
        assertEquals("and the stored count is what it had, not fifteen",
                2, loaded.get(0).periodCount());
        assertEquals(495, loaded.get(0).startMinute(1));
        assertEquals(575, loaded.get(0).endMinute(2));
    }

    @Test
    public void aScheduleWithItsOwnLengthSurvivesASerialiseRoundTrip() {
        List<BellSchedule> written = BellScheduleJson.parse(
                BellScheduleJson.write(Arrays.asList(longDay())));

        assertEquals(1, written.size());
        assertEquals(50, written.get(0).durationMinutes());
        assertEquals(20, written.get(0).periodCount());
        assertEquals(8 * 60, written.get(0).startMinute(1));
        assertEquals(8 * 60 + 50, written.get(0).endMinute(1));
    }

    @Test
    public void aScheduleWithItsOwnLengthRoundTripsThroughStorage() {
        CourseRepository repository = repositoryWithLive(longDay());
        repository.reload();

        BellSchedule stored = BellSchedules.byName(repository.getBellSchedules(),
                "Long day");
        assertNotNull(stored);
        assertEquals(50, stored.durationMinutes());
        assertEquals(20, stored.periodCount());
        assertEquals(8 * 60, stored.startMinute(1));
    }

    @Test
    public void savingAScheduleDoesNotTouchAnotherOnesLength() {
        CourseRepository.useStoreForNextInstance(new FakePreferences());
        CourseRepository repository = new CourseRepository((android.content.Context) null);
        repository.saveBellSchedules(new ArrayList<>(Arrays.asList(
                BellSchedules.standard(), longDay())));
        repository.reload();

        List<BellSchedule> schedules = repository.getBellSchedules();
        BellSchedule standard = BellSchedules.byName(schedules,
                BellSchedules.STANDARD_NAME);
        BellSchedule longDay = BellSchedules.byName(schedules, "Long day");

        assertNotNull(standard);
        assertNotNull(longDay);
        assertEquals("the standard table keeps forty",
                40, standard.durationMinutes());
        assertEquals("and its fifteen periods", 15, standard.periodCount());
        assertEquals("while the other keeps fifty", 50, longDay.durationMinutes());
        assertEquals("and its twenty", 20, longDay.periodCount());
    }
}
