package com.weihe.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;
import com.weihe.schedule.design.Palette;

public class CourseRepository {
    private static final String PREFS = "weihe_schedule";
    /** Legacy keys, read once by the migration and then retired. */
    private static final String KEY_COURSES = "courses";
    private static final String KEY_START = "semester_start";
    private static final String KEY_COURSES_BACKUP = "courses_backup";
    private static final String KEY_BELL_SCHEDULES = "bell_schedules";
    private static final String KEY_SEMESTERS = "semesters";
    private static final String KEY_ACTIVE_SEMESTER = "active_semester";

    /** Per-semester keys are the legacy key plus the semester id. */
    private static final String SUFFIX_SEPARATOR = ":";

    /**
     * The pre-restore copy of everything a restore replaces.
     *
     * <p>Written in the same transaction as the restored data, so it cannot be
     * half-there, and kept until the next restore overwrites it. It is the only
     * thing standing between a bad restore and a lost timetable, so nothing
     * clears it on a hunch.</p>
     */
    private static final String KEY_RESTORE_SNAPSHOT = "restore_snapshot";

    /**
     * What a restore intended to write, present only between the commit and the
     * read-back check.
     *
     * <p>If the process dies in that window the marker survives, and the next
     * repository reads it and finishes the job: the data is checked, and put back
     * if it does not match. Without it an interrupted restore would be
     * indistinguishable from a completed one.</p>
     */
    private static final String KEY_RESTORE_PENDING = "restore_pending";

    /**
     * The courses a semester held before its most recent import.
     *
     * <p>Written in the same commit as the import itself, so the two can never
     * disagree about whether an import happened: a store that refused the write
     * holds neither, and an import therefore cannot become irreversible because
     * its snapshot did not make it. One entry per semester, replaced by each
     * import, so only the most recent one can be undone.</p>
     */
    private static final String KEY_COURSES_PREVIOUS = "courses_previous";

    private final SharedPreferences preferences;
    /**
     * Snapshot of the last read, dropped by every write.
     *
     * <p>The screens ask for the course list several times per redraw (the card
     * list, the conflict pass, the week grid), and each call used to re-read the
     * preference and re-parse the whole JSON payload. The list handed out is
     * never mutated by callers -- {@link CourseList} works on a fresh list -- so
     * caching one snapshot is safe and turns the repeated work into a field
     * read.</p>
     */
    private volatile List<Course> cachedCourses;
    private volatile List<BellSchedule> cachedSchedules;
    private volatile List<Semester> cachedSemesters;

    /**
     * Id of the one semester allowed to seed itself with the sample timetable.
     *
     * <p>Set once, when the install is created, and never moved: it is what
     * separates "first run, show a filled timetable" from "this term simply has
     * no courses yet". A flag that is cleared on some later event would break
     * whichever code path reads before that event -- which is exactly what
     * happened when it was cleared on creating a semester.</p>
     */
    private long seededSemesterId;

    /**
     * Id of the semester being read and written, or 0 when the storage is still
     * the legacy layout and no semester has been adopted.
     */
    private long semesterId;

    /** Set only when this instance had to settle an interrupted restore. */
    private RestoreReport recoveryReport;

    public CourseRepository(Context context) {
        this(context == null ? pendingPreferences.get() : preferencesOf(context));
        pendingPreferences.remove();
    }

    /** Visible for tests: the repository around an already-built store. */
    CourseRepository(SharedPreferences store) {
        preferences = store;
        migrateLegacyStorage();
        // Finishes a restore that was cut short between its commit and its check.
        // Returns null on every ordinary start, which is what makes it cheap.
        recoveryReport = resolvePendingRestore();
        adoptSemester(activeSemester());
    }

    /**
     * What the last interrupted restore did, when this instance had to settle one.
     *
     * <p>Null on an ordinary start. Only {@link RestoreReport.Status#NEEDS_ATTENTION}
     * is worth showing the user: the other outcomes leave the app in a state it
     * understands.</p>
     */
    public RestoreReport recoveryReport() {
        return recoveryReport;
    }

    /**
     * Hands the next repository the store to use instead of asking a
     * {@link Context} for one.
     *
     * <p>{@code Context} is a class whose every method is a stub on the JVM, so
     * unit tests cannot build one; without this the constructor -- and with it
     * the storage migration -- could not run under test at all. The value is
     * consumed by the very next constructor call on this thread and is never set
     * by the app.</p>
     */
    static void useStoreForNextInstance(SharedPreferences store) {
        pendingPreferences.set(store);
    }

    private static final ThreadLocal<SharedPreferences> pendingPreferences =
            new ThreadLocal<>();

