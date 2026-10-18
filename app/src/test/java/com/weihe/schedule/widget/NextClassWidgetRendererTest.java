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
 * What the 2x1 widget shows: the header shared with the 3x2, then one class.
 *
 * <p>The strip no longer carries a status word or a countdown, so what is left to get
 * right is the header (date and teaching week, in the same shape as the large widget),
 * the three text lines, and the empty states -- which must never borrow a class that is
 * not there.</p>
 */
public class NextClassWidgetRendererTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate MONDAY = TERM_START;

    // ============================================ the header

    @Test
    public void theHeaderIsTheDatePlusTheWeekCapsule() {
        TodayPlan plan = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2));

        assertEquals("8\u670831\u65E5 \u5468\u4E00", CourseWidgetRenderer.dateLine(plan));
        assertEquals("\u7B2C1\u5468 (\u5355\u5468)", NextClassWidgetRenderer.pillText(plan));
        assertEquals("\u7B2C1\u5468 \u5355\u5468", NextClassWidgetRenderer.weekLabel(plan));
    }

    @Test
    public void outsideTheTermTheHeaderHasNoCapsule() {
        TodayPlan before = day(TERM_START.minusWeeks(2).atTime(9, 0), course(1L, 1, 1, 2));
        TodayPlan after = day(TERM_START.plusWeeks(20).atTime(9, 0), course(1L, 1, 1, 2));

        assertEquals("", NextClassWidgetRenderer.pillText(before));
        assertEquals("", NextClassWidgetRenderer.pillText(after));
        assertTrue(CourseWidgetRenderer.dateLine(before).contains("\u5468\u4E00"));
    }

    @Test
    public void anEvenWeekSaysSoInTheCapsule() {
        TodayPlan plan = day(TERM_START.plusWeeks(1).atTime(7, 0), course(1L, 1, 1, 2));

        assertEquals("\u7B2C2\u5468 (\u53CC\u5468)", NextClassWidgetRenderer.pillText(plan));
    }

    // ============================================ which class

    @Test
    public void theClassIsTheRunningOneThenTheNextOne() {
        TodayPlan before = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2));
        TodayPlan during = day(MONDAY.atTime(8, 20),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4));

        assertEquals("before the first class it is that class", 1L,
                before.headline().courseId());
        assertEquals("08:15", NextClassWidgetRenderer.clockOf(before.headline()));
        assertEquals("while one runs it is still that class", 1L,
                during.headline().courseId());
        assertEquals("09:35", NextClassWidgetRenderer.clockOf(during.next()));
    }

    // ============================================ the empty states

    @Test
    public void finishedAndNeverHadAreDifferentSentences() {
        TodayPlan finished = day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2));
        TodayPlan nothing = TodayPlan.of(Collections.<Course>emptyList(), semester(),
                schedules(), null, MONDAY.atTime(9, 0), 2);

        assertEquals("\u4ECA\u5929\u7684\u8BFE\u90FD\u4E0A\u5B8C\u4E86",
                NextClassWidgetRenderer.emptyTitle(finished));
        assertEquals("\u70B9\u5F00\u770B\u770B\u4ECA\u5929",
                NextClassWidgetRenderer.emptyHint(finished));

        assertEquals("\u8FD8\u6CA1\u6709\u8BFE\u8868",
                NextClassWidgetRenderer.emptyTitle(nothing));
        assertEquals("\u70B9\u5F00\u6DFB\u52A0\u6216\u5BFC\u5165",
                NextClassWidgetRenderer.emptyHint(nothing));
    }

    @Test
    public void aDayWithNoClassesAtAllSaysSoInItsOwnWords() {
        TodayPlan noClasses = day(MONDAY.atTime(9, 0), course(1L, 2, 1, 2));

        assertEquals(0, noClasses.todayCount());
        assertEquals("\u4ECA\u5929\u6CA1\u6709\u8BFE",
                NextClassWidgetRenderer.emptyTitle(noClasses));
    }

    @Test
    public void outsideTheTermSaysWhichSideOfItItIs() {
        TodayPlan before = day(TERM_START.minusWeeks(2).atTime(9, 0), course(1L, 1, 1, 2));
        TodayPlan after = day(TERM_START.plusWeeks(20).atTime(9, 0), course(1L, 1, 1, 2));

        assertEquals("\u5B66\u671F\u8FD8\u6CA1\u5F00\u59CB",
                NextClassWidgetRenderer.emptyTitle(before));
        assertEquals("\u5B66\u671F\u5DF2\u7ED3\u675F",
                NextClassWidgetRenderer.emptyTitle(after));
    }

    // ============================================ the text lines

    @Test
    public void theRoomIsWhateverTheTimetableSaysAndNothingWhenItSaysNothing() {
        TodayPlan.Session withRoom = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2)).headline();
        Course bare = course(2L, 1, 1, 2);
        bare.location = "   ";
        TodayPlan.Session withoutRoom = day(MONDAY.atTime(7, 0), bare).headline();

        assertEquals("A\u697C101", NextClassWidgetRenderer.roomOf(withRoom));
        assertEquals("", NextClassWidgetRenderer.roomOf(withoutRoom));
        assertEquals("", NextClassWidgetRenderer.roomOf(null));
        assertEquals("", NextClassWidgetRenderer.clockOf(null));
        assertNull(day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2)).headline());
    }

    // ============================================ what gives way first

    @Test
    public void theRoomLineIsTheOnlyThingThatCostsHeight() {
        // The strip is four lines: header, name, time, room. Only the room is optional,
        // so it is the one line with a height threshold -- and that threshold has to
        // sit at or under the height one cell really reports (about 96dp here) or it
        // would only ever hide the line it was meant to protect.
        assertTrue("the room has to fit on a one-cell-tall strip",
                NextClassWidgetRenderer.ROOM_MIN_HEIGHT_DP <= 99);
        assertTrue("and it should still be shown well below two cells",
                NextClassWidgetRenderer.ROOM_MIN_HEIGHT_DP >= 60);
    }

    // ---------------------------------------------------------------- fixtures

    /** A day read the way the 2x1 widget reads it. */
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
