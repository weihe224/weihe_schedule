package com.weihe.schedule.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.weihe.schedule.CourseDetailActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.rule.Reminder;

/**
 * Builds and posts the class notification, and owns the channel it goes to.
 *
 * <p>One channel, created once and re-created harmlessly: adding a channel that
 * already exists does nothing, so every entry point can call
 * {@link #ensureChannel} without coordinating. Nothing here touches any other
 * channel -- the app has none, and that is the point.</p>
 *
 * <p>There is no version branching in this class, and that is deliberate rather
 * than an oversight. {@code minSdk} is 26, so every API used here --
 * {@link NotificationChannel}, {@code areNotificationsEnabled},
 * {@code Notification.Builder(Context, String)}, {@code FLAG_IMMUTABLE} -- exists
 * on every device the app can be installed on. A branch for an older release
 * would be dead code that no test could reach and no device could exercise.</p>
 *
 * <h2>What the notification says</h2>
 *
 * <p>Everything comes from the course and the schedule, so a notification can
 * never disagree with the timetable. The title names the kind of alert and then
 * the course -- "上课提醒 · 高等数学" -- because the shade already shows the app
 * name, and a title that is only a course name does not say what is about to
 * happen to it. The line under it carries when, which periods and where; venue
 * and teacher are optional in the model, so an empty one is left out rather than
 * printed as a gap, and a reminder whose second field is blank followed by a
 * separator reads as broken. The lead time is named, so the reader can tell five
 * minutes from fifteen.</p>
 *
 * <p>Tapping opens the course, not the app. The intent carries the course id and
 * nothing else: the id is the timetable's own stable key, so a reminder for a
 * course that has since been renamed or moved still lands on the right one, which
 * a name or a list position could not promise. No extra rides along that would
 * tell a screen to change anything -- a tap may open a page, never edit data.</p>
 */
public final class ReminderNotifications {

    private static final String TAG = "ReminderNotification";

    /** Channel id. Stable: changing it would orphan the user's channel settings. */
    public static final String CHANNEL_ID = "class_reminders";

    /** What every reminder's title starts with. */
    private static final String TITLE_PREFIX = "\u4E0A\u8BFE\u63D0\u9192";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("M\u6708d\u65E5");

    private ReminderNotifications() {
    }

    /** Creates the channel if it is not there yet. Safe to call on every launch. */
    public static void ensureChannel(Context context) {
        NotificationManager manager = manager(context);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "\u4E0A\u8BFE\u63D0\u9192", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("\u5728\u8BFE\u7A0B\u5F00\u59CB\u524D\u63D0\u9192\u4F60"
                + "\uFF0C\u5305\u542B\u8BFE\u7A0B\u3001\u65F6\u95F4\u4E0E\u6559\u5BA4\u3002");
        channel.enableVibration(true);
        // Importance is chosen once and cannot be raised again by code: a channel
        // the user has turned down stays down, which is why this asks for HIGH up
        // front instead of quietly settling for less and hoping.
        manager.createNotificationChannel(channel);
    }

