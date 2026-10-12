package com.weihe.schedule.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.Reminder;
import com.weihe.schedule.rule.ReminderPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wakes up when an alarm goes off, shows the reminder, and lines up the next one.
 *
 * <h2>The notification is built now, not then</h2>
 *
 * <p>The alarm carries no course details, only which slot it belongs to and when
 * it was meant to fire. The class is looked up in the timetable as it stands at
 * this moment. That is what makes an outdated alarm harmless: if the course was
 * deleted, moved to another period, narrowed to weeks that no longer include
 * today, or had its bell schedule edited, the lookup simply finds nothing that
 * matches and the alarm ends quietly. A notification assembled when the alarm was
 * registered could only have announced a class that may no longer exist.</p>
 *
 * <h2>Every check is a reason to do nothing</h2>
 *
 * <p>This runs whether or not anyone is looking, and it is the one component that
 * can misbehave while the app is closed. So each step fails towards silence: the
 * feature is off, the alarm belongs to no entry, a notification for this class
 * and day has already gone out, notifications cannot be shown, the class is not
 * in the timetable -- each of those returns without a notification. The whole
 * body is wrapped, because a broadcast that throws takes the app's process down
 * with it and a reminder is never worth that.</p>
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_REMIND = "com.weihe.schedule.action.REMIND";

    /** Which registered alarm fired. */
    public static final String EXTRA_SLOT = "slot";

    /** When it was meant to fire, so a stale alarm can be told apart. */
    public static final String EXTRA_TRIGGER_AT = "trigger_at";

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !ACTION_REMIND.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        try {
            announce(app, intent.getIntExtra(EXTRA_SLOT, -1),
                    intent.getLongExtra(EXTRA_TRIGGER_AT, 0L));
        } catch (RuntimeException failed) {
            Log.w(TAG, "\u5904\u7406\u4E0A\u8BFE\u63D0\u9192\u5931\u8D25", failed);
        } finally {
            // Whatever happened, bring the plan forward: this alarm has done its
            // job, and the rebuild is also what clears it out of the ledger.
            try {
                ReminderScheduler.sync(app);
            } catch (RuntimeException ignored) {
                Log.w(TAG, "\u63D0\u9192\u89E6\u53D1\u540E\u91CD\u5EFA\u5931\u8D25");
            }
        }
    }

    private void announce(Context context, int slot, long firedAtMillis) {
        if (slot < 0) {
            return;
        }
        if (!ReminderSettings.isEnabled(context)) {
            // Switched off between registering and firing.
            ReminderScheduler.cancelAll(context);
            return;
        }
        ReminderSchedule.Entry entry = ReminderSchedule.findBySlot(
                ReminderSettings.scheduled(context), slot);
        if (entry == null || !entry.describes(slot, firedAtMillis)) {
            // No entry claims this slot at this instant: a leftover alarm, or one
            // whose cancellation did not take. Silence is the right answer.
            return;
        }
        if (ReminderSettings.alreadyNotified(context, entry)) {
            return;
        }
        if (!ReminderNotifications.canPost(context)) {
            // No permission, or the channel is silenced. The settings screen says
            // so; a notification here could not be shown anyway.
            return;
        }

        Reminder reminder = lookup(context, entry);
        if (reminder == null) {
            // The class is gone, moved, or no longer runs today.
            return;
        }
        LocalDateTime startAt = reminder.startAt();
        if (!LocalDateTime.now().isBefore(startAt)) {
            // The alarm arrived after the class began -- a very late delivery, or a
            // clock change. Reminding someone about a class already under way is
            // worse than saying nothing.
            return;
        }
        if (ReminderNotifications.post(context, reminder)) {
            ReminderSettings.markNotified(context, entry);
        }
    }

    /** The class this entry refers to, read from the timetable as it is now. */
    private Reminder lookup(Context context, ReminderSchedule.Entry entry) {
        CourseRepository repository = new CourseRepository(context);
        Semester semester = repository.getActiveSemester();
        if (semester == null) {
            return null;
        }
        List<Course> courses = repository.getCourses();
        List<BellSchedule> schedules = repository.getBellSchedules();
        LocalDate day = entry.classDay();
        return ReminderPlan.forCourseOnDay(courses, semester, schedules,
                entry.courseId(), day, ReminderSettings.leadMinutes(context),
                repository.getDefaultScheduleId());
    }
}
