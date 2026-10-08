package com.weihe.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    public CourseRepository(Context context) {
        this(context == null ? pendingPreferences.get() : preferencesOf(context));
        pendingPreferences.remove();
    }

    /** Visible for tests: the repository around an already-built store. */
    CourseRepository(SharedPreferences store) {
        preferences = store;
        migrateLegacyStorage();
        adoptSemester(activeSemester());
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
        writeCourses(coursesKeyFor(target.id()), incoming);
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
        List<Object> array = MiniJson.array();
        for (Course course : courses) {
            array.add(toJson(course));
        }
        preferences.edit().putString(key, MiniJson.write(array)).apply();
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

    /** Replaces the stored timetable with imported courses. */
    public void replaceCourses(List<Course> imported) {
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
        saveCourses(courses);
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

    private Map<String, Object> toJson(Course course) {
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
}