    private static SharedPreferences preferencesOf(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------- semesters

    /**
     * Every semester, oldest first.
     *
     * <p>An install from before semesters existed was already migrated by the
     * constructor, so this is a plain read.</p>
     */
    public List<Semester> getSemesters() {
        List<Semester> snapshot = cachedSemesters;
        if (snapshot != null) {
            return new ArrayList<>(snapshot);
        }
        List<Semester> stored = SemesterJson.parse(
                preferences.getString(KEY_SEMESTERS, null));
        if (stored.isEmpty()) {
            // Only reachable if the stored list was wiped by hand; a single
            // semester is always better than none.
            stored = java.util.Collections.singletonList(
                    Semester.of(System.currentTimeMillis(),
                            defaultSemesterStart(LocalDate.now())));
            saveSemesters(stored);
        }
        sortByStart(stored);
        cachedSemesters = stored;
        return new ArrayList<>(stored);
    }

    /** The semester the screens are showing. */
    public Semester getActiveSemester() {
        List<Semester> semesters = getSemesters();
        long wanted = preferences.getLong(KEY_ACTIVE_SEMESTER, 0L);
        Semester found = findSemester(semesters, wanted);
        if (found != null) {
            return found;
        }
        Semester first = semesters.get(0);
        setActiveSemester(first);
        return first;
    }

    /**
     * Switches which semester every following read and write applies to.
     *
     * <p>The course cache is dropped because it belongs to the semester that was
     * active a moment ago.</p>
     */
    public void setActiveSemester(Semester semester) {
        if (semester == null) {
            return;
        }
        preferences.edit()
                .putLong(KEY_ACTIVE_SEMESTER, semester.id())
                .apply();
        adoptSemester(semester);
    }

    /**
     * Adds a semester, or returns the existing one when {@code start} already
     * falls in a stored semester's first week.
     */
    public Semester addSemester(LocalDate start) {
        return addSemester(start, "");
    }

    /**
     * Adds a semester with the details the user typed.
     *
     * <p>Nothing is invented here: a blank start date would mean a semester with
     * no week 1, so it is rejected rather than filled in with a guess, and a
     * blank name simply leaves the label to be derived from the date.</p>
     *
     * @param start Monday of week 1; any day of that week is accepted
     * @param name  user-chosen label, or empty to derive one from the date
     * @return the new semester, or the existing one that already starts that week
     * @throws IllegalArgumentException when {@code start} is null
     */
    public Semester addSemester(LocalDate start, String name) {
        if (start == null) {
            throw new IllegalArgumentException("a semester needs a start date");
        }
        LocalDate monday = ScheduleUtils.mondayOf(start);
        List<Semester> semesters = getSemesters();
        for (Semester semester : semesters) {
            if (semester.start().equals(monday)) {
                return semester;
            }
        }
        Semester created = Semester.of(nextSemesterId(semesters), monday)
                .renamed(name);
        semesters.add(created);
        saveSemesters(semesters);
        // The start date is stored twice on purpose -- in the record above, and
        // under the key the week maths counts from. Writing only the record left the
        // other key absent, and the first read of it fell back to a default worked
        // out from today and saved that, so a term created for next February
        // reported this term's August instead. Every other write path stores both
        // (see setSemesterStart, and the restore, which writes the key for every
        // term in the backup); this was the one that did not.
        preferences.edit()
                .putString(startKeyFor(created.id()), monday.toString())
                .apply();
        return created;
    }

    /**
     * Copies every course of {@code source} into {@code target}.
     *
     * <p>For the terms that mostly repeat the last one: the user can carry a
     * timetable forward and then edit the differences, instead of retyping it.
     * Ids are reassigned, so the two semesters share nothing afterwards and the
     * active semester never changes.</p>
     *
     * @return how many courses were copied
     */
    public int copyCourses(Semester source, Semester target) {
        if (source == null || target == null || source.id() == target.id()) {
            return 0;
        }
        List<Course> originals = coursesOf(source);
        List<Course> incoming = new ArrayList<>();
        long preferred = System.currentTimeMillis();
        for (Course original : originals) {
            Course copy = original.copy();
            copy.id = CourseList.nextId(incoming, preferred++);
            incoming.add(copy);
        }
        // One commit: the copied courses, and the removal of whatever the target
        // could still undo. A copy replaces the target's whole timetable, so a
        // snapshot taken before the target's own last import no longer describes
        // a state the user can go back to -- offering it would silently throw the
        // copy away. The source's own snapshot is deliberately left where it is:
        // it belongs to the term that took the import.
        preferences.edit()
                .putString(coursesKeyFor(target.id()), coursesJsonOf(incoming))
                .remove(previousCoursesKeyFor(target.id()))
                .apply();
        if (target.id() == semesterId) {
            // The active semester's snapshot is now wrong.
            cachedCourses = null;
        }
        return incoming.size();
    }

    /** Stores a changed semester (a new start date or a new label). */
    public void updateSemester(Semester semester) {
        if (semester == null) {
            return;
        }
        List<Semester> semesters = getSemesters();
        for (int i = 0; i < semesters.size(); i++) {
            if (semesters.get(i).id() == semester.id()) {
                semesters.set(i, semester);
                saveSemesters(semesters);
                if (semester.id() == semesterId) {
                    // Move this semester's own start key with it, so the two
                    // never disagree about week 1.
                    preferences.edit()
                            .putString(startKeyFor(semester.id()),
                                    semester.start().toString())
                            .apply();
                }
                return;
            }
        }
    }

    /**
     * Deletes a semester and everything filed under it.
     *
     * @return false when it is the only semester, which may not be removed
     */
    public boolean deleteSemester(Semester semester) {
        if (semester == null) {
            return false;
        }
        List<Semester> semesters = getSemesters();
        if (semesters.size() <= 1) {
            return false;
        }
        for (int i = 0; i < semesters.size(); i++) {
            if (semesters.get(i).id() == semester.id()) {
                semesters.remove(i);
                break;
            }
        }
        preferences.edit()
                .remove(coursesKeyFor(semester.id()))
                .remove(startKeyFor(semester.id()))
                // The snapshot described this term's own last import; with the
                // term gone there is nothing left for it to undo.
                .remove(previousCoursesKeyFor(semester.id()))
                .apply();
        saveSemesters(semesters);

        // Never leave the app pointing at the semester that just disappeared.
        if (semester.id() == semesterId) {
            setActiveSemester(semesters.get(0));
        }
        return true;
    }

    /**
     * Moves an install from before semesters existed onto the new layout.
     *
     * <p>Runs once, before anything is read. The stored courses and start date
     * are copied onto the first semester and the legacy keys are then removed:
     * leaving them behind would make a later fresh install read them instead of
     * writing its own sample courses. The original payload is not lost -- it
     * lives on under the semester's key, and the weekly copy to
     * {@link #KEY_COURSES_BACKUP} still applies to damaged data.</p>
     */
    private void migrateLegacyStorage() {
        if (preferences.contains(KEY_SEMESTERS)) {
            return;
        }
        String legacyStart = preferences.getString(KEY_START, null);
        LocalDate start = legacyStart == null ? null
                : ScheduleUtils.parseDate(legacyStart);
        if (start == null) {
            start = defaultSemesterStart(LocalDate.now());
        }
        Semester first = Semester.of(System.currentTimeMillis(), start);
        List<Semester> semesters = new ArrayList<>();
        semesters.add(first);

        String legacyCourses = preferences.getString(KEY_COURSES, null);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_SEMESTERS, SemesterJson.write(semesters))
                .putLong(KEY_ACTIVE_SEMESTER, first.id());
        // Only copy the legacy values when this really is a pre-semester
        // install. If the semester list was wiped by hand and a fresh semester
        // is being created instead, its own keys must not be overwritten with
        // someone else's data.
        if (legacyCourses != null) {
            editor.putString(coursesKeyFor(first.id()), legacyCourses)
                    .remove(KEY_COURSES);
        }
        if (legacyStart != null && ScheduleUtils.parseDate(legacyStart) != null) {
            editor.putString(startKeyFor(first.id()), start.toString())
                    .remove(KEY_START);
        } else {
            editor.putString(startKeyFor(first.id()), start.toString());
        }
        editor.apply();
        cachedSemesters = semesters;
        seededSemesterId = first.id();
    }

    private long nextSemesterId(List<Semester> semesters) {
        long candidate = System.currentTimeMillis();
        while (findSemester(semesters, candidate) != null) {
            candidate++;
        }
        return candidate;
    }

    private static Semester findSemester(List<Semester> semesters, long id) {
        for (Semester semester : semesters) {
            if (semester.id() == id) {
                return semester;
            }
        }
        return null;
    }

    private static void sortByStart(List<Semester> semesters) {
        semesters.sort(Comparator.comparing(Semester::start)
                .thenComparingLong(Semester::id));
    }

    private void saveSemesters(List<Semester> semesters) {
        sortByStart(semesters);
        preferences.edit()
                .putString(KEY_SEMESTERS, SemesterJson.write(semesters))
                .apply();
        cachedSemesters = new ArrayList<>(semesters);
    }

    private Semester activeSemester() {
        List<Semester> semesters = getSemesters();
        long wanted = preferences.getLong(KEY_ACTIVE_SEMESTER, 0L);
        Semester found = findSemester(semesters, wanted);
        return found != null ? found : semesters.get(0);
    }

    /** Points the read/write keys at one semester and drops its course cache. */
    private void adoptSemester(Semester semester) {
        semesterId = semester.id();
        cachedCourses = null;
    }

    /** The courses key of the active semester. */
    private String coursesKey() {
        return coursesKeyFor(semesterId);
    }

    /** The start-date key of the active semester. */
    private String startKey() {
        return startKeyFor(semesterId);
    }

    private static String coursesKeyFor(long id) {
        return KEY_COURSES + SUFFIX_SEPARATOR + id;
    }

    private static String startKeyFor(long id) {
        return KEY_START + SUFFIX_SEPARATOR + id;
    }

    /** The pre-import snapshot key of one semester. */
    private static String previousCoursesKeyFor(long id) {
        return KEY_COURSES_PREVIOUS + SUFFIX_SEPARATOR + id;
    }

    /**
     * Reads every stored course.
     *
     * <p>Parsing is deliberately forgiving. A single malformed entry used to make
     * the whole list unreadable and silently return an empty list, which looked
     * exactly like "no courses" -- and because the next save rewrote the store,
     * the real courses were gone for good. Now the readable entries are kept and
     * the original payload is copied to {@link #KEY_COURSES_BACKUP} before any
     * later save can overwrite it.</p>
     */
    public List<Course> getCourses() {
        List<Course> snapshot = cachedCourses;
        if (snapshot != null) {
            // A copy per call, because a caller that edits a returned course in
            // place (the editor does) must not reach into the cache. The cached
            // entries themselves are never handed out.
            return copyOf(snapshot);
        }
        // ...and the cache is only ever published once, with the sorted,
        // time-stamped list, so concurrent readers cannot see a half-built one.
        List<Course> loaded = loadCourses();
        cachedCourses = loaded;
        return copyOf(loaded);
    }

