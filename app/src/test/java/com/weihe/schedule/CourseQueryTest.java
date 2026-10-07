package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Tests for the schedule queries the UI is built on.
 *
 * <p>The behaviour asserted here used to be buried inside
 * {@code MainActivity.renderCourses()}, where the only way to check it was to
 * launch the app and look at the screen.</p>
 */
public class CourseQueryTest {

    private static Course course(long id, int day, int fromPeriod, int toPeriod,
            int fromWeek, int toWeek, int parity) {
        Course course = new Course();
        course.id = id;
        course.name = "C" + id;
        course.dayOfWeek = day;
        course.startPeriod = fromPeriod;
        course.endPeriod = toPeriod;
        course.startWeek = fromWeek;
        course.endWeek = toWeek;
        course.parity = parity;
        return course;
    }

    /**
     * Deliberately handed out in a "wrong" order so the sorting is exercised:
     * Monday 3-4, Monday 1-2, then Tuesday 1-2 on odd weeks only.
     */
    private static List<Course> courses() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 3, 4, 1, 16, Course.PARITY_ALL));
        courses.add(course(2, 1, 1, 2, 1, 16, Course.PARITY_ALL));
        courses.add(course(3, 2, 1, 2, 1, 16, Course.PARITY_ODD));
        return courses;
    }

    @Test
    public void onDayReturnsOnlyThatWeekday() {
        List<Course> monday = CourseQuery.onDay(courses(), 1, 1);

        assertEquals(2L, monday.size());
        assertEquals(1L, monday.get(0).dayOfWeek);
        assertEquals(1L, monday.get(1).dayOfWeek);
    }

    @Test
    public void onDaySortsByStartThenEndPeriod() {
        List<Course> monday = CourseQuery.onDay(courses(), 1, 1);

        assertEquals(2L, monday.get(0).id);
        assertEquals(1L, monday.get(1).id);
    }

    @Test
    public void onDayRespectsOddEvenParity() {
        assertEquals(1L, CourseQuery.onDay(courses(), 1, 2).size());
        assertTrue(CourseQuery.onDay(courses(), 2, 2).isEmpty());
    }

    @Test
    public void onDayRespectsTheWeekWindow() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        assertEquals(1L, CourseQuery.onDay(courses, 8, 1).size());
        assertTrue(CourseQuery.onDay(courses, 9, 1).isEmpty());
    }

    @Test
    public void inWeekSortsByWeekdayThenPeriod() {
        List<Course> week = CourseQuery.inWeek(courses(), 1);

        assertEquals(3L, week.size());
        assertEquals(2L, week.get(0).id);
        assertEquals(1L, week.get(1).id);
        assertEquals(3L, week.get(2).id);
    }

    @Test
    public void inWeekDropsCoursesThatDoNotHappenThatWeek() {
        assertEquals(2L, CourseQuery.inWeek(courses(), 2).size());
    }

    @Test
    public void emptyInputYieldsEmptyOutput() {
        List<Course> none = Collections.emptyList();

        assertTrue(CourseQuery.onDay(none, 1, 1).isEmpty());
        assertTrue(CourseQuery.inWeek(none, 1).isEmpty());
    }

    @Test
    public void allReturnsEveryCourseInDayThenPeriodOrder() {
        List<Course> everything = CourseQuery.all(courses());

        assertEquals(3L, everything.size());
        assertEquals(2L, everything.get(0).id);
        assertEquals(1L, everything.get(1).id);
        assertEquals(3L, everything.get(2).id);
    }

    @Test
    public void allKeepsCoursesThatOnlyRunInPartOfTheTerm() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 1, 2, 1, 4, Course.PARITY_ALL));

        assertEquals(1L, CourseQuery.all(courses).size());
    }

    @Test
    public void allDoesNotReorderTheCallersList() {
        List<Course> courses = courses();

        CourseQuery.all(courses);

        assertEquals(1L, courses.get(0).id);
        assertEquals(2L, courses.get(1).id);
        assertEquals(3L, courses.get(2).id);
    }

    @Test
    public void startingAtFindsOnlyCoursesBeginningAtThatPeriod() {
        List<Course> mondayFirst = CourseQuery.startingAt(courses(), 1, 1);

        assertEquals(1L, mondayFirst.size());
        assertEquals(2L, mondayFirst.get(0).id);
        assertTrue(CourseQuery.startingAt(courses(), 1, 2).isEmpty());
        assertEquals(1L, CourseQuery.startingAt(courses(), 2, 1).size());
    }

    @Test
    public void startingAtReturnsEveryCourseSharingTheSlot() {
        List<Course> courses = courses();
        courses.add(course(4, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        List<Course> sharing = CourseQuery.startingAt(courses, 1, 1);

        assertEquals(2L, sharing.size());
        assertEquals(2L, sharing.get(0).id);
        assertEquals(4L, sharing.get(1).id);
    }

    @Test
    public void maxParallelCountsTheBusiestSlot() {
        assertEquals(1L, CourseQuery.maxParallel(courses()));

        List<Course> crowded = courses();
        crowded.add(course(4, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        assertEquals(2L, CourseQuery.maxParallel(crowded));
    }

    @Test
    public void maxParallelIsOneWhenThereAreNoCourses() {
        assertEquals(1L, CourseQuery.maxParallel(new ArrayList<Course>()));
    }

    @Test
    public void queryingDoesNotReorderTheCallersList() {
        List<Course> courses = courses();

        CourseQuery.onDay(courses, 1, 1);
        CourseQuery.inWeek(courses, 1);

        assertEquals(1L, courses.get(0).id);
        assertEquals(2L, courses.get(1).id);
        assertEquals(3L, courses.get(2).id);
    }
}
