package com.weihe.schedule.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * The scheduling layer that can be reasoned about without a device: which alarm
 * belongs to which class, and what makes two classes different alarms.
 *
 * <p>{@link ReminderSchedule} touches no Android API, so all of this runs on a
 * plain JVM. That matters more here than it looks. An alarm's identity is the one
 * thing in the feature whose failure is silent and asymmetric: get it wrong in
 * one direction and two classes share a notification, in the other and a class
 * never gets one at all -- and neither shows up as a crash or an error anywhere.</p>
 *
 * <p>Reminders are produced through {@link ReminderPlan} rather than constructed,
 * because a reminder that the planner would not emit is not a case worth
 * defending.</p>
 */
public class ReminderScheduleTest {

    /** A Monday. Week 1 runs 2026-08-31 .. 2026-09-06. */
    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SCHEDULE = "Test schedule";

    private static final int LEAD = 10;

    // ============================================ what an entry records

    @Test
    public void slotsAreHandedOutInOrderFromTheFirstReservedNumber() {
        List<Reminder> reminders = planFor(mondayToFriday(), LEAD, TERM_START);

        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(reminders, ZONE);

        assertTrue("a week of classes should produce alarms", entries.size() >= 3);
        for (int index = 0; index < entries.size(); index++) {
            assertEquals(ReminderSchedule.FIRST_SLOT + index, entries.get(index).slot());
        }
    }

    @Test
    public void anEntryCarriesTheCourseAndTheClassDay() {
        List<Reminder> reminders = planFor(singleCourse(42L, 1, 1), LEAD, TERM_START);

        ReminderSchedule.Entry entry =
                ReminderSchedule.entriesFor(reminders, ZONE).get(0);

        assertEquals(42L, entry.courseId());
        assertEquals(TERM_START, entry.classDay());
        assertEquals(TERM_START.toEpochDay(), entry.epochDay());
        assertEquals(ReminderSchedule.FIRST_SLOT, entry.slot());
    }

    @Test
    public void anEntryHoldsTheInstantTheAlarmShouldFire() {
        List<Reminder> reminders = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START);

        ReminderSchedule.Entry entry =
                ReminderSchedule.entriesFor(reminders, ZONE).get(0);

