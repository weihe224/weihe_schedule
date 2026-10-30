package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import java.time.LocalDate;
import org.junit.Test;
import com.weihe.schedule.model.Semester;

public class SemesterSummaryTest {
    private final Semester semester = new Semester(1, LocalDate.of(2026, 10, 26), "", 2);

    @Test
    public void futureTermIsNotCompletedOrInWeekOne() {
        LocalDate before = semester.start().minusDays(1);
        assertEquals("\u672A\u5F00\u5B66", SemesterSummary.status(semester, before));
        assertEquals("\u672A\u5F00\u5B66", SemesterSummary.todayLabel(semester, before));
    }

    @Test
    public void firstDayIsInSessionAndInWeekOne() {
        assertEquals("\u8FDB\u884C\u4E2D", SemesterSummary.status(semester, semester.start()));
        assertEquals("\u4ECA\u5929\u662F\u7B2C 1 \u5468",
                SemesterSummary.todayLabel(semester, semester.start()));
    }

    @Test
    public void lastDayIsStillInSessionAndUsesTheTermsLength() {
        assertEquals("\u8FDB\u884C\u4E2D", SemesterSummary.status(semester, semester.lastDay()));
        assertEquals("\u4ECA\u5929\u662F\u7B2C 2 \u5468",
                SemesterSummary.todayLabel(semester, semester.lastDay()));
    }

    @Test
    public void completedTermDoesNotClaimTodayIsATeachingWeek() {
        LocalDate after = semester.lastDay().plusDays(1);
        assertEquals("\u5DF2\u7ED3\u675F", SemesterSummary.status(semester, after));
        assertEquals("\u5DF2\u7ED3\u675F", SemesterSummary.todayLabel(semester, after));
    }
}
