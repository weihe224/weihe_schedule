package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.time.format.DateTimeFormatter;

import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Draws the 2x1 widget: the next class, as large as one strip allows.
 *
 * <h2>A different shape, not a squeezed copy</h2>
 *
 * <p>Two cells wide and one tall is about 180x96dp: width to spend, very little
 * height. The top row is the 3x2's header -- date and teaching week -- so the sizes
 * read as one family; the rest is the course at title size with its start time, and the
 * room underneath.</p>
 *
 * <h2>No countdown, no status word</h2>
 *
 * <p>Which class the strip shows already answers whether it is running: it is always
 * the current or the next one. A ticking number and a "正在上课" label would both cost
 * a line and neither adds a fact the name and the time do not already carry.</p>
 *
 * <h2>States</h2>
 *
 * <p>Running, upcoming, finished for today, no class today, no timetable and outside
 * the term are all distinct, and the empty ones show no course at all: an expired
 * class left on screen would be a lie about the next thing that happens.</p>
 */
final class NextClassWidgetRenderer {

    /** Below this height the room line is dropped: the name and the time come first. */
    static final int ROOM_MIN_HEIGHT_DP = 88;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private NextClassWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan, int heightDp) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_next_class);
        TodayPlan.Session session = plan == null ? null : plan.headline();

        views.setTextViewText(R.id.widget_next_date, CourseWidgetRenderer.dateLine(plan));
        views.setTextColor(R.id.widget_next_date, Palette.ON_SURFACE_VARIANT);
        String week = pillText(plan);
        views.setViewVisibility(R.id.widget_next_week, week.isEmpty() ? View.GONE : View.VISIBLE);
        views.setTextViewText(R.id.widget_next_week, week);
        views.setTextColor(R.id.widget_next_week, Palette.PRIMARY);
        views.setOnClickPendingIntent(R.id.widget_next_root, openApp(context));

        if (session == null) {
            // Nothing to show: the state is the content. No time or room is invented to
            // fill the space, which is what keeps this honest.
            views.setTextViewText(R.id.widget_next_name, emptyTitle(plan));
            views.setTextColor(R.id.widget_next_name, Palette.ON_SURFACE);
            // The time row goes entirely: an empty line between the title and the hint
            // would push the two sentences that belong together apart.
            views.setViewVisibility(R.id.widget_next_time, View.GONE);
            views.setTextViewText(R.id.widget_next_room, emptyHint(plan));
            views.setTextColor(R.id.widget_next_room, Palette.ON_SURFACE_VARIANT);
            views.setViewVisibility(R.id.widget_next_room, View.VISIBLE);
            // No class, no colour: a bar beside "今天没有课" would point at nothing.
            views.setViewVisibility(R.id.widget_next_bar, View.GONE);
            return views;
        }

        views.setTextViewText(R.id.widget_next_name, session.name());
        views.setTextColor(R.id.widget_next_name, Palette.ON_SURFACE);
        views.setViewVisibility(R.id.widget_next_time, View.VISIBLE);
        views.setTextViewText(R.id.widget_next_time, clockOf(session));
        views.setTextColor(R.id.widget_next_time, Palette.ON_SURFACE);

        String room = roomOf(session);
        boolean showRoom = heightDp >= ROOM_MIN_HEIGHT_DP && !room.isEmpty();
        views.setViewVisibility(R.id.widget_next_room,
                showRoom ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_next_room, room);
        views.setTextColor(R.id.widget_next_room, Palette.ON_SURFACE_VARIANT);

        // The bar is the course's own colour, the one the detail screen marks it with.
        views.setViewVisibility(R.id.widget_next_bar, View.VISIBLE);
        views.setInt(R.id.widget_next_bar, "setBackgroundColor", session.color());
        return views;
    }

    // -------------------------------------------------------------------- text

    /**
     * {@code 第5周 单周}, or empty outside the term.
     *
     * <p>Outside the term the week number is either zero or past the last teaching
     * week; printing it next to "学期已结束" would name a week that does not exist.</p>
     */
    static String weekLabel(TodayPlan plan) {
        if (!hasTeachingWeek(plan)) {
            return "";
        }
        return "\u7B2C" + plan.week() + "\u5468 " + plan.parity();
    }

    /** The header capsule: {@code 第5周 (单周)}. */
    static String pillText(TodayPlan plan) {
        if (!hasTeachingWeek(plan)) {
            return "";
        }
        return "\u7B2C" + plan.week() + "\u5468 (" + plan.parity() + ")";
    }

    private static boolean hasTeachingWeek(TodayPlan plan) {
        return plan != null
                && plan.state() != TodayPlan.State.OUTSIDE_TERM
                && plan.week() > 0
                && !plan.parity().isEmpty();
    }

    /** The big number: when the class starts. */
    static String clockOf(TodayPlan.Session session) {
        return session == null ? "" : CLOCK.format(session.startAt());
    }

    /** Where it happens; empty when the timetable has no venue, so the line can go. */
    static String roomOf(TodayPlan.Session session) {
        return session == null ? "" : session.location().trim();
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

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
