package com.weihe.schedule.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import com.weihe.schedule.CourseDetailActivity;
import com.weihe.schedule.MainActivity;
import com.weihe.schedule.R;
import com.weihe.schedule.rule.TodayPlan;

/** Current and upcoming courses use natural-height collection rows. */
final class ScrollableWidgetRenderer {
    private static final int INK = 0xFF626B67;
    private ScrollableWidgetRenderer() { }

    static RemoteViews render(Context context, TodayPlan plan, boolean compact) {
        RemoteViews views = new RemoteViews(context.getPackageName(), compact
                ? R.layout.widget_scrolling_compact : R.layout.widget_scrolling);
        views.setTextViewText(R.id.scrolling_date,
                CourseWidgetRenderer.compactDateLine(plan));
        views.setTextColor(R.id.scrolling_date, INK);
        views.setTextViewText(R.id.scrolling_week, NextClassWidgetRenderer.weekLabel(plan));
        views.setTextViewText(R.id.scrolling_title,
                CourseWidgetRenderer.entryText(plan.todayCount()));
        views.setTextColor(R.id.scrolling_title, INK);
        if (compact) views.setViewVisibility(R.id.scrolling_title, android.view.View.GONE);
        Intent today = new Intent(context, MainActivity.class);
        today.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, today,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.scrolling_date, open);
        views.setOnClickPendingIntent(R.id.scrolling_title, open);
        Intent detail = new Intent(context, CourseDetailActivity.class);
        detail.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Collection rows supply only course extras; the explicit component is fixed.
        views.setPendingIntentTemplate(R.id.scrolling_list, PendingIntent.getActivity(
                context, compact ? 4202 : 4201, detail,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews.RemoteCollectionItems.Builder items =
                    new RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(true).setViewTypeCount(1);
            for (TodayPlan.Session session : sessions(plan, compact)) {
                items.addItem(session.courseId(), row(context, session, compact, plan.now()));
            }
            views.setRemoteAdapter(R.id.scrolling_list, items.build());
        } else {
            Intent service = new Intent(context, ScrollableWidgetService.class);
            service.putExtra("compact", compact);
            service.setData(Uri.parse("weihe://scrolling/" + (compact ? "compact" : "full")
                    + "/" + plan.now()));
            views.setRemoteAdapter(R.id.scrolling_list, service);
        }
        return views;
    }

    static java.util.List<TodayPlan.Session> sessions(TodayPlan plan, boolean compact) {
        java.util.List<TodayPlan.Session> rows = new java.util.ArrayList<>();
        if (plan == null || plan.headline() == null) return rows;
        rows.add(plan.headline());
        rows.addAll(plan.following());
        if (!compact && plan.previous() != null) rows.add(plan.previous());
        return rows;
    }

    static RemoteViews row(Context context, TodayPlan.Session session, boolean compact, java.time.LocalDateTime now) {
        RemoteViews row;
        int clickId;
        if (compact) {
            row = new RemoteViews(context.getPackageName(), R.layout.widget_scrolling_row_compact);
            row.setTextViewText(R.id.widget_next_name, session.name());
            row.setTextViewText(R.id.widget_next_time, NextClassWidgetRenderer.clockOf(session));
            row.setTextViewText(R.id.widget_next_room, session.location());
            row.setTextColor(R.id.widget_next_room, com.weihe.schedule.design.Palette.ON_SURFACE_VARIANT);
            row.setInt(R.id.widget_next_bar, "setBackgroundColor", session.color());
            clickId = R.id.scrolling_row;
        } else {
            row = CourseWidgetRenderer.collectionItem(context, session, now);
            clickId = R.id.widget_card;
        }
        Intent fill = new Intent();
        fill.putExtra(CourseDetailActivity.EXTRA_COURSE_ID, session.courseId());
        row.setOnClickFillInIntent(clickId, fill);
        return row;
    }
}
