package com.weihe.schedule.widget;

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import com.weihe.schedule.rule.TodayPlan;
import java.util.Collections;
import java.util.List;

/** Legacy collection adapter for Android 8-11; modern hosts receive rows directly. */
public final class ScrollableWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        boolean compact = intent.getBooleanExtra("compact", false);
        return new RemoteViewsFactory() {
            private java.time.LocalDateTime now;
            private List<TodayPlan.Session> sessions = Collections.emptyList();
            @Override public void onCreate() { onDataSetChanged(); }
            @Override public void onDataSetChanged() {
                TodayPlan plan = CourseWidgetDataSource.read(
                        ScrollableWidgetService.this, Integer.MAX_VALUE);
                now = plan.now();
                sessions = ScrollableWidgetRenderer.sessions(plan, compact);
            }
            @Override public void onDestroy() { sessions = Collections.emptyList(); }
            @Override public int getCount() { return sessions.size(); }
            @Override public RemoteViews getViewAt(int position) {
                if (position < 0 || position >= sessions.size()) return null;
                return ScrollableWidgetRenderer.row(ScrollableWidgetService.this,
                        sessions.get(position), compact, now);
            }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public long getItemId(int position) {
                return sessions.get(position).courseId();
            }
            @Override public boolean hasStableIds() { return true; }
        };
    }
}
