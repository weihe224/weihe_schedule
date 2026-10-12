package com.weihe.schedule.rule;

import java.util.List;

import com.weihe.schedule.model.Course;

/**
 * Which of the search screen's three states it is in, and what it says.
 *
 * <p>Small, but it carries the one distinction the screen can get wrong. An
 * empty keyword makes {@link CourseQuery#search} return nothing, and so does a
 * keyword that matches nothing -- the same empty list for two situations that
 * mean opposite things. Rendering "没有匹配的课程" before anything has been typed
 * tells the user their search failed when they have not searched yet. The keyword
 * is therefore checked first, so a blank one is idle however empty the results
 * are.</p>
 *
 * <p>It lives here rather than in the Activity because it is the only part of the
 * search screen a JVM test can reach, and because the timetable's own wording
 * rules belong with the rest of them.</p>
 */
public final class CourseSearchSummary {

    /** What the screen is showing. */
    public enum State {
        /** Nothing typed yet: guidance, not a result. */
        IDLE,
        /** Something typed, nothing found. */
        NO_MATCHES,
        /** Something typed, something found. */
        RESULTS
    }

    private final State state;
    private final int count;

    private CourseSearchSummary(State state, int count) {
        this.state = state;
        this.count = count;
    }

    /**
     * Reads the state from what was typed and what came back.
     *
     * @param keyword what the user has typed, possibly blank
     * @param matches what {@link CourseQuery#search} returned for it
     */
    public static CourseSearchSummary of(String keyword, List<Course> matches) {
        int count = matches == null ? 0 : matches.size();
        if (keyword == null || keyword.trim().isEmpty()) {
            // Checked before the count on purpose: see the class comment.
            return new CourseSearchSummary(State.IDLE, 0);
        }
        return new CourseSearchSummary(count == 0 ? State.NO_MATCHES : State.RESULTS,
                count);
    }

    public State state() {
        return state;
    }

    /** How many courses matched; zero unless the state is {@link State#RESULTS}. */
    public int count() {
        return count;
    }

    /** Whether there are result rows to draw. */
    public boolean showsResults() {
        return state == State.RESULTS;
    }

    /** Whether the screen should explain itself instead of listing anything. */
    public boolean showsGuidance() {
        return state != State.RESULTS;
    }

    /** The count line above the results, or the empty state's title. */
    public String headline() {
        switch (state) {
            case RESULTS:
                return "\u627E\u5230 " + count + " \u95E8\u8BFE\u7A0B";
            case NO_MATCHES:
                return "\u6CA1\u6709\u5339\u914D\u7684\u8BFE\u7A0B";
            default:
                return "\u8F93\u5165\u5173\u952E\u8BCD\u5F00\u59CB\u641C\u7D22";
        }
    }

    /** The line under the empty state's title; empty when there are results. */
    public String hint() {
        switch (state) {
            case NO_MATCHES:
                return "\u8BD5\u8BD5\u66F4\u77ED\u7684\u5173\u952E\u8BCD\uFF0C"
                        + "\u6216\u68C0\u67E5\u6709\u6CA1\u6709\u6253\u9519\u5B57\u3002";
            case IDLE:
                return "\u53EF\u4EE5\u641C\u7D22\u8BFE\u7A0B\u540D\u79F0\u3001"
                        + "\u6559\u5E08\u6216\u4E0A\u8BFE\u5730\u70B9\u3002";
            default:
                return "";
        }
    }

    @Override
    public String toString() {
        return state + "(" + count + ")";
    }
}
