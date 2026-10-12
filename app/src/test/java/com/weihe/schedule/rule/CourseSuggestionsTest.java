package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * The teachers and venues the course form offers as shortcuts.
 *
 * <p>Everything offered has to come from the timetable: a form that suggests a
 * name the user never wrote is worse than one that suggests nothing. Most of what
 * is checked here is therefore about what does <em>not</em> get through -- blanks,
 * another term's courses, invented values -- and about the order being the same
 * every time the same courses are read.</p>
 */
public class CourseSuggestionsTest {

    // ============================================ counting and ordering

    @Test
    public void teachersComeBackMostUsedFirst() {
        List<Course> courses = Arrays.asList(
                course("A", "Prof. Li", "A101"),
                course("B", "Prof. Wang", "A102"),
                course("C", "Prof. Li", "A103"),
                course("D", "Prof. Li", "A104"),
                course("E", "Prof. Wang", "A105"));

        assertEquals(Arrays.asList("Prof. Li", "Prof. Wang"),
                CourseSuggestions.teachers(courses));
    }

    @Test
    public void locationsComeBackMostUsedFirst() {
        List<Course> courses = Arrays.asList(
                course("A", "Prof. Li", "Lab 1"),
                course("B", "Prof. Wang", "Lab 2"),
                course("C", "Prof. Li", "Lab 1"));

        assertEquals(Arrays.asList("Lab 1", "Lab 2"),
                CourseSuggestions.locations(courses));
    }

    @Test
    public void theSameTextCountsOnce() {
        List<Course> courses = Arrays.asList(
                course("A", "Prof. Li", "A101"),
                course("B", "Prof. Li", "A101"),
                course("C", "Prof. Li", "A101"));

        assertEquals(Collections.singletonList("Prof. Li"),
                CourseSuggestions.teachers(courses));
        assertEquals(Collections.singletonList("A101"),
                CourseSuggestions.locations(courses));
    }

    @Test
    public void aTieIsBrokenTheSameWayEveryTime() {
        // Three teachers used once each, listed in an arbitrary order.
        List<Course> courses = Arrays.asList(
                course("A", "Prof. Chen", "A101"),
                course("B", "Prof. An", "A102"),
                course("C", "Prof. Bo", "A103"));

        List<String> once = CourseSuggestions.teachers(courses);
        List<String> again = CourseSuggestions.teachers(courses);
        assertEquals(once, again);
        assertEquals("a tie is settled by the text, not by the input order",
                Arrays.asList("Prof. An", "Prof. Bo", "Prof. Chen"), once);
    }

    @Test
    public void reorderingTheCoursesDoesNotReorderTheSuggestions() {
        Course first = course("A", "Prof. Chen", "A101");
        Course second = course("B", "Prof. An", "A102");
        Course third = course("C", "Prof. Bo", "A103");

        assertEquals(CourseSuggestions.teachers(Arrays.asList(first, second, third)),
                CourseSuggestions.teachers(Arrays.asList(third, first, second)));
    }

    @Test
    public void theMostUsedSurviveTheLimit() {
        List<Course> courses = new ArrayList<>();
        // "common" four times, then four others once each.
        for (int i = 0; i < 4; i++) {
            courses.add(course("C" + i, "Prof. Common", "Room " + i));
        }
        for (int i = 0; i < 4; i++) {
            courses.add(course("D" + i, "Prof. " + i, "Room " + i));
        }

        List<String> top = CourseSuggestions.teachers(courses, 3);

        assertEquals(3, top.size());
        assertEquals("the most used one is never the one dropped",
                "Prof. Common", top.get(0));
    }

    @Test
    public void theLimitIsRespected() {
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            courses.add(course("C" + i, "Teacher " + i, "Room " + i));
        }

