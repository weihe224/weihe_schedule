package com.weihe.schedule.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.weihe.schedule.rule.TodayPlan;

/**
 * Redraws every placed widget from the timetable as it stands.
 *
 * <p>The app's screens call {@link #refreshAll} after a write they know about; the
 * providers call it for the events only the system can see. Both end up here, so there
 * is exactly one path that reads the timetable and pushes pixels, and it is the same
 * path whether the change came from an edit, an import, a restore or a reboot.</p>
 *
 * <p>Both widget sizes are served from that one read. They differ in what they draw,
 * never in what they consider the truth: a second read for the 1x2 widget could see a
 * different "now" and disagree with the 3x2 about which class is next.</p>
 *
 * <p>It is deliberately cheap to call when no widget exists: the launcher is asked for
 * our ids, gets none, and the call ends there. That is what makes it safe to put next
 * to every write instead of inventing a dirty flag to get wrong.</p>
 */
public final class CourseWidgetUpdater {

    private static final String TAG = "CourseWidget";

    private CourseWidgetUpdater() {
    }

    /**
     * Re-renders every placed widget, of either size, and re-aims the refresh alarm.
     *
     * <p>Called after a commit, never before one: a widget that updated ahead of the
     * store would briefly describe a timetable that does not exist yet.</p>
     */
    public static void refreshAll(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        if (manager == null) {
            return;
        }
        int[] ids = idsOf(app, manager, CourseWidgetProvider.class);
        int[] nextIds = idsOf(app, manager, NextClassWidgetProvider.class);
        int[] squareIds = idsOf(app, manager, SquareWidgetProvider.class);
        if (ids.length + nextIds.length + squareIds.length == 0) {
            // Nothing on any home screen: there is nothing to draw and no reason to
            // keep waking the device for it.
            CourseWidgetRefreshScheduler.cancel(app);
            return;
        }

        // Two cards and the headline's successor are all the big layout can show;
        // asking for more would only make the plan carry a list nothing reads.
        TodayPlan plan = CourseWidgetDataSource.read(app, CourseWidgetRenderer.MAX_CARDS);
        int updated = 0;
        for (int id : ids) {
            try {
                manager.updateAppWidget(id, CourseWidgetRenderer.render(app, plan));
                updated++;
            } catch (RuntimeException failed) {
                // One bad widget must not stop the others, and a widget is never a
                // reason for a write path to throw.
                Log.w(TAG, "update failed for widget " + id, failed);
            }
        }
        for (int id : nextIds) {
            try {
                int height = heightDp(app, manager, id);
                manager.updateAppWidget(id,
                        NextClassWidgetRenderer.render(app, plan, height));
                Log.i(TAG, "next-class widget=" + id + " height=" + height + "dp");
                updated++;
            } catch (RuntimeException failed) {
                Log.w(TAG, "update failed for next-class widget " + id, failed);
            }
        }

        for (int id : squareIds) {
            try {
                manager.updateAppWidget(id,
                        SquareWidgetRenderer.render(app, plan, heightDp(app, manager, id)));
                updated++;
            } catch (RuntimeException failed) {
                Log.w(TAG, "update failed for square widget " + id, failed);
            }
        }

        CourseWidgetRefreshScheduler.scheduleNext(app, plan);
        Log.i(TAG, "refreshed=" + updated + "/"
                + (ids.length + nextIds.length + squareIds.length)
                + " state=" + plan.state()
                + " cards=" + CourseWidgetRenderer.cards(plan).size()
                + " today=" + plan.todayCount()
                + " nextClass=" + nextIds.length
                + " square=" + squareIds.length);
    }

    private static int[] idsOf(Context app, AppWidgetManager manager,
            Class<?> provider) {
        try {
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, provider));
            return ids == null ? new int[0] : ids;
        } catch (RuntimeException unsupported) {
            // A device with no launcher-backed widget service at all.
            Log.w(TAG, "no app widget service", unsupported);
            return new int[0];
        }
    }

    /**
     * The height the launcher is currently giving one widget, in dp.
     *
     * <p>0 when the size cannot be read. Both renderers treat that as "assume the
     * smallest", which drops the optional lines instead of clipping them.</p>
     */
    private static int heightDp(Context app, AppWidgetManager manager, int widgetId) {
        try {
            Bundle options = manager.getAppWidgetOptions(widgetId);
            if (options == null) {
                return 0;
            }
            // The options carry a range, not the current size. On the phone this was
            // built for a 1x2 widget reports 99dp..203dp while actually being about
            // 152dp tall, so neither end is the truth; the larger end is the one that
            // does not hide lines the widget really has room for.
            int min = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            int max = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            Log.i(TAG, "options widget=" + widgetId + " minH=" + min + " maxH=" + max);
            return Math.max(min, max);
        } catch (RuntimeException unsupported) {
            return 0;
        }
    }
}
