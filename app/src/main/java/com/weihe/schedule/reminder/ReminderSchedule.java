package com.weihe.schedule.reminder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.weihe.schedule.data.MiniJson;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * The list of alarms that are currently registered, as plain data.
 *
 * <p>This is the part of scheduling that can be reasoned about without a device:
 * which alarm belongs to which class, what identifies it, and what survives a
 * round trip through storage. It touches no Android API on purpose, so all of it
 * runs under a plain JVM test -- which matters, because the identity rules below
 * are exactly where a mistake turns into either a missing reminder or two
 * notifications for one class.</p>
 *
 * <h2>Why a slot rather than a hash of the course</h2>
 *
 * <p>{@code PendingIntent} identity is a {@code requestCode}, an int. Deriving it
 * from a course id would mean folding a long into an int, and two courses whose
 * ids differ only above the 32nd bit would then share an alarm -- one of them
 * silently replacing the other. Instead the slot is a small index assigned in
 * trigger order, and the mapping from slot to class is kept here, in storage. It
 * costs one small string and removes the collision case entirely.</p>
 *
 * <p>The slot is also why cancelling is exact: a reschedule cancels every slot it
 * recorded last time before registering anything, so an alarm can never be left
 * behind by a changed plan.</p>
 */
public final class ReminderSchedule {

    /** Slots are handed out from here, so a stray zero can never look like one. */
    public static final int FIRST_SLOT = 1000;

    /** How many alarms may be registered at once. */
    public static final int MAX_SLOTS = ReminderPlan.DEFAULT_MAX_REMINDERS;

    private ReminderSchedule() {
    }

    /** One registered alarm: which slot, which class, and when it should fire. */
    public static final class Entry {

        private final int slot;
        private final long courseId;
        private final long epochDay;
        private final long triggerAtMillis;

        Entry(int slot, long courseId, long epochDay, long triggerAtMillis) {
            this.slot = slot;
            this.courseId = courseId;
            this.epochDay = epochDay;
            this.triggerAtMillis = triggerAtMillis;
        }

        public int slot() {
            return slot;
        }

        public long courseId() {
            return courseId;
        }

        /** The nominal class day, as {@link LocalDate#toEpochDay()}. */
        public long epochDay() {
            return epochDay;
        }

        public LocalDate classDay() {
            return LocalDate.ofEpochDay(epochDay);
        }

        public long triggerAtMillis() {
            return triggerAtMillis;
        }

