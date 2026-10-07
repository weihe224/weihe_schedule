package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for the clash rules.
 *
 * <p>These run on a plain JVM: no emulator, no device, no Android SDK. That is
 * the whole point of keeping the rules in a class that knows nothing about
 * Android.</p>
 */
public class CourseConflictsTest {

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

    @Test
    public void identicalSlotsClash() {
        assertTrue(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL),
                course(2, 1, 1, 2, 1, 16, Course.PARITY_ALL)));
    }

    @Test
    public void differentWeekdaysNeverClash() {
        assertFalse(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL),
                course(2, 2, 1, 2, 1, 16, Course.PARITY_ALL)));
    }

    @Test
    public void adjacentPeriodsDoNotClash() {
        assertFalse(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL),
                course(2, 1, 3, 4, 1, 16, Course.PARITY_ALL)));
    }

    @Test
    public void partiallyOverlappingPeriodsClash() {
        assertTrue(CourseConflicts.overlaps(
                course(1, 1, 2, 3, 1, 16, Course.PARITY_ALL),
                course(2, 1, 3, 4, 1, 16, Course.PARITY_ALL)));
    }

    @Test
    public void oddAndEvenWeekCoursesNeverMeet() {
        assertFalse(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ODD),
                course(2, 1, 1, 2, 1, 16, Course.PARITY_EVEN)));
    }

    @Test
    public void weeklyCourseClashesWithOddWeekCourse() {
        assertTrue(CourseConflicts.overlaps(
                course(1, 4, 1, 2, 1, 16, Course.PARITY_ALL),
                course(2, 4, 1, 2, 1, 16, Course.PARITY_ODD)));
    }

    @Test
    public void disjointWeekRangesDoNotClash() {
        assertFalse(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 8, Course.PARITY_ODD),
                course(2, 1, 1, 2, 9, 16, Course.PARITY_ODD)));
        assertFalse(CourseConflicts.overlaps(
                course(1, 1, 1, 2, 1, 8, Course.PARITY_ALL),
                course(2, 1, 1, 2, 9, 16, Course.PARITY_ALL)));
    }

    @Test
    public void overlappingWeekRangesClash() {
        assertTrue(CourseConflicts.overlaps(
                course(1, 3, 5, 6, 1, 8, Course.PARITY_ALL),
                course(2, 3, 5, 6, 5, 12, Course.PARITY_ALL)));
    }

    @Test
    public void editingACourseNeverReportsItself() {
        List<Course> stored = storedCourses();
        List<Course> clashes = CourseConflicts.findConflicts(stored,
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL));

        assertEquals(1L, clashes.size());
        assertEquals(3L, clashes.get(0).id);
    }

    @Test
    public void aBrandNewCourseReportsEveryClash() {
        assertEquals(2L, CourseConflicts.findConflicts(storedCourses(),
                course(0, 1, 2, 3, 1, 16, Course.PARITY_ALL)).size());
    }

    @Test
    public void aDayWithoutCoursesReportsNoClash() {
        assertTrue(CourseConflicts.findConflicts(storedCourses(),
                course(0, 5, 1, 2, 1, 16, Course.PARITY_ALL)).isEmpty());
    }

    @Test
    public void conflictingIdsFlagsOnlyTheClashingCourses() {
        Set<Long> ids = CourseConflicts.conflictingIds(storedCourses());

        assertTrue(ids.contains(Long.valueOf(1L)));
        assertFalse(ids.contains(Long.valueOf(2L)));
        assertTrue(ids.contains(Long.valueOf(3L)));
        assertEquals(2L, ids.size());
    }

    @Test
    public void describeMentionsTheNameAndThePeriods() {
        String text = CourseConflicts.describe(
                course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL));

        assertTrue(text.contains("C1"));
        assertTrue(text.contains("1-2"));
    }

    /** Math 1-2, English 9-10 and Physics 2-3, all on Monday in every week. */
    private static List<Course> storedCourses() {
        List<Course> stored = new ArrayList<>();
        stored.add(course(1, 1, 1, 2, 1, 16, Course.PARITY_ALL));
        stored.add(course(2, 1, 9, 10, 1, 16, Course.PARITY_ALL));
        stored.add(course(3, 1, 2, 3, 1, 16, Course.PARITY_ALL));
        return stored;
    }
}
