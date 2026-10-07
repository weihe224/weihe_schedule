package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.Test;

/**
 * Tests for the semester model.
 *
 * <p>A semester is a started week plus a label, and both have to behave: the
 * start is always normalised to a Monday because every screen computes weeks
 * from Monday, and the label is derived from the start date so a term created in
 * spring and later moved to autumn renames itself instead of lying.</p>
 */
public class SemesterTest {

    private static final LocalDate AUTUMN_2026 = LocalDate.of(2026, 8, 31);
    private static final LocalDate SPRING_2026 = LocalDate.of(2026, 2, 23);

    @Test
    public void theLabelIsDerivedFromTheStartDate() {
        assertEquals("2026 \u79CB\u5B63\u5B66\u671F",
                Semester.of(1L, AUTUMN_2026).label());
        assertEquals("2026 \u6625\u5B63\u5B66\u671F",
                Semester.of(2L, SPRING_2026).label());
        assertEquals("\u79CB\u5B63\u5B66\u671F",
                Semester.of(3L, AUTUMN_2026).seasonName());
    }

    @Test
    public void aTypedLabelWinsOverTheDerivedOne() {
        Semester renamed = Semester.of(1L, AUTUMN_2026)
                .renamed("  \u5927\u4E09\u4E0A ");

        assertTrue(renamed.hasCustomName());
        assertEquals("\u5927\u4E09\u4E0A", renamed.label());
        assertEquals(AUTUMN_2026, renamed.start());
        assertEquals(1L, renamed.id());
    }

    @Test
    public void clearingTheTypedLabelBringsTheDerivedOneBack() {
        Semester back = Semester.of(1L, AUTUMN_2026)
                .renamed("\u5927\u4E09\u4E0A")
                .renamed("");

        assertFalse(back.hasCustomName());
        assertEquals("2026 \u79CB\u5B63\u5B66\u671F", back.label());
    }

    @Test
    public void theStartIsAlwaysTheMondayOfThatWeek() {
        // A Wednesday is pulled back to its Monday.
        Semester semester = Semester.of(1L, LocalDate.of(2026, 9, 2));

        assertEquals(DayOfWeek.MONDAY, semester.start().getDayOfWeek());
        assertEquals(LocalDate.of(2026, 8, 31), semester.start());
    }

    @Test
    public void aNullStartFallsBackToTheCurrentWeekInsteadOfCrashing() {
        Semester semester = new Semester(1L, null, "");

        assertEquals(DayOfWeek.MONDAY, semester.start().getDayOfWeek());
        assertFalse(semester.label().trim().isEmpty());
    }

    @Test
    public void theLastDayIsTheEndOfTheFinalWeek() {
        Semester semester = Semester.of(1L, AUTUMN_2026);

        assertEquals(AUTUMN_2026.plusWeeks(ScheduleUtils.SEMESTER_WEEKS)
                .minusDays(1), semester.lastDay());
        assertEquals(DayOfWeek.SUNDAY, semester.lastDay().getDayOfWeek());
    }

    @Test
    public void autumnIsFollowedBySpringAndSpringByAutumn() {
        assertEquals(LocalDate.of(2027, 2, 22),
                Semester.of(1L, AUTUMN_2026).nextStart());
        assertEquals(LocalDate.of(2026, 8, 31),
                Semester.of(2L, SPRING_2026).nextStart());
    }

    @Test
    public void theNextStartIsAMondayInTheExpectedMonth() {
        LocalDate next = Semester.of(2L, SPRING_2026).nextStart();

        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
        assertEquals(8, next.getMonthValue());
        assertEquals(2026, next.getYear());

        LocalDate followingSpring = Semester.of(1L, AUTUMN_2026).nextStart();
        assertEquals(DayOfWeek.MONDAY, followingSpring.getDayOfWeek());
        assertEquals(2, followingSpring.getMonthValue());
        assertEquals(2027, followingSpring.getYear());
    }

    @Test
    public void aFollowingSemesterStartsLaterAndKeepsADerivedLabel() {
        Semester autumn = Semester.of(1L, AUTUMN_2026);

        Semester next = autumn.next(2L);

        assertTrue(next.start().isAfter(autumn.start()));
        assertEquals(2L, next.id());
        assertFalse(next.hasCustomName());
        assertEquals("2027 \u6625\u5B63\u5B66\u671F", next.label());
    }

    @Test
    public void movingTheStartKeepsTheTypedLabel() {
        Semester moved = Semester.of(1L, AUTUMN_2026)
                .renamed("\u5927\u4E09\u4E0A")
                .startingOn(LocalDate.of(2026, 9, 7));

        assertEquals("\u5927\u4E09\u4E0A", moved.label());
        assertEquals(LocalDate.of(2026, 9, 7), moved.start());
        assertNotEquals(AUTUMN_2026, moved.start());
    }
}
