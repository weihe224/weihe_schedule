package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * Draws the widget: the date, at most two classes, and the way into the whole day.
 *
 * <h2>Which two classes</h2>
 *
 * <p>Never "the first two of the day": that answers a question nobody asked at four
 * in the afternoon. The widget shows what is happening and what is next, and while a
 * class is running that class keeps its place instead of being replaced by the one
 * after it. With nothing running, the two soonest classes take those slots.</p>
 *
 * <p>When the day runs out, fewer cards are drawn. There is no placeholder card and
 * no empty shell: a card is only ever added for a class that exists, which is also
 * why "never a third card" needs no size check anywhere. The cards are nested
 * RemoteViews added to a container, so the count is decided in this code, not by how
 * much room the launcher happens to give.</p>
 *
 * <h2>The entry line</h2>
 *
 * <p>Two cards are never the whole day, and a widget that showed two out of six
 * without saying so would be misleading. The bottom line counts the day's classes
 * and opens the app's own timetable, which is the only place that can show all of
 * them.</p>
 *
 * <h2>Countdowns</h2>
 *
 * <p>Each card carries its own. {@link android.widget.Chronometer} counts against
 * {@link android.os.SystemClock#elapsedRealtime()}, not the wall clock; handing it an
 * epoch value makes it announce a countdown measured in decades, so the remaining
 * duration is computed here and re-based onto the monotonic clock.</p>
 */
public final class CourseWidgetRenderer {

    /** The widget shows at most this many cards, whatever its size. */
    static final int MAX_CARDS = 2;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M\u6708d\u65E5");

    private CourseWidgetRenderer() {
    }

    static RemoteViews render(Context context, TodayPlan plan) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_course);
        views.setTextViewText(R.id.widget_date, dateTitle(plan));
        views.setTextColor(R.id.widget_date, Palette.ON_SURFACE_VARIANT);

        LocalDateTime now = LocalDateTime.now();
        List<TodayPlan.Session> cards = cards(plan);
        // Cleared first: after an edit the day can be shorter, and re-adding on top of
        // the previous cards would leave stale ones behind a single class.
        views.removeAllViews(R.id.widget_cards);
        for (TodayPlan.Session session : cards) {
            views.addView(R.id.widget_cards, cardView(context, session, now));
        }

        boolean any = !cards.isEmpty();
        views.setViewVisibility(R.id.widget_cards, any ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_empty, any ? View.GONE : View.VISIBLE);
        if (!any) {
            views.setTextViewText(R.id.widget_empty_title, stateTitle(plan));
            views.setTextColor(R.id.widget_empty_title, Palette.ON_SURFACE);
            views.setTextViewText(R.id.widget_empty_detail, emptyDetail(plan));
            views.setTextColor(R.id.widget_empty_detail, Palette.ON_SURFACE_VARIANT);
        }

        // The entry is always there, even on a day with nothing left: it counts the
        // whole day, so there is no state where the line has to be hidden.
        views.setTextViewText(R.id.widget_entry, entryText(totalOf(plan)));
        views.setTextColor(R.id.widget_entry, Palette.PRIMARY);
        views.setOnClickPendingIntent(R.id.widget_entry, openToday(context));
        return views;
    }

    // -------------------------------------------------------------------- cards

    /**
     * The classes the widget shows, at most {@link #MAX_CARDS}.
     *
     * <p>The first is the plan's headline, which is the class in progress when there
     * is one and the next class otherwise; the rest follow in start order. That single
     * rule covers both halves of "running plus next" and "the two soonest".</p>
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
            return "\u6253\u5F00\u4ECA\u65E5\u8BFE\u8868";
        }
        return "\u67E5\u770B\u4ECA\u65E5\u5168\u90E8 " + total + " \u8282\u8BFE\u7A0B";
    }

    private static RemoteViews cardView(Context context, TodayPlan.Session session,
            LocalDateTime now) {
        RemoteViews card = new RemoteViews(context.getPackageName(),
                R.layout.widget_course_card);
        card.setTextViewText(R.id.widget_card_name, session.name());
        card.setTextColor(R.id.widget_card_name, Palette.ON_SURFACE);
        card.setTextViewText(R.id.widget_card_detail, detailOf(session));
        card.setTextColor(R.id.widget_card_detail, Palette.ON_SURFACE_VARIANT);
        card.setInt(R.id.widget_card_accent, "setBackgroundColor", session.color());
        // One request code per course, because a PendingIntent's identity is its
        // request code, action and component; the extras are not part of it.
        card.setOnClickPendingIntent(R.id.widget_card,
                openCourse(context, session.courseId()));
        bindCountdown(card, session, now);
        return card;
    }

    // ---------------------------------------------------------------- countdown

    /**
     * The instant a class's countdown runs to: its start while it is still ahead, its
     * end once it is running.
     *
     * @return null when the class is already over, or there is no class at all
     */
    public static LocalDateTime countdownTargetOf(TodayPlan.Session session,
            LocalDateTime now) {
        if (session == null || now == null) {
            return null;
        }
        if (session.inProgress(now)) {
            return session.endAt();
        }
        return session.startAt().isAfter(now) ? session.startAt() : null;
    }

    /** {@code 距下课} / {@code 距上课}, or null when nothing is being counted down to. */
    public static String countdownLabelOf(TodayPlan.Session session, LocalDateTime now) {
        if (countdownTargetOf(session, now) == null) {
            return null;
        }
        return session.inProgress(now) ? "\u8DDD\u4E0B\u8BFE" : "\u8DDD\u4E0A\u8BFE";
    }

    private static void bindCountdown(RemoteViews views, TodayPlan.Session session,
            LocalDateTime now) {
        // Through the shared helper, so a finished class says "已结束" here and in the
        // other sizes instead of growing its own wording.
        WidgetChronometer.bind(views, R.id.widget_card_countdown_label,
                R.id.widget_card_countdown, session, now);
    }
    // -------------------------------------------------------------------- text

    /** {@code 10月19日 周一}, with {@code · 第5周 单周} while the term is running. */
    public static String dateTitle(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        String date = DAY.format(plan.day()) + " "
                + ScheduleUtils.dayName(plan.day().getDayOfWeek().getValue());
        if (plan.week() <= 0 || plan.parity().isEmpty()) {
            return date;
        }
        return date + " \u00B7 \u7B2C" + plan.week() + "\u5468 " + plan.parity();
    }

    /** Why there is nothing to show: no class left, no timetable, or no term. */
    public static String stateTitle(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        switch (plan.state()) {
            case IN_CLASS:
            case UPCOMING:
                return plan.headline() == null ? "" : plan.headline().name();
            case NO_CLASS_TODAY:
                return "\u4ECA\u5929\u6CA1\u6709\u8BFE";
            case NO_TIMETABLE:
                return "\u8FD8\u6CA1\u6709\u8BFE\u8868";
            case OUTSIDE_TERM:
            default:
                return plan.week() <= 0
                        ? "\u5B66\u671F\u8FD8\u6CA1\u5F00\u59CB"
                        : "\u5B66\u671F\u5DF2\u7ED3\u675F";
        }
    }

    /** The second line of the empty block: what to do about it. */
    public static String emptyDetail(TodayPlan plan) {
        if (plan == null) {
            return "";
        }
        switch (plan.state()) {
            case NO_CLASS_TODAY:
                return "\u4ECA\u5929\u7684\u8BFE\u90FD\u4E0A\u5B8C\u4E86";
            case NO_TIMETABLE:
                return "\u70B9\u5F00\u6DFB\u52A0\u6216\u5BFC\u5165";
            case OUTSIDE_TERM:
            default:
                return plan.semesterLabel();
        }
    }

    /**
     * A card's second line: {@code 08:15 - 09:35 · 教学楼F楼F202多 · 李海波}.
     *
     * <p>Two lines per card is a budget, not a preference: a third line is what makes
     * the second card fall out of the widget once the system font is enlarged. The
     * period numbers therefore give way to the times they describe, and a missing
     * venue or teacher drops out of the line instead of leaving a separator with
     * nothing after it.</p>
     */
    public static String detailOf(TodayPlan.Session session) {
        if (session == null) {
            return "";
        }
        String time = session.timeRange();
        StringBuilder line = new StringBuilder(time);
        appendPart(line, session.location());
        appendPart(line, session.teacher());
        if (line.length() == time.length()) {
            // No venue and no teacher: the period numbers are the only useful thing
            // left to say about where this class is.
            appendPart(line, session.periodLabel());
        }
        return line.toString();
    }

    private static void appendPart(StringBuilder line, String part) {
        String trimmed = part == null ? "" : part.trim();
        if (!trimmed.isEmpty()) {
            line.append(" \u00B7 ").append(trimmed);
        }
    }

    // ---------------------------------------------------------------- clicking

    private static PendingIntent openToday(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openCourse(Context context, long courseId) {
        Intent intent = new Intent(context, CourseDetailActivity.class);
        intent.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, courseId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode(courseId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Folds a course id into an int without letting two ids collide on it. */
    static int requestCode(long courseId) {
        return (int) (courseId ^ (courseId >>> 32));
    }
}
