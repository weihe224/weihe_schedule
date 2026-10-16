package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * Which courses may share one stacked card.
 *
 * <p>The point of the rule is that the overview stops splitting a slot into a
 * column per course -- but only where the courses never actually meet. A clash
 * is still a clash, and the tests below hold both halves of that: alternatives
 * collapse onto one card, and two lessons in the same week do not.</p>
 */
public class CourseStackingTest {

    private static Course course(long id, int day, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = id;
        course.name = "课" + id;
        course.dayOfWeek = day;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        return course;
    }

    /** A course with an explicit week range; parity defaults to every week. */
    private static Course inWeeks(long id, int day, int startPeriod, int endPeriod,
            int startWeek, int endWeek) {
        Course course = course(id, day, startPeriod, endPeriod);
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        return course;
    }

    private static Course parity(long id, int day, int startPeriod, int endPeriod,
            int parity) {
        Course course = course(id, day, startPeriod, endPeriod);
        course.parity = parity;
        return course;
    }

    // ------------------------------------------------------------------- slot

    @Test
    public void theSameDayAndPeriodRangeIsTheSameSlot() {
        Course a = inWeeks(1, 2, 3, 4, 1, 5);
        Course b = inWeeks(2, 2, 3, 4, 8, 12);

        assertTrue(CourseStacking.sameSlot(a, b));
    }

    @Test
    public void aDifferentDayOrPeriodRangeIsNotTheSameSlot() {
        Course base = course(1, 2, 3, 4);

        assertFalse(CourseStacking.sameSlot(base, course(2, 3, 3, 4)));
        assertFalse(CourseStacking.sameSlot(base, course(3, 2, 4, 4)));
        assertFalse(CourseStacking.sameSlot(base, course(4, 2, 3, 5)));
        assertFalse(CourseStacking.sameSlot(base, course(5, 2, 2, 4)));
    }

    @Test
    public void nothingIsTheSameSlotAsNothing() {
        Course a = course(1, 1, 1, 2);
        assertFalse(CourseStacking.sameSlot(null, a));
        assertFalse(CourseStacking.sameSlot(a, null));
    }

    // ------------------------------------------------------------------ weeks

    @Test
    public void overlappingWeekRangesMeet() {
        assertTrue(CourseStacking.weeksMeet(
                inWeeks(1, 1, 1, 2, 1, 5), inWeeks(2, 1, 1, 2, 5, 9)));
        assertTrue(CourseStacking.weeksMeet(
                inWeeks(1, 1, 1, 2, 3, 4), inWeeks(2, 1, 1, 2, 1, 18)));
    }

    @Test
    public void separateWeekRangesNeverMeet() {
        assertFalse(CourseStacking.weeksMeet(
                inWeeks(1, 1, 1, 2, 1, 5), inWeeks(2, 1, 1, 2, 6, 10)));
    }

    @Test
    public void touchingWeekRangesMeet() {
        // Week 5 in both: the end of one is the start of the other.
        assertTrue(CourseStacking.weeksMeet(
                inWeeks(1, 1, 1, 2, 1, 5), inWeeks(2, 1, 1, 2, 5, 10)));
    }

    @Test
    public void oddAndEvenWeeksNeverMeetEvenOverTheSameRange() {
        // The whole term each, but never the same week: this is the case a
        // hand-rolled range comparison would get wrong.
        Course odd = parity(1, 1, 3, 4, Course.PARITY_ODD);
        Course even = parity(2, 1, 3, 4, Course.PARITY_EVEN);

        assertFalse(CourseStacking.weeksMeet(odd, even));
        assertTrue(CourseStacking.canStack(odd, even));
    }

    @Test
    public void theSameParityOverTheSameRangeDoesMeet() {
        Course first = parity(1, 1, 3, 4, Course.PARITY_ODD);
        Course second = parity(2, 1, 3, 4, Course.PARITY_ODD);

        assertTrue(CourseStacking.weeksMeet(first, second));
        assertFalse(CourseStacking.canStack(first, second));
    }

    // ------------------------------------------------------------------ stacking

    @Test
    public void coursesInDifferentWeeksMayShareACard() {
        Course early = inWeeks(1, 2, 3, 4, 1, 9);
        Course late = inWeeks(2, 2, 3, 4, 10, 18);

        assertTrue(CourseStacking.canStack(early, late));
    }

    @Test
    public void aClashMayNotShareACard() {
        Course a = inWeeks(1, 2, 3, 4, 1, 9);
        Course b = inWeeks(2, 2, 3, 4, 9, 18);

        assertTrue("these really do clash in week 9",
                CourseStacking.weeksMeet(a, b));
        assertFalse(CourseStacking.canStack(a, b));
    }

    @Test
    public void aDifferentTimeRangeMayNotShareACard() {
        // Never the same week, but not the same slot either: one is a three period
        // lesson and the other a two, so they are not alternatives for each other.
        Course shortOne = inWeeks(1, 2, 3, 4, 1, 9);
        Course longOne = inWeeks(2, 2, 3, 5, 10, 18);

        assertFalse(CourseStacking.canStack(shortOne, longOne));
    }

