package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;

/**
 * What the week section says about the selection.
 *
 * <p>Two settings together decide which weeks a course meets, and the interesting
 * cases are the ones where they disagree: a range that is all odd weeks on an even
 * parity meets never. The summary has to say that rather than describe the range
 * as though it were the answer.</p>
 */
public class WeekSelectionTest {

    // ============================================ counting the real weeks

    @Test
    public void everyWeekCountsTheWholeRange() {
        assertEquals(16, WeekSelection.teachingWeeks(1, 16, Course.PARITY_ALL));
        assertEquals(1, WeekSelection.teachingWeeks(5, 5, Course.PARITY_ALL));
        assertEquals(18, WeekSelection.teachingWeeks(1, 18, Course.PARITY_ALL));
    }

    @Test
    public void oddWeeksCountHalfOfAnEvenlyStartingRange() {
        // 1..16 on odd weeks is 1, 3, 5, 7, 9, 11, 13, 15.
        assertEquals(8, WeekSelection.teachingWeeks(1, 16, Course.PARITY_ODD));
        assertEquals(8, WeekSelection.teachingWeeks(1, 16, Course.PARITY_EVEN));
    }

    @Test
    public void aRangeThatIsAllOddWeeksMeetsNeverOnEvenWeeks() {
        // The case two separate controls never showed: this course never happens.
        assertEquals(0, WeekSelection.teachingWeeks(1, 1, Course.PARITY_EVEN));
        assertTrue(WeekSelection.meetsOnNoWeek(1, 1, Course.PARITY_EVEN));
        assertTrue(WeekSelection.meetsOnNoWeek(3, 3, Course.PARITY_EVEN));
        assertTrue(WeekSelection.meetsOnNoWeek(2, 2, Course.PARITY_ODD));
        assertTrue(WeekSelection.meetsOnNoWeek(4, 4, Course.PARITY_ODD));
    }

    @Test
    public void theSameRangeOnItsOwnParityDoesMeet() {
        assertFalse(WeekSelection.meetsOnNoWeek(1, 1, Course.PARITY_ODD));
        assertFalse(WeekSelection.meetsOnNoWeek(1, 1, Course.PARITY_ALL));
        assertFalse(WeekSelection.meetsOnNoWeek(2, 2, Course.PARITY_EVEN));
        assertEquals(1, WeekSelection.teachingWeeks(3, 3, Course.PARITY_ODD));
    }

    @Test
    public void anUpsideDownRangeMeetsNeverRatherThanCountingUp() {
        // The form rejects this before it can be saved; the count must not pretend
        // a range it was handed backwards is a range.
        assertEquals(0, WeekSelection.teachingWeeks(16, 1, Course.PARITY_ALL));
        assertEquals(0, WeekSelection.teachingWeeks(9, 8, Course.PARITY_ALL));
    }

    // ============================================ the rule is the timetable's

    @Test
    public void theCountAgreesWithTheRuleTheTimetableUses() {
        // The same answer as occursInWeek, over every range and parity the form can
        // produce: this must not become a second parity rule.
        int[] parities = {Course.PARITY_ALL, Course.PARITY_ODD, Course.PARITY_EVEN};
        for (int start = 1; start <= ScheduleUtils.SEMESTER_WEEKS; start++) {
            for (int end = start; end <= ScheduleUtils.SEMESTER_WEEKS; end++) {
                for (int parity : parities) {
                    Course course = new Course();
                    course.startWeek = start;
                    course.endWeek = end;
                    course.parity = parity;
                    int expected = 0;
                    for (int week = 1; week <= ScheduleUtils.SEMESTER_WEEKS; week++) {
                        if (ScheduleUtils.occursInWeek(course, week)) {
                            expected++;
                        }
                    }
                    assertEquals("weeks " + start + "-" + end + " parity " + parity,
                            expected, WeekSelection.teachingWeeks(start, end, parity));
                }
            }
        }
    }

    // ============================================ what it says

    @Test
    public void theSummaryNamesTheRangeTheParityAndTheRealCount() {
        assertEquals("\u7B2C1-16\u5468 \u00B7 \u53CC\u5468 \u00B7 \u4E0A\u8BFE 8 \u5468",
                WeekSelection.summary(1, 16, Course.PARITY_EVEN));
        assertEquals("\u7B2C1-16\u5468 \u00B7 \u6BCF\u5468 \u00B7 \u4E0A\u8BFE 16 \u5468",
                WeekSelection.summary(1, 16, Course.PARITY_ALL));
    }

    @Test
    public void aSingleWeekIsNamedAsOneWeekNotARange() {
        assertEquals("\u7B2C5\u5468 \u00B7 \u6BCF\u5468 \u00B7 \u4E0A\u8BFE 1 \u5468",
                WeekSelection.summary(5, 5, Course.PARITY_ALL));
    }

    @Test
    public void aContradictionSaysSoInsteadOfCountingZeroWeeks() {
        // "上课 0 周" reads like a small number; the user needs to be told plainly
        // that nothing will happen.
        String summary = WeekSelection.summary(1, 1, Course.PARITY_EVEN);
        assertEquals("\u7B2C1\u5468 \u00B7 \u53CC\u5468 \u00B7 \u4E0D\u4E0A\u8BFE", summary);
        assertFalse(summary.contains("0"));
    }

    @Test
    public void theSummaryNeverClaimsWeeksOutsideTheTerm() {
        // 1-18 on odd weeks: 9 of them, not the 18 the range suggests.
        assertEquals("\u7B2C1-18\u5468 \u00B7 \u5355\u5468 \u00B7 \u4E0A\u8BFE 9 \u5468",
                WeekSelection.summary(1, 18, Course.PARITY_ODD));
        // And a range running past the end counts only what the term holds.
        assertEquals(9, WeekSelection.teachingWeeks(1, 99, Course.PARITY_ODD));
    }
}
