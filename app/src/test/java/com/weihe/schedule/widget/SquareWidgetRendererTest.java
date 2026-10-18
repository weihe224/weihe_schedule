package com.weihe.schedule.widget;

import static org.junit.Assert.assertEquals;
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
 * What the 2x2 widget says about the next two classes.
 *
 * <p>The square has room for one sentence about the second class, so that sentence
 * carries the whole difference from the 3x2: it must name the right class and say
 * whether it is the next one or the one after. Those are the cases pinned here.</p>
 */
public class SquareWidgetRendererTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate MONDAY = TERM_START;

    // ============================================ the header

    @Test
    public void theHeaderCarriesTheWeekdayAndTheTeachingWeekButNotTheDate() {
        String header = SquareWidgetRenderer.headerOf(day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 2)));

        assertEquals("\u5468\u4E00 \u00B7 \u7B2C1\u5468 \u5355\u5468", header);
    }

    @Test
    public void outsideTheTermTheHeaderIsJustTheWeekday() {
        String header = SquareWidgetRenderer.headerOf(day(TERM_START.minusWeeks(2).atTime(9, 0),
                course(1L, 1, 1, 2)));

        assertEquals("\u5468\u4E00", header);
    }

    @Test
    public void anEvenWeekSaysSo() {
        String header = SquareWidgetRenderer.headerOf(day(TERM_START.plusWeeks(1).atTime(7, 0),
                course(1L, 1, 1, 2)));

        assertTrue(header, header.contains("\u7B2C2\u5468"));
        assertTrue(header, header.contains("\u53CC\u5468"));
    }

    // ============================================ the second class line

    @Test
    public void whileAClassRunsTheSecondLineIsTheNextClass() {
        TodayPlan plan = day(MONDAY.atTime(8, 20),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4));
        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals(2, cards.size());
        assertEquals("\u4E0B\u8282 09:35 \u00B7 Course 2",
                SquareWidgetRenderer.secondaryText(plan, cards.get(1)));
    }

    @Test
    public void betweenClassesTheSecondLineIsTheOneAfterTheNext() {
        TodayPlan plan = day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4));
        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals("\u518D\u4E0B\u8282 09:35 \u00B7 Course 2",
                SquareWidgetRenderer.secondaryText(plan, cards.get(1)));
    }

    @Test
    public void aDayWithOneClassLeftFillsTheSecondLineWithTheFinishedOne() {
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 6, 7));
        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals("one upcoming class, one that just ended", 2, cards.size());
        assertEquals("\u5DF2\u7ED3\u675F 09:35 \u00B7 Course 1",
                SquareWidgetRenderer.secondaryText(plan, cards.get(1)));
        assertEquals("", SquareWidgetRenderer.secondaryText(plan, null));
    }

    @Test
    public void theSecondLineNeverInvitesAnEmptyName() {
        assertEquals("", SquareWidgetRenderer.secondaryText(null, null));
    }

    // ============================================ the main card

    @Test
    public void theMainCardCarriesTheTimesThenTheRoom() {
        TodayPlan.Session session = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2))
                .headline();

        assertEquals("08:15 - 09:35 \u00B7 A\u697C101",
                SquareWidgetRenderer.detailOf(session, true));
        assertEquals("a short widget keeps the times and drops the room",
                "08:15 - 09:35", SquareWidgetRenderer.detailOf(session, false));
        assertEquals("", SquareWidgetRenderer.detailOf(null, true));
    }

    @Test
    public void aSessionWithoutAVenueDoesNotLeaveADanglingDot() {
        Course bare = course(1L, 1, 1, 2);
        bare.location = "  ";
        TodayPlan.Session session = day(MONDAY.atTime(7, 0), bare).headline();

        assertEquals("08:15 - 09:35", SquareWidgetRenderer.detailOf(session, true));
    }

    // ============================================ what gives way first

    @Test
    public void theSecondLineGoesBeforeTheRoomsDoes() {
        // The main card is the point of the widget; the second class is context. So
        // the second line has the higher threshold and disappears first.
        assertTrue(SquareWidgetRenderer.ROOM_MIN_HEIGHT_DP
                < SquareWidgetRenderer.SECONDARY_MIN_HEIGHT_DP);
        assertTrue("both have to fit inside a two-cell square",
                SquareWidgetRenderer.SECONDARY_MIN_HEIGHT_DP <= 184);
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