    // --------------------------------------------------------------- partition

    @Test
    public void oneCourseIsOneStack() {
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 18)), CourseStacking.NO_WEEK);

        assertEquals(1, stacks.size());
        assertEquals(1, stacks.get(0).size());
    }

    @Test
    public void twoAlternativesBecomeOneStack() {
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 3, 4, 10, 18),
                inWeeks(2, 1, 3, 4, 1, 9)), CourseStacking.NO_WEEK);

        assertEquals(1, stacks.size());
        assertEquals(2, stacks.get(0).size());
        // Led by the course that starts term, whichever order they arrived in.
        assertEquals(2L, stacks.get(0).get(0).id);
        assertEquals(1L, stacks.get(0).get(1).id);
    }

    @Test
    public void aChainOfAlternativesBecomesOneStack() {
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 9, 12),
                inWeeks(2, 1, 1, 2, 13, 18),
                inWeeks(3, 1, 1, 2, 1, 8)), CourseStacking.NO_WEEK);

        assertEquals(1, stacks.size());
        assertEquals(3, stacks.get(0).size());
        assertEquals(Arrays.asList(3L, 1L, 2L), ids(stacks.get(0)));
    }

    @Test
    public void aClashingPairStaysTwoStacks() {
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 9),
                inWeeks(2, 1, 1, 2, 9, 18)), CourseStacking.NO_WEEK);

        assertEquals(2, stacks.size());
        assertEquals(1, stacks.get(0).size());
        assertEquals(1, stacks.get(1).size());
    }

    @Test
    public void aCourseThatClashesWithEveryExistingStackStartsItsOwn() {
        // Two alternatives, plus a third that overlaps both of them.
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 4),
                inWeeks(2, 1, 1, 2, 5, 9),
                inWeeks(3, 1, 1, 2, 2, 6)), CourseStacking.NO_WEEK);

        assertEquals(2, stacks.size());
        assertEquals(2, stacks.get(0).size());
        assertEquals(1, stacks.get(1).size());
        assertEquals(3L, stacks.get(1).get(0).id);
    }

    @Test
    public void everyCourseOfASlotEndsUpInExactlyOneStack() {
        List<Course> slot = Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 2), inWeeks(2, 1, 1, 2, 3, 4),
                inWeeks(3, 1, 1, 2, 2, 3), inWeeks(4, 1, 1, 2, 5, 6),
                inWeeks(5, 1, 1, 2, 4, 5));

        List<List<Course>> stacks = CourseStacking.partition(slot, CourseStacking.NO_WEEK);
        int placed = 0;
        List<Long> seen = new ArrayList<>();
        for (List<Course> stack : stacks) {
            placed += stack.size();
            for (Course course : stack) {
                seen.add(Long.valueOf(course.id));
            }
        }
        assertEquals(slot.size(), placed);
        assertEquals(slot.size(), new java.util.HashSet<>(seen).size());
    }

    @Test
    public void nothingIsAStackOfNothing() {
        assertTrue(CourseStacking.partition(null, CourseStacking.NO_WEEK).isEmpty());
        assertTrue(CourseStacking.partition(new ArrayList<Course>(), CourseStacking.NO_WEEK).isEmpty());
    }

    // ------------------------------------------------------------------- units

    @Test
    public void eachSlotIsSplitIntoItsOwnStacks() {
        List<Course> cluster = Arrays.asList(
                inWeeks(1, 1, 3, 4, 1, 4), inWeeks(2, 1, 3, 4, 5, 9),
                inWeeks(3, 1, 5, 6, 1, 18));

        List<List<Course>> units = CourseStacking.unitsOf(cluster, CourseStacking.NO_WEEK);

        assertEquals(2, units.size());
        assertEquals(2, units.get(0).size());
        assertEquals(1, units.get(1).size());
        // Ordered by the slot, not by the week.
        assertEquals(3, units.get(0).get(0).startPeriod);
        assertEquals(5, units.get(1).get(0).startPeriod);
    }

    @Test
    public void coursesThatStartTogetherButEndDifferentlyAreSeparateSlots() {
        // Both start at period 3, but one runs to 4 and the other to 5: they are
        // not alternatives for each other, so they must not be folded together.
        List<Course> cluster = Arrays.asList(
                inWeeks(1, 1, 3, 4, 1, 18), inWeeks(2, 1, 3, 5, 1, 18));

        List<List<Course>> units = CourseStacking.unitsOf(cluster, CourseStacking.NO_WEEK);

        assertEquals(2, units.size());
        assertEquals(1, units.get(0).size());
        assertEquals(1, units.get(1).size());
    }

    @Test
    public void theUnitsCoverEveryCourseExactlyOnce() {
        List<Course> cluster = Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 4), inWeeks(2, 1, 1, 2, 5, 9),
                inWeeks(3, 1, 1, 2, 2, 6), inWeeks(4, 1, 3, 4, 1, 18),
                inWeeks(5, 1, 3, 4, 1, 18));

        List<List<Course>> units = CourseStacking.unitsOf(cluster, CourseStacking.NO_WEEK);

        int total = 0;
        for (List<Course> unit : units) {
            total += unit.size();
        }
        assertEquals(cluster.size(), total);
    }

    @Test
    public void nothingIsAUnitOfNothing() {
        assertTrue(CourseStacking.unitsOf(null, CourseStacking.NO_WEEK).isEmpty());
        assertTrue(CourseStacking.unitsOf(new ArrayList<Course>(), CourseStacking.NO_WEEK).isEmpty());
    }

    private static List<Long> ids(List<Course> courses) {
        List<Long> ids = new ArrayList<>();
        for (Course course : courses) {
            ids.add(Long.valueOf(course.id));
        }
        return ids;
    }

    // -------------------------------------------------- the current week leads

    @Test
    public void theCourseTaughtThisWeekLeadsItsStack() {
        // The later half of term is under way, so the second course is the one the
        // user has to attend -- and it must be the one drawn on top.
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 8), inWeeks(2, 1, 1, 2, 9, 16)), 12);

        assertEquals(1, stacks.size());
        assertEquals(2L, stacks.get(0).get(0).id);
        assertEquals(Arrays.asList(2L, 1L), ids(stacks.get(0)));
    }

    @Test
    public void theEarliestCourseLeadsWhenNoWeekIsGiven() {
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 8), inWeeks(2, 1, 1, 2, 9, 16)),
                CourseStacking.NO_WEEK);

        assertEquals(1L, stacks.get(0).get(0).id);
    }

    @Test
    public void theParityCourseTaughtThisWeekLeads() {
        // Both run the whole term, but only one of them is on this week.
        Course odd = inWeeks(1, 1, 1, 2, 1, 18);
        odd.parity = Course.PARITY_ODD;
        Course even = inWeeks(2, 1, 1, 2, 1, 18);
        even.parity = Course.PARITY_EVEN;

        List<List<Course>> stacks = CourseStacking.partition(
                Arrays.asList(odd, even), 6);

        assertEquals(1, stacks.size());
        assertEquals(2L, stacks.get(0).get(0).id);
    }

    @Test
    public void aWeekOutsideEveryCourseFallsBackToWeekOrder() {
        // Week 30 is past the end of the term, so nothing is "this week" and the
        // order is the plain one rather than an arbitrary pick.
        List<List<Course>> stacks = CourseStacking.partition(Arrays.asList(
                inWeeks(1, 1, 1, 2, 9, 16), inWeeks(2, 1, 1, 2, 1, 8)), 30);

        assertEquals(1, stacks.size());
        assertEquals(2L, stacks.get(0).get(0).id);
    }

    @Test
    public void atMostOneCourseInAStackRunsInAnyOneWeek() {
        // The invariant the ordering rests on: because a stack only holds courses
        // that never share a week, "the course running this week" is a single
        // unambiguous answer and never a tie to break.
        List<Course> slot = Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 3), inWeeks(2, 1, 1, 2, 4, 6),
                inWeeks(3, 1, 1, 2, 7, 9), inWeeks(4, 1, 1, 2, 10, 12));

        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            for (List<Course> stack : CourseStacking.partition(slot, week)) {
                int running = 0;
                for (Course course : stack) {
                    if (CourseStacking.runsIn(course, week)) {
                        running++;
                    }
                }
                assertTrue("week " + week + " has " + running
                                + " courses running in one stack",
                        running <= 1);
            }
        }
    }

    @Test
    public void runsInReadsTheWeekRangeAndTheParity() {
        Course all = inWeeks(1, 1, 1, 2, 5, 9);
        assertTrue(CourseStacking.runsIn(all, 5));
        assertTrue(CourseStacking.runsIn(all, 9));
        assertFalse(CourseStacking.runsIn(all, 4));
        assertFalse(CourseStacking.runsIn(all, 10));

        Course odd = inWeeks(2, 1, 1, 2, 1, 18);
        odd.parity = Course.PARITY_ODD;
        assertTrue(CourseStacking.runsIn(odd, 3));
        assertFalse(CourseStacking.runsIn(odd, 4));

        assertFalse("no week means nothing is running",
                CourseStacking.runsIn(all, CourseStacking.NO_WEEK));
        assertFalse(CourseStacking.runsIn(null, 5));
    }

    @Test
    public void theCurrentWeekDoesNotChangeHowManyStacksThereAre() {
        // Ordering only, not grouping: the stacks are the same units whatever week
        // leads them.
        List<Course> slot = Arrays.asList(
                inWeeks(1, 1, 1, 2, 1, 4), inWeeks(2, 1, 1, 2, 5, 9),
                inWeeks(3, 1, 1, 2, 3, 6));

        assertEquals(2, CourseStacking.partition(slot, 1).size());
        assertEquals(2, CourseStacking.partition(slot, 5).size());
        assertEquals(2, CourseStacking.partition(slot, 8).size());
        assertEquals(2, CourseStacking.partition(slot, 30).size());
    }
}
