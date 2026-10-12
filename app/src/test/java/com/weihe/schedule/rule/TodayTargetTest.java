package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * The decision behind the "back to today" control.
 *
 * <p>Two questions: where should the screen go, and is it already there. The
 * second is the one that goes wrong quietly -- get it wrong in the hiding
 * direction and the way back disappears; get it wrong in the showing direction
 * and the screen offers a control that does nothing. Neither is visible in a
 * build, and neither was covered by a test while the logic lived inside the
 * activity.</p>
 *
 * <p>What a JVM test cannot show is where the control ends up on screen or what
 * happens when it is tapped. That is checked on a device.</p>
 */
public class TodayTargetTest {

    /** Monday of week 1. */
    private static final LocalDate TERM_START = LocalDate.parse("2026-08-31");

    // ============================================ where the screen should go

    @Test
    public void theTargetIsTheWeekTheDateFallsIn() {
        // Week 1 starts on the given Monday; the Sunday after it is still week 1.
        assertEquals(1, TodayTarget.week(TERM_START, LocalDate.parse("2026-08-31")));
        assertEquals(1, TodayTarget.week(TERM_START, LocalDate.parse("2026-09-06")));
        assertEquals(2, TodayTarget.week(TERM_START, LocalDate.parse("2026-09-07")));
        assertEquals(6, TodayTarget.week(TERM_START, LocalDate.parse("2026-10-11")));
    }

    @Test
    public void theTargetDayIsTheWeekdayAsTheScreenNumbersIt() {
        // Monday is 1 and Sunday is 7, which is what selectedDay holds.
        assertEquals(1, TodayTarget.day(LocalDate.parse("2026-10-05")));  // Monday
        assertEquals(3, TodayTarget.day(LocalDate.parse("2026-10-07")));  // Wednesday
        assertEquals(6, TodayTarget.day(LocalDate.parse("2026-10-10")));  // Saturday
        assertEquals(7, TodayTarget.day(LocalDate.parse("2026-10-11")));  // Sunday
    }

    @Test
    public void theWeekIsTheTimetablesOwnArithmeticNotASecondOne() {
        // The same answer as the method the rest of the app uses, for a spread of
        // dates: this must not become a parallel implementation of the term maths.
        for (String text : new String[] {"2026-08-31", "2026-09-13", "2026-10-11",
                "2026-12-25", "2027-01-03", "2027-05-01"}) {
            LocalDate date = LocalDate.parse(text);
            assertEquals("week for " + text,
                    ScheduleUtils.currentWeek(TERM_START, date),
                    TodayTarget.week(TERM_START, date));
        }
    }

    @Test
    public void anotherSemesterMovesTheTarget() {
        LocalDate today = LocalDate.parse("2026-10-11");
        // 2026-08-31 puts that date in week 6; a term that started eight weeks
        // earlier puts it in week 14; one that has not begun clamps to week 1.
        assertEquals(6, TodayTarget.week(TERM_START, today));
        assertEquals(14, TodayTarget.week(LocalDate.parse("2026-07-06"), today));
        assertEquals(1, TodayTarget.week(LocalDate.parse("2027-02-22"), today));
    }

    // ============================================ dates outside the term

    @Test
    public void aDateBeforeTheTermReadsAsWeekOne() {
        // The app's existing answer for a date outside the term, kept rather than
        // revisited: the week is clamped, not refused.
        assertEquals(1, TodayTarget.week(TERM_START, LocalDate.parse("2026-01-01")));
        assertEquals(1, TodayTarget.week(TERM_START, LocalDate.parse("2026-08-30")));
    }

