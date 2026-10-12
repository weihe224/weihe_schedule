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
 * Who counts as "the same course" when one of them is repainted.
 *
 * <p>The rules under test are the conservative ones: the name after trimming, no
 * looser, and never the record being edited. What the matching must NOT do matters
 * as much as what it does -- a repaint that reaches a course the user never opened
 * is worse than one that reaches too few.</p>
 */
public class CourseColorSyncTest {

    // ============================================ what matches

    @Test
    public void arrangementsOfOneCourseAreMatchedWhateverElseDiffers() {
        // The differences are the reason the course was stored twice; requiring any
        // of them to agree would find nothing.
        Course monday = course(1L, "高等数学", 0xFF1F7F63);
        monday.teacher = "张老师";
        monday.location = "教学楼 A201";
        monday.dayOfWeek = 1;
        monday.startPeriod = 1;
        monday.endPeriod = 2;
        monday.startWeek = 1;
        monday.endWeek = 8;

        Course thursday = course(2L, "高等数学", 0xFF1F7F63);
        thursday.teacher = "李老师";
        thursday.location = "实验楼 B305";
        thursday.dayOfWeek = 4;
        thursday.startPeriod = 5;
        thursday.endPeriod = 6;
        thursday.startWeek = 9;
        thursday.endWeek = 16;
        thursday.parity = Course.PARITY_ODD;
        thursday.scheduleName = "B/C 楼作息";

        List<Course> matches = CourseColorSync.sameCourseAs(
                Arrays.asList(monday, thursday), monday);

        assertEquals(1, matches.size());
        assertEquals(2L, matches.get(0).id);
    }

    @Test
    public void theEditedRecordIsExcludedById() {
        // By id, not by name and not by position: the list may hold the same course
        // three times and only the one open in the editor is the current one.
        Course first = course(7L, "线性代数", 0xFF1F7F63);
        Course second = course(8L, "线性代数", 0xFF1F7F63);
        Course third = course(9L, "线性代数", 0xFF1F7F63);

        List<Course> matches = CourseColorSync.sameCourseAs(
                Arrays.asList(first, second, third), second);

        assertEquals(2, matches.size());
        for (Course match : matches) {
            assertFalse("the edited record must not be among the candidates",
                    match.id == second.id);
        }
        assertEquals(Long.valueOf(7L), Long.valueOf(matches.get(0).id));
        assertEquals(Long.valueOf(9L), Long.valueOf(matches.get(1).id));
    }

    @Test
    public void aNameThatOnlyLooksSimilarIsNotMatched() {
        // No fuzzy matching and no prefix reading: these are different courses.
        Course base = course(1L, "大学英语", 0xFF1F7F63);
        List<Course> others = Arrays.asList(
                course(2L, "大学英语（二）", 0xFF1F7F63),
                course(3L, "大学英语听说", 0xFF1F7F63),
                course(4L, "英语", 0xFF1F7F63),
                course(5L, "大学英", 0xFF1F7F63),
                course(6L, "大学英语2", 0xFF1F7F63));

        assertTrue("none of these is the same name",
                CourseColorSync.sameCourseAs(others, base).isEmpty());
    }

    @Test
    public void surroundingSpaceIsTheOnlyNormalisation() {
        Course base = course(1L, "高等数学", 0xFF1F7F63);
        List<Course> others = Arrays.asList(
                course(2L, "  高等数学  ", 0xFF1F7F63),
                course(3L, "\t高等数学\n", 0xFF1F7F63));

        assertEquals("trimmed names match", 2,
                CourseColorSync.sameCourseAs(others, base).size());
        assertEquals("高等数学", CourseColorSync.normalize("  高等数学  "));
        assertEquals("", CourseColorSync.normalize(null));
        assertEquals("", CourseColorSync.normalize("   "));
    }

