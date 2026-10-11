package com.weihe.schedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

/**
 * The only place an alarm is registered or cancelled.
 *
 * <p>It is a thin shell around three things that are already tested elsewhere:
 * {@link ReminderPlan} decides what is coming up, {@link ReminderSchedule}
 * decides what identifies each alarm, and {@link ReminderNotifications} decides
 * what a notification says. What is left here is the conversation with
 * {@link AlarmManager}, and the rules about when not to have one -- which is
 * where the care is needed, because a wrong answer means either a reminder that
 * never comes or a phone full of alarms.</p>
 *
 * <h2>Inexact on purpose</h2>
 *
 * <p>Alarms use {@code setAndAllowWhileIdle}. That needs no permission at all,
 * which matters because the exact alternative does: from Android 14 an app
 * targeting 33 or later is refused {@code SCHEDULE_EXACT_ALARM} by default and
 * has to send the user into system settings to get it. The cost is that a
 * reminder can arrive a few minutes late when the phone has been still and
 * asleep, and the settings screen says so rather than promising punctuality.</p>
 *
 * <h2>Rebuilt, not patched</h2>
 *
 * <p>Every {@link #sync} cancels the whole previous ledger and registers the plan
 * again. That makes it idempotent whatever happened before -- a course deleted,
 * a lead time changed, a semester switched, an earlier cancel that failed -- and
 * it keeps the number of registered alarms bounded by the plan's own cap instead
 * of growing with the term.</p>
 */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    /** How far ahead one rebuild looks. */
    public static final int DAYS_AHEAD = ReminderPlan.DEFAULT_DAYS_AHEAD;

    private ReminderScheduler() {
    }

    /**
     * Brings the registered alarms in line with the current timetable.
     *
     * <p>Called from the settings screen, from the app's own start, and by the
     * receiver after a reminder has fired. Cheap enough for all three: it reads
     * the timetable, computes at most a week of reminders, and writes one small
     * string.</p>
     *
     * @return how many alarms are now registered
     */
    public static int sync(Context context) {
        if (context == null) {
            return 0;
        }
        return sync(context, LocalDateTime.now());
    }

    /** {@link #sync(Context)} with the current time supplied, for tests. */
    static int sync(Context context, LocalDateTime now) {
        Context app = context.getApplicationContext();
        try {
            if (!ReminderSettings.isEnabled(app)) {
                cancelAll(app);
                return 0;
            }
            if (!ReminderNotifications.canPost(app)) {
                // The user asked for reminders but notifications cannot be shown.
                // Registering alarms that can only end in a silent no-op would
                // burn battery to achieve nothing; the settings screen says the
                // permission is missing, and the next sync after it is granted
                // picks the work up again.
                cancelAll(app);
                return 0;
            }
            List<Reminder> reminders = planFor(app, now);
            List<ReminderSchedule.Entry> entries =
                    ReminderSchedule.entriesFor(reminders, ZoneId.systemDefault());
            register(app, entries);
            return entries.size();
        } catch (RuntimeException failed) {
            // A reminder is an addition to the app, never a reason for it to fall
            // over. Leave whatever is registered alone rather than cancelling: a
            // stale alarm is better than none at all.
            Log.w(TAG, "\u91CD\u5EFA\u63D0\u9192\u5931\u8D25\uFF0C\u4FDD\u7559\u539F"
                    + "\u6709\u8BA1\u5212", failed);
            return -1;
        }
    }

    /** Cancels every registered alarm and forgets the ledger. */
    public static void cancelAll(Context context) {
        Context app = context.getApplicationContext();
        // The whole slot range, not merely what the ledger remembers. A ledger that
        // was lost or read back empty would otherwise leave alarms behind that
        // nothing can ever find again -- they would fire with no entry to claim
        // them. Cancelling a slot that holds nothing costs a lookup: the cancel
        // path uses FLAG_NO_CREATE and returns immediately.
        for (int index = 0; index < ReminderSchedule.MAX_SLOTS; index++) {
            cancel(app, ReminderSchedule.slotOf(index));
        }
        if (!ReminderSettings.scheduled(app).isEmpty()) {
            ReminderSettings.setScheduled(app,
                    Collections.<ReminderSchedule.Entry>emptyList());
        }
    }

    /** How many alarms are registered right now, according to the ledger. */
    public static int registeredCount(Context context) {
        return ReminderSettings.scheduled(context).size();
    }

    // ------------------------------------------------------------- internals

    private static List<Reminder> planFor(Context context, LocalDateTime now) {
        CourseRepository repository = new CourseRepository(context);
        Semester semester = repository.getActiveSemester();
        if (semester == null) {
            return Collections.emptyList();
        }
        List<Course> courses = repository.getCourses();
        List<BellSchedule> schedules = repository.getBellSchedules();
        int lead = ReminderSettings.leadMinutes(context);
        return ReminderPlan.upcoming(courses, semester, schedules,
                LocalDate.now(), DAYS_AHEAD, lead, now,
                ReminderPlan.DEFAULT_MAX_REMINDERS).reminders();
    }

    private static void register(Context context, List<ReminderSchedule.Entry> entries) {
        // Cancel by the previous ledger first. Slots are recycled, so this also
        // covers the case where a slot now means a different class: the old alarm
        // is gone before the new one takes the number.
        List<ReminderSchedule.Entry> previous = ReminderSettings.scheduled(context);
        for (ReminderSchedule.Entry stale : previous) {
            cancel(context, stale.slot());
        }

        AlarmManager alarms = alarms(context);
        if (alarms == null) {
            ReminderSettings.setScheduled(context,
                    Collections.<ReminderSchedule.Entry>emptyList());
            return;
        }
        // Registered again even when the plan has not changed, because re-asserting
        // an alarm is how it gets repaired after something outside the app dropped
        // it -- a reboot clears every alarm there is. The ledger, on the other
        // hand, only needs writing when it actually differs.
        for (ReminderSchedule.Entry entry : entries) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    entry.triggerAtMillis(), pendingIntent(context, entry));
        }
        if (!entries.equals(previous)) {
            ReminderSettings.setScheduled(context, entries);
        }
    }

    private static void cancel(Context context, int slot) {
        // FLAG_NO_CREATE: cancelling must never be the reason a PendingIntent
        // starts existing. If nothing was registered on this slot there is nothing
        // to cancel, and creating one here would leave a stray.
        PendingIntent existing = PendingIntent.getBroadcast(context, slot,
                intentFor(context, slot, 0L), PendingIntent.FLAG_NO_CREATE
                        | PendingIntent.FLAG_IMMUTABLE);
        if (existing == null) {
            return;
        }
        AlarmManager alarms = alarms(context);
        if (alarms != null) {
            alarms.cancel(existing);
        }
        existing.cancel();
    }

    private static AlarmManager alarms(Context context) {
        return (AlarmManager) context.getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent pendingIntent(Context context,
            ReminderSchedule.Entry entry) {
        return PendingIntent.getBroadcast(context, entry.slot(),
                intentFor(context, entry.slot(), entry.triggerAtMillis()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * The intent an alarm fires, identified by its slot.
     *
     * <p>{@code PendingIntent} identity is the request code plus the intent's
     * action and component -- the extras are not part of it. So the slot alone
     * decides which alarm this is, which is exactly what is wanted: a slot always
     * means one alarm, and re-registering on it replaces rather than adds.</p>
     *
     * <p>The trigger time rides along as an extra. When the alarm fires the
     * receiver compares it with the ledger, so an alarm that outlived its plan --
     * or one whose cancellation did not take -- finds no entry claiming that
     * instant and is dropped instead of announcing the wrong class.</p>
     */
    private static Intent intentFor(Context context, int slot, long triggerAtMillis) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMIND);
        intent.putExtra(ReminderReceiver.EXTRA_SLOT, slot);
        intent.putExtra(ReminderReceiver.EXTRA_TRIGGER_AT, triggerAtMillis);
        return intent;
    }
}
