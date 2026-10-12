package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Course;

/**
 * Tests for the schedule queries the UI is built on.
 *
 * <p>The behaviour asserted here used to be buried inside
 * {@code MainActivity.renderCourses()}, where the only way to check it was to
 * launch the app and look at the screen.</p>
 */
public class CourseQueryTest {

    private static Course course(long id, int day, int fromPeriod, int toPeriod,
            int fromWeek, int toWeek, int parity) {
        Course course = new Course();
        course.id = id;
        course.name = "C" + id;
        course.dayOfWeek = day;
        course.startPeriod = fromPeriod;
        course.endPeriod = toPeriod;
        course.startWeek = fromWeek;
        course.endWeek = toWeek;
        course.parity = parity;
        return course;
    }

    /**
     * Deliberately handed out in a "wrong" order so the sorting is exercised:
     * Monday 3-4, Monday 1-2, then Tuesday 1-2 on odd weeks only.
     */
    private static List<Course> courses() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 3, 4, 1, 16, Course.PARITY_ALL));
        courses.add(course(2, 1, 1, 2, 1, 16, Course.PARITY_ALL));
        courses.add(course(3, 2, 1, 2, 1, 16, Course.PARITY_ODD));
        return courses;
    }

    @Test
    public void onDayReturnsOnlyThatWeekday() {
        List<Course> monday = CourseQuery.onDay(courses(), 1, 1);

        assertEquals(2L, monday.size());
        assertEquals(1L, monday.get(0).dayOfWeek);
        assertEquals(1L, monday.get(1).dayOfWeek);
    }

    @Test
    public void onDaySortsByStartThenEndPeriod() {
        List<Course> monday = CourseQuery.onDay(courses(), 1, 1);

        assertEquals(2L, monday.get(0).id);
        assertEquals(1L, monday.get(1).id);
    }

    @Test
    public void onDayRespectsOddEvenParity() {
        assertEquals(1L, CourseQuery.onDay(courses(), 1, 2).size());
        assertTrue(CourseQuery.onDay(courses(), 2, 2).isEmpty());
    }

    @Test
    public void onDayRespectsTheWeekWindow() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        assertEquals(1L, CourseQuery.onDay(courses, 8, 1).size());
        assertTrue(CourseQuery.onDay(courses, 9, 1).isEmpty());
    }

    @Test
    public void inWeekSortsByWeekdayThenPeriod() {
        List<Course> week = CourseQuery.inWeek(courses(), 1);

        assertEquals(3L, week.size());
        assertEquals(2L, week.get(0).id);
        assertEquals(1L, week.get(1).id);
        assertEquals(3L, week.get(2).id);
    }

    @Test
    public void inWeekDropsCoursesThatDoNotHappenThatWeek() {
        assertEquals(2L, CourseQuery.inWeek(courses(), 2).size());
    }

    @Test
    public void emptyInputYieldsEmptyOutput() {
        List<Course> none = Collections.emptyList();

        assertTrue(CourseQuery.onDay(none, 1, 1).isEmpty());
        assertTrue(CourseQuery.inWeek(none, 1).isEmpty());
    }

    @Test
    public void allReturnsEveryCourseInDayThenPeriodOrder() {
        List<Course> everything = CourseQuery.all(courses());

        assertEquals(3L, everything.size());
        assertEquals(2L, everything.get(0).id);
        assertEquals(1L, everything.get(1).id);
        assertEquals(3L, everything.get(2).id);
    }

    @Test
    public void allKeepsCoursesThatOnlyRunInPartOfTheTerm() {
        List<Course> courses = new ArrayList<>();
        courses.add(course(1, 1, 1, 2, 1, 4, Course.PARITY_ALL));

        assertEquals(1L, CourseQuery.all(courses).size());
    }

    @Test
    public void allDoesNotReorderTheCallersList() {
        List<Course> courses = courses();

        CourseQuery.all(courses);

        assertEquals(1L, courses.get(0).id);
        assertEquals(2L, courses.get(1).id);
        assertEquals(3L, courses.get(2).id);
    }

    @Test
    public void startingAtFindsOnlyCoursesBeginningAtThatPeriod() {
        List<Course> mondayFirst = CourseQuery.startingAt(courses(), 1, 1);

        assertEquals(1L, mondayFirst.size());
        assertEquals(2L, mondayFirst.get(0).id);
        assertTrue(CourseQuery.startingAt(courses(), 1, 2).isEmpty());
        assertEquals(1L, CourseQuery.startingAt(courses(), 2, 1).size());
    }

    @Test
    public void startingAtReturnsEveryCourseSharingTheSlot() {
        List<Course> courses = courses();
        courses.add(course(4, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        List<Course> sharing = CourseQuery.startingAt(courses, 1, 1);

        assertEquals(2L, sharing.size());
        assertEquals(2L, sharing.get(0).id);
        assertEquals(4L, sharing.get(1).id);
    }

    @Test
    public void maxParallelCountsTheBusiestSlot() {
        assertEquals(1L, CourseQuery.maxParallel(courses()));

        List<Course> crowded = courses();
        crowded.add(course(4, 1, 1, 2, 1, 8, Course.PARITY_ALL));

        assertEquals(2L, CourseQuery.maxParallel(crowded));
    }

    @Test
    public void maxParallelIsOneWhenThereAreNoCourses() {
        assertEquals(1L, CourseQuery.maxParallel(new ArrayList<Course>()));
    }

    @Test
    public void queryingDoesNotReorderTheCallersList() {
        List<Course> courses = courses();

        CourseQuery.onDay(courses, 1, 1);
        CourseQuery.inWeek(courses, 1);

        assertEquals(1L, courses.get(0).id);
        assertEquals(2L, courses.get(1).id);
        assertEquals(3L, courses.get(2).id);
    }

    // ---------------------------------------------------------------- search

    /**
     * What the search screen is built on: three text fields, a substring, and a
     * stable order.
     *
     * <p>Everything here is about one of those, and about the two things a search
     * must not do -- invent a match, or disturb the list it was handed.</p>
     */

    @Test
    public void searchFindsACourseByItsName() {
        List<Course> matches = CourseQuery.search(searchable(), "\u9AD8\u7B49\u6570\u5B66");

        assertEquals(1L, matches.size());
        assertEquals("\u9AD8\u7B49\u6570\u5B66", matches.get(0).name);
    }

    @Test
    public void searchFindsEveryCourseOfATeacher() {
        List<Course> matches = CourseQuery.search(searchable(), "\u5F20\u8001\u5E08");

        assertEquals(2L, matches.size());
        assertEquals("\u9AD8\u7B49\u6570\u5B66", matches.get(0).name);
        assertEquals("\u5927\u5B66\u7269\u7406", matches.get(1).name);
    }

    @Test
    public void searchFindsACourseByItsVenue() {
        List<Course> matches = CourseQuery.search(searchable(), "\u6559\u5B66\u697C E");

        assertEquals(1L, matches.size());
        assertEquals("\u5927\u5B66\u7269\u7406", matches.get(0).name);
    }

    @Test
    public void searchMatchesAContiguousRunOfAChineseName() {
        // Substring matching is what makes typing part of a name work without a
        // pinyin table: "数学" is inside "高等数学" and needs no word boundary.
        List<Course> matches = CourseQuery.search(searchable(), "\u6570\u5B66");

        assertEquals(1L, matches.size());
        assertEquals("\u9AD8\u7B49\u6570\u5B66", matches.get(0).name);
    }

    @Test
    public void searchDoesNotMatchASpokenAbbreviationThatIsNotASubstring() {
        // "高数" is how the course is said, but those two characters are not
        // adjacent in 高等数学. Matching it would need a fuzzy matcher or a pinyin
        // table, and this deliberately has neither -- so the boundary is asserted
        // rather than left to be discovered.
        assertTrue(CourseQuery.search(searchable(), "\u9AD8\u6570").isEmpty());
    }

    @Test
    public void searchIgnoresCaseInTheLatinFields() {
        assertEquals(2L, CourseQuery.search(searchable(), "a203").size());
        assertEquals(2L, CourseQuery.search(searchable(), "A203").size());
        assertEquals(1L, CourseQuery.search(searchable(), "li").size());
    }

    @Test
    public void searchIgnoresSurroundingSpaceInTheKeyword() {
        assertEquals(1L, CourseQuery.search(searchable(), "  \u9AD8\u7B49\u6570\u5B66  ").size());
        assertEquals(1L, CourseQuery.search(searchable(), "\t\u6570\u5B66 ").size());
    }

    @Test
    public void anEmptyKeywordMatchesNothingRatherThanEverything() {
        assertTrue(CourseQuery.search(searchable(), "").isEmpty());
        assertTrue(CourseQuery.search(searchable(), "   ").isEmpty());
        assertTrue(CourseQuery.search(searchable(), null).isEmpty());
    }

    @Test
    public void aKeywordNothingMatchesReturnsNothing() {
        assertTrue(CourseQuery.search(searchable(), "\u4E0D\u5B58\u5728\u7684\u8BFE")
                .isEmpty());
    }

    @Test
    public void coursesThatShareANameComeBackAsSeparateResults() {
        // Same name, different ids: the name is not an identity, so both must
        // appear and each must stay reachable by its own id.
        List<Course> matches = CourseQuery.search(searchable(), "\u5B9E\u9A8C");

        assertEquals(2L, matches.size());
        assertFalse(matches.get(0).id == matches.get(1).id);
        assertEquals(7L, matches.get(0).id);
        assertEquals(8L, matches.get(1).id);
    }

    @Test
    public void aSearchOnlySeesTheSemesterItWasGiven() {
        List<Course> thisTerm = new ArrayList<>();
        thisTerm.add(searchable(1L, "\u7EBF\u6027\u4EE3\u6570", "\u5218\u8001\u5E08",
                "A101", 1, 1));
        List<Course> otherTerm = new ArrayList<>();
        otherTerm.add(searchable(2L, "\u7EBF\u6027\u4EE3\u6570", "\u9648\u8001\u5E08",
                "B202", 1, 1));

        List<Course> found = CourseQuery.search(thisTerm, "\u7EBF\u6027\u4EE3\u6570");

        assertEquals(1L, found.size());
        assertEquals(1L, found.get(0).id);
        assertTrue("the other term's course must not leak in",
                CourseQuery.search(otherTerm, "\u5218\u8001\u5E08").isEmpty());
    }

    @Test
    public void searchingDoesNotModifyTheListOrTheCourses() {
        List<Course> courses = searchable();
        List<String> before = new ArrayList<>();
        for (Course course : courses) {
            before.add(describe(course));
        }
        int size = courses.size();

        CourseQuery.search(courses, "\u5F20\u8001\u5E08");
        CourseQuery.search(courses, "");

        assertEquals("the list keeps its length and its order",
                before, describeAll(courses));
        assertEquals(size, courses.size());
    }

    @Test
    public void searchOrdersResultsByDayThenPeriodThenId() {
        List<Course> courses = new ArrayList<>();
        // Handed over out of order on purpose.
        courses.add(searchable(5L, "\u8BFE\u4E94", "\u540C\u4E00\u4F4D\u8001\u5E08",
                "X", 2, 3));
        courses.add(searchable(3L, "\u8BFE\u4E09", "\u540C\u4E00\u4F4D\u8001\u5E08",
                "X", 1, 5));
        courses.add(searchable(9L, "\u8BFE\u4E5D", "\u540C\u4E00\u4F4D\u8001\u5E08",
                "X", 1, 1));
        courses.add(searchable(1L, "\u8BFE\u4E00", "\u540C\u4E00\u4F4D\u8001\u5E08",
                "X", 1, 1));

        List<Course> matches = CourseQuery.search(courses, "\u540C\u4E00\u4F4D\u8001\u5E08");

        assertEquals(4L, matches.size());
        // Monday period 1, and within that slot the lower id first.
        assertEquals(1L, matches.get(0).id);
        assertEquals(9L, matches.get(1).id);
        assertEquals(3L, matches.get(2).id);
        assertEquals(5L, matches.get(3).id);
    }

    @Test
    public void searchIsStableWhateverOrderTheCoursesArriveIn() {
        List<Course> courses = new ArrayList<>();
        courses.add(searchable(1L, "\u8BFE", "\u8001\u5E08", "X", 1, 1));
        courses.add(searchable(9L, "\u8BFE", "\u8001\u5E08", "X", 1, 1));

        List<Course> first = CourseQuery.search(courses, "\u8001\u5E08");
        Collections.reverse(courses);
        List<Course> second = CourseQuery.search(courses, "\u8001\u5E08");

        assertEquals(first, second);
    }

    @Test
    public void aBlankFieldIsSkippedRatherThanMatching() {
        List<Course> courses = new ArrayList<>();
        Course bare = searchable(1L, "\u4F53\u80B2", "", "", 1, 1);
        courses.add(bare);

        assertTrue("an empty teacher must not match a keyword", CourseQuery.search(
                courses, "\u5F20").isEmpty());
        assertTrue(CourseQuery.search(courses, " ").isEmpty());
        assertEquals("while its name still does", 1L,
                CourseQuery.search(courses, "\u4F53\u80B2").size());
    }

    @Test
    public void searchHandlesNothingToSearch() {
        assertTrue(CourseQuery.search(new ArrayList<Course>(), "\u4EFB\u4F55").isEmpty());
        assertTrue(CourseQuery.search(null, "\u4EFB\u4F55").isEmpty());
    }

    /** The searchable fields of a course, so "unchanged" means all of them. */
    private static String describe(Course course) {
        return course.id + "|" + course.name + "|" + course.teacher + "|"
                + course.location + "|" + course.dayOfWeek + "|" + course.startPeriod
                + "|" + course.endPeriod + "|" + course.startWeek + "|" + course.endWeek
                + "|" + course.parity;
    }

    private static List<String> describeAll(List<Course> courses) {
        List<String> descriptions = new ArrayList<>();
        for (Course course : courses) {
            descriptions.add(describe(course));
        }
        return descriptions;
    }

    /** A course with the three searchable fields filled in. */
    private static Course searchable(long id, String name, String teacher,
            String location, int day, int startPeriod) {
        Course course = course(id, day, startPeriod, startPeriod, 1, 16,
                Course.PARITY_ALL);
        course.name = name;
        course.teacher = teacher;
        course.location = location;
        return course;
    }

    /**
     * Five courses covering every field the search looks at.
     *
     * <p>Monday holds two classes by one teacher, Tuesday one in a venue whose
     * name carries a letter and a space, and Wednesday two courses that share a
     * name and differ only by id.</p>
     */
    private static List<Course> searchable() {
        List<Course> courses = new ArrayList<>();
        courses.add(searchable(1L, "\u9AD8\u7B49\u6570\u5B66", "\u5F20\u8001\u5E08",
                "\u6559\u5B66\u697C A203", 1, 1));
        courses.add(searchable(2L, "\u5927\u5B66\u7269\u7406", "\u5F20\u8001\u5E08",
                "\u6559\u5B66\u697C E302", 1, 3));
        courses.add(searchable(3L, "\u5927\u5B66\u82F1\u8BED", "Li", "A203", 2, 1));
        courses.add(searchable(7L, "\u7269\u7406\u5B9E\u9A8C", "\u738B\u8001\u5E08",
                "\u5B9E\u9A8C\u697C", 3, 1));
        courses.add(searchable(8L, "\u5316\u5B66\u5B9E\u9A8C", "\u674E\u8001\u5E08",
                "\u5B9E\u9A8C\u697C", 3, 3));
        return courses;
    }
}
