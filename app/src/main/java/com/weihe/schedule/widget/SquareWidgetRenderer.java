package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Draws the 2x2 widget: the same list as the 3x2, with the finished class as a footer.
 *
 * <h2>The same items, not a second design</h2>
 *
 * <p>The two rows are built with {@link CourseWidgetRenderer#itemView}, the very view
 * the 3x2 list uses, and added to this layout's container. Reusing it is what makes the
 * square read as the same widget at a different size: a 4dp course-colour bar, the
 * name, the times, where it happens, and the same divider rules.</p>
 *
 * <h2>Which classes</h2>
 *
 * <p>The headline first (running if there is one, the next class otherwise), then the
 * class after it -- and, separately, one grey line at the bottom for the class that just
 * ended. The footer is deliberately not part of the list: it is context, not a row to
 * sit alongside the ones still to come.</p>
 *
 * <h2>Empty states</h2>
 *
 * <p>When the day has nothing left the list is replaced by the state block, not filled
 * with a plausible-looking class. The wording comes from the 2x1 widget's helpers, so
 * the same day produces the same sentence on every size.</p>
 */
final class SquareWidgetRenderer {

    /** Below this height the finished-class footer is dropped rather than squashed. */
    static final int SECONDARY_MIN_HEIGHT_DP = 150;

    /** The grey the finished class line is written in: read, but not competing. */
    private static final int ENDED_GREY = 0xFFB9C0BD;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private SquareWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan, int heightDp) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_square);
        LocalDateTime now = LocalDateTime.now();
        views.setOnClickPendingIntent(R.id.widget_square_root, openApp(context));

        views.setTextViewText(R.id.widget_square_date, CourseWidgetRenderer.dateLine(plan));
        views.setTextColor(R.id.widget_square_date, Palette.ON_SURFACE_VARIANT);
        String week = NextClassWidgetRenderer.pillText(plan);
        views.setViewVisibility(R.id.widget_square_week,
                week.isEmpty() ? View.GONE : View.VISIBLE);
        views.setTextViewText(R.id.widget_square_week, week);
        views.setTextColor(R.id.widget_square_week, Palette.PRIMARY);

        List<TodayPlan.Session> items = items(plan);
        // Cleared first: after an edit the day can be shorter, and a stale row would be
        // a lie about the timetable.
        views.removeAllViews(R.id.widget_square_cards);
        for (int index = 0; index < items.size(); index++) {
            views.addView(R.id.widget_square_cards, CourseWidgetRenderer.itemView(
                    context, items.get(index), true, now));
        }

        boolean any = !items.isEmpty();
        views.setViewVisibility(R.id.widget_square_cards, any ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_square_empty, any ? View.GONE : View.VISIBLE);
        // Without rows the spacer has nothing to push, and two weights would split the
        // card in half; hiding it lets the state block centre itself instead.
        views.setViewVisibility(R.id.widget_square_spacer,
                any ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_square_divider, View.GONE);
        views.setViewVisibility(R.id.widget_square_ended_row, View.GONE);

        if (!any) {
            views.setTextViewText(R.id.widget_square_empty_title,
                    NextClassWidgetRenderer.emptyTitle(plan));
            views.setTextColor(R.id.widget_square_empty_title, Palette.ON_SURFACE);
            views.setTextViewText(R.id.widget_square_empty_hint,
                    NextClassWidgetRenderer.emptyHint(plan));
            views.setTextColor(R.id.widget_square_empty_hint, Palette.ON_SURFACE_VARIANT);
            return views;
        }

        TodayPlan.Session finished = plan.previous();
        boolean showFinished = finished != null && heightDp >= SECONDARY_MIN_HEIGHT_DP;
        views.setViewVisibility(R.id.widget_square_divider,
                showFinished ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_square_ended_row,
                showFinished ? View.VISIBLE : View.GONE);
        if (showFinished) {
            views.setTextViewText(R.id.widget_square_ended, finishedText(finished));
            views.setTextColor(R.id.widget_square_ended, ENDED_GREY);
            views.setInt(R.id.widget_square_ended_bar, "setBackgroundColor",
                    finished.color());
        }
        return views;
    }

    // -------------------------------------------------------------------- lists

    /**
     * The rows the list holds: the headline, then the class after it, at most two.
     *
     * <p>Not {@link CourseWidgetRenderer#cards}: that helper fills a free slot with the
     * finished class, which would repeat the grey footer line here.</p>
     */
    static List<TodayPlan.Session> items(TodayPlan plan) {
        if (plan == null || plan.headline() == null) {
            return Collections.emptyList();
        }
        List<TodayPlan.Session> items = new ArrayList<>(CourseWidgetRenderer.MAX_CARDS);
        items.add(plan.headline());
        List<TodayPlan.Session> following = plan.following();
        if (following != null && !following.isEmpty()
                && items.size() < CourseWidgetRenderer.MAX_CARDS) {
            items.add(following.get(0));
        }
        return items;
    }

    // -------------------------------------------------------------------- text

    /**
     * The footer: the class that just finished, and when.
     *
     * <p>{@code dd (09:35结束)}: the name stays readable, the time says which class it
     * was, and the parenthesis keeps the line from being mistaken for another class
     * still to come.</p>
     */
    static String finishedText(TodayPlan.Session session) {
        if (session == null) {
            return "";
        }
        return session.name() + " (" + CLOCK.format(session.endAt()) + "\u7ED3\u675F)";
    }

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
