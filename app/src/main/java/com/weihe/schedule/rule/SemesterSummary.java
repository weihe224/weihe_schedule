package com.weihe.schedule.rule;

import java.time.LocalDate;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/** Calendar labels must distinguish future terms from completed terms. */
public final class SemesterSummary {
    private SemesterSummary() {
    }

    public static String status(Semester semester, LocalDate today) {
        if (today.isBefore(semester.start())) {
            return "\u672A\u5F00\u5B66";
        }
        if (today.isAfter(semester.lastDay())) {
            return "\u5DF2\u7ED3\u675F";
        }
        return "\u8FDB\u884C\u4E2D";
    }

    public static String todayLabel(Semester semester, LocalDate today) {
        if (today.isBefore(semester.start()) || today.isAfter(semester.lastDay())) {
            return status(semester, today);
        }
        return "\u4ECA\u5929\u662F\u7B2C "
                + ScheduleUtils.currentWeek(semester.start(), today) + " \u5468";
    }
}
