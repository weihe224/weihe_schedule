package com.weihe.schedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
 * <h2>Exact when the phone allows it, honest when it does not</h2>
 *
 * <p>A class reminder has a time requirement, so the default is
 * {@code setExactAndAllowWhileIdle}: "ten minutes before the bell" is not answered
 * by "some time in the next while". Exact alarms need
 * {@code SCHEDULE_EXACT_ALARM} from Android 12, and the user can refuse it or take
 * it away again, so every registration first asks
 * {@link AlarmManager#canScheduleExactAlarms()} and falls back to
 * {@code setAndAllowWhileIdle} -- which needs no permission -- only when the exact
 * route is unavailable.</p>
 *
 * <p>That fallback is never silent. The settings screen reports which mode the
 * alarms were registered in, the inexact path is logged, and the exact-alarm
 * permission has its own entry point and its own broadcast listener: a reminder
 * feature that claims punctuality while scheduling inexactly is the failure this
 * class exists to avoid.</p>
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
                Log.i(TAG, "sync: reminders are off, nothing registered");
                return 0;
            }
            if (!ReminderNotifications.canPost(app)) {
                // The user asked for reminders but notifications cannot be shown.
                // Registering alarms that can only end in a silent no-op would
                // burn battery to achieve nothing; the settings screen says the
                // permission is missing, and the next sync after it is granted
                // picks the work up again.
                cancelAll(app);
                Log.i(TAG, "sync: notifications blocked, nothing registered");
                return 0;
            }
            List<Reminder> reminders = planFor(app, now);
            List<ReminderSchedule.Entry> entries =
                    ReminderSchedule.entriesFor(reminders, ZoneId.systemDefault());
            // The count that comes back is the number of alarms that were actually
            // set. Reporting the size of the plan instead was a claim about alarms
            // that may never have been registered -- no alarm service at all being
            // the plainest case.
            int registered = register(app, entries);
            Log.i(TAG, "sync: planned=" + reminders.size() + " registered=" + registered
                    + " exact=" + canScheduleExact(app));
            return registered;
        } catch (RuntimeException failed) {
            // A reminder is an addition to the app, never a reason for it to fall
            // over. Leave whatever is registered alone rather than cancelling: a
            // stale alarm is better than none at all.
            Log.w(TAG, "sync failed: " + describe(failed), failed);
            return -1;
        }
    }

    /** A short, log-safe description of a failure: type plus message, no stack. */
    private static String describe(RuntimeException failed) {
        String message = failed.getMessage();
        return failed.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    /**
     * Whether this phone will let the app set exact alarms.
     *
     * <p>Only Android 12 and later have the permission; below that the exact APIs
     * need nothing, so the answer is yes. The check is repeated before every
     * registration rather than remembered, because the grant can be withdrawn while
     * the app is running and calling the exact API without it throws.</p>
     */
    public static boolean canScheduleExact(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarms = alarms(context);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    /** Cancels every registered alarm and forgets the ledger. */
    public static void cancelAll(Context context) {
        Context app = context.getApplicationContext();
        // The whole slot range, not merely what the ledger remembers. A ledger that
        // was lost or read back empty would otherwise leave alarms behind that
        // nothing can ever find again -- they would fire with no entry to claim
        // them. Cancelling a slot that holds nothing costs a lookup: the cancel
        // path uses FLAG_NO_CREATE and returns immediately.
        int cleared = 0;
        for (int index = 0; index < ReminderSchedule.MAX_SLOTS; index++) {
            if (cancel(app, ReminderSchedule.slotOf(index))) {
                cleared++;
            }
        }
        if (!ReminderSettings.scheduled(app).isEmpty()) {
            ReminderSettings.setScheduled(app,
                    Collections.<ReminderSchedule.Entry>emptyList());
        }
        Log.i(TAG, "cancel all: " + cleared + " registered alarm(s) cleared");
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
                ReminderPlan.DEFAULT_MAX_REMINDERS,
                repository.getDefaultScheduleId()).reminders();
    }

    /**
     * Registers the plan and reports how many alarms were actually set.
     *
     * <p>Register first, cancel afterwards. A slot is the {@code PendingIntent}'s
     * request code, so writing a new alarm into a slot replaces whatever was there;
     * the old plan does not have to be torn down for the new one to take its place.
     * Tearing it down first is what left a sync that failed partway with no alarms
     * at all, while the caller above promised the old plan was being kept.</p>
     *
     * <p>What the ledger records is what happened, not what was intended. A sync
     * that stopped halfway stores the entries that were set, so the next sync -- and
     * {@link #cancelAll} -- sees the alarms that exist rather than the ones that were
     * planned, and the retry converges instead of working from a stale idea of the
     * state.</p>
     *
     * <p>Only slots the new plan does not reuse are cancelled, and only after the new
     * ones are in place. A slot that is still used never passes through an empty
     * moment, and a slot that is no longer used is cleared in the same sync rather
     * than left to fire for a course that has moved.</p>
     *
     * @return how many alarms were set; less than {@code entries.size()} when one was
     *         refused, in which case the failure is rethrown after the ledger has
     *         been brought up to date
     */
    private static int register(Context context, List<ReminderSchedule.Entry> entries) {
        AlarmManager alarms = alarms(context);
        List<ReminderSchedule.Entry> previous = ReminderSettings.scheduled(context);
        List<ReminderSchedule.Entry> registered = new ArrayList<>();
        boolean exactAllowed = canScheduleExact(context);

        RuntimeException failure = null;
        if (alarms != null) {
            // Registered again even when the plan has not changed, because
            // re-asserting an alarm is how it gets repaired after something outside
            // the app dropped it -- a reboot clears every alarm there is.
            for (ReminderSchedule.Entry entry : entries) {
                try {
                    setAlarm(context, alarms, entry, exactAllowed);
                    registered.add(entry);
                    // Ids and instants only: enough to match a reminder against the
                    // alarm log, and no course names or venues in a system log.
                    Log.i(TAG, "alarm slot=" + entry.slot() + " course="
                            + entry.courseId() + " at=" + entry.triggerAtMillis()
                            + (exactAllowed ? " exact" : " inexact"));
                } catch (RuntimeException refused) {
                    failure = refused;
                    Log.w(TAG, "alarm refused slot=" + entry.slot() + " course="
                            + entry.courseId() + ": " + describe(refused));
                    break;
                }
            }
        } else {
            Log.w(TAG, "no AlarmManager; nothing can be registered");
        }

        for (ReminderSchedule.Entry stale : previous) {
            if (!usesSlot(registered, stale.slot())) {
                if (cancel(context, stale.slot())) {
                    Log.i(TAG, "alarm cancelled slot=" + stale.slot() + " course="
                            + stale.courseId() + " (no longer planned)");
                }
            }
        }

        if (!registered.equals(previous)) {
            ReminderSettings.setScheduled(context, registered);
        }
        if (failure != null) {
            // The ledger already describes what is actually registered, so the
            // caller can report a failure without the state being a guess.
            throw failure;
        }
        return registered.size();
    }

    /**
     * Puts one alarm in the system, exactly when that is allowed.
     *
     * <p>The permission can be revoked between the check and the call, which throws
     * {@link SecurityException} rather than returning a failure; catching it here and
     * registering the inexact alarm instead is what keeps a revoked permission from
     * costing the user every reminder. The event is logged because the accuracy the
     * user was promised has just changed.</p>
     */
    private static void setAlarm(Context context, AlarmManager alarms,
            ReminderSchedule.Entry entry, boolean exactAllowed) {
        PendingIntent intent = pendingIntent(context, entry);
        if (exactAllowed) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        entry.triggerAtMillis(), intent);
                return;
            } catch (SecurityException revoked) {
                Log.w(TAG, "exact alarm refused, scheduling inexact instead", revoked);
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.triggerAtMillis(),
                intent);
    }

    /** Whether {@code slot} is one of the slots the new plan occupies. */
    private static boolean usesSlot(List<ReminderSchedule.Entry> entries, int slot) {
        for (ReminderSchedule.Entry entry : entries) {
            if (entry.slot() == slot) {
                return true;
            }
        }
        return false;
    }

    private static boolean cancel(Context context, int slot) {
        // FLAG_NO_CREATE: cancelling must never be the reason a PendingIntent
        // starts existing. If nothing was registered on this slot there is nothing
        // to cancel, and creating one here would leave a stray.
        PendingIntent existing = PendingIntent.getBroadcast(context, slot,
                intentFor(context, slot, 0L), PendingIntent.FLAG_NO_CREATE
                        | PendingIntent.FLAG_IMMUTABLE);
        if (existing == null) {
            return false;
        }
        AlarmManager alarms = alarms(context);
        if (alarms != null) {
            alarms.cancel(existing);
        }
        existing.cancel();
        return true;
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
