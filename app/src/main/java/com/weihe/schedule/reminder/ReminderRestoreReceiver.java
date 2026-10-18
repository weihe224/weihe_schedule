package com.weihe.schedule.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.weihe.schedule.widget.CourseWidgetUpdater;

/**
 * Rebuilds the reminder plan when something outside the app invalidates it.
 *
 * <p>{@link ReminderScheduler} rebuilds its alarms from the timetable every time
 * it runs, so recovery is not a second mechanism: it is the same {@code sync} the
 * settings screen and the timetable screens already call, triggered by an event
 * the app cannot observe on its own. Nothing here decides when a class happens --
 * no week maths, no odd/even rule, no bell-schedule lookup -- because a second
 * copy of those would eventually disagree with the first, and the disagreement
 * would surface as a reminder at the wrong minute.</p>
 *
 * <h2>Why these events</h2>
 *
 * <p>All of them are on the platform's list of broadcasts that may still be received
 * by a manifest-declared receiver after Android 8.0's background limits:</p>
 *
 * <ul>
 *   <li>{@link Intent#ACTION_BOOT_COMPLETED} -- every alarm in the system is
 *       cleared by a reboot, so without this the plan is simply gone;</li>
 *   <li>{@link Intent#ACTION_TIME_CHANGED} and
 *       {@link Intent#ACTION_TIMEZONE_CHANGED} -- the plan is a list of absolute
 *       instants, so moving the clock or the zone moves every one of them;</li>
 *   <li>{@link Intent#ACTION_MY_PACKAGE_REPLACED} -- sent only to this app, and so
 *       not an implicit broadcast at all. An update may leave alarms the new build
 *       would not have chosen, and re-planning costs one pass;</li>
 *   <li>{@link #ACTION_EXACT_ALARM_PERMISSION_CHANGED} -- the user just granted or
 *       revoked exact alarms. Granting it is what turns a plan that arrived "some
 *       time later" into a plan that arrives on the minute, so the alarms have to be
 *       registered again through the new route immediately, even if the app is not
 *       open.</li>
 * </ul>
 *
 * <p>{@code ACTION_DATE_CHANGED} is deliberately <em>absent</em>: it is not on that
 * list, so a manifest receiver would never see it and an entry for it would be
 * decoration. The rollover it would have covered is already handled -- each alarm
 * that fires re-plans in {@link ReminderReceiver}, and every screen syncs on
 * resume, so the window moves forward without needing a midnight broadcast.</p>
 *
 * <h2>Not exported, and why that still works</h2>
 *
 * <p>{@code android:exported="false"} means the only senders this receiver accepts
 * are the system and the app itself, which is exactly the set wanted. The platform
 * documents that a non-exported receiver still receives system broadcasts, and all
 * four actions here are protected broadcasts that no other app could send anyway.
 * The alarms' own broadcasts go to {@link ReminderReceiver}, not here.</p>
 *
 * <h2>Run inline, on purpose</h2>
 *
 * <p>{@code sync} reads the timetable, computes at most a week of reminders and
 * writes one small string; it is the same work the app already does on every
 * resume, and it is measured in milliseconds. Going asynchronous would mean
 * holding a {@code PendingResult} open across a thread for no benefit -- and
 * getting that wrong ends the broadcast before the work is done, which is a
 * reminder that silently never comes back.</p>
 *
 * <h2>What this cannot promise</h2>
 *
 * <p>A broadcast is not guaranteed to arrive: a force-stopped app receives none
 * until it is started again, and some vendor builds block boot broadcasts
 * outright. That is why the home screen's resume path exists as well -- recovery
 * is repeated whenever the app is opened, so a missed broadcast costs time rather
 * than the feature.</p>
 */
public class ReminderRestoreReceiver extends BroadcastReceiver {

    /**
     * Sent when the user grants or revokes the exact-alarm permission.
     *
     * <p>Written out rather than taken from {@code AlarmManager}, because that
     * constant only exists from API 31 while this receiver is compiled against
     * every level the app supports. Same string, no version check at the call.</p>
     */
    static final String ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";

    private static final String TAG = "ReminderRestore";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!isRestoreEvent(action)) {
            return;
        }
        Context app = context.getApplicationContext();
        try {
            int registered = ReminderScheduler.sync(app);
            // The widget is rebuilt from the same data and goes stale on exactly the
            // same events: a reboot clears nothing it reads, but the date has moved
            // and the alarms that were going to change it are gone.
            CourseWidgetUpdater.refreshAll(app);
            // The count is the answer to "did my reminders come back?", which is
            // otherwise invisible for an event nobody was looking at. Rare enough
            // to be worth a line: a boot, a clock change, or an update.
            Log.i(TAG, action + ": \u5DF2\u91CD\u5EFA " + registered + " \u6761\u63D0\u9192");
        } catch (RuntimeException failed) {
            // A broadcast that throws takes the process with it, and a reminder is
            // never worth that. sync() leaves the existing alarms alone when it
            // fails, so doing nothing here is the safe answer.
            Log.w(TAG, action + ": \u91CD\u5EFA\u63D0\u9192\u5931\u8D25", failed);
        }
    }

    /**
     * Whether this broadcast invalidates the current plan.
     *
     * <p>Package-private so a test can pin the list. An event that is not on it is
     * ignored rather than synced "just in case": every sync cancels and re-registers
     * the whole ledger, and doing that for broadcasts the app does not care about
     * would be battery spent to arrive where it already was.</p>
     */
    static boolean isRestoreEvent(String action) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case ACTION_EXACT_ALARM_PERMISSION_CHANGED:
                return true;
            default:
                return false;
        }
    }
}