    @Test
    public void aDateAfterTheTermReadsAsTheLastWeek() {
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                TodayTarget.week(TERM_START, LocalDate.parse("2027-06-01")));
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                TodayTarget.week(TERM_START, LocalDate.parse("2030-01-01")));
    }

    @Test
    public void theLastWeekOfTheTermIsNotClampedAway() {
        LocalDate lastWeekMonday = TERM_START.plusWeeks(ScheduleUtils.SEMESTER_WEEKS - 1);
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                TodayTarget.week(TERM_START, lastWeekMonday));
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                TodayTarget.week(TERM_START, lastWeekMonday.plusDays(6)));
        // The Monday after it is past the end, and still reads as the last week.
        assertEquals(ScheduleUtils.SEMESTER_WEEKS,
                TodayTarget.week(TERM_START, lastWeekMonday.plusWeeks(1)));
    }

    @Test
    public void theTargetWeekIsAlwaysOneTheScreenCanShow() {
        for (int offset = -400; offset <= 400; offset += 7) {
            int week = TodayTarget.week(TERM_START, TERM_START.plusDays(offset));
            assertTrue("week " + week + " must be selectable",
                    week >= 1 && week <= ScheduleUtils.SEMESTER_WEEKS);
        }
    }

    // ============================================ is the screen already there

    @Test
    public void theSameWeekAndDayIsRecognised() {
        LocalDate today = LocalDate.parse("2026-10-11");   // week 6, Sunday
        assertTrue(TodayTarget.isShowing(6, 7, TERM_START, today));
    }

    @Test
    public void anotherDayOfThisWeekIsNotToday() {
        LocalDate today = LocalDate.parse("2026-10-11");   // week 6, Sunday
        for (int day = 1; day <= 6; day++) {
            assertFalse("week 6 day " + day + " is not today",
                    TodayTarget.isShowing(6, day, TERM_START, today));
        }
    }

    @Test
    public void theSameWeekdayOfAnotherWeekIsStillNotToday() {
        // The case the rule exists for. Deciding on the weekday alone would hide
        // the way back from anyone who had stepped to another week and left the
        // day where it was -- which is what stepping weeks does.
        LocalDate today = LocalDate.parse("2026-10-11");   // week 6, Sunday
        for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
            if (week == 6) {
                continue;
            }
            assertFalse("week " + week + " Sunday is not today",
                    TodayTarget.isShowing(week, 7, TERM_START, today));
        }
    }

    @Test
    public void bothHalvesHaveToMatch() {
        LocalDate today = LocalDate.parse("2026-10-11");   // week 6, Sunday
        assertFalse(TodayTarget.isShowing(5, 7, TERM_START, today));  // right day, wrong week
        assertFalse(TodayTarget.isShowing(6, 1, TERM_START, today));  // right week, wrong day
        assertFalse(TodayTarget.isShowing(5, 1, TERM_START, today));  // neither
        assertTrue(TodayTarget.isShowing(6, 7, TERM_START, today));   // both
    }

    @Test
    public void outsideTheTermItMatchesTheClampedWeek() {
        // A date before the term reads as week 1, and the screen opens on week 1
        // with today's weekday, so that is the state the control treats as "here".
        LocalDate before = LocalDate.parse("2026-01-07");   // a Wednesday
        assertTrue(TodayTarget.isShowing(1, 3, TERM_START, before));
        assertFalse(TodayTarget.isShowing(1, 4, TERM_START, before));
    }

    @Test
    public void theAnswerFollowsTheSelectionAndKeepsNothing() {
        // Running the selections of a burst of arrow taps in order: each answer
        // depends only on the selection handed in, so no earlier step can leave
        // anything behind for a later one.
        LocalDate today = LocalDate.parse("2026-10-11");   // week 6, Sunday
        int[] weeks = {6, 7, 8, 9, 8, 7, 6, 5, 4, 6};

        for (int week : weeks) {
            int day = 7;
            boolean expected = week == 6;
            assertEquals("week " + week, expected,
                    TodayTarget.isShowing(week, day, TERM_START, today));
        }
        // And the last answer is still the same answer when asked again.
        assertTrue(TodayTarget.isShowing(6, 7, TERM_START, today));
    }

    @Test
    public void movingToAnotherSemesterChangesTheAnswer() {
        LocalDate today = LocalDate.parse("2026-10-11");
        // On the term that started in August this date is week 6; the screen is
        // parked on week 6, so it is showing today.
        assertTrue(TodayTarget.isShowing(6, 7, TERM_START, today));
        // Switch to a term that started eight weeks earlier and the same parked
        // selection is no longer today, so the control has to offer the way back.
        assertFalse(TodayTarget.isShowing(6, 7, LocalDate.parse("2026-07-06"), today));
        assertEquals(14, TodayTarget.week(LocalDate.parse("2026-07-06"), today));
    }

    @Test
    public void everyWeekdayMapsToItsOwnNumber() {
        // Days come from the date itself, so every weekday maps to itself and none
        // is skipped or repeated.
        LocalDate monday = LocalDate.parse("2026-10-05");
        for (int day = 1; day <= 7; day++) {
            LocalDate date = monday.plusDays(day - 1);
            assertEquals(day, TodayTarget.day(date));
            assertEquals(DayOfWeek.of(day), date.getDayOfWeek());
        }
    }

    // ============================================ it cannot touch anything

    @Test
    public void nothingAboutItCanReachTheTimetable() {
        // The control must not be able to save, delete or edit a course, and the
        // guarantee is structural: there is no state to carry and no course to
        // carry it in. Every method is a pure function of the arguments.
        assertEquals("a stateless helper holds nothing between calls",
                0, TodayTarget.class.getDeclaredFields().length);
        for (Method method : TodayTarget.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse("no course may be handed in: " + method.getName(),
                        parameter == Course.class);
                assertFalse("no course list may be handed in: " + method.getName(),
                        parameter == java.util.List.class);
            }
            assertFalse("nor handed back: " + method.getName(),
                    method.getReturnType() == Course.class);
        }
    }

    @Test
    public void callingItRepeatedlyChangesNothingItWasGiven() {
        LocalDate today = LocalDate.parse("2026-10-11");
        int selectedWeek = 6;
        int selectedDay = 7;

        for (int i = 0; i < 50; i++) {
            TodayTarget.week(TERM_START, today);
            TodayTarget.day(today);
            TodayTarget.isShowing(selectedWeek, selectedDay, TERM_START, today);
        }

        // Asking where today is cannot move the screen, and cannot change what the
        // arguments were; the answers are the same at the end as at the start.
        assertTrue(TodayTarget.isShowing(selectedWeek, selectedDay, TERM_START, today));
        assertEquals(6, TodayTarget.week(TERM_START, today));
        assertEquals(7, TodayTarget.day(today));
    }
}
