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

import com.weihe.schedule.CourseDetailActivity;
import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.rule.TodayPlan;

/**
 * Draws the 3x2 widget: today's classes as a list.
 *
 * <h2>What the screen says</h2>
 *
 * <p>A date and a week capsule in the header, then up to two class items separated by
 * hairlines, then one light line into the app. The colour budget is spent on the 4dp
 * bar of each item, which answers the only question a home screen asks at a glance:
 * is this class still ahead, or already over. Upcoming keeps the brand accent,
 * finished drops to a quiet grey.</p>
 *
 * <h2>Which classes</h2>
 *
 * <p>Never "the first two of the day". The headline leads -- the class running if
 * there is one, the next one otherwise -- then the classes after it, and only when
 * fewer than two are left does the class that just ended take the free slot, labelled
 * as such. The list is therefore always full of true things, never a placeholder.</p>
 */
public final class CourseWidgetRenderer {

    /** The widget shows at most this many classes, whatever its size. */
    static final int MAX_CARDS = 2;

    /** The grey a finished class uses for both its bar and its label. */
    private static final int ENDED_GREY = 0xFFB9C0BD;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M\u6708d\u65E5");

    private CourseWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_course);
        LocalDateTime now = LocalDateTime.now();

        views.setTextViewText(R.id.widget_date, dateLine(plan));
        views.setTextColor(R.id.widget_date, Palette.ON_SURFACE_VARIANT);

        String week = NextClassWidgetRenderer.weekLabel(plan);
        views.setViewVisibility(R.id.widget_week, week.isEmpty() ? View.GONE : View.VISIBLE);
        views.setTextViewText(R.id.widget_week, week);
        views.setTextColor(R.id.widget_week, Palette.PRIMARY);

        List<TodayPlan.Session> cards = cards(plan);
        // Cleared first: after an edit the day can be shorter, and leaving the old
        // items behind a single class would be a lie about the timetable.
        views.removeAllViews(R.id.widget_cards);
        for (int index = 0; index < cards.size(); index++) {
            TodayPlan.Session session = cards.get(index);
            views.addView(R.id.widget_cards,
                    itemView(context, session, index == cards.size() - 1, now));
        }

        boolean any = !cards.isEmpty();
        views.setViewVisibility(R.id.widget_cards, any ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_empty, any ? View.GONE : View.VISIBLE);
        if (!any) {
            views.setTextViewText(R.id.widget_empty_title,
                    NextClassWidgetRenderer.emptyTitle(plan));
            views.setTextColor(R.id.widget_empty_title, Palette.ON_SURFACE);
            views.setTextViewText(R.id.widget_empty_detail,
                    NextClassWidgetRenderer.emptyHint(plan));
            views.setTextColor(R.id.widget_empty_detail, Palette.ON_SURFACE_VARIANT);
        }

        // The whole card opens the app, not just the entry line: a tap that lands on
        // the padding between items would otherwise fall through to the launcher's
        // default launch intent, which works but reports a different caller.
        views.setOnClickPendingIntent(R.id.widget_root, openToday(context));
        views.setTextViewText(R.id.widget_entry, entryText(totalOf(plan)));
        views.setTextColor(R.id.widget_entry, Palette.ON_SURFACE_VARIANT);
        views.setOnClickPendingIntent(R.id.widget_entry, openToday(context));
        return views;
    }

    // -------------------------------------------------------------------- items

    /**
     * One class as a list item, shared with the 2x2 widget.
     *
     * <p>The square adds these to its own container, which is what keeps two rows
     * there identical to two rows here instead of a second, slightly different item.</p>
     */
    static RemoteViews itemView(Context context, TodayPlan.Session session,
            boolean last, LocalDateTime now) {
        RemoteViews item = new RemoteViews(context.getPackageName(),
                R.layout.widget_course_card);
        boolean ended = session.ended(now);

        item.setTextViewText(R.id.widget_card_name, session.name());
        item.setTextColor(R.id.widget_card_name, Palette.ON_SURFACE);
        item.setTextViewText(R.id.widget_card_time, session.timeRange());
        item.setTextColor(R.id.widget_card_time, Palette.ON_SURFACE_VARIANT);
        item.setTextViewText(R.id.widget_card_meta, metaOf(session));
        item.setTextColor(R.id.widget_card_meta, Palette.ON_SURFACE_VARIANT);

        // The bar is the course's own colour, the same one the detail screen marks it
        // with, so a person can match a row on the home screen to a row in the app.
        item.setInt(R.id.widget_card_bar, "setBackgroundColor", session.color());

        item.setViewVisibility(R.id.widget_card_status,
                ended ? View.VISIBLE : View.GONE);
        if (ended) {
            item.setTextViewText(R.id.widget_card_status, "\u5DF2\u7ED3\u675F");
            item.setTextColor(R.id.widget_card_status, ENDED_GREY);
        }

        // A divider between items, never after the last one: a hairline hanging above
        // the entry line reads as a missing third class.
        item.setViewVisibility(R.id.widget_card_divider, last ? View.GONE : View.VISIBLE);
        item.setOnClickPendingIntent(R.id.widget_card,
                openCourse(context, session.courseId()));
        return item;
    }

    /** The item's third line: {@code 地点 · 教师}, or the periods when neither exists. */
    public static String metaOf(TodayPlan.Session session) {
        if (session == null) {
            return "";
        }
        String location = session.location().trim();
        String teacher = session.teacher().trim();
        StringBuilder line = new StringBuilder();
        appendPart(line, location);
        appendPart(line, teacher);
        if (line.length() == 0) {
            return session.periodLabel();
        }
        return line.toString();
    }

    private static void appendPart(StringBuilder line, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (line.length() > 0) {
            line.append(" \u00B7 ");
        }
        line.append(part);
    }

    // ---------------------------------------------------------------- selection

    /**
     * The classes the widget shows, at most {@link #MAX_CARDS}.
     *
     * <p>The first is the plan's headline, which is the class in progress when there
     * is one and the next class otherwise; the rest follow in start order. That single
     * rule covers both "running plus next" and "the two soonest".</p>
     */
    public static List<TodayPlan.Session> cards(TodayPlan plan) {
        if (plan == null || plan.headline() == null) {
            return Collections.emptyList();
        }
        List<TodayPlan.Session> cards = new ArrayList<>(MAX_CARDS);
        cards.add(plan.headline());
        for (TodayPlan.Session session : plan.following()) {
            if (cards.size() >= MAX_CARDS) {
                break;
            }
            cards.add(session);
        }
        // A free second slot is filled with the class that just ended, labelled as
        // such: the widget has room for two, and a blank half reads as a timetable
        // that failed to load. An upcoming class always wins the slot, so this only
        // ever appears when nothing else is left to show.
        if (cards.size() < MAX_CARDS && plan.previous() != null) {
            cards.add(plan.previous());
        }
        return cards;
    }

    /** How many classes the day holds, finished ones included; 0 when unknown. */
    public static int totalOf(TodayPlan plan) {
        return plan == null ? 0 : plan.todayCount();
    }

    /**
     * The bottom line: where to see the rest of the day.
     *
     * <p>It names the number instead of just saying "all", because the widget cannot
     * show all of them and that number is the one honest thing it can say about the
     * classes it is not showing.</p>
     */
    public static String entryText(int total) {
        if (total <= 0) {
            return "\u6253\u5F00\u4ECA\u65E5\u8BFE\u8868 \u203A";
        }
        return "\u67E5\u770B\u4ECA\u65E5\u5168\u90E8 " + total + " \u8282\u8BFE\u7A0B \u203A";
    }

    // -------------------------------------------------------------------- text

    /** {@code 10月18日 周日}: a date, with the teaching week left to the capsule. */
    public static String dateLine(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        return DATE.format(plan.day()) + " "
                + ScheduleUtils.dayName(plan.day().getDayOfWeek().getValue());
    }

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openToday(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openCourse(Context context, long courseId) {
        Intent intent = new Intent(context, CourseDetailActivity.class);
        intent.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, courseId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // One request code per course: a PendingIntent's identity is the request code,
        // the action and the component -- the extras are not part of it.
        return PendingIntent.getActivity(context, requestCode(courseId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Folds a course id into an int without letting two ids collide on it. */
    static int requestCode(long courseId) {
        return (int) (courseId ^ (courseId >>> 32));
    }
}