        // Period 1 is 08:00 in the test schedule and the lead is ten minutes, so
        // 07:50 in Shanghai -- named as an absolute instant here, independently of
        // the code that produced it.
        assertEquals(OffsetDateTime.parse("2026-08-31T07:50+08:00").toInstant()
                .toEpochMilli(), entry.triggerAtMillis());
        assertEquals(reminders.get(0).triggerMillis(ZONE), entry.triggerAtMillis());
    }

    @Test
    public void theZoneDecidesTheInstantNotTheWallClock() {
        List<Reminder> reminders = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START);

        long shanghai = ReminderSchedule.entriesFor(reminders, ZONE)
                .get(0).triggerAtMillis();
        long utc = ReminderSchedule.entriesFor(reminders, ZoneId.of("UTC"))
                .get(0).triggerAtMillis();

        assertEquals("the same wall clock, eight hours apart as an instant",
                8L * 60L * 60L * 1000L, utc - shanghai);
    }

    @Test
    public void moreRemindersThanSlotsLeavesTheRestForTheNextRebuild() {
        List<Course> many = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            for (int period = 1; period <= 6; period++) {
                many.add(course(day * 100L + period, day, period));
            }
        }
        List<Reminder> reminders = planFor(many, LEAD, TERM_START, 0);
        assertTrue("the plan itself should be longer than the cap",
                reminders.size() > ReminderSchedule.MAX_SLOTS);

        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(reminders, ZONE);

        assertEquals(ReminderSchedule.MAX_SLOTS, entries.size());
        // And they are the soonest ones, not an arbitrary slice.
        for (int index = 0; index < entries.size(); index++) {
            assertEquals(reminders.get(index).triggerMillis(ZONE),
                    entries.get(index).triggerAtMillis());
        }
    }

    @Test
    public void anUnchangedPlanRebuiltTwiceGivesTheSameLedger() {
        List<Course> courses = mondayToFriday();

        List<ReminderSchedule.Entry> once =
                ReminderSchedule.entriesFor(planFor(courses, LEAD, TERM_START), ZONE);
        List<ReminderSchedule.Entry> twice =
                ReminderSchedule.entriesFor(planFor(courses, LEAD, TERM_START), ZONE);

        assertEquals("rescheduling the same timetable must not churn the ledger",
                once, twice);
    }

    // ============================================ identity of one class

    @Test
    public void oneClassOnOneDayAlwaysGetsTheSameNotificationId() {
        int first = ReminderSchedule.notificationId(7L, TERM_START.toEpochDay());
        int second = ReminderSchedule.notificationId(7L, TERM_START.toEpochDay());

        assertEquals(first, second);
    }

    @Test
    public void theSameClassOnDifferentDaysGetsDifferentNotificationIds() {
        int monday = ReminderSchedule.notificationId(7L, TERM_START.toEpochDay());
        int tuesday = ReminderSchedule.notificationId(7L, TERM_START.plusDays(1).toEpochDay());

        assertFalse("a class that meets twice must not collapse into one notification",
                monday == tuesday);
    }

    @Test
    public void twoCoursesAtTheSameHourDoNotShareAnIdentity() {
        // Both at 08:00 on the same Monday, two different classes.
        List<Course> courses = Arrays.asList(
                course(1L, 1, 1), course(2L, 1, 1));

        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(courses, LEAD, TERM_START), ZONE);

        assertEquals(2, entries.size());
        assertEquals("same instant, two alarms",
                entries.get(0).triggerAtMillis(), entries.get(1).triggerAtMillis());
        assertFalse("same instant, two slots", entries.get(0).slot() == entries.get(1).slot());
        assertFalse("same instant, two notifications",
                ReminderSchedule.notificationId(entries.get(0))
                        == ReminderSchedule.notificationId(entries.get(1)));
    }

    @Test
    public void aWholeWeeksNotificationsAreAllDistinct() {
        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(mondayToFriday(), LEAD, TERM_START), ZONE);
        List<Integer> ids = new ArrayList<>();
        for (ReminderSchedule.Entry entry : entries) {
            ids.add(Integer.valueOf(ReminderSchedule.notificationId(entry)));
        }

        assertEquals("every class of the week needs its own notification",
                ids.size(), new java.util.HashSet<>(ids).size());
    }

    @Test
    public void anEntryIdentifiesItsOwnAlarmAndNoOther() {
        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(singleCourse(1L, 1, 1), LEAD, TERM_START), ZONE);
        ReminderSchedule.Entry entry = entries.get(0);

        assertTrue(entry.describes(entry.slot(), entry.triggerAtMillis()));
        assertFalse("an alarm from another slot is not this one",
                entry.describes(entry.slot() + 1, entry.triggerAtMillis()));
        assertFalse("an alarm for another instant is not this one",
                entry.describes(entry.slot(), entry.triggerAtMillis() + 1));
    }

    @Test
    public void findReturnsNothingForASlotThatHoldsNoAlarm() {
        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(singleCourse(1L, 1, 1), LEAD, TERM_START), ZONE);

        assertNotNull(ReminderSchedule.findBySlot(entries, ReminderSchedule.FIRST_SLOT));
        assertNull(ReminderSchedule.findBySlot(entries, ReminderSchedule.FIRST_SLOT + 5));
        assertNull(ReminderSchedule.findBySlot(Collections.<ReminderSchedule.Entry>emptyList(),
                ReminderSchedule.FIRST_SLOT));
        assertNull(ReminderSchedule.findBySlot(null, ReminderSchedule.FIRST_SLOT));
    }

    @Test
    public void onlyTheSlotsThisClassHandsOutAreRecognised() {
        assertTrue(ReminderSchedule.isOurSlot(ReminderSchedule.FIRST_SLOT));
        assertTrue(ReminderSchedule.isOurSlot(
                ReminderSchedule.FIRST_SLOT + ReminderSchedule.MAX_SLOTS - 1));
        assertFalse(ReminderSchedule.isOurSlot(ReminderSchedule.FIRST_SLOT - 1));
        assertFalse(ReminderSchedule.isOurSlot(
                ReminderSchedule.FIRST_SLOT + ReminderSchedule.MAX_SLOTS));
        assertFalse("a stray zero is not a slot", ReminderSchedule.isOurSlot(0));
    }

    // ============================================ what is refused

    @Test
    public void aSlotFromOutsideTheRangeIsRefused() {
        assertFalse(ReminderSchedule.isUsable(newEntry(0, 1L, TERM_START.toEpochDay(), 1000L)));
        assertFalse(ReminderSchedule.isUsable(newEntry(99, 1L, TERM_START.toEpochDay(), 1000L)));
    }

    @Test
    public void anEntryWithNoCourseIsRefused() {
        assertFalse(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 0L, TERM_START.toEpochDay(), 1000L)));
    }

    @Test
    public void anEntryWithNoRealInstantIsRefused() {
        assertFalse(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 1L, TERM_START.toEpochDay(), 0L)));
        assertFalse(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 1L, TERM_START.toEpochDay(), -1L)));
    }

    @Test
    public void anEntryWithAnImpossibleDayIsRefused() {
        assertFalse(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 1L, Long.MIN_VALUE, 1000L)));
        assertFalse(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 1L, 999999L, 1000L)));
    }

    @Test
    public void aGoodEntryIsUsable() {
        assertTrue(ReminderSchedule.isUsable(
                newEntry(ReminderSchedule.FIRST_SLOT, 1L, TERM_START.toEpochDay(), 1000L)));
        assertFalse(ReminderSchedule.isUsable(null));
    }

    @Test
    public void aClassThePlannerCannotPlaceIsNeverRegistered() {
        // Period 99 does not exist, so the planner refuses it; there is nothing
        // left to hand to the alarm manager.
        Course broken = course(1L, 1, 1);
        broken.endPeriod = 99;

        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(Arrays.asList(broken), LEAD, TERM_START), ZONE);

        assertTrue("no time was known, so no alarm may be set", entries.isEmpty());
    }

    @Test
    public void anEmptyPlanRegistersNothing() {
        assertTrue(ReminderSchedule.entriesFor(
                Collections.<Reminder>emptyList(), ZONE).isEmpty());
        assertTrue(ReminderSchedule.entriesFor(null, ZONE).isEmpty());
        assertTrue(ReminderSchedule.entriesFor(
                planFor(mondayToFriday(), LEAD, TERM_START), null).isEmpty());
    }

    // ============================================ the ledger round trip

    @Test
    public void theLedgerSurvivesBeingWrittenAndReadBack() {
        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(mondayToFriday(), LEAD, TERM_START), ZONE);

        assertEquals(entries, ReminderSchedule.parse(ReminderSchedule.write(entries)));
    }

    @Test
    public void theWrittenLedgerIsOrderedBySlot() {
        List<ReminderSchedule.Entry> entries =
                ReminderSchedule.entriesFor(planFor(mondayToFriday(), LEAD, TERM_START), ZONE);

        List<ReminderSchedule.Entry> read = ReminderSchedule.parse(
                ReminderSchedule.write(entries));

        for (int index = 1; index < read.size(); index++) {
            assertTrue(read.get(index).slot() > read.get(index - 1).slot());
        }
    }

    @Test
    public void anUnreadableLedgerReadsBackAsNothingRatherThanFailing() {
        for (String garbage : new String[] { null, "", "   ", "not json", "{", "{}",
                "[1,2,3]", "[{\"slot\":\"x\"}]" }) {
            List<ReminderSchedule.Entry> read = ReminderSchedule.parse(garbage);
            assertNotNull("parsing must never return null: " + garbage, read);
            assertTrue("nothing usable in: " + garbage, read.isEmpty());
        }
    }

    @Test
    public void oneBadRecordDoesNotDiscardTheGoodOnes() {
        String good = ReminderSchedule.write(ReminderSchedule.entriesFor(
                planFor(mondayToFriday(), LEAD, TERM_START), ZONE));
        // Splice a malformed entry into an otherwise valid ledger.
        String mixed = good.substring(0, good.length() - 1)
                + ",{\"slot\":99,\"courseId\":0,\"epochDay\":0,\"triggerAt\":0}]";

        List<ReminderSchedule.Entry> read = ReminderSchedule.parse(mixed);

        assertEquals("the good records must survive the bad one",
                ReminderSchedule.parse(good).size(), read.size());
    }

    @Test
    public void writingAnEmptyLedgerGivesSomethingReadable() {
        String written = ReminderSchedule.write(
                Collections.<ReminderSchedule.Entry>emptyList());

        assertNotNull(written);
        assertTrue(ReminderSchedule.parse(written).isEmpty());
    }

    // ============================================ the plan changing

    @Test
    public void changingTheLeadTimeMovesEveryAlarm() {
        List<Course> courses = mondayToFriday();

        long atFive = ReminderSchedule.entriesFor(planFor(courses, 5, TERM_START), ZONE)
                .get(0).triggerAtMillis();
        long atFifteen = ReminderSchedule.entriesFor(planFor(courses, 15, TERM_START), ZONE)
                .get(0).triggerAtMillis();

        assertEquals("ten minutes of difference, in milliseconds",
                10L * 60L * 1000L, atFive - atFifteen);
    }

    @Test
    public void changingTheLeadTimeChangesTheLedgerRatherThanAddingToIt() {
        List<Course> courses = mondayToFriday();

        List<ReminderSchedule.Entry> before =
                ReminderSchedule.entriesFor(planFor(courses, 5, TERM_START), ZONE);
        List<ReminderSchedule.Entry> after =
                ReminderSchedule.entriesFor(planFor(courses, 15, TERM_START), ZONE);

        assertEquals("same slots, so nothing can be left behind",
                before.size(), after.size());
        assertFalse("and the instants really did move", before.equals(after));
        for (int index = 0; index < before.size(); index++) {
            assertEquals(before.get(index).slot(), after.get(index).slot());
            assertTrue(after.get(index).triggerAtMillis()
                    < before.get(index).triggerAtMillis());
        }
    }

    @Test
    public void withRemindersOffThereIsNothingToRegister() {
        // The scheduler asks the planner for nothing when the switch is off, which
        // is exactly this: an empty plan, and so an empty ledger.
        assertTrue(ReminderSchedule.entriesFor(
                Collections.<Reminder>emptyList(), ZONE).isEmpty());
        assertEquals("[]", ReminderSchedule.write(
                Collections.<ReminderSchedule.Entry>emptyList()));
    }

    // ============================================ the notification wording

    @Test
    public void theNotificationNamesTheTimeTheVenueTheTeacherAndTheLead() {
        Reminder reminder = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(reminder);

        assertTrue(detail, detail.contains("08:00"));
        assertTrue(detail, detail.contains("A203"));
        assertTrue(detail, detail.contains("Prof. Li"));
        assertTrue(detail, detail.contains("10"));
    }

    @Test
    public void anEmptyVenueOrTeacherLeavesNoGapBehind() {
        Course bare = course(1L, 1, 1);
        bare.location = "";
        bare.teacher = "";
        Reminder reminder = planFor(Arrays.asList(bare), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(reminder);

        // The periods are always known, so they are always the one separator; what
        // the two empty fields must not do is add two more.
        assertEquals("only the period, and nothing for the two empty fields",
                1, detail.split("\u00B7", -1).length - 1);
        assertTrue(detail, detail.contains("08:00"));
        assertTrue(detail, detail.contains("\u7B2C 1 \u8282"));
        assertFalse("a blank field must not leave a stray separator", detail.contains("\u00B7 \u00B7"));
    }

    @Test
    public void aVenueWithoutATeacherStillReadsProperly() {
        Course bare = course(1L, 1, 1);
        bare.teacher = "";
        Reminder reminder = planFor(Arrays.asList(bare), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(reminder);

        assertTrue(detail, detail.contains("A203"));
        assertEquals("the period and the venue, and nothing for the missing teacher",
                2, detail.split("\u00B7", -1).length - 1);
        assertFalse(detail.contains("\u00B7 \u00B7"));
    }

    @Test
    public void theNotificationSaysWhatItIsAndWhichPeriods() {
        Course spanning = course(1L, 1, 3);
        spanning.endPeriod = 4;
        Reminder reminder = planFor(Arrays.asList(spanning), LEAD, TERM_START).get(0);

        String title = ReminderNotifications.titleOf(reminder);
        String detail = ReminderNotifications.detailOf(reminder);

        assertTrue(title, title.contains("\u4E0A\u8BFE\u63D0\u9192"));
        assertTrue(title, title.contains(reminder.courseName()));
        assertEquals("\u7B2C 3-4 \u8282", ReminderNotifications.periodLabel(reminder));
        assertTrue(detail, detail.contains("\u7B2C 3-4 \u8282"));
    }

    @Test
    public void aOnePeriodClassIsNotWrittenAsARange() {
        Reminder reminder = planFor(singleCourse(1L, 2, 2), LEAD, TERM_START).get(0);

        assertEquals("\u7B2C 2 \u8282", ReminderNotifications.periodLabel(reminder));
        assertFalse(ReminderNotifications.periodLabel(reminder).contains("-"));
    }

    @Test
    public void anOnTimeDeliveryKeepsTheCountdown() {
        Reminder reminder = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(reminder, reminder.triggerAt());

        assertTrue(detail, detail.contains(LEAD + " \u5206\u949F\u540E\u5F00\u59CB"));
        assertFalse("an on-time reminder has no delay to explain",
                detail.contains("\u5DF2\u5230\u4E0A\u8BFE\u65F6\u95F4"));
    }

    @Test
    public void aLateDeliveryNamesTheDelayInsteadOfAnElapsedCountdown() {
        Reminder reminder = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(
                reminder, reminder.startAt().plusMinutes(6));

        assertTrue(detail, detail.contains("\u5DF2\u5230\u4E0A\u8BFE\u65F6\u95F4"));
        assertTrue("the delay is named so it can be told from a wrong time",
                detail.contains("\u5EF6\u8FDF 6 \u5206\u949F"));
        assertFalse("a countdown that has already elapsed would be a lie",
                detail.contains("\u5206\u949F\u540E\u5F00\u59CB"));
    }

    @Test
    public void theWeekdayIsNamedInChineseWhateverTheDeviceLocale() {
        // The app's screens are Chinese on every phone. A date pattern letter would
        // have printed whatever the device's locale said -- "Tuesday" on this build
        // machine -- so the notification read in two languages at once.
        Reminder reminder = planFor(singleCourse(1L, 1, 1), LEAD, TERM_START).get(0);

        String detail = ReminderNotifications.detailOf(reminder);

        // TERM_START is a Monday.
        assertTrue(detail, detail.contains("\u5468\u4E00"));
        for (String english : new String[] {"Monday", "Tuesday", "Wednesday",
                "Thursday", "Friday", "Saturday", "Sunday"}) {
            assertFalse("a device locale must not decide this: " + detail,
                    detail.contains(english));
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static ReminderSchedule.Entry newEntry(int slot, long courseId,
            long epochDay, long triggerAtMillis) {
        // Goes through the parser, because that is the only way an Entry is built
        // from outside the class -- and it is also how a corrupt one arrives.
        return ReminderSchedule.parse("[{\"slot\":" + slot + ",\"courseId\":" + courseId
                + ",\"epochDay\":" + epochDay + ",\"triggerAt\":" + triggerAtMillis
                + "}]").isEmpty()
                ? null
                : ReminderSchedule.parse("[{\"slot\":" + slot + ",\"courseId\":" + courseId
                        + ",\"epochDay\":" + epochDay + ",\"triggerAt\":" + triggerAtMillis
                        + "}]").get(0);
    }

    private static List<Reminder> planFor(List<Course> courses, int leadMinutes,
            LocalDate from) {
        return planFor(courses, leadMinutes, from, ReminderPlan.DEFAULT_MAX_REMINDERS);
    }

    private static List<Reminder> planFor(List<Course> courses, int leadMinutes,
            LocalDate from, int cap) {
        return ReminderPlan.upcoming(courses, semester(), schedules(), from, 7,
                leadMinutes, from.atStartOfDay().minusDays(1), cap).reminders();
    }

    private static List<Course> singleCourse(long id, int day, int period) {
        return Arrays.asList(course(id, day, period));
    }

    private static List<Course> mondayToFriday() {
        List<Course> courses = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            courses.add(course(day, day, 1));
        }
        return courses;
    }

    private static Semester semester() {
        return new Semester(1L, TERM_START, "Test term");
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
        int[] starts = new int[ScheduleUtils.PERIODS_PER_DAY];
        int[] ends = new int[ScheduleUtils.PERIODS_PER_DAY];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = 8 * 60 + i * 60;
            ends[i] = starts[i] + 45;
        }
        return Arrays.asList(new BellSchedule(SCHEDULE, starts, ends,
                Collections.<String>emptyList()));
    }
}