        /**
         * Whether a firing alarm is the one this entry describes.
         *
         * <p>The alarm carries its own trigger time as an extra, and the receiver
         * checks it against the entry. An alarm whose cancellation failed -- or one
         * left over from a previous plan on a recycled slot -- then finds itself
         * unclaimed and is dropped instead of notifying about the wrong class.</p>
         */
        public boolean describes(int slot, long firedAtMillis) {
            return this.slot == slot && triggerAtMillis == firedAtMillis;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Entry)) {
                return false;
            }
            Entry that = (Entry) other;
            return slot == that.slot && courseId == that.courseId
                    && epochDay == that.epochDay
                    && triggerAtMillis == that.triggerAtMillis;
        }

        @Override
        public int hashCode() {
            int result = slot;
            result = 31 * result + Long.valueOf(courseId).hashCode();
            result = 31 * result + Long.valueOf(epochDay).hashCode();
            result = 31 * result + Long.valueOf(triggerAtMillis).hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "slot " + slot + " course " + courseId + " on " + classDay()
                    + " at " + triggerAtMillis;
        }
    }

    /** The slot for the n-th reminder of a plan, counting from zero. */
    public static int slotOf(int index) {
        return FIRST_SLOT + index;
    }

    /** Whether {@code slot} is one this class could have handed out. */
    public static boolean isOurSlot(int slot) {
        return slot >= FIRST_SLOT && slot < FIRST_SLOT + MAX_SLOTS;
    }

    /**
     * The alarms to register for a plan, in trigger order.
     *
     * <p>The plan arrives already sorted and already free of classes that cannot
     * be placed, so the only work here is assigning slots and resolving each
     * moment to an instant. Anything past {@link #MAX_SLOTS} is left for the next
     * rebuild, which is what keeps the number of registered alarms bounded.</p>
     */
    public static List<Entry> entriesFor(List<Reminder> reminders, ZoneId zone) {
        List<Entry> entries = new ArrayList<>();
        if (reminders == null || zone == null) {
            return entries;
        }
        int limit = Math.min(reminders.size(), MAX_SLOTS);
        for (int index = 0; index < limit; index++) {
            Reminder reminder = reminders.get(index);
            if (reminder == null) {
                continue;
            }
            Entry entry = new Entry(slotOf(entries.size()), reminder.courseId(),
                    reminder.classDay().toEpochDay(), reminder.triggerMillis(zone));
            if (isUsable(entry)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * Whether an entry is worth registering.
     *
     * <p>An entry read back from storage has been out of the app's hands, so it is
     * checked rather than trusted: a slot outside the range, a course id of zero,
     * a day that cannot be a date, or a trigger time that is not a real instant
     * all mean "not ours", and a bad entry is dropped instead of being registered.</p>
     */
    public static boolean isUsable(Entry entry) {
        return entry != null
                && isOurSlot(entry.slot())
                && entry.courseId() != 0L
                && entry.triggerAtMillis() > 0L
                && entry.epochDay() > -100000L && entry.epochDay() < 100000L;
    }

    /** The entry in {@code slot}, or null when that slot holds nothing. */
    public static Entry findBySlot(List<Entry> entries, int slot) {
        if (entries == null) {
            return null;
        }
        for (Entry entry : entries) {
            if (entry.slot() == slot) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The notification id for one class on one day.
     *
     * <p>Two classes at the same hour are two notifications, so the id must tell
     * them apart; the same class notified twice is one notification, so the id
     * must be stable. Mixing the course id with the day gives both: keying on the
     * day alone would collapse every class of a morning into one, and keying on
     * the trigger time would make a re-post after a small delay look like a new
     * class.</p>
     */
    public static int notificationId(long courseId, long epochDay) {
        long mixed = courseId * 0x9E3779B97F4A7C15L + epochDay * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 29;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 32;
        return (int) mixed;
    }

    /** {@link #notificationId} for an entry. */
    public static int notificationId(Entry entry) {
        return notificationId(entry.courseId(), entry.epochDay());
    }

    // ------------------------------------------------------------- storage

    /** Serialises the list for storage. Never null. */
    public static String write(List<Entry> entries) {
        List<Object> array = MiniJson.array();
        if (entries != null) {
            for (Entry entry : entries) {
                if (!isUsable(entry)) {
                    continue;
                }
                Map<String, Object> object = MiniJson.object();
                object.put("slot", Integer.valueOf(entry.slot()));
                object.put("courseId", Long.valueOf(entry.courseId()));
                object.put("epochDay", Long.valueOf(entry.epochDay()));
                object.put("triggerAt", Long.valueOf(entry.triggerAtMillis()));
                array.add(object);
            }
        }
        return MiniJson.write(array);
    }

    /**
     * Reads the list back.
     *
     * <p>Returns an empty list for anything unreadable, and skips individual
     * entries that do not check out. A corrupt record must cost at most the alarms
     * it described -- the caller rebuilds from the timetable anyway -- and must
     * never take the planner down with it.</p>
     */
    public static List<Entry> parse(String json) {
        List<Entry> entries = new ArrayList<>();
        for (Object item : MiniJson.parseArray(json)) {
            Map<String, Object> object = item instanceof Map
                    ? asMap(item) : null;
            if (object == null) {
                continue;
            }
            Entry entry = new Entry(
                    MiniJson.optInt(object, "slot", -1),
                    MiniJson.optLong(object, "courseId", 0L),
                    MiniJson.optLong(object, "epochDay", Long.MIN_VALUE),
                    MiniJson.optLong(object, "triggerAt", 0L));
            if (isUsable(entry)) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, (left, right) -> Integer.compare(left.slot(), right.slot()));
        return entries;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object item) {
        return (Map<String, Object>) item;
    }
}