        assertEquals(6, CourseSuggestions.teachers(courses).size());
        assertEquals(2, CourseSuggestions.teachers(courses, 2).size());
        assertEquals(10, CourseSuggestions.teachers(courses, 50).size());
        assertTrue("a limit of nothing offers nothing",
                CourseSuggestions.teachers(courses, 0).isEmpty());
        assertTrue(CourseSuggestions.teachers(courses, -3).isEmpty());
    }

    @Test
    public void theDefaultLimitIsBetweenFiveAndEight() {
        assertTrue("the form should offer a handful, not a list",
                CourseSuggestions.DEFAULT_LIMIT >= 5);
        assertTrue(CourseSuggestions.DEFAULT_LIMIT <= 8);
    }

    // ============================================ what must not be offered

    @Test
    public void blankValuesAreNotSuggestions() {
        // Every shape of "nothing was typed" on both fields at once, so the
        // surviving value proves each blank really was skipped.
        List<Course> courses = Arrays.asList(
                course("A", "", "A101"),
                course("B", "   ", "   "),
                course("C", "\t", "\t"),
                course("D", "Prof. Li", "A101"));

        assertEquals(Collections.singletonList("Prof. Li"),
                CourseSuggestions.teachers(courses));
        assertEquals(Collections.singletonList("A101"),
                CourseSuggestions.locations(courses));
    }

    @Test
    public void anEmptyTimetableSuggestsNothingRatherThanSomething() {
        assertTrue(CourseSuggestions.teachers(Collections.<Course>emptyList()).isEmpty());
        assertTrue(CourseSuggestions.locations(Collections.<Course>emptyList()).isEmpty());
    }

    @Test
    public void nothingToReadIsNotAnError() {
        assertTrue(CourseSuggestions.teachers(null).isEmpty());
        assertTrue(CourseSuggestions.locations(null).isEmpty());
        assertTrue(CourseSuggestions.teachers(Arrays.asList((Course) null)).isEmpty());
    }

    @Test
    public void onlyTheCoursesHandedInAreRead() {
        // The repository hands back the active term's courses, so what a caller
        // passes IS the scope. Two terms' lists must not mix.
        List<Course> firstTerm = Arrays.asList(course("A", "Prof. Li", "A101"));
        List<Course> secondTerm = Arrays.asList(course("B", "Prof. Wang", "D305"));

        assertEquals(Collections.singletonList("Prof. Li"),
                CourseSuggestions.teachers(firstTerm));
        assertEquals(Collections.singletonList("Prof. Wang"),
                CourseSuggestions.teachers(secondTerm));
        assertFalse("the other term's teacher must not leak in",
                CourseSuggestions.teachers(firstTerm).contains("Prof. Wang"));
    }

    @Test
    public void theStoredTextIsHandedBackUnchanged() {
        List<Course> courses = Arrays.asList(
                course("A", "Prof.  Li", "A101"),
                course("B", "Prof.  Li", "A101"));

        // Two spaces are the user's, not a formatting mistake to be tidied up.
        assertEquals(Collections.singletonList("Prof.  Li"),
                CourseSuggestions.teachers(courses));
        assertEquals(Collections.singletonList("A101"),
                CourseSuggestions.locations(courses));
    }

    // ============================================ harmless to the timetable

    @Test
    public void rankingDoesNotTouchTheCoursesOrTheList() {
        Course first = course("A", "Prof. Li", "A101");
        Course second = course("B", "Prof. Wang", "D305");
        List<Course> courses = new ArrayList<>(Arrays.asList(first, second));
        String before = describe(courses);

        CourseSuggestions.teachers(courses);
        CourseSuggestions.locations(courses);
        CourseSuggestions.teachers(courses, 1);

        assertEquals(before, describe(courses));
        assertEquals(2, courses.size());
        assertEquals("Prof. Li", first.teacher);
        assertEquals("A101", first.location);
    }

    @Test
    public void whatIsHandedBackCanBeWrittenIntoAFieldAndChangedAgain() {
        // The row offers strings, not references into the timetable: a caller that
        // uses one and then edits the result cannot reach back into a course.
        List<Course> courses = Arrays.asList(course("A", "Prof. Li", "A101"));
        List<String> teachers = CourseSuggestions.teachers(courses);

        String picked = teachers.get(0);
        assertEquals("Prof. Li", picked);
        assertEquals("A101", courses.get(0).location);
        assertEquals("A", courses.get(0).name);
    }

    // ---------------------------------------------------------------- fixtures

    private static Course course(String name, String teacher, String location) {
        Course course = new Course();
        course.id = name.hashCode();
        course.name = name;
        course.teacher = teacher;
        course.location = location;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }

    private static String describe(List<Course> courses) {
        StringBuilder text = new StringBuilder();
        for (Course course : courses) {
            text.append(course.name).append('|').append(course.teacher).append('|')
                    .append(course.location).append('|').append(course.dayOfWeek)
                    .append('|').append(course.startPeriod).append('|')
                    .append(course.endPeriod).append('|').append(course.startWeek)
                    .append('|').append(course.endWeek).append('|')
                    .append(course.parity).append('\n');
        }
        return text.toString();
    }
}
