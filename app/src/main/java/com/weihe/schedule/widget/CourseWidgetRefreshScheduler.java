package com.weihe.schedule.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.time.ZoneId;

import com.weihe.schedule.reminder.ReminderScheduler;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Keeps one alarm pointing at the next moment the widget would be wrong.
 *
 * <p>The platform will redraw a widget every half hour at best, and that number is
 * both the floor and a promise it does not really make. Half an hour of a schedule
 * is two classes, so the periodic update is only a safety net here; the real work is
 * done by an alarm set for the exact instant something changes: this class ends, the
 * next one begins, or the day rolls over.</p>
 *
 * <p>One alarm, always re-aimed rather than accumulated -- the same discipline as
 * the reminder ledger, for the same reason. It is scheduled after every refresh, and
 * a refresh happens on every event that changes the answer, so the alarm can never
 * be older than the last thing that happened.</p>
 *
 * <p>Exactness follows the reminder module's answer: a widget that flips its
 * countdown a few minutes late is a cosmetic problem, not a missed class, so the
 * inexact fallback is acceptable here in a way it is not for a reminder -- but the
 * permission is already being asked for, so the exact alarm is used whenever the
 * phone allows it.</p>
 */
public final class CourseWidgetRefreshScheduler {

    private static final String TAG = "CourseWidget";

    /** Sent by the alarm, handled by {@link CourseWidgetProvider}. */
    static final String ACTION_TICK = "com.weihe.schedule.action.WIDGET_TICK";

    /** Distinct from the reminder slots (1000..1009) and from the notification ids. */
    private static final int REQUEST_CODE = 7301;

    /** Never schedule closer than this; an alarm in the past fires immediately. */
    private static final long MIN_LEAD_MILLIS = 30_000L;

    private CourseWidgetRefreshScheduler() {
    }

    /** Aims the single refresh alarm at {@code plan}'s next boundary. */
    static void scheduleNext(Context context, TodayPlan plan) {
        if (context == null || plan == null) {
            return;
        }
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            Log.w(TAG, "no AlarmManager; the widget will only follow the periodic update");
            return;
        }
        long at = plan.nextRefreshAt()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        if (at <= now + 1000L) {
            at = now + MIN_LEAD_MILLIS;
        }
        PendingIntent intent = tickIntent(app, PendingIntent.FLAG_UPDATE_CURRENT);
        if (ReminderScheduler.canScheduleExact(app)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
                Log.i(TAG, "refresh alarm at=" + at + " exact");
                return;
            } catch (SecurityException revoked) {
                // Taken away between the check and the call; the inexact alarm below
                // is still better than no refresh at all.
                Log.w(TAG, "exact refresh refused, scheduling inexact", revoked);
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        Log.i(TAG, "refresh alarm at=" + at + " inexact");
    }

    /** Drops the refresh alarm; called when the last widget is removed. */
    static void cancel(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        PendingIntent existing = tickIntent(app, PendingIntent.FLAG_NO_CREATE);
        if (existing == null) {
            return;
        }
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) {
            alarms.cancel(existing);
        }
        existing.cancel();
        Log.i(TAG, "refresh alarm cancelled");
    }

    private static PendingIntent tickIntent(Context context, int flags) {
        Intent intent = new Intent(context, CourseWidgetProvider.class);
        intent.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }
}
