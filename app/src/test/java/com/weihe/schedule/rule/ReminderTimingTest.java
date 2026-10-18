package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

/**
 * When a delivered alarm is still worth a notification.
 *
 * <p>This is the rule that a real device exposed: alarms are not always delivered
 * at the instant they were set for, and the old code dropped every delivery that
 * arrived at or after the start time. On a phone whose alarms are inexact -- or
 * whose battery manager holds broadcasts -- that turned "a few minutes late" into
 * "no reminder at all", which is indistinguishable from a broken feature.</p>
 *
 * <p>The boundaries are what matter, so each one is pinned: the start instant
 * itself, the last useful minute, and one minute past it.</p>
 */
public class ReminderTimingTest {

    /** A Monday. The class runs 08:00 - 09:40, warned about ten minutes early. */
    private static final LocalDate DAY = LocalDate.parse("2026-08-31");
    private static final LocalDateTime START = DAY.atTime(8, 0);

    @Test
    public void aDeliveryBeforeTheClassIsNotLate() {
        Reminder reminder = reminder();

        assertFalse(reminder.isLate(START.minusMinutes(10)));
        assertTrue(reminder.isStillUsefulAt(START.minusMinutes(10)));
        assertEquals(0L, reminder.lateMinutes(START.minusMinutes(10)));
    }

    @Test
    public void theStartInstantItselfCountsAsLate() {
        Reminder reminder = reminder();

        assertTrue("a delivery exactly on the bell has missed the lead time",
                reminder.isLate(START));
        assertTrue(reminder.isStillUsefulAt(START));
        assertEquals(0L, reminder.lateMinutes(START));
    }

    @Test
    public void aDeliveryInsideTheWindowIsStillWorthShowing() {
        Reminder reminder = reminder();
        LocalDateTime late = START.plusMinutes(Reminder.MAX_LATE_MINUTES);

        assertTrue(reminder.isLate(late));
        assertTrue("the last minute of the window still helps", reminder.isStillUsefulAt(late));
        assertEquals(Reminder.MAX_LATE_MINUTES, reminder.lateMinutes(late));
    }

    @Test
    public void aDeliveryPastTheWindowIsNoLongerWorthShowing() {
        Reminder reminder = reminder();
        LocalDateTime tooLate = START.plusMinutes(Reminder.MAX_LATE_MINUTES + 1);

        assertFalse("a class already half missed is not a reminder",
                reminder.isStillUsefulAt(tooLate));
        assertEquals(Reminder.MAX_LATE_MINUTES + 1, reminder.lateMinutes(tooLate));
    }

    @Test
    public void aMissingClockIsNotTreatedAsOnTime() {
        Reminder reminder = reminder();

        // Callers pass "now" explicitly; null must mean "do not announce", never
        // "announce as if it were on time".
        assertFalse(reminder.isLate(null));
        assertFalse(reminder.isStillUsefulAt(null));
        assertEquals(0L, reminder.lateMinutes(null));
    }

    @Test
    public void theWindowIsShortEnoughNotToAnnounceAClassAlreadyMissed() {
        // A period is about forty minutes. A window longer than one would let the
        // notification arrive after the class it describes has ended.
        assertTrue("MAX_LATE_MINUTES must stay under a period",
                Reminder.MAX_LATE_MINUTES < 40);
        assertTrue("and long enough to cover a delayed inexact alarm",
                Reminder.MAX_LATE_MINUTES >= 10);
    }

    /** The course the tests describe, with a ten-minute lead like the default. */
    private static Reminder reminder() {
        return new Reminder(1L, "Linear Algebra", "Prof. Li", "A203", "Test schedule",
                0xFF2878B5, 1, 2, DAY, START, START.minusMinutes(10), 10);
    }
}
