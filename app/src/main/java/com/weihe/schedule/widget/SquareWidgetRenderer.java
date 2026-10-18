package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Draws the 2x2 widget: what is happening now, and what comes after it.
 *
 * <h2>A square, not a shrunken 3x2</h2>
 *
 * <p>The 3x2 spends its width on a second card and an entry line. A square has less
 * of one and less of the other, so it keeps one main card at full size and reduces
 * the second class to a single line: the next class's start time and name, in the
 * muted voice, which is exactly how much a second class deserves when it is not the
 * one the person looked at the home screen for.</p>
 *
 * <h2>Which classes these are</h2>
 *
 * <p>The same rule the 3x2 uses, from the same helper: the headline first, which is
 * the class running if there is one and the next class otherwise, then the one after
 * it. The selection is not re-derived here -- {@link CourseWidgetRenderer#cards} is
 * the single answer to "what are the next two classes", and every size reads it.</p>
 *
 * <h2>Empty states</h2>
 *
 * <p>When the day has nothing left the card is replaced by the state block, not filled
 * with a plausible-looking class. The wording comes from the 2x1 widget's helpers, so
 * the same day produces the same sentence on every size.</p>
 */
final class SquareWidgetRenderer {

    /** Below this height the second class line is dropped rather than squashed. */
    static final int SECONDARY_MIN_HEIGHT_DP = 150;

    /** Below this height the room gives way, leaving the time alone on the detail line. */
    static final int ROOM_MIN_HEIGHT_DP = 130;

    private static final DateTimeFormatter END_CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private SquareWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan, int heightDp) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_square);
        LocalDateTime now = LocalDateTime.now();
        views.setTextViewText(R.id.widget_square_header, headerOf(plan));
        views.setTextColor(R.id.widget_square_header, Palette.ON_SURFACE_VARIANT);
        views.setOnClickPendingIntent(R.id.widget_square_root, openApp(context));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);
        boolean hasClass = !cards.isEmpty();
        views.setViewVisibility(R.id.widget_square_card,
                hasClass ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_square_empty,
                hasClass ? View.GONE : View.VISIBLE);

        if (!hasClass) {
            views.setTextViewText(R.id.widget_square_empty_title,
                    NextClassWidgetRenderer.emptyTitle(plan));
            views.setTextColor(R.id.widget_square_empty_title, Palette.ON_SURFACE);
            views.setTextViewText(R.id.widget_square_empty_hint,
                    NextClassWidgetRenderer.emptyHint(plan));
            views.setTextColor(R.id.widget_square_empty_hint, Palette.ON_SURFACE_VARIANT);
            views.setViewVisibility(R.id.widget_square_next, View.GONE);
            return views;
        }

        TodayPlan.Session main = cards.get(0);
        views.setTextViewText(R.id.widget_square_status,
                NextClassWidgetRenderer.statusLabel(plan, false));
        views.setTextColor(R.id.widget_square_status, Palette.ON_SURFACE_VARIANT);
        views.setTextViewText(R.id.widget_square_name, main.name());
        views.setTextColor(R.id.widget_square_name, Palette.ON_SURFACE);
        views.setTextViewText(R.id.widget_square_detail,
                detailOf(main, heightDp >= ROOM_MIN_HEIGHT_DP));
        views.setTextColor(R.id.widget_square_detail, Palette.ON_SURFACE_VARIANT);
        views.setInt(R.id.widget_square_rail, "setBackgroundColor", main.color());
        WidgetChronometer.bind(views, R.id.widget_square_countdown_label,
                R.id.widget_square_countdown, main, now);

        TodayPlan.Session following = cards.size() > 1 ? cards.get(1) : null;
        boolean showSecond = following != null && heightDp >= SECONDARY_MIN_HEIGHT_DP;
        views.setViewVisibility(R.id.widget_square_next,
                showSecond ? View.VISIBLE : View.GONE);
        if (showSecond) {
            views.setTextViewText(R.id.widget_square_next,
                    secondaryText(plan, following));
            views.setTextColor(R.id.widget_square_next, Palette.ON_SURFACE_VARIANT);
        }
        return views;
    }

    // -------------------------------------------------------------------- text

    /**
     * The header: the weekday and, while the term runs, the teaching week.
     *
     * <p>Not the full date as well: the 3x2 has room for "10月18日 周日 · 第5周 单周",
     * and a square does not. The weekday is what a person checks against the timetable,
     * and the week number is what tells them whether today's classes are the odd or
     * even ones.</p>
     */
    static String headerOf(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        String day = ScheduleUtils.dayName(plan.day().getDayOfWeek().getValue());
        if (plan.week() <= 0 || plan.parity().isEmpty()) {
            return day;
        }
        return day + " \u00B7 \u7B2C" + plan.week() + "\u5468 " + plan.parity();
    }

    /**
     * The second class, as one muted line: {@code 下节 15:30 · 高等数学}.
     *
     * <p>The label says which class it is. When the main card is the class in progress
     * this is the next one; when nothing is running, the main card already is the next
     * one, so this is the one after that.</p>
     */
    static String secondaryText(TodayPlan plan, TodayPlan.Session session) {
        if (session == null) {
            return "";
        }
        if (plan != null && session.ended(plan.now())) {
            // The slot was filled with the class that just ended, so it says when it
            // ended rather than pretending to be another upcoming class.
            return "\u5DF2\u7ED3\u675F " + END_CLOCK.format(session.endAt())
                    + " \u00B7 " + session.name();
        }
        String label = plan != null && plan.state() == TodayPlan.State.IN_CLASS
                ? "\u4E0B\u8282" : "\u518D\u4E0B\u8282";
        return label + " " + NextClassWidgetRenderer.clockOf(session)
                + " \u00B7 " + session.name();
    }

    /** {@code 10:40 - 14:00 · 教学楼F楼F102多}, or just the times when there is no room. */
    static String detailOf(TodayPlan.Session session, boolean withRoom) {
        if (session == null) {
            return "";
        }
        String time = session.timeRange();
        if (!withRoom) {
            return time;
        }
        String room = NextClassWidgetRenderer.roomOf(session);
        return room.isEmpty() ? time : time + " \u00B7 " + room;
    }

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
