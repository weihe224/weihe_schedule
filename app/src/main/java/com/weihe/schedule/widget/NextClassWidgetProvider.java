package com.weihe.schedule.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The 1x2 widget's lifecycle.
 *
 * <p>It shares the drawing machinery with the 3x2 widget rather than owning any: the
 * same timetable read, the same boundary alarm and the same data-change hooks redraw
 * both sizes. Two providers means two entries in the launcher's picker and two sets of
 * widget ids, nothing more -- duplicating the refresh logic per size is how the two
 * sizes would start disagreeing about what day it is.</p>
 */
public class NextClassWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "CourseWidget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ids) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager appWidgetManager, int appWidgetId, Bundle options) {
        // A resize changes how many lines fit, which is content for this widget.
        Log.i(TAG, "next-class resized widget=" + appWidgetId);
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        CourseWidgetUpdater.refreshAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        // Not "cancel the alarm": the other size may still be on a home screen. The
        // updater cancels it only when no widget of either size is left.
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
