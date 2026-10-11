package com.weihe.schedule.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
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
 * never disagree with the timetable. The title is the course name; the line under
 * it is the date, the start time, the venue and the teacher. Venue and teacher
 * are optional in the model, so an empty one is left out rather than printed as a
 * gap -- a reminder whose second field is blank, followed by a separator and then
 * the teacher's name, reads as broken. The lead time is named, so the reader can
 * tell five minutes from fifteen.</p>
 */
public final class ReminderNotifications {

    /** Channel id. Stable: changing it would orphan the user's channel settings. */
    public static final String CHANNEL_ID = "class_reminders";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("M\u6708d\u65E5 EEEE");

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
     * Posts the reminder for one class.
     *
     * @return whether anything was shown
     */
    public static boolean post(Context context, Reminder reminder) {
        if (context == null || reminder == null || !canPost(context)) {
            return false;
        }
        ensureChannel(context);
        NotificationManager manager = manager(context);
        if (manager == null) {
            return false;
        }

        int id = ReminderSchedule.notificationId(reminder.courseId(),
                reminder.classDay().toEpochDay());
        String detail = detailOf(reminder);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // One request code per class and day, so opening a reminder never reuses
        // another's PendingIntent. No extras ride along: a tap may only open a
        // screen, never carry an instruction to change any data.
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_today)
                .setContentTitle(reminder.courseName())
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setWhen(reminder.startMillis(ZoneId.systemDefault()))
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build();
        try {
            manager.notify(id, notification);
            return true;
        } catch (RuntimeException refused) {
            // A notification that will not post must not take the app down.
            return false;
        }
    }

    /**
     * The line under the title: when, where and with whom.
     *
     * <p>A method of its own so the wording can be checked without a device, which
     * is otherwise the hardest part of a notification to test.</p>
     */
    public static String detailOf(Reminder reminder) {
        StringBuilder text = new StringBuilder();
        text.append(DATE.format(reminder.startAt())).append(' ')
                .append(TIME.format(reminder.startAt()));
        if (!reminder.location().trim().isEmpty()) {
            text.append(" \u00B7 ").append(reminder.location().trim());
        }
        if (!reminder.teacher().trim().isEmpty()) {
            text.append(" \u00B7 ").append(reminder.teacher().trim());
        }
        return text.append('\n').append(reminder.leadMinutes())
                .append(" \u5206\u949F\u540E\u5F00\u59CB").toString();
    }

    private static NotificationManager manager(Context context) {
        return (NotificationManager) context.getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