    /** Whether a notification posted right now would actually be shown. */
    public static boolean canPost(Context context) {
        NotificationManager manager = manager(context);
        if (manager == null) {
            return false;
        }
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        // A channel the user has silenced reports itself as blocked, and there is
        // no way to post around that; claiming otherwise would be a reminder that
        // silently never arrives.
        return channel == null
                || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    /**
     * Posts the reminder for one class, timed against the device clock.
     *
     * @return whether anything was shown
     */
    public static boolean post(Context context, Reminder reminder) {
        return post(context, reminder, LocalDateTime.now());
    }

    /**
     * Posts the reminder, with the moment of delivery supplied.
     *
     * <p>The delivery moment is an argument because an alarm does not always arrive
     * when it was planned for: an inexact alarm on a sleeping phone can be minutes
     * late, and a late reminder that still says "10 分钟后开始" would be lying about
     * the one fact it exists to carry. Passing the clock in is also what lets the
     * wording be tested without a device.</p>
     *
     * @param deliveredAt when the alarm actually fired, or {@code null} to describe
     *                    the reminder as planned
     * @return whether anything was shown
     */
    public static boolean post(Context context, Reminder reminder,
            LocalDateTime deliveredAt) {
        if (context == null || reminder == null) {
            return false;
        }
        if (!canPost(context)) {
            // Says which half failed: the app-level switch, or the channel. The
            // settings screen shows the same thing; this is the copy that survives
            // in a bug report.
            Log.i(TAG, "not posted: notifications blocked (course "
                    + reminder.courseId() + ")");
            return false;
        }
        ensureChannel(context);
        NotificationManager manager = manager(context);
        if (manager == null) {
            Log.w(TAG, "not posted: no NotificationManager");
            return false;
        }

        int id = ReminderSchedule.notificationId(reminder.courseId(),
                reminder.classDay().toEpochDay());
        String detail = detailOf(reminder, deliveredAt);

        // The course, by its own id. No FLAG_ACTIVITY_CLEAR_TOP: the detail screen
        // reads its extra in onCreate and has no onNewIntent, so clearing on top of
        // an existing instance would deliver this id to a page that never looks at
        // it again -- the user would tap a reminder for one course and be shown
        // another.
        Intent open = new Intent(context, CourseDetailActivity.class);
        open.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, reminder.courseId());
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // One request code per class and day, so opening a reminder never reuses
        // another's PendingIntent.
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_today)
                .setContentTitle(titleOf(reminder))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setWhen(reminder.startMillis(ZoneId.systemDefault()))
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build();
        try {
            manager.notify(id, notification);
            Log.i(TAG, "posted course=" + reminder.courseId() + " id=" + id
                    + " lateMinutes=" + reminder.lateMinutes(deliveredAt));
            return true;
        } catch (RuntimeException refused) {
            // A notification that will not post must not take the app down.
            Log.w(TAG, "notify() refused course=" + reminder.courseId(), refused);
            return false;
        }
    }

    /**
     * The notification's title: what this is, then which course.
     *
     * <p>The kind of alert comes first because the shade already carries the app's
     * name above the title, and "拾课 / 高等数学" does not say that the class is
     * about to start. The course name follows so that the one thing the reader is
     * deciding about is still the most prominent word on the row.</p>
     */
    public static String titleOf(Reminder reminder) {
        return TITLE_PREFIX + " \u00B7 " + reminder.courseName();
    }

    /**
     * Which periods the class covers: {@code 第 3-4 节}, or {@code 第 3 节}.
     *
     * <p>A single-period class is not written "3-3"; a range that names the same
     * number twice reads like a mistake.</p>
     */
    public static String periodLabel(Reminder reminder) {
        if (reminder.endPeriod() <= reminder.startPeriod()) {
            return "\u7B2C " + reminder.startPeriod() + " \u8282";
        }
        return "\u7B2C " + reminder.startPeriod() + "-" + reminder.endPeriod() + " \u8282";
    }

    /**
     * The line under the title: when, which periods, where and with whom.
     *
     * <p>A method of its own so the wording can be checked without a device, which
     * is otherwise the hardest part of a notification to test.</p>
     */
    public static String detailOf(Reminder reminder) {
        return detailOf(reminder, null);
    }

    /**
     * The same line, told against the moment the alarm actually arrived.
     *
     * <p>When the delivery is late the countdown is replaced rather than left
     * stating a lead time that has already elapsed: "10 分钟后开始" on a reminder
     * that arrived fifteen minutes after the bell is the kind of wrong that makes
     * people stop trusting the feature. The delay is named, so a user whose phone
     * held the alarm can tell a late reminder from a broken one.</p>
     *
     * @param deliveredAt when the reminder was delivered, or {@code null} to
     *                    describe it as planned
     */
    public static String detailOf(Reminder reminder, LocalDateTime deliveredAt) {
        StringBuilder text = new StringBuilder();
        text.append(DATE.format(reminder.startAt())).append(' ')
                // The weekday comes from the app's own names rather than from the
                // formatter's locale. The screen is Chinese on every phone, and a
                // pattern letter would have printed "Tuesday" on an English one --
                // the app would have been reading in two languages at once, and the
                // device's locale would have decided which.
                .append(ScheduleUtils.dayName(
                        reminder.startAt().getDayOfWeek().getValue()))
                .append(' ').append(TIME.format(reminder.startAt()))
                .append(" \u00B7 ").append(periodLabel(reminder));
        if (!reminder.location().trim().isEmpty()) {
            text.append(" \u00B7 ").append(reminder.location().trim());
        }
        if (!reminder.teacher().trim().isEmpty()) {
            text.append(" \u00B7 ").append(reminder.teacher().trim());
        }
        text.append('\n');
        if (reminder.isLate(deliveredAt)) {
            return text.append("\u5DF2\u5230\u4E0A\u8BFE\u65F6\u95F4\uFF08\u63D0"
                    + "\u9192\u5EF6\u8FDF ").append(reminder.lateMinutes(deliveredAt))
                    .append(" \u5206\u949F\uFF09").toString();
        }
        return text.append(reminder.leadMinutes())
                .append(" \u5206\u949F\u540E\u5F00\u59CB").toString();
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
