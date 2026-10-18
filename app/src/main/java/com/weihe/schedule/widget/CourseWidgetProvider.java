package com.weihe.schedule.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The widget's lifecycle and the system broadcasts it cares about.
 *
 * <p>Everything this receives ends in the same place -- {@link CourseWidgetUpdater}
 * re-reading the timetable -- because none of these events carries any information
 * worth keeping: an update broadcast, a resize and a clock change all mean the same
 * thing, which is "the timetable or the way it should be drawn may have moved".</p>
 *
 * <p>The one thing that is not a redraw is the last widget being removed: then the
 * refresh alarm is cancelled, because a home screen with no widget has nothing to
 * be woken up for.</p>
 *
 * <p>Kept out of the reminder receivers on purpose. Reminders are about alarms that
 * must fire at a minute; the widget is a view of the same data. Sharing a receiver
 * would make one feature's failure look like the other's, and a widget must keep
 * working when notifications are blocked -- it does not depend on that permission
 * at all.</p>
 */
public class CourseWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "CourseWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ids) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager appWidgetManager, int appWidgetId, Bundle options) {
        // There is one layout at every size now, but a resize still means the
        // launcher is rebinding us: redraw so the cards and the count are current.
        Log.i(TAG, "resized widget=" + appWidgetId);
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        // First widget placed: nothing has been drawn yet and no alarm is aimed.
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Not "cancel the alarm": the 1x2 widget may still be on a home screen. The
        // updater cancels it only when no widget of either size is left.
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // The alarm is shared by every instance, so it is re-aimed rather than
        // cancelled here; onDisabled is what finally drops it.
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (CourseWidgetRefreshScheduler.ACTION_TICK.equals(action)) {
            // The boundary alarm: the class ended, the next began, or the day moved.
            Log.i(TAG, "tick");
            CourseWidgetUpdater.refreshAll(context);
            return;
        }
        super.onReceive(context, intent);
    }
}
