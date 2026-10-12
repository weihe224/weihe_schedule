package com.weihe.schedule.rule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import com.weihe.schedule.model.Semester;

/**
 * The name offered when a term is added.
 *
 * <p>The progression is the timetable's own -- a term's label comes from its start
 * date, and spring is followed by autumn in the same year, autumn by spring in the
 * next. These tests pin the three the request named, and the cases where the answer
 * has to be "no suggestion" rather than a name built out of something that does not
 * follow the pattern.</p>
 */
public class SemesterNamingTest {

    // ============================================ the three named progressions

    @Test
    public void anAutumnTermIsFollowedByTheNextSpring() {
        Semester autumn = Semester.of(1L, LocalDate.parse("2026-08-31"));

        assertEquals("2026 \u79CB\u5B63\u5B66\u671F", autumn.label());
        assertEquals("2027 \u6625\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Collections.singletonList(autumn)));
    }

    @Test
    public void aSpringTermIsFollowedByTheSameAutumn() {
        Semester spring = Semester.of(1L, LocalDate.parse("2027-02-22"));

        assertEquals("2027 \u6625\u5B63\u5B66\u671F", spring.label());
        assertEquals("2027 \u79CB\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Collections.singletonList(spring)));
    }

    @Test
    public void anAutumnTermIsFollowedByTheNextYearSpringAgain() {
        Semester autumn = Semester.of(1L, LocalDate.parse("2027-08-30"));

        assertEquals("2027 \u79CB\u5B63\u5B66\u671F", autumn.label());
        assertEquals("2028 \u6625\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Collections.singletonList(autumn)));
    }

    @Test
    public void theProgressionRepeatsForAsLongAsItIsAsked() {
        Semester term = Semester.of(1L, LocalDate.parse("2026-08-31"));
        String[] expected = {
            "2027 \u6625\u5B63\u5B66\u671F",
            "2027 \u79CB\u5B63\u5B66\u671F",
            "2028 \u6625\u5B63\u5B66\u671F",
            "2028 \u79CB\u5B63\u5B66\u671F"
        };
        for (String wanted : expected) {
            assertEquals(wanted,
                    SemesterNaming.defaultNameFor(Collections.singletonList(term)));
            term = term.next(term.id() + 1);
        }
    }

    // ============================================ latest, not last or selected

    @Test
    public void theLatestIsChosenByStartDateNotByListOrder() {
        Semester older = Semester.of(1L, LocalDate.parse("2026-08-31"));
        Semester newer = Semester.of(2L, LocalDate.parse("2027-02-22"));

        // The newer term first, the older one last: position would say otherwise.
        assertEquals("2027 \u79CB\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Arrays.asList(newer, older)));
        assertEquals("2027 \u79CB\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Arrays.asList(older, newer)));
    }

    @Test
    public void anUnorderedListOfManyTermsStillFindsTheLatest() {
        List<Semester> terms = new ArrayList<>(Arrays.asList(
                Semester.of(3L, LocalDate.parse("2027-02-22")),
                Semester.of(1L, LocalDate.parse("2025-09-01")),
                Semester.of(4L, LocalDate.parse("2028-02-21")),
                Semester.of(2L, LocalDate.parse("2026-08-31"))));

        // 2028-02-21 is the latest start and it is a spring term, so the one after
        // it is that year's autumn -- 2028, not 2029.
        assertEquals("2028 \u79CB\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(terms));
        assertEquals(4L, SemesterNaming.latestOf(terms).id());
    }

    @Test
    public void theActiveTermDoesNotDecideIt() {
        // The user may be reading an older term. Nothing in this rule is given the
        // active semester, and the newest one still wins wherever it sits.
        Semester past = Semester.of(1L, LocalDate.parse("2025-09-01"));
        Semester newest = Semester.of(9L, LocalDate.parse("2027-02-22"));

        List<Semester> terms = Arrays.asList(past, newest);
        assertEquals(newest.id(), SemesterNaming.latestOf(terms).id());
        assertEquals("2027 \u79CB\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(terms));
    }

    // ============================================ when it declines

    @Test
    public void aHandNamedLatestTermGetsNoInventedSuggestion() {
        Semester named = Semester.of(1L, LocalDate.parse("2026-08-31"))
                .renamed("\u5927\u4E09\u4E0A");

        assertTrue(named.hasCustomName());
        assertEquals("\u5927\u4E09\u4E0A", named.label());
        assertEquals("a name that follows no pattern suggests nothing", "",
                SemesterNaming.defaultNameFor(Collections.singletonList(named)));
    }

    @Test
    public void aHandNamedLatestTermIsStillTheLatestOneForTheDate() {
        // It declines the name, not the position: the start date is still what the
        // dialog uses to pre-fill the date field.
        Semester older = Semester.of(1L, LocalDate.parse("2026-08-31"));
        Semester named = Semester.of(2L, LocalDate.parse("2027-02-22"))
                .renamed("\u5927\u4E09\u4E0B");

        assertEquals("", SemesterNaming.defaultNameFor(Arrays.asList(older, named)));
        assertNotNull(SemesterNaming.latestOf(Arrays.asList(older, named)));
        assertEquals(2L, SemesterNaming.latestOf(Arrays.asList(older, named)).id());
    }

    @Test
    public void anOlderHandNamedTermDoesNotBlockTheNextName() {
        // Only the latest term is read. A custom name further back says nothing
        // about what follows the term that actually comes last.
        Semester named = Semester.of(1L, LocalDate.parse("2026-02-23"))
                .renamed("\u5927\u4E8C\u4E0B");
        Semester newest = Semester.of(2L, LocalDate.parse("2026-08-31"));

        assertEquals("2027 \u6625\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(Arrays.asList(named, newest)));
    }

    @Test
    public void noSemestersAtAllKeepsTheOldBehaviour() {
        assertEquals("", SemesterNaming.defaultNameFor(Collections.<Semester>emptyList()));
        assertEquals("", SemesterNaming.defaultNameFor(new ArrayList<Semester>()));
        assertEquals("", SemesterNaming.defaultNameFor(null));
        assertNull(SemesterNaming.latestOf(null));
        assertNull(SemesterNaming.latestOf(Collections.<Semester>emptyList()));
    }

    @Test
    public void aNullEntryDoesNotBreakTheSearch() {
        Semester term = Semester.of(2L, LocalDate.parse("2026-08-31"));
        List<Semester> withHole = Arrays.asList(null, term);

        assertEquals(term.id(), SemesterNaming.latestOf(withHole).id());
        assertEquals("2027 \u6625\u5B63\u5B66\u671F",
                SemesterNaming.defaultNameFor(withHole));
    }

    // ============================================ what it is not

    @Test
    public void itOnlyReadsAndNeverChangesWhatItWasGiven() {
        Semester older = Semester.of(1L, LocalDate.parse("2026-08-31"));
        Semester newer = Semester.of(2L, LocalDate.parse("2027-02-22"));
        List<Semester> terms = new ArrayList<>(Arrays.asList(older, newer));

        SemesterNaming.defaultNameFor(terms);
        SemesterNaming.defaultNameFor(terms);

        assertEquals(2, terms.size());
        assertEquals(1L, terms.get(0).id());
        assertEquals(2L, terms.get(1).id());
        // And the terms themselves are untouched, custom names included.
        assertEquals("", older.name());
        assertEquals("", newer.name());
    }
}
