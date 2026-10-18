package com.weihe.schedule.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * What the 3x2 widget lists, and what it says around the list.
 *
 * <p>The selection rules are the part most likely to be got wrong quietly: showing the
 * day's first two classes at four in the afternoon looks fine in a screenshot and is
 * useless in a pocket. Every rule from the brief is pinned here instead.</p>
 */
public class CourseWidgetRendererTest {

    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");
    private static final LocalDate MONDAY = TERM_START;

    // ============================================ which classes the list holds

    @Test
    public void whileAClassRunsItKeepsItsPlaceBesideTheNextOne() {
        TodayPlan plan = day(MONDAY.atTime(8, 20),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 5, 6));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals(2, cards.size());
        assertEquals("the class in progress stays", 1L, cards.get(0).courseId());
        assertEquals("and the next one joins it", 2L, cards.get(1).courseId());
    }

    @Test
    public void beforeTheFirstClassTheTwoSoonestAreShown() {
        TodayPlan plan = day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 5, 6));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals(2, cards.size());
        assertEquals(1L, cards.get(0).courseId());
        assertEquals(2L, cards.get(1).courseId());
        assertFalse("nothing has ended at seven in the morning",
                cards.get(1).ended(plan.now()));
    }

    @Test
    public void aLaterHourLeadsWithTheRemainingClassNotTheFirstOne() {
        // The whole point: at noon the morning is gone, so the next class leads. The
        // free second slot is taken by the class that just ended, and only by that.
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 6, 7));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals(2, cards.size());
        assertEquals("the next class comes first", 3L, cards.get(0).courseId());
        assertEquals("the one that just ended fills the other slot", 2L,
                cards.get(1).courseId());
        assertTrue(cards.get(1).ended(plan.now()));
    }

    @Test
    public void aDayWithOneClassLeftShowsItAlongsideTheFinishedOne() {
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 6, 7));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        // Periods 6-7 run to 12:55, so at 12:30 that class is the one in progress and
        // keeps the first slot; the morning class fills the second and says it is over.
        assertEquals(2, cards.size());
        assertEquals(2L, cards.get(0).courseId());
        assertTrue(cards.get(0).inProgress(plan.now()));
        assertEquals(1L, cards.get(1).courseId());
        assertTrue(cards.get(1).ended(plan.now()));
    }

    @Test
    public void twoUpcomingClassesLeaveNoRoomForTheFinishedOne() {
        TodayPlan plan = day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 5, 6));

        List<TodayPlan.Session> cards = CourseWidgetRenderer.cards(plan);

        assertEquals(2, cards.size());
        assertEquals(1L, cards.get(0).courseId());
        assertEquals(2L, cards.get(1).courseId());
        assertFalse("an upcoming class always wins the slot",
                cards.get(1).ended(plan.now()));
    }

    @Test
    public void anEveningWithNothingLeftShowsNoCardsAtAll() {
        TodayPlan plan = day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2), course(2L, 1, 3, 4));

        assertTrue(CourseWidgetRenderer.cards(plan).isEmpty());
        assertTrue(CourseWidgetRenderer.cards(null).isEmpty());
    }

    @Test
    public void neverMoreThanTwoCardsHoweverLongTheDayIs() {
        TodayPlan plan = day(MONDAY.atTime(7, 0),
                course(1L, 1, 1, 1), course(2L, 1, 2, 2), course(3L, 1, 3, 3),
                course(4L, 1, 4, 4), course(5L, 1, 5, 5), course(6L, 1, 6, 6));

        assertEquals(CourseWidgetRenderer.MAX_CARDS,
                CourseWidgetRenderer.cards(plan).size());
        assertEquals(2, CourseWidgetRenderer.MAX_CARDS);
    }

    // ============================================ the entry into the whole day

    @Test
    public void theEntryCountsTheWholeDayNotTheCardsOnScreen() {
        TodayPlan plan = day(MONDAY.atTime(12, 30),
                course(1L, 1, 1, 2), course(2L, 1, 3, 4), course(3L, 1, 6, 7));

        assertEquals(3, CourseWidgetRenderer.totalOf(plan));
        assertEquals("the cards never add up to the day, which is why the line says so",
                2, CourseWidgetRenderer.cards(plan).size());
        assertEquals("\u67E5\u770B\u4ECA\u65E5\u5168\u90E8 3 \u8282\u8BFE\u7A0B \u203A",
                CourseWidgetRenderer.entryText(CourseWidgetRenderer.totalOf(plan)));
    }

    @Test
    public void anEmptyDayStillHasAnEntryAndSaysTheRealNumberOfClasses() {
        TodayPlan evening = day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2), course(2L, 1, 3, 4));

        assertEquals("both classes are still part of today", 2,
                CourseWidgetRenderer.totalOf(evening));
        assertEquals("\u67E5\u770B\u4ECA\u65E5\u5168\u90E8 2 \u8282\u8BFE\u7A0B \u203A",
                CourseWidgetRenderer.entryText(CourseWidgetRenderer.totalOf(evening)));
    }

    @Test
    public void aDayWithoutAnyClassHasAnEntryThatDoesNotClaimACount() {
        TodayPlan empty = TodayPlan.of(Collections.<Course>emptyList(), semester(),
                schedules(), null, MONDAY.atTime(9, 0), 2);

        assertEquals(0, CourseWidgetRenderer.totalOf(empty));
        assertEquals("\u6253\u5F00\u4ECA\u65E5\u8BFE\u8868 \u203A",
                CourseWidgetRenderer.entryText(0));
        assertEquals("\u67E5\u770B\u4ECA\u65E5\u5168\u90E8 1 \u8282\u8BFE\u7A0B \u203A",
                CourseWidgetRenderer.entryText(1));
    }

    // ============================================ the item's text

    @Test
    public void aCardReadsWhereItHappensThenWhoTeachesIt() {
        TodayPlan.Session session = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2)).headline();

        assertEquals("A\u697C101 \u00B7 Prof. Li", CourseWidgetRenderer.metaOf(session));
    }

    @Test
    public void aCardWithNoVenueOrTeacherFallsBackToItsPeriods() {
        TodayPlan.Session bare = sessionWith("", "");
        TodayPlan.Session venueOnly = sessionWith("A\u697C101", "");
        TodayPlan.Session teacherOnly = sessionWith("", "Prof. Li");

        assertEquals("\u7B2C 1-2 \u8282", CourseWidgetRenderer.metaOf(bare));
        assertEquals("A\u697C101", CourseWidgetRenderer.metaOf(venueOnly));
        assertEquals("Prof. Li", CourseWidgetRenderer.metaOf(teacherOnly));
        assertEquals("", CourseWidgetRenderer.metaOf(null));
    }

    @Test
    public void theHeaderShowsTheDateAndLeavesTheWeekToTheCapsule() {
        TodayPlan plan = day(MONDAY.atTime(7, 0), course(1L, 1, 1, 2));

        assertEquals("8\u670831\u65E5 \u5468\u4E00", CourseWidgetRenderer.dateLine(plan));
        assertEquals("\u7B2C1\u5468 (\u5355\u5468)", NextClassWidgetRenderer.pillText(plan));
    }

    @Test
    public void outsideTheTermTheHeaderHasNoCapsule() {
        TodayPlan plan = day(TERM_START.minusWeeks(3).atTime(9, 0), course(1L, 1, 1, 2));

        assertTrue(CourseWidgetRenderer.dateLine(plan).contains("\u5468\u4E00"));
        assertEquals("", NextClassWidgetRenderer.pillText(plan));
        assertEquals("", NextClassWidgetRenderer.weekLabel(plan));
    }

    @Test
    public void anEmptyWidgetSaysWhyItIsEmpty() {
        TodayPlan none = TodayPlan.of(Collections.<Course>emptyList(), semester(),
                schedules(), null, MONDAY.atTime(9, 0), 2);
        TodayPlan outside = day(TERM_START.minusWeeks(2).atTime(9, 0), course(1L, 1, 1, 2));
        TodayPlan evening = day(MONDAY.atTime(23, 0), course(1L, 1, 1, 2));

        assertEquals("\u8FD8\u6CA1\u6709\u8BFE\u8868",
                NextClassWidgetRenderer.emptyTitle(none));
        assertEquals("\u70B9\u5F00\u6DFB\u52A0\u6216\u5BFC\u5165",
                NextClassWidgetRenderer.emptyHint(none));
        assertEquals("\u5B66\u671F\u8FD8\u6CA1\u5F00\u59CB",
                NextClassWidgetRenderer.emptyTitle(outside));
        assertEquals("all of today is over, and that is not the same as an empty day",
                "\u4ECA\u5929\u7684\u8BFE\u90FD\u4E0A\u5B8C\u4E86",
                NextClassWidgetRenderer.emptyTitle(evening));
        assertEquals("\u70B9\u5F00\u770B\u770B\u4ECA\u5929",
                NextClassWidgetRenderer.emptyHint(evening));
    }

    @Test
    public void twoCardsNeverShareAClickRequestCode() {
        int one = CourseWidgetRenderer.requestCode(1L);
        int two = CourseWidgetRenderer.requestCode(2L);
        // Ids that differ only above the 32nd bit are the case a plain cast loses.
        int wide = CourseWidgetRenderer.requestCode(0x1_0000_0001L);

        assertFalse(one == two);
        assertFalse("folding must not collapse ids that differ high up", wide == one);
    }

    // ---------------------------------------------------------------- fixtures

    /** A day read the way the widget reads it: two cards is all it asks for. */
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

    /** A session with exactly the venue and teacher asked for. */
    private static TodayPlan.Session sessionWith(String location, String teacher) {
        Course course = course(9L, 1, 1, 2);
        course.location = location;
        course.teacher = teacher;
        return TodayPlan.of(Arrays.asList(course), semester(), schedules(), null,
                MONDAY.atTime(7, 0), 2).headline();
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
