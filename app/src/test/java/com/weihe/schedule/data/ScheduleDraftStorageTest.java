package com.weihe.schedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.rule.BellScheduleEditor;
import com.weihe.schedule.rule.ScheduleDraft;

/**
 * That restoring an unsaved form is a read, and only a read.
 *
 * <p>The draft itself is covered by {@code ScheduleDraftTest}. What is checked
 * here is the half that test cannot see: that carrying a draft back into an editor
 * built from a real repository leaves storage exactly as it was, and that the
 * editor is still usable afterwards. A restore that quietly saved would turn a
 * half-typed time into a stored timetable, which is the one outcome this whole
 * feature exists to avoid.</p>
 *
 * <p>Lives in {@code com.weihe.schedule.data} for the same reason the other
 * repository tests do: {@link CourseRepository#useStoreForNextInstance} and the
 * repository's own constructor are package-private test seams.</p>
 */
public class ScheduleDraftStorageTest {

    @Test
    public void restoringADraftWritesNothingToStorage() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellScheduleEditor editor = editorFor(repository, BellSchedules.STANDARD_NAME);
        editor.setStartText(1, "8.0");
        editor.setName("Edited but unsaved");

        Map<String, Object> before = preferences.snapshot();
        int commits = preferences.commitCount();

        // A rebuild: capture the draft, then put it into a freshly built editor.
        ScheduleDraft draft = ScheduleDraft.of(editor);
        BellScheduleEditor rebuilt = editorFor(repository, BellSchedules.STANDARD_NAME);
        draft.applyTo(rebuilt);

        assertEquals("restoring a draft is not saving it", before,
                preferences.snapshot());
        assertEquals(commits, preferences.commitCount());
    }

    @Test
    public void theStoredScheduleIsUnchangedByARestore() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellSchedule stored = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);
        assertNotNull(stored);

        BellScheduleEditor editor = new BellScheduleEditor(stored,
                new ArrayList<String>());
        editor.setStartText(1, "23:59");
        editor.setEndText(1, "23:59");
        editor.setName("Renamed but unsaved");

        ScheduleDraft draft = ScheduleDraft.of(editor);
        BellScheduleEditor rebuilt = new BellScheduleEditor(
                BellSchedules.byName(repository.getBellSchedules(),
                        BellSchedules.STANDARD_NAME),
                new ArrayList<String>());
        draft.applyTo(rebuilt);

        // Storage still describes the schedule as it was, not as the form has it.
        repository.reload();
        BellSchedule after = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);
        assertNotNull(after);
        assertEquals(stored.name(), after.name());
        assertEquals(stored.startMinute(1), after.startMinute(1));
        assertTrue("the renamed draft must not have reached storage",
                BellSchedules.byName(repository.getBellSchedules(),
                        "Renamed but unsaved") == null);
    }

    @Test
    public void theDraftRestoresTheTextThatStorageNeverHad() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellSchedule stored = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);

        BellScheduleEditor editor = new BellScheduleEditor(stored,
                new ArrayList<String>());
        // A time being typed: not a time, and therefore not something storage can
        // hold or should be asked to.
        editor.setStartText(2, "9:");

        BellScheduleEditor rebuilt = new BellScheduleEditor(
                BellSchedules.byName(repository.getBellSchedules(),
                        BellSchedules.STANDARD_NAME),
                new ArrayList<String>());
        ScheduleDraft.of(editor).applyTo(rebuilt);

        assertEquals("9:", rebuilt.startText(2));
        // And storage still holds the time it held before: the draft lived in the
        // editor and never reached the repository.
        repository.reload();
        BellSchedule stillStored = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);
        assertNotNull(stillStored);
        assertNotNull(stored);
        assertEquals(stored.startMinute(2), stillStored.startMinute(2));
    }

    @Test
    public void savingAfterARestoreStillWorks() {
        FakePreferences preferences = new FakePreferences();
        CourseRepository repository = repositoryFor(preferences);
        BellSchedule stored = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);

        BellScheduleEditor editor = new BellScheduleEditor(stored,
                new ArrayList<String>());
        editor.setStartText(3, "10:05");
        editor.setEndText(3, "10:50");

        BellScheduleEditor rebuilt = new BellScheduleEditor(
                BellSchedules.byName(repository.getBellSchedules(),
                        BellSchedules.STANDARD_NAME),
                new ArrayList<String>());
        ScheduleDraft.of(editor).applyTo(rebuilt);

        // The save path is untouched: a restored form still saves the usual way.
        assertTrue(rebuilt.problems().toString(), rebuilt.problems().isEmpty());
        repository.saveBellSchedule(rebuilt.originalName(), rebuilt.build());
        repository.reload();

        BellSchedule saved = BellSchedules.byName(repository.getBellSchedules(),
                BellSchedules.STANDARD_NAME);
        assertNotNull(saved);
        assertEquals(10 * 60 + 5, saved.startMinute(3));
        assertEquals(10 * 60 + 50, saved.endMinute(3));
    }

    // ------------------------------------------------------------------ fixtures

    private static CourseRepository repositoryFor(FakePreferences preferences) {
        CourseRepository.useStoreForNextInstance(preferences);
        return new CourseRepository((Context) null);
    }

    private static BellScheduleEditor editorFor(CourseRepository repository,
            String name) {
        List<BellSchedule> schedules = repository.getBellSchedules();
        return new BellScheduleEditor(BellSchedules.byName(schedules, name),
                new ArrayList<String>());
    }
}