    private static List<Course> copyOf(List<Course> courses) {
        List<Course> copy = new ArrayList<>(courses.size());
        for (Course course : courses) {
            copy.add(course.copy());
        }
        return copy;
    }

    private List<Course> loadCourses() {
        String raw = preferences.getString(coursesKey(), null);
        if (raw == null) {
            // The sample timetable belongs to the semester the install was born
            // with. Any other term starts empty -- inheriting nine example
            // courses would be a bug, not a convenience.
            if (semesterId == seededSemesterId) {
                List<Course> samples = createSampleCourses();
                saveCourses(samples);
                for (Course course : samples) {
                    applyTimeRange(course);
                }
                return samples;
            }
            return new ArrayList<>();
        }
        return readCourses(raw);
    }

    /**
     * How many arrangements are filed under {@code semester}.
     *
     * <p>Reads that semester's own key without changing which semester is
     * active, so the list can show counts for terms the user is not looking
     * at.</p>
     */
    public int countCoursesOf(Semester semester) {
        return coursesOf(semester).size();
    }

    /** The courses of any semester, without changing the active one. */
    public List<Course> coursesOf(Semester semester) {
        if (semester == null) {
            return new ArrayList<>();
        }
        if (semester.id() == semesterId) {
            return getCourses();
        }
        String raw = preferences.getString(coursesKeyFor(semester.id()), null);
        return raw == null ? new ArrayList<Course>() : readCourses(raw);
    }

    /** Writes a course list straight to a semester's key. */
    private void writeCourses(String key, List<Course> courses) {
        preferences.edit().putString(key, coursesJsonOf(courses)).apply();
    }

    /** Parses one stored payload, applying the same repair rules everywhere. */
    private List<Course> readCourses(String raw) {
        List<Course> courses = new ArrayList<>();
        boolean damaged = false;
        List<Object> array = MiniJson.parseArray(raw);
        if (array.isEmpty() && raw.trim().length() > 2) {
            // Something was stored but it is not a JSON array: keep it aside
            // instead of letting the next save overwrite it for good.
            backupDamaged(raw);
            return new ArrayList<>();
        }
        for (int i = 0; i < array.size(); i++) {
            Map<String, Object> item = MiniJson.objectAt(array, i);
            if (item == null) {
                damaged = true;
                continue;
            }
            courses.add(fromJson(item));
        }
        if (damaged) {
            backupDamaged(raw);
        }
        courses.sort(Comparator.comparingInt(course -> course.startPeriod));
        for (Course course : courses) {
            applyTimeRange(course);
        }
        return courses;
    }

    /**
     * Stamps the displayed clock span onto a course.
     *
     * <p>Done once, when the snapshot is built, so that drawing a list of cards
     * does not resolve the same schedule once per card.</p>
     */
    private void applyTimeRange(Course course) {
        List<BellSchedule> schedules = getBellSchedules();
        course.timeRange = BellSchedules.resolve(schedules, course.scheduleName,
                course.location).timeRange(course.startPeriod, course.endPeriod);
    }

    /** Keeps an unreadable payload around so it can still be recovered by hand. */
    private void backupDamaged(String raw) {
        preferences.edit().putString(KEY_COURSES_BACKUP, raw).apply();
    }

    /**
     * The raw payload kept aside when a stored course list could not be read.
     *
     * <p>Exists so the copy can leave the device: until now it was written and
     * never read, which made the safety net unreachable without pulling the
     * preferences file off the device by hand.</p>
     *
     * <p>The copy is read as text, never as courses. Whether one is there is
     * answered by whether the slot holds something -- parsing it would answer
     * "nothing here" for every payload this slot ever holds, since a payload only
     * lands here because it could not be parsed. Reading changes nothing: the slot
     * is neither cleared nor rewritten, and no course key is touched.</p>
     */
    public DamagedCourseCopy damagedCopy() {
        try {
            return DamagedCourseCopy.of(
                    preferences.getString(KEY_COURSES_BACKUP, null));
        } catch (ClassCastException notText) {
            // Something other than text is filed under that key. Reporting "there
            // is none" would be untrue, and handing back a guess would be worse.
            return DamagedCourseCopy.unreadable();
        }
    }

    /**
     * Drops every in-memory snapshot so the next read comes from storage.
     *
     * <p>Each Activity builds its own repository, and each repository caches. The
     * cache is only invalidated by writes that go through <em>that same</em>
     * instance, so a screen that was in the background while another screen
     * imported a timetable keeps serving its own stale snapshot: the overview
     * shows the new courses and the home screen still shows the old ones. The two
     * screens disagree because they are literally holding different data.</p>
     *
     * <p>Call this from {@code onResume}, before reading. That is the cheap
     * moment to re-read -- the user has just come back and is waiting anyway --
     * and it makes "another screen changed something" the normal case rather than
     * a special one.</p>
     */
    public void reload() {
        cachedCourses = null;
        cachedSchedules = null;
        cachedSemesters = null;
        // The active semester can move on another screen too, and every key this
        // instance reads is built from its own idea of which one that is. Without
        // re-adopting it here, "reload" meant "re-read the term this screen was
        // opened on" rather than "re-read what storage says": coming back from the
        // semester screen after a switch left the home screen reading the term it
        // had been showing, so a term with nothing in it drew an empty list -- the
        // home said no courses while the overview, a newer instance, showed them.
        adoptSemester(activeSemester());
    }

    public Course findCourse(long id) {
        return CourseList.find(getCourses(), id);
    }

    public void upsertCourse(Course course) {
        List<Course> courses = getCourses();
        CourseList.ensureId(courses, course, System.currentTimeMillis());
        CourseList.upsert(courses, course);
        saveCourses(courses);
    }

    public void deleteCourse(long id) {
        List<Course> courses = getCourses();
        CourseList.remove(courses, id);
        saveCourses(courses);
    }

    /**
     * Replaces the stored timetable with imported courses, keeping what was
     * there before so the import can be undone.
     *
     * <p>Both payloads go into a single {@link SharedPreferences.Editor}: the
     * imported list, and the payload it replaces. One commit is what makes the
     * safety net trustworthy -- an editor applies all-or-nothing, so an import
     * can never land without the snapshot that undoes it, and a refused write
     * cannot leave an irreversible overwrite behind.</p>
     *
     * <p>The previous payload is kept as the <em>raw stored string</em>, not as a
     * re-serialised course list. Undoing then puts the store back byte for byte,
     * including anything the ordinary reader would have repaired or skipped.</p>
     *
     * @return {@code true} when the store accepted the write. {@code false} when
     *         it refused, and the caller must then not report the import as
     *         successful. A refusal is not proof that nothing changed -- the
     *         platform updates its in-memory map before the disk write that may
     *         fail -- so callers should re-read rather than assume either state
     */
    public boolean replaceCourses(List<Course> imported) {
        List<Course> courses = new ArrayList<>();
        long preferred = System.currentTimeMillis();
        for (Course source : imported) {
            Course course = source.copy();
            if (course.id == 0L) {
                course.id = CourseList.nextId(courses, preferred++);
            }
            // An imported row carries no schedule choice: the file only has
            // period numbers, so the choice is made later, from the venue.
            course.scheduleName = "";
            courses.add(course);
        }
        boolean written = preferences.edit()
                .putString(coursesKey(), coursesJsonOf(courses))
                .putString(previousCoursesKeyFor(semesterId),
                        previousEnvelope(preferences.getString(coursesKey(), null)))
                .commit();
        // The cache is dropped either way: a refused commit may still have
        // reached this process's in-memory map, so the snapshot it holds is no
        // longer something to hand out.
        cachedCourses = null;
        return written;
    }

