package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * What the search screen decides before it draws anything.
 *
 * <p>The rule layer's matching is covered by {@link CourseQueryTest} and is not
 * repeated here. What this covers is the part the screen adds: telling three
 * situations apart from two inputs, one of which -- an empty result list -- looks
 * identical whether the user has typed nothing or typed something hopeless.</p>
 */
public class CourseSearchSummaryTest {

    // ============================================ nothing typed yet

    @Test
    public void anEmptyKeywordIsIdleRatherThanNoMatches() {
        // The distinction the screen exists to get right: both of these arrive as
        // an empty list, and only one of them means "nothing matched".
        CourseSearchSummary summary =
                CourseSearchSummary.of("", Collections.<Course>emptyList());

        assertEquals(CourseSearchSummary.State.IDLE, summary.state());
        assertNotEquals("a blank field must not read as a failed search",
                CourseSearchSummary.State.NO_MATCHES, summary.state());
        assertFalse(summary.showsResults());
    }

    @Test
    public void aKeywordOfNothingButSpacesIsIdleToo() {
        assertEquals(CourseSearchSummary.State.IDLE,
                CourseSearchSummary.of("   ", Collections.<Course>emptyList()).state());
        assertEquals(CourseSearchSummary.State.IDLE,
                CourseSearchSummary.of("\t", Collections.<Course>emptyList()).state());
    }

    @Test
    public void aMissingKeywordIsIdleRatherThanACrash() {
        CourseSearchSummary summary = CourseSearchSummary.of(null, null);

        assertEquals(CourseSearchSummary.State.IDLE, summary.state());
        assertEquals(0, summary.count());
        assertFalse(summary.showsResults());
    }

    @Test
    public void theIdleAndNoMatchesStatesDoNotSayTheSameThing() {
        String idle = CourseSearchSummary.of("", Collections.<Course>emptyList())
                .headline();
        String none = CourseSearchSummary.of("zzz", Collections.<Course>emptyList())
                .headline();

        assertFalse(idle.trim().isEmpty());
        assertFalse(none.trim().isEmpty());
        assertNotEquals("the two empty screens must be distinguishable", idle, none);
    }

    // ============================================ something typed

    @Test
    public void aKeywordWithResultsReportsThem() {
        CourseSearchSummary summary = CourseSearchSummary.of("math", courses(3));

        assertEquals(CourseSearchSummary.State.RESULTS, summary.state());
        assertTrue(summary.showsResults());
        assertEquals(3, summary.count());
        assertTrue("the count belongs in the headline",
                summary.headline().contains("3"));
    }

    @Test
    public void aKeywordWithoutResultsIsNoMatches() {
        CourseSearchSummary summary =
                CourseSearchSummary.of("zzz", Collections.<Course>emptyList());

        assertEquals(CourseSearchSummary.State.NO_MATCHES, summary.state());
        assertFalse(summary.showsResults());
        assertEquals(0, summary.count());
    }

    @Test
    public void theCountIsTheNumberOfCoursesItWasGiven() {
        for (int size = 1; size <= 4; size++) {
            CourseSearchSummary summary = CourseSearchSummary.of("x", courses(size));
            assertEquals(size, summary.count());
            assertTrue(summary.headline().contains(String.valueOf(size)));
        }
    }

    @Test
    public void aMissingResultListCountsAsNone() {
        CourseSearchSummary summary = CourseSearchSummary.of("math", null);

        assertEquals(CourseSearchSummary.State.NO_MATCHES, summary.state());
        assertEquals(0, summary.count());
    }

    // ============================================ what each state says

    @Test
    public void theTwoGuidanceStatesBothCarryATitleAndAHint() {
        for (String keyword : new String[] { "", "zzz" }) {
            CourseSearchSummary summary = CourseSearchSummary.of(keyword,
                    Collections.<Course>emptyList());
            assertTrue(summary.showsGuidance());
            assertFalse("the title must say something: " + summary.state(),
                    summary.headline().trim().isEmpty());
            assertFalse("the hint must say something: " + summary.state(),
                    summary.hint().trim().isEmpty());
        }
    }

    @Test
    public void theResultsStateCarriesNoHint() {
        CourseSearchSummary summary = CourseSearchSummary.of("math", courses(2));

        assertFalse(summary.showsGuidance());
        assertEquals("with results on screen there is nothing to explain",
                "", summary.hint());
    }

    private static List<Course> courses(int size) {
        List<Course> courses = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Course course = new Course();
            course.id = index + 1;
            course.name = "Course " + (index + 1);
            courses.add(course);
        }
        return courses;
    }
}
