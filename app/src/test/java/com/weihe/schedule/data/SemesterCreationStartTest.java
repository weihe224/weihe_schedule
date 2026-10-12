package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;

import java.time.LocalDate;

import org.junit.Test;
import com.weihe.schedule.model.Semester;

/**
 * The start date a semester is created with.
 *
 * <p>A term's week 1 is stored twice: in the semester record, and under the key the
 * week maths reads. Creating a term wrote only the record, so the first read of the
 * other one found nothing, fell back to a default worked out from today, and stored
 * that -- silently replacing the date the user had just typed. Both terms then
 * reported the same start, and since a term's label is derived from that date, two
 * terms could carry the same name.</p>
 */
public class SemesterCreationStartTest {

    private static final LocalDate NEW_TERM = LocalDate.parse("2027-02-22");

    @Test
    public void aNewSemesterKeepsTheStartDateItWasCreatedWith() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        LocalDate before = repository.getSemesterStart();

        Semester created = repository.addSemester(NEW_TERM, "");
        repository.setActiveSemester(created);

        assertEquals("the date the term was made with is the date it reports",
                NEW_TERM, repository.getSemesterStart());
        assertNotEquals("not a default worked out from today",
                before, repository.getSemesterStart());
    }

    @Test
    public void theSemesterRecordAndTheWeekMathsAgree() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester created = repository.addSemester(NEW_TERM, "");
        repository.setActiveSemester(created);

        // The record says 2027-02-22; so must the key the timetable counts weeks
        // from, or the term would claim one start and lay its weeks out from another.
        assertEquals(NEW_TERM, created.start());
        assertEquals(created.start(), repository.getSemesterStart());
    }

    @Test
    public void theEarlierSemesterKeepsItsOwnStart() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        Semester first = repository.getActiveSemester();
        LocalDate firstStart = repository.getSemesterStart();

        Semester created = repository.addSemester(NEW_TERM, "");
        repository.setActiveSemester(created);
        assertEquals(NEW_TERM, repository.getSemesterStart());

        repository.setActiveSemester(first);
        assertEquals("switching back reports the first term's own start",
                firstStart, repository.getSemesterStart());
        assertEquals(firstStart, first.start());
    }

    @Test
    public void everySemesterMadeInTurnKeepsItsOwnStart() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        LocalDate[] starts = {
            LocalDate.parse("2027-02-22"),
            LocalDate.parse("2027-08-30"),
            LocalDate.parse("2028-02-21")
        };
        for (LocalDate start : starts) {
            Semester created = repository.addSemester(start, "");
            repository.setActiveSemester(created);
            assertEquals("term made with " + start, start,
                    repository.getSemesterStart());
        }
        assertEquals(4, repository.getSemesters().size());
    }

    @Test
    public void aDateThatIsNotAMondayIsStoredAsItsMonday() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);

        // The dialog allows any day and the repository rounds to week 1's Monday;
        // what it stores has to be that Monday rather than the day as typed.
        Semester created = repository.addSemester(LocalDate.parse("2027-02-24"), "");
        repository.setActiveSemester(created);

        assertEquals(LocalDate.parse("2027-02-22"), repository.getSemesterStart());
        assertEquals(LocalDate.parse("2027-02-22"), created.start());
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }
}