    /**
     * Whether the active semester has an import that can still be undone.
     *
     * <p>True only while the snapshot its last import wrote is still there. The
     * settings screen reads this to decide whether to offer the entry at all,
     * so a term that never imported anything -- or whose import has already been
     * undone -- does not advertise one.</p>
     */
    public boolean hasPreviousCourses() {
        return readPrevious(semesterId) != null;
    }

    /**
     * Puts the active semester's courses back the way they were before its last
     * import, and forgets that import.
     *
     * <p>The payload and the removal of the snapshot are one commit, so the same
     * import cannot be undone twice: the second call finds nothing and changes
     * nothing. A semester with no snapshot is refused outright rather than read
     * as "restore an empty timetable", which would silently wipe a term whose
     * courses were typed by hand.</p>
     *
     * <p>The cache is dropped whether or not the write was accepted, so the
     * screens re-read instead of serving a snapshot that may no longer match the
     * store.</p>
     *
     * @return {@code true} when the store accepted the write; {@code false} when
     *         there was nothing to undo, or the store refused it. As with
     *         {@link #replaceCourses}, a refusal is not proof that nothing
     *         changed
     */
    public boolean restorePreviousCourses() {
        PreviousCourses previous = readPrevious(semesterId);
        if (previous == null) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .remove(previousCoursesKeyFor(semesterId));
        if (previous.present) {
            editor.putString(coursesKey(), previous.raw);
        } else {
            // The term had no payload at all before the import, and putting an
            // empty array back would be a different state: "no courses yet" and
            // "a timetable that was deliberately emptied" do not read alike.
            editor.remove(coursesKey());
        }
        boolean written = editor.commit();
        // reload() rather than dropping only the course cache: this is the same
        // "the store changed under you" situation a restore is in, and the next
        // screen to read should come back to storage for all of it.
        reload();
        return written;
    }

    /**
     * One semester's pre-import state: the payload it held, or the fact that it
     * held none.
     *
     * <p>Absence needs recording because it has no representation of its own --
     * {@code putString} with a null value removes the key -- and undoing has to
     * put back the state that was there, not a lookalike.</p>
     */
    private static final class PreviousCourses {
        private final boolean present;
        private final String raw;

        PreviousCourses(boolean present, String raw) {
            this.present = present;
            this.raw = raw;
        }
    }

    /** The stored form of a pre-import payload. */
    private static String previousEnvelope(String payload) {
        Map<String, Object> envelope = MiniJson.object();
        envelope.put("present", Boolean.valueOf(payload != null));
        if (payload != null) {
            envelope.put("raw", payload);
        }
        return MiniJson.write(envelope);
    }

    /**
     * The pre-import state recorded for a semester.
     *
     * @return {@code null} when no snapshot is stored, or the stored one cannot
     *         be read -- an unreadable snapshot is treated as none rather than
     *         acted on
     */
    private PreviousCourses readPrevious(long id) {
        Object parsed = MiniJson.parse(
                preferences.getString(previousCoursesKeyFor(id), null));
        if (!(parsed instanceof Map)) {
            return null;
        }
        Map<?, ?> envelope = (Map<?, ?>) parsed;
        if (!Boolean.TRUE.equals(envelope.get("present"))) {
            return new PreviousCourses(false, null);
        }
        Object raw = envelope.get("raw");
        return raw instanceof String ? new PreviousCourses(true, (String) raw) : null;
    }

    /**
     * The bell schedules the user works with.
     *
     * <p>A fresh install starts from {@link BellSchedules#presets()}, which are
     * written out on first read so that the timetable editor has something real
     * to edit rather than an empty tuple. A store still holding an earlier
     * build's placeholder times is moved onto the real ones once, see
     * {@link #replacePlaceholderSchedules}.</p>
     */
    public List<BellSchedule> getBellSchedules() {
        List<BellSchedule> snapshot = cachedSchedules;
        if (snapshot != null) {
            return snapshot;
        }
        String raw = preferences.getString(KEY_BELL_SCHEDULES, null);
        List<BellSchedule> schedules = BellScheduleJson.parse(raw);
        if (schedules.isEmpty()) {
            schedules = BellSchedules.copyOf(BellSchedules.presets());
            saveBellSchedules(schedules);
            return schedules;
        }
        if (replacePlaceholderSchedules(schedules)) {
            saveBellSchedules(schedules);
            return schedules;
        }
        cachedSchedules = schedules;
        return schedules;
    }

    /**
     * Swaps an older build's invented times for the school's real ones.
     *
     * <p>Two earlier builds shipped schedules that were not the school's: one with
     * periods starting at 08:00 and two buildings nobody had described, and one
     * whose afternoon hours were a guess because only the morning was known. A
     * store whose schedules match those templates exactly has never been edited,
     * so replacing them is a correction rather than a loss -- while any other
     * content is left alone, because it is the user's own timetable.</p>
     *
     * @return true when the list was changed and should be saved
     */
    private boolean replacePlaceholderSchedules(List<BellSchedule> schedules) {
        boolean placeholder = false;
        for (BellSchedule schedule : schedules) {
            if (isPlaceholder(schedule)) {
                placeholder = true;
                break;
            }
        }
        if (!placeholder) {
            return false;
        }
        schedules.clear();
        schedules.addAll(BellSchedules.presets());
        return true;
    }

    /**
     * True for one of the exact schedules an earlier build invented.
     *
     * <p>Matched by the <em>pair</em> of name and times, not by times alone. Times
     * alone is not enough: the school kept the same afternoons across every
     * revision, so a user who edited only the morning would still match the old
     * afternoon signature -- and migrating them would throw the edit away. The
     * names below are the ones this app shipped and has since retired, so a match
     * on both means nobody typed either of them.</p>
     */
    private static boolean isPlaceholder(BellSchedule schedule) {
        String name = schedule.name();
        if (name.startsWith("\u5B9E\u9A8C\u697C\u4F5C\u606F")
                || name.startsWith("\u4E1C\u6821\u533A\u4F5C\u606F")) {
            return true;
        }
        boolean shippedName = name.equals(OLD_STANDARD_NAME)
                || name.equals(OLD_CDE_NAME);
        return shippedName
                && (sameTimes(schedule, PLACEHOLDER_0800_STARTS,
                            PLACEHOLDER_0800_ENDS)
                    || sameTimes(schedule, PLACEHOLDER_GUESSED_AFTERNOON_STARTS,
                            PLACEHOLDER_GUESSED_AFTERNOON_ENDS)
                    || sameTimes(schedule, OLD_CDE_STARTS, OLD_CDE_ENDS));
    }

    /** The name early builds gave the default schedule. */
    private static final String OLD_STANDARD_NAME = "\u6807\u51C6\u4F5C\u606F";
    /** The name early builds gave the D/E schedule. */
    private static final String OLD_CDE_NAME = "C/D/E \u697C\u4F5C\u606F";

    /** The earliest placeholder timetable: periods starting at 08:00. */
    private static final int[] PLACEHOLDER_0800_STARTS = {
            480, 535, 600, 655, 840, 895, 960, 1015, 1140, 1195,
            1250, 1300, 1350, 1400, 1450};
    private static final int[] PLACEHOLDER_0800_ENDS = {
            525, 580, 645, 700, 885, 940, 1005, 1060, 1185, 1240,
            1295, 1345, 1395, 1445, 1495};

