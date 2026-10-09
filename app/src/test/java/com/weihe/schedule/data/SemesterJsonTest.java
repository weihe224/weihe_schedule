package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Semester;

/**
 * Tests for the stored form of semesters.
 *
 * <p>Two things matter here. First, a semester survives a write and read with
 * its id, start and label intact. Second, a damaged payload is skipped entry by
 * entry instead of throwing: the semester list is what the app uses to find the
 * user's courses, so an unreadable entry must not be able to hide the rest.</p>
 */
public class SemesterJsonTest {

    @Test
    public void semestersSurviveARoundTrip() {
        List<Semester> stored = java.util.Arrays.asList(
                Semester.of(1L, LocalDate.of(2026, 2, 23)),
                Semester.of(2L, LocalDate.of(2026, 8, 31)).renamed("\u5927\u4E09\u4E0A"));

        List<Semester> restored = SemesterJson.parse(SemesterJson.write(stored));

        assertEquals(2, restored.size());
        assertEquals(1L, restored.get(0).id());
        assertEquals(LocalDate.of(2026, 2, 23), restored.get(0).start());
        assertEquals("2026 \u6625\u5B63\u5B66\u671F", restored.get(0).label());
        assertEquals("\u5927\u4E09\u4E0A", restored.get(1).label());
        assertTrue(restored.get(1).hasCustomName());
    }

    @Test
    public void unreadablePayloadsYieldNoSemesters() {
        assertTrue(SemesterJson.parse(null).isEmpty());
        assertTrue(SemesterJson.parse("").isEmpty());
        assertTrue(SemesterJson.parse("not json").isEmpty());
        assertTrue(SemesterJson.parse("{\"id\":1}").isEmpty());
    }

    @Test
    public void entriesWithoutAnIdOrADateAreSkipped() {
        String raw = "[{\"id\":0,\"start\":\"2026-08-31\"},"
                + "{\"id\":5,\"start\":\"whenever\"},"
                + "{\"id\":7,\"start\":\"2026-09-02\"}]";

        List<Semester> semesters = SemesterJson.parse(raw);

        assertEquals(1, semesters.size());
        assertEquals(7L, semesters.get(0).id());
        // A Wednesday in the file is still read back as the Monday of that week.
        assertEquals(LocalDate.of(2026, 8, 31), semesters.get(0).start());
    }

    @Test
    public void writingNothingProducesAnEmptyStore() {
        assertEquals("[]", SemesterJson.write(null));
        assertEquals("[]", SemesterJson.write(new java.util.ArrayList<Semester>()));
    }
}
