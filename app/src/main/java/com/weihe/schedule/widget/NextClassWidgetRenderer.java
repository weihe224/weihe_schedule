package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Draws the 2x1 widget: one class on a single wide strip.
 *
 * <h2>A different shape, not a squeezed copy</h2>
 *
 * <p>Two cells wide and one tall is roughly 180x99dp. That is the opposite of the
 * column this widget started as: there is width to spend and very little height, so
 * the information lies down. The status and the countdown share the first line, the
 * name and the start time share the second, and the room gets a third that is dropped
 * when the launcher reports a box too short to hold it. Only the room costs height;
 * the countdown rides along on the status line and costs width instead.</p>
 *
 * <h2>It follows the real dimensions</h2>
 *
 * <p>A launcher's cell is not a fixed number of dp, so nothing here assumes the
 * nominal 2x1: the height actually reported in the widget options decides whether the
 * room and the countdown fit at all. A line is dropped rather than squeezed, because
 * a half-drawn line is worse than an absent one.</p>
 *
 * <h2>States</h2>
 *
 * <p>Running, upcoming, finished for today, no class today, no timetable and outside
 * the term are all distinct, and the empty ones show no course at all: an expired
 * class left on screen would be a lie about the next thing that happens.</p>
 */
final class NextClassWidgetRenderer {

    /**
     * Below this height the room line is dropped: the name and the time come first.
     *
     * <p>One cell tall is about 99dp on the phone this was built for, and the three
     * lines need about that much. The numbers are content heights, not guesses at a
     * launcher's cell size.</p>
     */
    static final int ROOM_MIN_HEIGHT_DP = 88;


    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private NextClassWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan, int heightDp) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_next_class);
        LocalDateTime now = LocalDateTime.now();
        TodayPlan.Session session = plan == null ? null : plan.headline();

        views.setTextViewText(R.id.widget_next_status, statusLabel(plan, true));
        views.setTextColor(R.id.widget_next_status, Palette.ON_SURFACE_VARIANT);
        views.setOnClickPendingIntent(R.id.widget_next_root, openApp(context));

        if (session == null) {
            // Nothing to show: the state is the content. No time, room or countdown is
            // invented to fill the space, which is what keeps this honest.
            views.setTextViewText(R.id.widget_next_name, emptyTitle(plan));
            views.setTextColor(R.id.widget_next_name, Palette.ON_SURFACE);
            views.setTextViewText(R.id.widget_next_time, "");
            views.setTextViewText(R.id.widget_next_room, emptyHint(plan));
            views.setTextColor(R.id.widget_next_room, Palette.ON_SURFACE_VARIANT);
            views.setViewVisibility(R.id.widget_next_countdown_label, View.GONE);
            views.setViewVisibility(R.id.widget_next_countdown, View.GONE);
            views.setInt(R.id.widget_next_rail, "setBackgroundColor", Palette.PRIMARY);
            return views;
        }

        views.setTextViewText(R.id.widget_next_name, session.name());
        views.setTextColor(R.id.widget_next_name, Palette.ON_SURFACE);
        views.setTextViewText(R.id.widget_next_time, clockOf(session));
        views.setTextColor(R.id.widget_next_time, Palette.ON_SURFACE);

        String room = roomOf(session);
        boolean showRoom = heightDp >= ROOM_MIN_HEIGHT_DP && !room.isEmpty();
        views.setViewVisibility(R.id.widget_next_room,
                showRoom ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_next_room, room);
        views.setTextColor(R.id.widget_next_room, Palette.ON_SURFACE_VARIANT);

        bindCountdown(views, session, now);

        // The rail is the only colour on the widget: a thin stripe instead of a tinted
        // card, because a saturated block at this size would be the loudest thing on
        // the home screen while saying nothing.
        views.setInt(R.id.widget_next_rail, "setBackgroundColor", session.color());
        return views;
    }

    // -------------------------------------------------------------------- text

    /**
     * The status line, and for a running class the start of the next one.
     *
     * <p>The hint is folded into the line rather than given one of its own: it costs
     * no height, and on a wide strip it usually fits. When it does not, the status
     * ellipsizes and the number that matters is still on the next line.</p>
     */
    static String statusLabel(TodayPlan plan, boolean withNextHint) {
        if (plan == null) {
            return "";
        }
        switch (plan.state()) {
            case IN_CLASS:
                if (withNextHint && plan.next() != null) {
                    return "\u6B63\u5728\u4E0A\u8BFE \u00B7 \u4E0B\u8282 " + clockOf(plan.next());
                }
                return "\u6B63\u5728\u4E0A\u8BFE";
            case UPCOMING:
                return "\u4E0B\u4E00\u8282";
            case NO_CLASS_TODAY:
                return plan.todayCount() > 0
                        ? "\u4ECA\u65E5\u8BFE\u7A0B\u5DF2\u7ED3\u675F"
                        : "\u4ECA\u65E5\u65E0\u8BFE";
            case NO_TIMETABLE:
                return "\u8FD8\u6CA1\u6709\u8BFE\u8868";
            case OUTSIDE_TERM:
            default:
                return plan.week() <= 0
                        ? "\u5B66\u671F\u8FD8\u6CA1\u5F00\u59CB"
                        : "\u5B66\u671F\u5DF2\u7ED3\u675F";
        }
    }

    /** The big number: when the class starts. */
    static String clockOf(TodayPlan.Session session) {
        return session == null ? "" : CLOCK.format(session.startAt());
    }

    /** Where it happens; empty when the timetable has no venue, so the line can go. */
    static String roomOf(TodayPlan.Session session) {
        return session == null ? "" : session.location().trim();
    }

    /** {@code 距下课} / {@code 距上课}, or null when nothing is being counted down to. */
    static String countdownLabelOf(TodayPlan.Session session, LocalDateTime now) {
        return WidgetChronometer.labelFor(session, now);
    }

    /**
     * The headline of an empty state.
     *
     * <p>Deliberately the same words the app uses, so a person who taps through does
     * not find a different explanation on the other side.</p>
     */
    static String emptyTitle(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        switch (plan.state()) {
            case NO_CLASS_TODAY:
                return plan.todayCount() > 0
                        ? "\u4ECA\u5929\u7684\u8BFE\u90FD\u4E0A\u5B8C\u4E86"
                        : "\u4ECA\u5929\u6CA1\u6709\u8BFE";
            case NO_TIMETABLE:
                return "\u8FD8\u6CA1\u6709\u8BFE\u8868";
            case OUTSIDE_TERM:
            default:
                return plan.week() <= 0
                        ? "\u5B66\u671F\u8FD8\u6CA1\u5F00\u59CB"
                        : "\u5B66\u671F\u5DF2\u7ED3\u675F";
        }
    }

    /** What to do about it, mirroring the app's own empty states. */
    static String emptyHint(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        switch (plan.state()) {
            case NO_CLASS_TODAY:
                return "\u70B9\u5F00\u770B\u770B\u4ECA\u5929";
            case NO_TIMETABLE:
                return "\u70B9\u5F00\u6DFB\u52A0\u6216\u5BFC\u5165";
            case OUTSIDE_TERM:
            default:
                return "\u70B9\u5F00\u770B\u770B\u65B0\u5B66\u671F";
        }
    }

    // ---------------------------------------------------------------- countdown

    private static void bindCountdown(RemoteViews views, TodayPlan.Session session,
            LocalDateTime now) {
        WidgetChronometer.bind(views, R.id.widget_next_countdown_label,
                R.id.widget_next_countdown, session, now);
    }

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