    /**
     * The default that shipped with a made-up afternoon.
     *
     * <p>Its periods 1-5 are the A/F times, but 4 and 5 were wrong (10:35 and 11:20
     * instead of the 10:40 block), and everything from period 6 was invented to
     * keep the editor usable.</p>
     */
    private static final int[] PLACEHOLDER_GUESSED_AFTERNOON_STARTS = {
            495, 535, 595, 635, 680, 800, 840, 900, 940, 995,
            1035, 1090, 1130, 1175, 1220};
    private static final int[] PLACEHOLDER_GUESSED_AFTERNOON_ENDS = {
            535, 575, 635, 675, 720, 840, 880, 940, 980, 1035,
            1075, 1130, 1170, 1215, 1260};

    /** The old "C/D/E 楼作息", whose third and fourth periods were also wrong. */
    private static final int[] OLD_CDE_STARTS = {
            495, 535, 595, 615, 680, 800, 840, 900, 940, 995,
            1035, 1090, 1130, 1175, 1220};
    private static final int[] OLD_CDE_ENDS = {
            535, 575, 635, 655, 720, 840, 880, 940, 980, 1035,
            1075, 1130, 1170, 1215, 1260};

    private static boolean sameTimes(BellSchedule schedule, int[] starts, int[] ends) {
        if (schedule.periodCount() != starts.length) {
            return false;
        }
        for (int period = 1; period <= schedule.periodCount(); period++) {
            if (schedule.startMinute(period) != starts[period - 1]
                    || schedule.endMinute(period) != ends[period - 1]) {
                return false;
            }
        }
        return true;
    }

    /** Replaces every stored bell schedule. */
    public void saveBellSchedules(List<BellSchedule> schedules) {
        List<BellSchedule> stored = BellSchedules.copyOf(schedules);
        preferences.edit()
                .putString(KEY_BELL_SCHEDULES, BellScheduleJson.write(stored))
                .apply();
        cachedSchedules = stored;
    }

    /**
     * Stores one bell schedule, replacing the one that carried
     * {@code originalName}, and keeps the courses that named it in step.
     *
     * <p>Addressed by <em>name</em>, not by position. The editor used to write
     * back with an index captured when it opened, against a repository that never
     * reloads; if any other screen added or removed a table in between, that index
     * named a different one and the save silently overwrote it.</p>
     *
     * <p>When the name changed, every course that pinned the old name is repointed
     * at the new one. Without that they keep a name that no longer exists, fall
     * back to venue resolution, and can end up on a different clock because
     * somebody renamed a timetable -- see {@link #renameSchedule}.</p>
     *
     * @param originalName the name the editor opened with, or {@code null}/empty
     *                     when this is a new schedule
     * @return the schedule that was stored
     */
    public BellSchedule saveBellSchedule(String originalName, BellSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        List<BellSchedule> schedules = BellSchedules.copyOf(getBellSchedules());
        int at = indexOfSchedule(schedules, originalName);
        if (at >= 0) {
            schedules.set(at, schedule);
        } else {
            schedules.add(schedule);
        }
        saveBellSchedules(schedules);
        if (originalName != null && !originalName.trim().isEmpty()
                && !originalName.trim().equals(schedule.name())) {
            renameSchedule(originalName, schedule.name());
        }
        return schedule;
    }

    /**
     * Deletes one bell schedule and clears the courses that named it.
     *
     * <p>Refuses to remove the last one. {@link #getBellSchedules} reads an empty
     * stored list as "this install has never stored any" and writes
     * {@link BellSchedules#presets()} back, so deleting the final schedule would
     * not delete anything -- it would look like the delete silently undid itself
     * on the next screen that reads.</p>
     *
     * @param name the schedule to remove
     * @return false when no schedule carries that name, or it is the only one
     */
    public boolean deleteBellSchedule(String name) {
        List<BellSchedule> schedules = BellSchedules.copyOf(getBellSchedules());
        if (schedules.size() <= 1) {
            return false;
        }
        int at = indexOfSchedule(schedules, name);
        if (at < 0) {
            return false;
        }
        schedules.remove(at);
        saveBellSchedules(schedules);
        clearScheduleReferences(name);
        return true;
    }

