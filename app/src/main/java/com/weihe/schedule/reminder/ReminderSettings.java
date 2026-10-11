package com.weihe.schedule.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import com.weihe.schedule.rule.ReminderPlan;

/**
 * Where the reminder preferences live.
 *
 * <p>A store of its own, not the course store. Two reasons, and both matter.
 * The timetable's file has a format that other things depend on -- the backup
 * reader, the restore protocol -- and reminder settings are not part of a
 * timetable: they describe this phone, not the user's term. Keeping them apart
 * also means a restore can replace every course without touching whether
 * reminders are on, and no key here can ever collide with one over there.</p>
 *
 * <p>The registered alarms are kept here too. They are device state as well: a
 * timetable restored onto a new phone should register its own alarms rather than
 * inherit a list of timestamps that mean nothing on it.</p>
 */
public final class ReminderSettings {

    private static final String STORE = "weihe_schedule_reminders";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LEAD_MINUTES = "lead_minutes";
    private static final String KEY_SCHEDULED = "scheduled";

    private ReminderSettings() {
    }

    static SharedPreferences store(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    /** Whether the user asked for reminders. Off until they do. */
    public static boolean isEnabled(Context context) {
        return store(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        store(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** How long before a class to warn. */
    public static int leadMinutes(Context context) {
        int stored = store(context).getInt(KEY_LEAD_MINUTES,
                ReminderPlan.DEFAULT_LEAD_MINUTES);
        // A value from outside the supported band could only come from an older or
        // hand-edited store; the default is a safer answer than a refusal here.
        return ReminderPlan.supportsLeadMinutes(stored)
                ? stored : ReminderPlan.DEFAULT_LEAD_MINUTES;
    }

    public static void setLeadMinutes(Context context, int minutes) {
        store(context).edit().putInt(KEY_LEAD_MINUTES, minutes).apply();
    }

    /** The alarms currently registered. Empty when nothing is set. */
    public static List<ReminderSchedule.Entry> scheduled(Context context) {
        return ReminderSchedule.parse(store(context).getString(KEY_SCHEDULED, null));
    }

    /** Replaces the registered-alarm list wholesale. */
    public static void setScheduled(Context context, List<ReminderSchedule.Entry> entries) {
        store(context).edit()
                .putString(KEY_SCHEDULED, ReminderSchedule.write(entries))
                .apply();
    }

    /** Records that this class on this day has been announced. */
    public static void markNotified(Context context, ReminderSchedule.Entry entry) {
        store(context).edit()
                .putLong(KEY_LAST_NOTIFIED + entry.courseId(), entry.epochDay())
                .apply();
    }

    /**
     * Whether this class on this day has already been announced.
     *
     * <p>The last line of defence against a second notification for one class. The
     * alarm ledger should already have moved on, but an alarm that fires twice --
     * a repeated delivery, a stale PendingIntent on a reused slot -- would
     * otherwise announce the same class again.</p>
     */
    public static boolean alreadyNotified(Context context, ReminderSchedule.Entry entry) {
        return store(context).getLong(KEY_LAST_NOTIFIED + entry.courseId(), Long.MIN_VALUE)
                == entry.epochDay();
    }

    private static final String KEY_LAST_NOTIFIED = "notified_";
}