    @Test
    public void innerSpaceIsNotCollapsedBecauseThatWouldWidenTheMatch() {
        // "高等 数学" is a different string; folding it would repaint a record the
        // user never opened.
        Course base = course(1L, "高等数学", 0xFF1F7F63);
        List<Course> others = Collections.singletonList(
                course(2L, "高等 数学", 0xFF1F7F63));

        assertTrue(CourseColorSync.sameCourseAs(others, base).isEmpty());
    }

    @Test
    public void aNamelessCourseMatchesNothing() {
        // A blank name would otherwise match every other blank one.
        Course blank = course(1L, "   ", 0xFF1F7F63);
        List<Course> others = Arrays.asList(
                course(2L, "", 0xFF1F7F63),
                course(3L, "   ", 0xFF1F7F63));

        assertTrue(CourseColorSync.sameCourseAs(others, blank).isEmpty());
    }

    @Test
    public void noArrangementsMeansNoQuestionToAsk() {
        Course only = course(1L, "体育", 0xFF1F7F63);

        assertTrue(CourseColorSync.sameCourseAs(
                Collections.singletonList(only), only).isEmpty());
        assertTrue(CourseColorSync.sameCourseAs(
                new ArrayList<Course>(), only).isEmpty());
        assertTrue(CourseColorSync.sameCourseAs(null, only).isEmpty());
        assertTrue(CourseColorSync.sameCourseAs(Collections.singletonList(only), null)
                .isEmpty());
    }

    // ============================================ which of them need the colour

    @Test
    public void onlyTheOnesNotAlreadyThatColourAreListed() {
        Course a = course(1L, "高等数学", 0xFF1F7F63);
        Course b = course(2L, "高等数学", 0xFFA95E17);
        Course c = course(3L, "高等数学", 0xFFA95E17);

        List<Long> ids = CourseColorSync.needingColor(
                Arrays.asList(a, b, c), 0xFFA95E17);

        assertEquals("only the one that differs", 1, ids.size());
        assertEquals(Long.valueOf(1L), ids.get(0));
        // The other way round: b and c agree with the target, so only a is listed.
        List<Long> toGreen = CourseColorSync.needingColor(
                Arrays.asList(a, b, c), 0xFF1F7F63);
        assertEquals(2, toGreen.size());
        assertEquals(Long.valueOf(2L), toGreen.get(0));
        assertEquals(Long.valueOf(3L), toGreen.get(1));
    }

    @Test
    public void changingToTheColourAlreadyHeldWouldChangeNothing() {
        List<Course> all = Arrays.asList(
                course(1L, "高等数学", 0xFF1F7F63),
                course(2L, "高等数学", 0xFF1F7F63));

        assertTrue(CourseColorSync.needingColor(all, 0xFF1F7F63).isEmpty());
        assertTrue(CourseColorSync.needingColor(null, 0xFF1F7F63).isEmpty());
    }

    // ============================================ the line shown in the dialog

    @Test
    public void eachArrangementIsDescribedByItsTimeNotItsName() {
        // Every line carries the same name, so the time is what tells them apart.
        Course monday = course(1L, "高等数学", 0xFF1F7F63);
        monday.dayOfWeek = 1;
        monday.startPeriod = 1;
        monday.endPeriod = 2;
        Course thursday = course(2L, "高等数学", 0xFF1F7F63);
        thursday.dayOfWeek = 4;
        thursday.startPeriod = 5;
        thursday.endPeriod = 6;

        String text = CourseColorSync.describe(Arrays.asList(monday, thursday));

        assertEquals(2, text.split("\n").length);
        assertTrue(text.contains("\u5468\u4E00"));
        assertTrue(text.contains("\u5468\u56DB"));
        assertTrue(text.startsWith("\u00B7"));
        assertEquals("", CourseColorSync.describe(null));
    }

    // ============================================ fixtures

    private static Course course(long id, String name, int color) {
        Course course = new Course();
        course.id = id;
        course.name = name;
        course.color = color;
        course.dayOfWeek = 1;
        course.startPeriod = 1;
        course.endPeriod = 2;
        course.startWeek = 1;
        course.endWeek = 16;
        course.parity = Course.PARITY_ALL;
        return course;
    }
}
