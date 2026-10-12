package com.weihe.schedule.rule;

import java.util.List;

import com.weihe.schedule.model.Semester;

/**
 * The name a new semester should be offered with.
 *
 * <p>Adding a term is almost always adding <em>the next</em> term, and the app
 * already knows what that is called: a term's label is derived from its start date
 * and the season runs spring, autumn, spring. So the default is not invented here
 * -- it is the label the timetable would give the term that follows the latest one,
 * asked of the same two methods that name every other term
 * ({@link Semester#nextStart()} and {@link Semester#label()}).</p>
 *
 * <h2>Latest means latest, not last or selected</h2>
 *
 * <p>The latest term is found by comparing start dates. The list happens to be
 * sorted today, and the term the user is looking at is often not the newest one --
 * they may have switched back to a past term to read something -- so neither the
 * end of the list nor the active term is a safe answer. Only the dates are.</p>
 *
 * <h2>When it declines to guess</h2>
 *
 * <p>A term the user named by hand, such as {@code 大三上}, says nothing about what
 * comes after it. Rather than build a name out of a string that does not follow the
 * pattern, this returns nothing, and the dialog falls back to the behaviour it had
 * before: an empty field and a hint that leaving it blank derives a name from the
 * date.</p>
 */
public final class SemesterNaming {

    private SemesterNaming() {
    }

    /**
     * The name to pre-fill for the next semester, or empty when there is nothing
     * to derive one from.
     *
     * @param semesters every stored semester, in any order
     */
    public static String defaultNameFor(List<Semester> semesters) {
        Semester latest = latestOf(semesters);
        if (latest == null || latest.hasCustomName()) {
            return "";
        }
        // next(0) is a semester with no id and no name of its own, so its label is
        // the one the timetable derives from that start date.
        return latest.next(0L).label();
    }

    /**
     * The semester that starts last, or null when there are none.
     *
     * <p>Compared by date rather than taken from a position, so the answer does not
     * depend on how the caller happened to order its list.</p>
     */
    public static Semester latestOf(List<Semester> semesters) {
        if (semesters == null || semesters.isEmpty()) {
            return null;
        }
        Semester latest = null;
        for (Semester semester : semesters) {
            if (semester == null) {
                continue;
            }
            if (latest == null || semester.start().isAfter(latest.start())) {
                latest = semester;
            }
        }
        return latest;
    }
}
