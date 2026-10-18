package com.weihe.schedule.widget;

import java.time.LocalDateTime;

import com.weihe.schedule.data.CourseRepository;
import com.weihe.schedule.rule.TodayPlan;

/**
 * The widget's only way in to the timetable.
 *
 * <p>Reads through {@link CourseRepository}, which is the app's single data entry
 * point, and hands the four things the plan needs to {@link TodayPlan}. Nothing is
 * cached between refreshes: the timetable is small, the widget rebuild reads it
 * once, and a cache would be the thing that shows yesterday's class after an
 * edit.</p>
 */
final class CourseWidgetDataSource {

    private CourseWidgetDataSource() {
    }

    /**
     * What the widget should show right now.
     *
     * @param maxFollowing how many classes after the headline to carry along
     */
    static TodayPlan read(android.content.Context context, int maxFollowing) {
        android.content.Context app = context.getApplicationContext();
        CourseRepository repository = new CourseRepository(app);
        return TodayPlan.of(repository.getCourses(), repository.getActiveSemester(),
                repository.getBellSchedules(), repository.getDefaultScheduleId(),
                LocalDateTime.now(), maxFollowing);
    }
}
