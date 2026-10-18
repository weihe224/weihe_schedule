package com.weihe.schedule.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The 2x2 widget's lifecycle.
 *
 * <p>It owns no drawing or scheduling of its own: the same timetable read, the same
 * boundary alarm and the same data-change hooks redraw every size. Three providers
 * means three entries in the picker and three sets of widget ids, nothing else.</p>
 */
public class SquareWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "CourseWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ids) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager appWidgetManager, int appWidgetId, Bundle options) {
        // A resize changes how many lines fit, which is content for this widget.
        Log.i(TAG, "square resized widget=" + appWidgetId);
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Not "cancel the alarm": the other sizes may still be on a home screen. The
        // updater cancels it only when no widget of any size is left.
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }
}
