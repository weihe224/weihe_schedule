package com.weihe.schedule.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.rule.TodayPlan;

/**
 * What the 2x2 widget lists, and what it says underneath the list.
 *
 * <p>The square is the 3x2's list with a footer: the same item view, up to two of them,
 * and one grey line for the class that just finished. The tests here pin which classes
 * become rows and which one becomes the footer -- the two are not interchangeable, and
 * getting it wrong shows the same class twice.</p>
 */
public class SquareWidgetRendererTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate MONDAY = TERM_START;

    // ============================================ which classes become rows

    @Test
    public void theRowsAreTheHeadlineAndTheClassAfterIt() {
        TodayPlan plan = day(MONDAY.atTime(8, 20),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 6, 7));

        List<TodayPlan.Session> items = SquareWidgetRenderer.items(plan);

        assertEquals(2, items.size());
        assertEquals("the class running keeps the first row", 1L, items.get(0).courseId());
        assertEquals("the next one takes the second", 2L, items.get(1).courseId());
    }

    @Test
    public void oneClassLeftMeansOneRow() {
        TodayPlan plan = day(MONDAY.atTime(12, 30), course(1L, 1, 1, 2), course(2L, 1, 6, 7));

        List<TodayPlan.Session> items = SquareWidgetRenderer.items(plan);

        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).courseId());
    }

    @Test
    public void theFinishedClassIsAFooterNotARow() {
        // The 3x2's helper fills a free slot with the finished class; the square must
        // not, or that class would appear twice -- once as a row and once as the footer.
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 6, 7));

        List<TodayPlan.Session> items = SquareWidgetRenderer.items(plan);
        TodayPlan.Session finished = plan.previous();

        assertEquals(1L, finished.courseId());
        for (TodayPlan.Session item : items) {
            assertTrue("the footer must not repeat a row",
                    item.courseId() != finished.courseId());
        }
    }

    @Test
    public void anEmptyEveningHasNoRowsAndNoFooter() {
        TodayPlan plan = day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2), course(2L, 1, 3, 4));

        assertTrue(SquareWidgetRenderer.items(plan).isEmpty());
        assertTrue(SquareWidgetRenderer.items(null).isEmpty());
        assertTrue("nothing left today means nothing to list",
                plan.state() == TodayPlan.State.NO_CLASS_TODAY);
    }

    @Test
    public void neverMoreThanTwoRows() {
        TodayPlan plan = day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 1), course(2L, 1, 2, 2), course(3L, 1, 3, 3),
                course(4L, 1, 4, 4));

        assertEquals(CourseWidgetRenderer.MAX_CARDS,
                SquareWidgetRenderer.items(plan).size());
    }

    // ============================================ the footer

    @Test
    public void theFooterSaysWhenTheClassEnded() {
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 6, 7));

        assertEquals("Course 1 (09:35\u7ED3\u675F)",
                SquareWidgetRenderer.finishedText(plan.previous()));
    }

    @Test
    public void theFooterFollowsTheMostRecentFinish() {
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 6, 7));

        assertEquals("1-2 ends 09:35 and 3-4 ends 10:55, so the later one wins",
                2L, plan.previous().courseId());
        assertEquals("Course 2 (10:55\u7ED3\u675F)",
                SquareWidgetRenderer.finishedText(plan.previous()));
    }

    @Test
    public void beforeTheFirstClassThereIsNoFooter() {
        TodayPlan plan = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2), course(2L, 1, 3, 4));

        assertNull(plan.previous());
        assertEquals("", SquareWidgetRenderer.finishedText(null));
    }

    // ============================================ what gives way first

    @Test
    public void theFooterGoesBeforeAnyRow() {
        // The rows are the point of the widget; the finished class is context. So the
        // footer has a height threshold and disappears first on a short square.
        assertTrue("the threshold has to sit at or under a two-cell square",
                SquareWidgetRenderer.SECONDARY_MIN_HEIGHT_DP <= 184);
        assertTrue("and it should still show at a normal square size",
                SquareWidgetRenderer.SECONDARY_MIN_HEIGHT_DP >= 120);
    }

    // ---------------------------------------------------------------- fixtures

    /** A day read the way the square widget reads it. */
    private static TodayPlan day(LocalDateTime now, Course... courses) {
        return TodayPlan.of(Arrays.asList(courses), semester(), schedules(), null, now,
                CourseWidgetRenderer.MAX_CARDS);
    }

    private static Semester semester() {
        return new Semester(1L, TERM_START, "Test term");
    }

    private static Course course(long id, int dayOfWeek, int startPeriod, int endPeriod) {
        Course course = new Course();
        course.id = id;
        course.name = "Course " + id;
        course.teacher = "Prof. Li";
        course.location = "A\u697C101";
        course.dayOfWeek = dayOfWeek;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = 1;
        course.endWeek = 18;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    /** Forty-minute periods from 08:15, which is what the fixture courses ring. */
    private static List<BellSchedule> schedules() {
        int[] starts = new int[15];
        int[] ends = new int[15];
        for (int index = 0; index < starts.length; index++) {
            starts[index] = 8 * 60 + 15 + index * 40;
            ends[index] = starts[index] + 40;
        }
        return Collections.singletonList(new BellSchedule("s1", "Standard", starts, ends,
                Collections.singletonList("a\u697C"), 40));
    }
}