    /** Position of the schedule with this name, or -1 when there is none. */
    private static int indexOfSchedule(List<BellSchedule> schedules, String name) {
        if (schedules == null || name == null) {
            return -1;
        }
        String wanted = name.trim();
        if (wanted.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).name().equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The displayed time span of a course, according to the schedule that
     * applies to it.
     *
     * <p>Resolution order is {@link BellSchedules#resolve}: the course's own
     * choice, then its venue, then the standard schedule.</p>
     */
    public String timeRangeOf(Course course) {
        if (course == null) {
            return "";
        }
        BellSchedule schedule = BellSchedules.resolve(getBellSchedules(),
                course.scheduleName, course.location);
        return schedule.timeRange(course.startPeriod, course.endPeriod);
    }

    /** The schedule that applies to a course, never null. */
    public BellSchedule scheduleFor(Course course) {
        if (course == null) {
            return BellSchedules.standard();
        }
        return BellSchedules.resolve(getBellSchedules(), course.scheduleName,
                course.location);
    }

    /**
     * Points every course that chose {@code from} at {@code to} instead.
     *
     * <p>Courses hold their schedule by name, so without this a rename would
     * silently turn every course that used it back to "decide by venue".</p>
     *
     * @return how many courses were repointed
     */
    public int renameSchedule(String from, String to) {
        if (from == null || to == null || from.trim().isEmpty()
                || from.trim().equals(to.trim())) {
            return 0;
        }
        List<Course> courses = getCourses();
        List<Long> affected = new ArrayList<>();
        for (Course course : courses) {
            if (from.trim().equals(course.scheduleName)) {
                course.scheduleName = to.trim();
                affected.add(course.id);
            }
        }
        if (!affected.isEmpty()) {
            saveCourses(courses, affected);
        }
        return affected.size();
    }

    /**
     * Clears the schedule choice of every course that pointed at {@code name}.
     *
     * <p>Called after a schedule is deleted so the courses fall back to
     * venue-based resolution instead of carrying a name that no longer
     * exists.</p>
     *
     * @return how many courses were cleared
     */
    public int clearScheduleReferences(String name) {
        if (name == null || name.trim().isEmpty()) {
            return 0;
        }
        List<Course> courses = getCourses();
        List<Long> affected = new ArrayList<>();
        for (Course course : courses) {
            if (name.trim().equals(course.scheduleName)) {
                course.scheduleName = "";
                affected.add(course.id);
            }
        }
        if (!affected.isEmpty()) {
            saveCourses(courses, affected);
        }
        return affected.size();
    }

    /**
     * Week 1 of the active semester.
     *
     * <p>Read from the active semester's own key, so two semesters can start on
     * different dates without touching each other.</p>
     */
    public LocalDate getSemesterStart() {
        String raw = preferences.getString(startKey(), null);
        if (raw != null) {
            LocalDate parsed = ScheduleUtils.parseDate(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        LocalDate start = defaultSemesterStart(LocalDate.now());
        setSemesterStart(start);
        return start;
    }

    /**
     * Moves week 1 of the active semester.
     *
     * <p>The semester record is updated too, through {@link #updateSemester},
     * which writes the same key -- so the date the semester reports and the date
     * the week maths uses cannot drift apart.</p>
     */
    public void setSemesterStart(LocalDate date) {
        LocalDate monday = ScheduleUtils.mondayOf(date);
        Semester active = findSemester(getSemesters(), semesterId);
        if (active != null) {
            updateSemester(active.startingOn(monday));
        } else {
            preferences.edit().putString(startKey(), monday.toString()).apply();
        }
    }

    private LocalDate defaultSemesterStart(LocalDate today) {
        LocalDate anchor;
        if (today.getMonthValue() >= 8) {
            anchor = LocalDate.of(today.getYear(), 8, 28);
        } else {
            anchor = LocalDate.of(today.getYear(), 2, 18);
        }
        return anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private void saveCourses(List<Course> courses) {
        writeCourses(coursesKey(), courses);
        cachedCourses = null;
    }

    /**
     * Writes the list back.
     *
     * @param changedIds ids whose stored course must be rewritten; {@code null}
     *                   rewrites everything
     */
    private void saveCourses(List<Course> courses, List<Long> changedIds) {
        List<Object> array;
        if (changedIds == null) {
            array = MiniJson.array();
            for (Course course : courses) {
                array.add(toJson(course));
            }
        } else {
            array = replaceEntries(preferences.getString(coursesKey(), null),
                    courses, changedIds);
        }
        preferences.edit().putString(coursesKey(), MiniJson.write(array)).apply();
        cachedCourses = null;
    }

    /**
     * Rewrites only the stored entries whose id is in {@code changedIds}.
     *
     * <p>Used by the operations that make a narrow change (renaming or clearing
     * a schedule reference) so that courses this repository never had to
     * understand -- including the ones skipped as damaged -- keep the exact
     * payload they were written with.</p>
     */
    private List<Object> replaceEntries(String raw, List<Course> courses,
            List<Long> changedIds) {
        List<Object> stored = MiniJson.parseArray(raw);
        for (int i = 0; i < stored.size(); i++) {
            Map<String, Object> item = MiniJson.objectAt(stored, i);
            if (item == null) {
                continue;
            }
            long id = MiniJson.optLong(item, "id", 0L);
            if (!changedIds.contains(id)) {
                continue;
            }
            for (Course course : courses) {
                if (course.id == id) {
                    stored.set(i, toJson(course));
                    break;
                }
            }
        }
        return stored;
    }

    private static Map<String, Object> toJson(Course course) {
        Map<String, Object> object = MiniJson.object();
        object.put("id", Long.valueOf(course.id));
        object.put("name", course.name);
        object.put("teacher", course.teacher);
        object.put("location", course.location);
        object.put("day", Integer.valueOf(course.dayOfWeek));
        object.put("startPeriod", Integer.valueOf(course.startPeriod));
        object.put("endPeriod", Integer.valueOf(course.endPeriod));
        object.put("startWeek", Integer.valueOf(course.startWeek));
        object.put("endWeek", Integer.valueOf(course.endWeek));
        object.put("parity", Integer.valueOf(course.parity));
        // Written as a long so it survives JSON's double-precision numbers.
        object.put("color", Long.valueOf(course.color & 0xFFFFFFFFL));
        object.put("scheduleName", course.scheduleName);
        return object;
    }

    private Course fromJson(Map<String, Object> object) {
        Course course = new Course();
        course.id = MiniJson.optLong(object, "id", 0L);
        course.name = MiniJson.optString(object, "name", "");
        course.teacher = MiniJson.optString(object, "teacher", "");
        course.location = MiniJson.optString(object, "location", "");
        course.dayOfWeek = MiniJson.optInt(object, "day", 1);
        course.startPeriod = MiniJson.optInt(object, "startPeriod", 1);
        course.endPeriod = MiniJson.optInt(object, "endPeriod", 2);
        course.startWeek = MiniJson.optInt(object, "startWeek", 1);
        course.endWeek = MiniJson.optInt(object, "endWeek", ScheduleUtils.SEMESTER_WEEKS);
        course.parity = MiniJson.optInt(object, "parity", Course.PARITY_ALL);
        course.color = (int) MiniJson.optLong(object, "color", Course.DEFAULT_COLOR);
        // Absent in payloads written before per-course schedules existed: an
        // empty name means "decide by venue", which is the old behaviour.
        course.scheduleName = MiniJson.optString(object, "scheduleName", "").trim();
        return course;
    }

    /**
     * The timetable a fresh install starts from.
     *
     * <p>Placeholder content, but not arbitrary: the venues are named after the
     * buildings the timetable actually distinguishes, so the sample week shows
     * the per-venue hours working (a course in 教学楼 C runs to a different clock
     * than one in 教学楼 A) instead of all of them looking alike.</p>
     *
     * <p>The colours come from {@link Palette#COURSE_COLORS} rather than from
     * literals here. They used to be nine hard-coded hex values, which meant the
     * first thing a new user saw was the one screen in the app that did not use
     * the design system's palette.</p>
     */
    private List<Course> createSampleCourses() {
        int[] colors = Palette.COURSE_COLORS;
        List<Course> courses = new ArrayList<>();
        courses.add(sample(1, "\u9AD8\u7B49\u6570\u5B66",
                "\u5F20\u8001\u5E08", "\u6559\u5B66\u697C A203",
                1, 1, 2, 1, 16, Course.PARITY_ALL, colors[0]));
        courses.add(sample(2, "\u5927\u5B66\u82F1\u8BED",
                "\u674E\u8001\u5E08", "\u6559\u5B66\u697C F305",
                1, 3, 4, 1, 16, Course.PARITY_ALL, colors[1]));
        courses.add(sample(3, "\u6570\u636E\u7ED3\u6784",
                "\u738B\u8001\u5E08", "\u6559\u5B66\u697C C203",
                2, 3, 4, 1, 8, Course.PARITY_ALL, colors[2]));
        courses.add(sample(4, "\u7EBF\u6027\u4EE3\u6570",
                "\u5218\u8001\u5E08", "\u6559\u5B66\u697C D102",
                3, 1, 2, 9, 16, Course.PARITY_ALL, colors[3]));
        courses.add(sample(5, "\u7A0B\u5E8F\u8BBE\u8BA1\u5B9E\u9A8C",
                "\u9648\u8001\u5E08", "\u6559\u5B66\u697C E302",
                3, 5, 6, 1, 16, Course.PARITY_ODD, colors[4]));
        courses.add(sample(6, "\u5927\u5B66\u7269\u7406",
                "\u8D75\u8001\u5E08", "\u6559\u5B66\u697C A201",
                4, 1, 2, 1, 16, Course.PARITY_EVEN, colors[5]));
        courses.add(sample(7, "\u4F53\u80B2",
                "\u5B59\u8001\u5E08", "\u4E1C\u64CD\u573A",
                5, 3, 4, 1, 16, Course.PARITY_ALL, colors[6]));
        courses.add(sample(8, "\u5F62\u52BF\u4E0E\u653F\u7B56",
                "\u5468\u8001\u5E08", "J301 \u5BA4",
                5, 7, 8, 1, 8, Course.PARITY_EVEN, colors[7]));
        courses.add(sample(9, "\u64CD\u4F5C\u7CFB\u7EDF",
                "\u4F55\u8001\u5E08", "\u6559\u5B66\u697C E402",
                6, 10, 11, 9, 16, Course.PARITY_ALL, colors[8]));
        return courses;
    }

    private Course sample(long id, String name, String teacher, String location,
            int day, int startPeriod, int endPeriod, int startWeek, int endWeek,
            int parity, int color) {
        Course course = new Course();
        course.id = id;
        course.name = name;
        course.teacher = teacher;
        course.location = location;
        course.dayOfWeek = day;
        course.startPeriod = startPeriod;
        course.endPeriod = endPeriod;
        course.startWeek = startWeek;
        course.endWeek = endWeek;
        course.parity = parity;
        course.color = color;
        return course;
    }
    // --------------------------------------------------------------- restoring

    /**
     * Replaces everything a backup covers, in one transaction, keeping the old
     * state aside until the new one has been read back and checked.
     *
     * <p>The whole restore is a single {@link SharedPreferences.Editor} commit.
     * That matters: the keys involved are {@code semesters}, {@code active_semester},
     * {@code bell_schedules} and one {@code courses:<id>} and {@code semester_start:<id>}
     * per semester -- three plus two per term. Saving them through the ordinary
     * methods would be one commit each, and a process that died between two of
     * them would leave a store holding the new semesters and the old courses,
     * which nothing in the app would repair.</p>
     *
     * <p>The snapshot is written in the same commit as the new data, so the two
     * cannot disagree about whether a restore was in progress. Everything the
     * backup does not cover -- the damaged-payload copy, the retired legacy keys,
     * anything a future version adds -- is left exactly as it was: this replaces
     * a known set of keys, it does not clear the file.</p>
     *
     * @return what actually happened, never null
     */
    public RestoreReport restoreFrom(BackupCodec.Document document) {
        if (document == null || document.semesters().isEmpty()
                || document.schedules().isEmpty()) {
            return RestoreReport.unchanged(
                    "\u5907\u4EFD\u5185\u5BB9\u4E0D\u5B8C\u6574\uFF0C\u672A\u505A\u4EFB"
                            + "\u4F55\u6539\u52A8");
        }
        Map<String, Object> snapshot = captureRestoreScope();
        String expectation = expectationOf(document);

        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (isScopedKey(key)) {
                editor.remove(key);
            }
        }
        editor.putString(KEY_RESTORE_SNAPSHOT, MiniJson.write(snapshot));
        editor.putString(KEY_RESTORE_PENDING, expectation);
        editor.putString(KEY_SEMESTERS, SemesterJson.write(document.semesters()));
        editor.putLong(KEY_ACTIVE_SEMESTER, document.activeSemesterId());
        editor.putString(KEY_BELL_SCHEDULES, BellScheduleJson.write(document.schedules()));
        for (Semester semester : document.semesters()) {
            editor.putString(coursesKeyFor(semester.id()),
                    coursesJsonOf(document.coursesOf(semester.id())));
            editor.putString(startKeyFor(semester.id()), semester.start().toString());
        }
        if (!editor.commit()) {
            // SharedPreferences applies an editor all-or-nothing, so a refusal
            // here means nothing was written and the old data is untouched.
            return RestoreReport.unchanged("\u5199\u5165\u5931\u8D25\uFF0C\u5F53\u524D"
                    + "\u6570\u636E\u4FDD\u6301\u4E0D\u53D8");
        }

        reload();
        String problem = verifyStored(expectation);
        if (problem == null) {
            clearPending();
            adoptSemester(activeSemester());
            return RestoreReport.restored("\u5DF2\u6062\u590D " + document.semesterCount()
                    + " \u4E2A\u5B66\u671F \u00B7 " + document.courseCount()
                    + " \u95E8\u8BFE\u7A0B \u00B7 " + document.scheduleCount()
                    + " \u5F20\u4F5C\u606F\u8868");
        }
        return rollBackTo(snapshot, problem);
    }

    /**
     * Settles a restore that was interrupted between its commit and its check.
     *
     * <p>Only the marker makes this distinguishable from an ordinary start, and
     * only the marker is checked, so the ordinary start costs one {@code contains}.
     * The new data is either all there -- the commit was atomic -- or it is not;
     * this re-checks it against what the restore intended and puts the snapshot
     * back when it does not match.</p>
     *
     * @return what was settled, or null when there was nothing to settle
     */
    private RestoreReport resolvePendingRestore() {
        String expectation = preferences.getString(KEY_RESTORE_PENDING, null);
        if (expectation == null) {
            return null;
        }
        String problem = verifyStored(expectation);
        if (problem == null) {
            clearPending();
            return RestoreReport.restored("\u4E0A\u4E00\u6B21\u6062\u590D\u5DF2\u5B8C"
                    + "\u6210\u5E76\u901A\u8FC7\u6821\u9A8C");
        }
        Map<String, Object> snapshot = readSnapshot();
        if (snapshot == null) {
            return RestoreReport.needsAttention(
                    "\u4E0A\u4E00\u6B21\u6062\u590D\u672A\u5B8C\u6210\uFF0C\u4E14\u627E"
                            + "\u4E0D\u5230\u56DE\u6EDA\u6750\u6599",
                    problem);
        }
        return rollBackTo(snapshot, problem);
    }

    /**
     * Puts the captured state back and checks that it landed.
     *
     * <p>A rollback is itself a single commit, so it either applies in full or
     * not at all. When it does not verify, nothing is cleared: the snapshot and
     * the marker both stay on disk so a later attempt -- or a person -- can still
     * see what the data was before.</p>
     */
    private RestoreReport rollBackTo(Map<String, Object> snapshot, String reason) {
        if (snapshot == null) {
            return RestoreReport.needsAttention(
                    "\u6062\u590D\u672A\u901A\u8FC7\u6821\u9A8C\uFF0C\u4E14\u6CA1\u6709"
                            + "\u53EF\u7528\u7684\u56DE\u6EDA\u6750\u6599", reason);
        }
        if (!applySnapshot(snapshot)) {
            return RestoreReport.needsAttention(
                    "\u6062\u590D\u672A\u901A\u8FC7\u6821\u9A8C\uFF0C\u56DE\u6EDA"
                            + "\u4E5F\u672A\u5199\u5165\u6210\u529F", reason);
        }
        String residual = verifyStored(expectationOfSnapshot(snapshot));
        if (residual != null) {
            return RestoreReport.needsAttention(
                    "\u56DE\u6EDA\u540E\u6821\u9A8C\u4ECD\u672A\u901A\u8FC7", residual);
        }
        clearPending();
        adoptSemester(activeSemester());
        return RestoreReport.rolledBack(
                "\u6062\u590D\u672A\u901A\u8FC7\u6821\u9A8C\uFF0C\u5DF2\u8FD8\u539F"
                        + "\u5230\u6062\u590D\u524D\u7684\u6570\u636E", reason);
    }

    /** Everything a restore would replace, exactly as it is stored now. */
    private Map<String, Object> captureRestoreScope() {
        Map<String, Object> snapshot = MiniJson.object();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!isScopedKey(key)) {
                continue;
            }
            Object value = entry.getValue();
            Map<String, Object> encoded = MiniJson.object();
            if (value instanceof String) {
                encoded.put("s", value);
            } else if (value instanceof Number) {
                encoded.put("l", Long.valueOf(((Number) value).longValue()));
            } else {
                // Nothing this app writes has another type; leave it alone rather
                // than pretend to have captured it.
                continue;
            }
            snapshot.put(key, encoded);
        }
        return snapshot;
    }

    /** Writes a captured state back in one commit, dropping keys it did not have. */
    private boolean applySnapshot(Map<String, Object> snapshot) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (isScopedKey(key) && !snapshot.containsKey(key)) {
                editor.remove(key);
            }
        }
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> encoded = (Map<?, ?>) value;
            Object text = encoded.get("s");
            Object number = encoded.get("l");
            if (text instanceof String) {
                editor.putString(entry.getKey(), (String) text);
            } else if (number instanceof Number) {
                editor.putLong(entry.getKey(), ((Number) number).longValue());
            }
        }
        boolean applied = editor.commit();
        reload();
        return applied;
    }

    private Map<String, Object> readSnapshot() {
        Object parsed = MiniJson.parse(
                preferences.getString(KEY_RESTORE_SNAPSHOT, null));
        if (!(parsed instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) parsed;
        return snapshot.isEmpty() ? null : snapshot;
    }

    /**
     * The keys a restore owns.
     *
     * <p>Deliberately a positive list. {@code courses_backup} holds a damaged
     * payload that has nothing to do with a restore, the retired {@code courses}
     * and {@code semester_start} keys are read-only history, and a future version
     * may add keys this one knows nothing about -- clearing the file would throw
     * all of that away.</p>
     *
     * <p>{@code courses_previous} is on the list because a restore replaces every
     * term's timetable: an undo entry left over from before it would take the user
     * back to courses the backup has just replaced, which is exactly the wrong
     * direction. Being scoped also puts those entries in the rollback material, so
     * a restore that fails to verify hands them back along with everything else.</p>
     */
    private static boolean isScopedKey(String key) {
        return KEY_SEMESTERS.equals(key)
                || KEY_ACTIVE_SEMESTER.equals(key)
                || KEY_BELL_SCHEDULES.equals(key)
                || key.startsWith(KEY_COURSES + SUFFIX_SEPARATOR)
                || key.startsWith(KEY_START + SUFFIX_SEPARATOR)
                || key.startsWith(KEY_COURSES_PREVIOUS + SUFFIX_SEPARATOR);
    }

    /**
     * Reads the store back and checks it against what the restore intended.
     *
     * <p>Reads the raw payloads rather than going through the ordinary getters.
     * They repair as they read -- a schedule whose time arrays do not line up
     * becomes the built-in timetable, an empty schedule list is re-seeded -- so
     * checking through them would report on what the app would decide the data
     * means rather than on what was actually written.</p>
     *
     * @return null when everything matches, otherwise what did not
     */
    private String verifyStored(String expectationJson) {
        Object parsed = MiniJson.parse(expectationJson);
        if (!(parsed instanceof Map)) {
            return "\u65E0\u6CD5\u8BFB\u53D6\u6062\u590D\u6821\u9A8C\u4FE1\u606F";
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) parsed;
        List<Object> wantedIds = MiniJson.optArray(expected, "semesterIds");

        List<Semester> stored = SemesterJson.parse(
                preferences.getString(KEY_SEMESTERS, null));
        if (stored.size() != wantedIds.size()) {
            return "\u5B66\u671F\u6570\u91CF\u4E0D\u7B26\uFF1A\u5E94\u4E3A "
                    + wantedIds.size() + "\uFF0C\u5B9E\u9645\u4E3A " + stored.size();
        }
        List<String> scheduleNames = new ArrayList<>();
        for (BellSchedule schedule : BellScheduleJson.parse(
                preferences.getString(KEY_BELL_SCHEDULES, null))) {
            scheduleNames.add(schedule.name());
        }
        if (scheduleNames.size() != MiniJson.optInt(expected, "scheduleCount", -1)) {
            return "\u4F5C\u606F\u8868\u6570\u91CF\u4E0D\u7B26\uFF1A\u5B9E\u9645\u4E3A "
                    + scheduleNames.size();
        }
        Object rawCounts = expected.get("courseCounts");
        Map<?, ?> counts = rawCounts instanceof Map ? (Map<?, ?>) rawCounts : null;
        for (int i = 0; i < wantedIds.size(); i++) {
            Object rawId = wantedIds.get(i);
            if (!(rawId instanceof Number)) {
                return "\u6062\u590D\u6821\u9A8C\u4FE1\u606F\u5DF2\u635F\u574F";
            }
            long id = ((Number) rawId).longValue();
            if (stored.get(i).id() != id) {
                return "\u5B66\u671F\u6807\u8BC6\u4E0D\u7B26\uFF1A\u7B2C " + (i + 1)
                        + " \u4E2A\u5E94\u4E3A " + id;
            }
            List<Object> courses = MiniJson.parseArray(
                    preferences.getString(coursesKeyFor(id), null));
            int want = 0;
            Object rawWant = counts == null ? null : counts.get(String.valueOf(id));
            if (rawWant instanceof Number) {
                want = ((Number) rawWant).intValue();
            }
            if (courses.size() != want) {
                return "\u8BFE\u7A0B\u6570\u91CF\u4E0D\u7B26\uFF1A\u5B66\u671F " + id
                        + " \u5E94\u4E3A " + want + "\uFF0C\u5B9E\u9645\u4E3A "
                        + courses.size();
            }
            for (Object entry : courses) {
                if (!(entry instanceof Map)) {
                    return "\u8BFE\u7A0B\u6570\u636E\u5DF2\u635F\u574F";
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) entry;
                String name = MiniJson.optString(item, "scheduleName", "").trim();
                if (!name.isEmpty() && !scheduleNames.contains(name)) {
                    return "\u8BFE\u7A0B\u5F15\u7528\u4E86\u4E0D\u5B58\u5728\u7684"
                            + "\u4F5C\u606F\u8868\uFF1A" + name;
                }
            }
        }
        long active = preferences.getLong(KEY_ACTIVE_SEMESTER, 0L);
        boolean activeFound = false;
        for (Semester semester : stored) {
            if (semester.id() == active) {
                activeFound = true;
            }
        }
        if (!activeFound) {
            return "\u5F53\u524D\u5B66\u671F\u4E0D\u5728\u6062\u590D\u7ED3\u679C\u4E2D";
        }
        return null;
    }

    /** What a restore of {@code document} must leave behind, in one small object. */
    private static String expectationOf(BackupCodec.Document document) {
        Map<String, Object> expectation = MiniJson.object();
        List<Object> ids = MiniJson.array();
        Map<String, Object> counts = MiniJson.object();
        for (Semester semester : document.semesters()) {
            ids.add(Long.valueOf(semester.id()));
            counts.put(String.valueOf(semester.id()),
                    Integer.valueOf(document.coursesOf(semester.id()).size()));
        }
        expectation.put("semesterIds", ids);
        expectation.put("courseCounts", counts);
        expectation.put("scheduleCount", Integer.valueOf(document.scheduleCount()));
        return MiniJson.write(expectation);
    }

    /** The same, worked out from a captured snapshot rather than a document. */
    private String expectationOfSnapshot(Map<String, Object> snapshot) {
        List<Semester> semesters = SemesterJson.parse(valueOf(snapshot, KEY_SEMESTERS));
        Map<String, Object> expectation = MiniJson.object();
        List<Object> ids = MiniJson.array();
        Map<String, Object> counts = MiniJson.object();
        for (Semester semester : semesters) {
            ids.add(Long.valueOf(semester.id()));
            counts.put(String.valueOf(semester.id()), Integer.valueOf(
                    MiniJson.parseArray(valueOf(snapshot, coursesKeyFor(semester.id())))
                            .size()));
        }
        expectation.put("semesterIds", ids);
        expectation.put("courseCounts", counts);
        expectation.put("scheduleCount", Integer.valueOf(BellScheduleJson.parse(
                valueOf(snapshot, KEY_BELL_SCHEDULES)).size()));
        return MiniJson.write(expectation);
    }

    /** The captured text of one key, or null when the snapshot did not hold it. */
    private static String valueOf(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (!(value instanceof Map)) {
            return null;
        }
        Object text = ((Map<?, ?>) value).get("s");
        return text instanceof String ? (String) text : null;
    }

    /**
     * A course list in the stored shape.
     *
     * <p>The single definition of that shape: the ordinary save, the copy into
     * another semester and the restore all come through here, and every one of
     * them has to agree with {@link #toJson} field for field or the same course
     * would be stored two different ways.</p>
     */
    private static String coursesJsonOf(List<Course> courses) {
        List<Object> array = MiniJson.array();
        for (Course course : courses) {
            array.add(toJson(course));
        }
        return MiniJson.write(array);
    }

    /** Clears the marker; a failure here only means the next start re-checks. */
    private void clearPending() {
        preferences.edit().remove(KEY_RESTORE_PENDING).apply();
    }

}
