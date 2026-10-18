package com.weihe.schedule.widget;

import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.weihe.schedule.design.Palette;
import com.weihe.schedule.rule.TodayPlan;

/**
 * The one place a widget's countdown is armed.
 *
 * <p>Both small sizes show a countdown next to a status line, and both must get two
 * things right: the label says which way the count runs, and the base handed to the
 * Chronometer is on the monotonic clock, not the wall clock. That second detail was
 * a real bug once -- an epoch value makes the widget announce a countdown measured in
 * decades -- so it is worth having exactly one copy of it.</p>
 *
 * <p>The three larger layouts keep their own binding: they draw the countdown inside
 * a card that is already laid out differently, and rewriting them for this would put
 * finished work at risk for no visible gain.</p>
 */
final class WidgetChronometer {

    private WidgetChronometer() {
    }

    /**
     * {@code 距下课} while a class runs, {@code 距上课} before one starts.
     *
     * @return null when the class is over, or there is no class
     */
    static String labelFor(TodayPlan.Session session, LocalDateTime now) {
        if (session == null || now == null) {
            return null;
        }
        if (session.inProgress(now)) {
            return "\u8DDD\u4E0B\u8BFE";
        }
        return session.startAt().isAfter(now) ? "\u8DDD\u4E0A\u8BFE" : null;
    }

    /**
     * Arms the countdown, or hides it when there is nothing left to count to.
     *
     * <p>A target already behind the clock is hidden rather than shown as a zero: the
     * widget is redrawn at exactly the moment it would reach zero, so a past target
     * means something is wrong, and a dead "00:00" would be the one part of the card
     * that is definitely false.</p>
     */
    static void bind(RemoteViews views, int labelId, int chronometerId,
            TodayPlan.Session session, LocalDateTime now) {
        if (session != null && session.ended(now)) {
            // A class that is over still belongs on the card when the slot would
            // otherwise be blank, but it must say so: a card with no countdown at all
            // would read as "starting soon".
            views.setTextViewText(labelId, "\u5DF2\u7ED3\u675F");
            views.setTextColor(labelId, Palette.ON_PRIMARY_CONTAINER);
            views.setViewVisibility(labelId, View.VISIBLE);
            views.setViewVisibility(chronometerId, View.GONE);
            return;
        }
        String label = labelFor(session, now);
        boolean running = session != null && session.inProgress(now);
        LocalDateTime target = session == null ? null
                : running ? session.endAt() : session.startAt();
        long remaining = target == null ? 0L : target
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                - System.currentTimeMillis();
        boolean visible = label != null && remaining > 0L;
        views.setViewVisibility(labelId, visible ? View.VISIBLE : View.GONE);
        views.setViewVisibility(chronometerId, visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        views.setTextViewText(labelId, label);
        views.setTextColor(labelId, Palette.ON_PRIMARY_CONTAINER);
        views.setTextColor(chronometerId, Palette.ON_PRIMARY_CONTAINER);
        views.setChronometerCountDown(chronometerId, true);
        views.setChronometer(chronometerId, SystemClock.elapsedRealtime() + remaining,
                null, true);
    }
}
