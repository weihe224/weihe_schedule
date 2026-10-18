package com.weihe.schedule.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * What a rebuild has to guarantee, tested where it can be: the plan, the ledger
 * and the event list.
 *
 * <p>A system event does not do anything new -- it calls the same
 * {@code ReminderScheduler.sync} the screens call. So what is worth pinning here
 * is the part that is pure: that rebuilding twice at the same moment gives the
 * same answer, that rebuilding after the timetable, the clock or the zone moved
 * gives the answer the new state implies, and that an alarm left over from before
 * a rebuild can no longer announce anything.</p>
 *
 * <p>The Android half -- that a reboot really delivers the broadcast, that
 * {@code AlarmManager} really fires, that a refused notification permission really
 * silences the feature -- cannot be shown here and is not claimed here. It needs a
 * device, and the receiver itself is deliberately thin enough to be verified by
 * reading it and by watching one boot.</p>
 */
public class ReminderRestoreTest {

    /** A Monday. Week 1 runs 2026-08-31 .. 2026-09-06. */
    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final String SCHEDULE = "Test schedule";
    private static final int LEAD = 10;

    // ============================================ rebuilding is idempotent

    @Test
    public void theSameMomentRebuildsTheSameLedger() {
        List<Course> courses = mondayToFriday();
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        List<ReminderSchedule.Entry> first = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);
        List<ReminderSchedule.Entry> second = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);

        assertEquals("a repeated event must not add a second alarm per class",
                first, second);
        assertEquals("nor must the ledger grow",
                ReminderSchedule.write(first), ReminderSchedule.write(second));
    }

    @Test
    public void repeatingARebuildDoesNotChangeACorrectlyBuiltLedger() {
        List<Course> courses = mondayToFriday();
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);
        List<ReminderSchedule.Entry> ledger = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);
        String written = ReminderSchedule.write(ledger);

        // Three more events in a row, as a boot, a clock change and an update
        // arriving together would.
        for (int round = 0; round < 3; round++) {
            ledger = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);
        }

        assertEquals(written, ReminderSchedule.write(ledger));
    }

    @Test
    public void aRebuildFollowsTheTimetableAsItIsNow() {
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);
        List<Course> before = mondayToFriday();
        List<Course> after = new ArrayList<>(before);
        Course added = course(99L, 6, 3);
        after.add(added);

        Set<Long> beforeIds = courseIds(rebuild(before, LEAD, TERM_START, now, SHANGHAI));
        Set<Long> afterIds = courseIds(rebuild(after, LEAD, TERM_START, now, SHANGHAI));

        assertFalse("the class that was there before must not vanish",
                Collections.disjoint(beforeIds, afterIds));
        assertTrue("the class added since must be picked up",
                afterIds.contains(99L));
    }

    @Test
    public void turningRemindersOffLeavesNothingToRegister() {
        // The scheduler never reaches the planner when the switch is off, so the
        // rebuild it performs is exactly this: an empty plan and an empty ledger.
        List<ReminderSchedule.Entry> ledger = ReminderSchedule.entriesFor(
                Collections.<Reminder>emptyList(), SHANGHAI);

        assertTrue(ledger.isEmpty());
        assertEquals("[]", ReminderSchedule.write(ledger));
    }

    // ============================================ the clock and the zone move

    @Test
    public void classesThatHaveAlreadyStartedAreNotRegisteredAgain() {
        List<Course> courses = mondayToFriday();
        LocalDateTime beforeMonday = TERM_START.atStartOfDay().minusDays(1);
        LocalDateTime duringTuesday = TERM_START.plusDays(1).atStartOfDay();

        List<ReminderSchedule.Entry> onSunday =
                rebuild(courses, LEAD, TERM_START, beforeMonday, SHANGHAI);
        List<ReminderSchedule.Entry> onTuesday =
                rebuild(courses, LEAD, TERM_START.plusDays(1), duringTuesday, SHANGHAI);

        assertTrue("Monday's class is still ahead on the Sunday",
                courseIds(onSunday).contains(1L));
        // The course is weekly, so it reappears for next Monday -- what must not
        // reappear is the occurrence whose moment has passed.
        for (ReminderSchedule.Entry entry : onTuesday) {
            assertFalse("the Monday that has gone must not be armed again",
                    entry.classDay().equals(TERM_START));
            assertTrue("and nothing before the day asked about may be armed",
                    entry.classDay().compareTo(TERM_START.plusDays(1)) >= 0);
        }
        assertTrue("while Tuesday's own class is still ahead",
                courseIds(onTuesday).contains(2L));
    }

    @Test
    public void movingTheDateForwardRecomputesTheWindow() {
        List<Course> courses = mondayToFriday();

        List<ReminderSchedule.Entry> monday = rebuild(courses, LEAD, TERM_START,
                TERM_START.atStartOfDay().minusDays(1), SHANGHAI);
        List<ReminderSchedule.Entry> thursday = rebuild(courses, LEAD,
                TERM_START.plusDays(3), TERM_START.plusDays(3).atStartOfDay().minusDays(1),
                SHANGHAI);

        assertFalse("a plan is about the days ahead, not the days behind",
                monday.equals(thursday));
        for (ReminderSchedule.Entry entry : thursday) {
            assertTrue("every instant must sit on or after the day asked about",
                    entry.classDay().compareTo(TERM_START.plusDays(3)) >= 0);
        }
    }

    @Test
    public void anotherZoneKeepsTheSameClassesAndMovesTheInstants() {
        List<Course> courses = mondayToFriday();
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        List<ReminderSchedule.Entry> here = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);
        List<ReminderSchedule.Entry> there = rebuild(courses, LEAD, TERM_START, now, LONDON);

        assertEquals("the timetable did not change, so neither did the plan",
                here.size(), there.size());
        for (int index = 0; index < here.size(); index++) {
            ReminderSchedule.Entry a = here.get(index);
            ReminderSchedule.Entry b = there.get(index);
            assertEquals("same class", a.courseId(), b.courseId());
            assertEquals("same class day", a.epochDay(), b.epochDay());
            assertEquals("same slot", a.slot(), b.slot());
            // The zone is the only difference: the same wall-clock class, stored as
            // the instant that wall clock means in each zone.
            assertEquals(localOf(a, SHANGHAI), localOf(b, LONDON));
            assertFalse("an instant is not the same in two zones",
                    a.triggerAtMillis() == b.triggerAtMillis());
        }
    }

    // ============================================ what must never be guessed

    @Test
    public void aCourseWhosePeriodTheScheduleDoesNotReachGetsNoReminder() {
        List<BellSchedule> shortSchedule = Arrays.asList(shortPeriods());
        Course course = course(1L, 1, 12);
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        ReminderPlan.Plan plan = ReminderPlan.upcoming(Arrays.asList(course),
                new Semester(1L, TERM_START, "Test term"), shortSchedule, TERM_START, 7,
                LEAD, now, ReminderPlan.DEFAULT_MAX_REMINDERS);

        assertTrue("no start time can be established, so nothing may be armed",
                plan.reminders().isEmpty());
        assertEquals("and the refusal must say why",
                1, plan.skippedFor(ReminderPlan.Skipped.INVALID_PERIOD).size());
    }

    @Test
    public void withNoSchedulesAtAllNothingIsArmed() {
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        ReminderPlan.Plan plan = ReminderPlan.upcoming(mondayToFriday(),
                new Semester(1L, TERM_START, "Test term"),
                Collections.<BellSchedule>emptyList(), TERM_START, 7, LEAD, now,
                ReminderPlan.DEFAULT_MAX_REMINDERS);

        assertTrue("no start time can be read, so nothing may be armed",
                plan.reminders().isEmpty());
        assertFalse("and every class day must say so",
                plan.skippedFor(ReminderPlan.Skipped.NO_SCHEDULES).isEmpty());
        assertEquals("with nothing but the missing schedules to blame",
                plan.skipped().size(),
                plan.skippedFor(ReminderPlan.Skipped.NO_SCHEDULES).size());
    }

    @Test
    public void aWeekWithNothingLeftToRemindProducesAnEmptyLedger() {
        // Every class of the term is behind us: the plan is legitimately empty, and
        // an empty ledger is the correct answer rather than a failure.
        List<Course> courses = mondayToFriday();
        LocalDate afterTheTerm = TERM_START.plusWeeks(ScheduleUtils.SEMESTER_WEEKS + 1);

        List<ReminderSchedule.Entry> ledger = rebuild(courses, LEAD, afterTheTerm,
                afterTheTerm.atStartOfDay(), SHANGHAI);

        assertTrue(ledger.isEmpty());
    }

    // ============================================ identity of an alarm

    @Test
    public void twoCoursesAtTheSameMomentGetDistinctSlotsAndNotifications() {
        Course first = course(1L, 1, 1);
        Course second = course(2L, 1, 1);
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        List<ReminderSchedule.Entry> ledger = rebuild(Arrays.asList(first, second),
                LEAD, TERM_START, now, SHANGHAI);

        assertEquals(2, ledger.size());
        assertFalse("two classes must not share a slot",
                ledger.get(0).slot() == ledger.get(1).slot());
        assertFalse("nor a notification id",
                ReminderSchedule.notificationId(ledger.get(0))
                        == ReminderSchedule.notificationId(ledger.get(1)));
    }

    @Test
    public void theSameClassOnAnotherDayIsANewEvent() {
        // Monday and Tuesday classes of one course, were the timetable to give it
        // two: the day is part of what identifies an event, so a notification sent
        // on Monday cannot suppress Tuesday's.
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);
        List<Course> courses = Arrays.asList(course(7L, 1, 1), course(7L, 2, 1));

        List<ReminderSchedule.Entry> ledger = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);

        assertEquals(2, ledger.size());
        assertFalse("same course, different day, so a different event",
                ledger.get(0).epochDay() == ledger.get(1).epochDay());
        assertFalse(ReminderSchedule.notificationId(ledger.get(0))
                == ReminderSchedule.notificationId(ledger.get(1)));
    }

    @Test
    public void anAlarmFromBeforeARebuildNoLongerMatchesTheLedger() {
        List<Course> courses = mondayToFriday();
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);
        List<ReminderSchedule.Entry> before = rebuild(courses, 15, TERM_START, now, SHANGHAI);
        List<ReminderSchedule.Entry> after = rebuild(courses, 5, TERM_START, now, SHANGHAI);

        assertFalse(before.isEmpty());
        assertEquals("slots are reused, so nothing is left registered",
                before.size(), after.size());
        for (int index = 0; index < before.size(); index++) {
            ReminderSchedule.Entry stale = before.get(index);
            ReminderSchedule.Entry current = after.get(index);
            assertEquals(stale.slot(), current.slot());
            assertFalse("the old alarm must no longer describe a live entry",
                    current.describes(stale.slot(), stale.triggerAtMillis()));
            assertTrue("while the new one does",
                    current.describes(current.slot(), current.triggerAtMillis()));
        }
    }

    @Test
    public void aRebuildNeverLeavesTwoEntriesOnOneSlot() {
        List<Course> courses = mondayToFriday();
        LocalDateTime now = TERM_START.atStartOfDay().minusDays(1);

        List<ReminderSchedule.Entry> ledger = rebuild(courses, LEAD, TERM_START, now, SHANGHAI);
        Set<Integer> slots = new HashSet<>();
        for (ReminderSchedule.Entry entry : ledger) {
            assertTrue("slot " + entry.slot() + " appears twice", slots.add(entry.slot()));
        }
    }

    // ============================================ event recognition

    @Test
    public void onlyTheEventsThatInvalidateThePlanAreRecognised() {
        for (String action : new String[] {
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                // Granting exact alarms is what turns a plan that may arrive late
                // into one that arrives on the minute, so the alarms have to be
                // registered again through the new route.
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" }) {
            assertTrue(action + " must rebuild the plan",
                    ReminderRestoreReceiver.isRestoreEvent(action));
        }
    }

    @Test
    public void eventsThatCannotBeDeliveredOrDoNotMatterAreIgnored() {
        // DATE_CHANGED and PACKAGE_REPLACED are not on the platform's exemption
        // list, so a manifest receiver would never be called: an entry for them
        // would be decoration. The app's own alarm action belongs to the other
        // receiver.
        for (String action : new String[] {
                "android.intent.action.DATE_CHANGED",
                "android.intent.action.PACKAGE_REPLACED",
                "android.intent.action.PACKAGE_ADDED",
                "android.intent.action.SCREEN_ON",
                "com.weihe.schedule.action.REMIND",
                "",
                null }) {
            assertFalse(String.valueOf(action) + " must be ignored",
                    ReminderRestoreReceiver.isRestoreEvent(action));
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** One rebuild, exactly as the receiver performs it: plan, then ledger. */
    private static List<ReminderSchedule.Entry> rebuild(List<Course> courses,
            int leadMinutes, LocalDate from, LocalDateTime now, ZoneId zone) {
        List<Reminder> reminders = ReminderPlan.upcoming(courses,
                new Semester(1L, TERM_START, "Test term"), schedules(), from, 7,
                leadMinutes, now, ReminderPlan.DEFAULT_MAX_REMINDERS).reminders();
        return ReminderSchedule.entriesFor(reminders, zone);
    }

    private static LocalDateTime localOf(ReminderSchedule.Entry entry, ZoneId zone) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.triggerAtMillis()), zone);
    }

    private static Set<Long> courseIds(List<ReminderSchedule.Entry> entries) {
        Set<Long> ids = new HashSet<>();
        for (ReminderSchedule.Entry entry : entries) {
            ids.add(entry.courseId());
        }
        return ids;
    }

    private static List<Course> mondayToFriday() {
        List<Course> courses = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            courses.add(course(day, day, 1));
        }
        return courses;
    }

    private static Course course(long id, int dayOfWeek, int period) {
        Course course = new Course();
        course.id = id;
        course.name = "Course " + id;
        course.teacher = "Prof. Li";
        course.location = "A203";
        course.dayOfWeek = dayOfWeek;
        course.startPeriod = period;
        course.endPeriod = period;
        course.startWeek = 1;
        course.endWeek = ScheduleUtils.SEMESTER_WEEKS;
        course.parity = Course.PARITY_ALL;
        course.scheduleName = SCHEDULE;
        return course;
    }

    /** Periods an hour apart from 08:00, named so the planner always picks it. */
    private static List<BellSchedule> schedules() {
        return Arrays.asList(fullPeriods());
    }

    private static BellSchedule fullPeriods() {
        int[] starts = new int[ScheduleUtils.PERIODS_PER_DAY];
        int[] ends = new int[ScheduleUtils.PERIODS_PER_DAY];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = 8 * 60 + i * 60;
            ends[i] = starts[i] + 45;
        }
        return new BellSchedule(SCHEDULE, starts, ends,
                Collections.<String>emptyList());
    }

    /** A schedule that stops at period 4, so period 12 does not exist in it. */
    private static BellSchedule shortPeriods() {
        int[] starts = new int[4];
        int[] ends = new int[4];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = 8 * 60 + i * 60;
            ends[i] = starts[i] + 45;
        }
        return new BellSchedule(SCHEDULE, starts, ends,
                Collections.<String>emptyList());
    }
}
