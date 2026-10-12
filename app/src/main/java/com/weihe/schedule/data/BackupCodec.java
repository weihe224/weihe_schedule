package com.weihe.schedule.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * The backup file format: one versioned JSON object holding everything a user
 * would lose if the app were reinstalled.
 *
 * <p>Deliberately <em>not</em> a dump of {@code SharedPreferences}. The store is
 * a flat key-value map whose keys embed the semester id ({@code courses:<id>}),
 * carries internal artefacts ({@code courses_backup}) and has no version marker;
 * none of that belongs in a file users keep and may hand to a future version of
 * the app. Here the courses are <em>nested inside</em> their semester, so the
 * relationship is expressed by the structure rather than by a key naming
 * convention, and the whole document carries {@code format} and
 * {@code formatVersion}.</p>
 *
 * <p>This class only reads, writes and checks. Nothing on its code path reaches
 * storage: it holds no {@code Context}, names no repository type, and calls no
 * save, delete, replace or migrate method. Restoring is a separate concern and is
 * not implemented yet.</p>
 *
 * <h3>Shape</h3>
 * <pre>{@code
 * { "format": "weihe-schedule-backup",
 *   "formatVersion": 1,
 *   "exportedAt": "2026-10-09T21:32:10+08:00",
 *   "appVersionName": "1.0",
 *   "activeSemesterId": 1757000000000,
 *   "semesters": [ { "id": ..., "start": "2026-08-31", "name": "",
 *                    "courses": [ { "id": ..., "name": ..., "teacher": ...,
 *                                   "location": ..., "day": ..., "startPeriod": ...,
 *                                   "endPeriod": ..., "startWeek": ...,
 *                                   "endWeek": ..., "parity": ..., "color": ...,
 *                                   "scheduleName": ... } ] } ],
 *   "schedules": [ { "name": ..., "keywords": [ ... ],
 *                    "id": ...,                // absent in old backups
 *                    "durationMinutes": ...,   // absent in old backups = 40
 *                    "starts": [ ... ], "ends": [ ... ] } ],
 *   "defaultScheduleId": ... }   // absent in old backups
 * }</pre>
 *
 * <p>The length of {@code starts} and {@code ends} is the number of periods; it is
 * not a fixed fifteen. {@code durationMinutes} was added with the settable period
 * length and is optional on read, so a backup made before it loads on exactly the
 * forty-minute clock it was written on.</p>
 *
 * <p>{@code id} on a schedule and the top-level {@code defaultScheduleId} were added
 * with the settable default timetable, and both are optional on read. They are
 * written together -- an id no schedule carries would be a pointer to nothing -- and
 * a file that names a default which its own schedule list does not contain is read
 * with the default cleared rather than refused, because the rest of the file is
 * still a complete set of work. A backup made before defaults existed simply has no
 * {@code defaultScheduleId}, and the restoring build adopts the same fallback the
 * exporting build used.</p>
 *
 * <p>The course field names and their meanings are the ones the store already
 * uses ({@code CourseRepository.toJson}), including {@code color} written as an
 * unsigned 32-bit value so JSON's double precision cannot eat it. From
 * {@code formatVersion 1} these names are a contract: if the store ever renames
 * one, the change has to be absorbed here rather than leak into the file.</p>
 */
public final class BackupCodec {

    /** Marker so a file chosen by mistake can be told apart from a real backup. */
    public static final String FORMAT = "weihe-schedule-backup";

    /** The version this build writes. */
    public static final int FORMAT_VERSION = 1;

    /** The highest version this build can read. */
    public static final int MAX_SUPPORTED_VERSION = 1;

    /**
     * Refuse anything larger than this, in characters of JSON text.
     *
     * <p>A real backup is a few kilobytes -- a hundred courses are still well
     * under a hundred kilobytes. The limit exists so a file picked by accident
     * cannot be pulled into memory whole, not to constrain real data. UTF-8
     * encodes one character as one or more bytes, so a byte-level limit at the
     * reading site is stricter still; see the export/restore callers.</p>
     */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    /** Sanity bounds on item counts, for the same reason as {@link #MAX_BYTES}. */
    private static final int MAX_SEMESTERS = 200;
    private static final int MAX_COURSES_PER_SEMESTER = 5000;
    private static final int MAX_SCHEDULES = 100;

    private BackupCodec() {
    }

    // ------------------------------------------------------------------ model

    /**
     * A parsed, checked backup: everything needed to show a preview, and
     * everything a later restore step would write.
     */
    public static final class Document {
        private final int formatVersion;
        private final String exportedAt;
        private final String appVersionName;
        private final long activeSemesterId;
        private final List<Semester> semesters;
        private final Map<Long, List<Course>> coursesBySemester;
        private final List<BellSchedule> schedules;
        private final String defaultScheduleId;
        private final List<String> notes;

        private Document(int formatVersion, String exportedAt, String appVersionName,
                long activeSemesterId, List<Semester> semesters,
                Map<Long, List<Course>> coursesBySemester,
                List<BellSchedule> schedules, String defaultScheduleId,
                List<String> notes) {
            this.formatVersion = formatVersion;
            this.exportedAt = exportedAt;
            this.appVersionName = appVersionName;
            this.activeSemesterId = activeSemesterId;
            this.semesters = semesters;
            this.coursesBySemester = coursesBySemester;
            this.schedules = schedules;
            this.defaultScheduleId = defaultScheduleId;
            this.notes = notes;
        }

        /** The {@code formatVersion} the file stated, or the assumed one. */
        public int formatVersion() {
            return formatVersion;
        }

        /** When the file was written, or {@code ""} when it did not say. */
        public String exportedAt() {
            return exportedAt;
        }

        /** The writing app's version name, or {@code ""} when it did not say. */
        public String appVersionName() {
            return appVersionName;
        }

        /** The semester to show first, or {@code 0} when there are none. */
        public long activeSemesterId() {
            return activeSemesterId;
        }

        /** The semester this document treats as current; null only when empty. */
        public Semester activeSemester() {
            for (Semester semester : semesters) {
                if (semester.id() == activeSemesterId) {
                    return semester;
                }
            }
            return semesters.isEmpty() ? null : semesters.get(0);
        }

        public List<Semester> semesters() {
            return Collections.unmodifiableList(semesters);
        }

        public List<BellSchedule> schedules() {
            return Collections.unmodifiableList(schedules);
        }

        /**
         * The schedule the file's author had chosen as the default, or null.
         *
         * <p>Null means the file does not say -- either it was written before the
         * choice existed, or it named a schedule its own list does not contain and
         * the reader dropped it. Both cases are handled the same way on restore:
         * the fallback the exporting build used is adopted, so the courses keep the
         * clock they were on.</p>
         */
        public String defaultScheduleId() {
            return defaultScheduleId;
        }

        /** The courses of one semester; empty when the document has none. */
        public List<Course> coursesOf(long semesterId) {
            List<Course> courses = coursesBySemester.get(Long.valueOf(semesterId));
            return courses == null
                    ? Collections.<Course>emptyList()
                    : Collections.unmodifiableList(courses);
        }

        public int semesterCount() {
            return semesters.size();
        }

        public int scheduleCount() {
            return schedules.size();
        }

        public int courseCount() {
            int total = 0;
            for (List<Course> courses : coursesBySemester.values()) {
                total += courses.size();
            }
            return total;
        }

        /**
         * What the reader had to repair, in the user's words; empty for a file
         * that needed nothing. A restore step must show these before writing.
         */
        public List<String> notes() {
            return Collections.unmodifiableList(notes);
        }
    }

    /** The outcome of {@link #parse}: either a document, or why it was refused. */
    public static final class Result {
        private final Document document;
        private final List<String> problems;

        private Result(Document document, List<String> problems) {
            this.document = document;
            this.problems = problems;
        }

        public boolean isOk() {
            return document != null;
        }

        /** The parsed document, or {@code null} when {@link #isOk()} is false. */
        public Document document() {
            return document;
        }

        /** Why the file was refused; empty when it was accepted. */
        public List<String> problems() {
            return Collections.unmodifiableList(problems);
        }

        /** The first problem, for a one-line message. */
        public String problem() {
            return problems.isEmpty() ? "" : problems.get(0);
        }
    }

    // ----------------------------------------------------------------- writing

    /**
     * Builds a document out of the pieces a caller read from storage.
     *
     * <p>Copies everything it is handed, so a later change to the caller's lists
     * cannot reach into the document -- {@code getBellSchedules()} in particular
     * hands out its cached list rather than a copy, so the copy matters.</p>
     */
    public static Document of(List<Semester> semesters,
            Map<Long, List<Course>> coursesBySemester,
            List<BellSchedule> schedules,
            long activeSemesterId,
            String exportedAt,
            String appVersionName) {
        return of(semesters, coursesBySemester, schedules, activeSemesterId,
                exportedAt, appVersionName, null);
    }

    /**
     * The same, carrying the chosen default timetable.
     *
     * <p>The extra field is a separate overload rather than a replaced signature:
     * every existing caller keeps compiling and keeps writing exactly the document
     * it wrote before, and only the export path -- which is the one place that
     * knows the current default -- passes it.</p>
     *
     * <p>The id is dropped when it does not name one of {@code schedules}. A backup
     * that pointed at a schedule it does not carry would fail to restore with a
     * default the reader had to invent, and writing such a pointer is a bug in the
     * caller rather than something a file should record.</p>
     */
    public static Document of(List<Semester> semesters,
            Map<Long, List<Course>> coursesBySemester,
            List<BellSchedule> schedules,
            long activeSemesterId,
            String exportedAt,
            String appVersionName,
            String defaultScheduleId) {
        List<Semester> semesterCopies = new ArrayList<>();
        Map<Long, List<Course>> courseCopies = new LinkedHashMap<>();
        if (semesters != null) {
            for (Semester semester : semesters) {
                if (semester == null) {
                    continue;
                }
                semesterCopies.add(new Semester(semester.id(), semester.start(),
                        semester.name()));
                List<Course> source = coursesBySemester == null
                        ? null : coursesBySemester.get(Long.valueOf(semester.id()));
                courseCopies.put(Long.valueOf(semester.id()), copyCourses(source));
            }
        }
        List<BellSchedule> scheduleCopies = new ArrayList<>();
        if (schedules != null) {
            for (BellSchedule schedule : schedules) {
                if (schedule != null) {
                    scheduleCopies.add(schedule);
                }
            }
        }
        return new Document(FORMAT_VERSION, nullToEmpty(exportedAt),
                nullToEmpty(appVersionName), activeSemesterId, semesterCopies,
                courseCopies, scheduleCopies, idIn(defaultScheduleId, scheduleCopies),
                new ArrayList<String>());
    }

    /** The id when a schedule carries it, otherwise null; see {@link #of}. */
    private static String idIn(String defaultScheduleId, List<BellSchedule> schedules) {
        if (defaultScheduleId == null || defaultScheduleId.trim().isEmpty()) {
            return null;
        }
        String wanted = defaultScheduleId.trim();
        for (BellSchedule schedule : schedules) {
            if (wanted.equals(schedule.id())) {
                return wanted;
            }
        }
        return null;
    }

    private static List<Course> copyCourses(List<Course> source) {
        List<Course> copies = new ArrayList<>();
        if (source != null) {
            for (Course course : source) {
                if (course != null) {
                    copies.add(course.copy());
                }
            }
        }
        return copies;
    }

    /**
     * Writes the document to a stream as UTF-8, without a byte-order mark.
     *
     * <p>The text is built in full before anything is written, so a stream that
     * fails part way through cannot leave a truncated file that looks complete.
     * The caller owns the stream and must close it -- typically with
     * try-with-resources, so a failure still closes it.</p>
     */
    public static void writeTo(Document document, OutputStream out) throws IOException {
        out.write(write(document).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** The whole document as JSON text. Key order is fixed, so output is stable. */
    public static String write(Document document) {
        Map<String, Object> root = MiniJson.object();
        root.put("format", FORMAT);
        root.put("formatVersion", Integer.valueOf(FORMAT_VERSION));
        root.put("exportedAt", document.exportedAt());
        root.put("appVersionName", document.appVersionName());
        root.put("activeSemesterId", Long.valueOf(document.activeSemesterId()));

        List<Object> semesters = MiniJson.array();
        for (Semester semester : document.semesters()) {
            Map<String, Object> item = MiniJson.object();
            item.put("id", Long.valueOf(semester.id()));
            item.put("start", semester.start().toString());
            item.put("name", semester.name());
            List<Object> courses = MiniJson.array();
            for (Course course : document.coursesOf(semester.id())) {
                courses.add(courseToJson(course));
            }
            item.put("courses", courses);
            semesters.add(item);
        }
        root.put("semesters", semesters);

        List<Object> schedules = MiniJson.array();
        for (BellSchedule schedule : document.schedules()) {
            schedules.add(scheduleToJson(schedule));
        }
        root.put("schedules", schedules);
        // Omitted rather than written blank: a blank id would read back as "an id
        // was assigned" and match nothing, where an absent key is the one signal
        // the reader treats as "this file predates the choice".
        if (document.defaultScheduleId() != null) {
            root.put("defaultScheduleId", document.defaultScheduleId());
        }
        return MiniJson.write(root);
    }

    /** The same mapping the store uses, field for field. */
    private static Map<String, Object> courseToJson(Course course) {
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
        // Unsigned, so JSON's double precision cannot change the colour.
        object.put("color", Long.valueOf(course.color & 0xFFFFFFFFL));
        object.put("scheduleName", course.scheduleName);
        return object;
    }

    private static Map<String, Object> scheduleToJson(BellSchedule schedule) {
        Map<String, Object> object = MiniJson.object();
        object.put("name", schedule.name());
        // Written only when the schedule has one -- same reason as the top-level
        // default: a blank id is indistinguishable from an assigned one that names
        // nothing. A schedule without an id simply gets one on the next read.
        if (schedule.id() != null) {
            object.put("id", schedule.id());
        }
        List<Object> keywords = MiniJson.array();
        keywords.addAll(schedule.locationKeywords());
        object.put("keywords", keywords);
        object.put("durationMinutes",
                Integer.valueOf(schedule.durationMinutes()));
        List<Object> starts = MiniJson.array();
        List<Object> ends = MiniJson.array();
        for (int period = 1; period <= schedule.periodCount(); period++) {
            starts.add(Integer.valueOf(schedule.startMinute(period)));
            ends.add(Integer.valueOf(schedule.endMinute(period)));
        }
        object.put("starts", starts);
        object.put("ends", ends);
        return object;
    }

    // ----------------------------------------------------------------- reading

    /** Parses and checks a backup. Never throws; failures come back as problems. */
    public static Result parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return fail("\u6587\u4EF6\u662F\u7A7A\u7684\uFF0C\u6CA1\u6709\u53EF\u8BFB"
                    + "\u53D6\u7684\u5185\u5BB9");
        }
        if (text.length() > MAX_BYTES) {
            return fail("\u5907\u4EFD\u6587\u4EF6\u8FC7\u5927\uFF0C\u5DF2\u62D2\u7EDD"
                    + "\u8BFB\u53D6");
        }
        Object parsed = MiniJson.parse(text);
        if (parsed == null) {
            return fail("\u6587\u4EF6\u4E0D\u662F\u5408\u6CD5\u7684 JSON \u6587\u672C"
                    + "\uFF0C\u53EF\u80FD\u5DF2\u635F\u574F");
        }
        if (!(parsed instanceof Map)) {
            return fail("\u5907\u4EFD\u6587\u4EF6\u7684\u9876\u5C42\u5E94\u5F53\u662F"
                    + "\u4E00\u4E2A JSON \u5BF9\u8C61");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        Object format = root.get("format");
        if (!(format instanceof String) || !FORMAT.equals(format)) {
            return fail("\u8FD9\u4E0D\u662F\u300C\u62FE\u8BFE\u300D\u5BFC\u51FA\u7684"
                    + "\u5907\u4EFD\u6587\u4EF6");
        }

        List<String> notes = new ArrayList<>();
        int version = FORMAT_VERSION;
        if (!root.containsKey("formatVersion")) {
            notes.add("\u5907\u4EFD\u6587\u4EF6\u672A\u6807\u6CE8\u683C\u5F0F\u7248"
                    + "\u672C\uFF0C\u5DF2\u6309\u7248\u672C 1 \u8BFB\u53D6");
        } else {
            Long parsedVersion = asLong(root.get("formatVersion"));
            if (parsedVersion == null) {
                return fail("\u65E0\u6CD5\u8BC6\u522B\u5907\u4EFD\u6587\u4EF6\u7684"
                        + "\u683C\u5F0F\u7248\u672C");
            }
            if (parsedVersion.longValue() < 1L) {
                notes.add("\u5907\u4EFD\u6587\u4EF6\u7684\u683C\u5F0F\u7248\u672C\u4E0D"
                        + "\u5408\u6CD5\uFF0C\u5DF2\u6309\u7248\u672C 1 \u8BFB\u53D6");
            } else {
                version = parsedVersion.intValue();
            }
        }
        if (version > MAX_SUPPORTED_VERSION) {
            return fail("\u5907\u4EFD\u6587\u4EF6\u7684\u683C\u5F0F\u7248\u672C\uFF08"
                    + version + "\uFF09\u9AD8\u4E8E\u5F53\u524D\u5E94\u7528\u652F\u6301"
                    + "\u7684\u7248\u672C\uFF08" + MAX_SUPPORTED_VERSION
                    + "\uFF09\uFF0C\u8BF7\u5148\u5347\u7EA7\u300C\u62FE\u8BFE\u300D");
        }

        List<Semester> semesters = new ArrayList<>();
        Map<Long, List<Course>> coursesBySemester = new LinkedHashMap<>();
        String problem = readSemesters(root, semesters, coursesBySemester, notes);
        if (problem != null) {
            return fail(problem);
        }

        List<BellSchedule> schedules = new ArrayList<>();
        problem = readSchedules(root, schedules);
        if (problem != null) {
            return fail(problem);
        }

        long activeSemesterId = 0L;
        if (root.containsKey("activeSemesterId")) {
            Long parsedActive = asLong(root.get("activeSemesterId"));
            if (parsedActive != null
                    && findSemester(semesters, parsedActive.longValue()) != null) {
                activeSemesterId = parsedActive.longValue();
            } else {
                notes.add("\u5907\u4EFD\u4E2D\u7684\u5F53\u524D\u5B66\u671F\u65E0\u6548"
                        + "\uFF0C\u5C06\u4EE5\u7B2C\u4E00\u4E2A\u5B66\u671F\u4E3A\u51C6");
            }
        }
        if (activeSemesterId == 0L) {
            activeSemesterId = semesters.get(0).id();
        }

        int cleared = clearDanglingScheduleNames(coursesBySemester, schedules);
        if (cleared > 0) {
            notes.add(cleared + " \u95E8\u8BFE\u7A0B\u6307\u5411\u7684\u4F5C\u606F\u8868"
                    + "\u4E0D\u5728\u5907\u4EFD\u4E2D\uFF0C\u5C06\u6539\u4E3A\u6309\u5730"
                    + "\u70B9\u81EA\u52A8\u5339\u914D");
        }

        String defaultScheduleId = readDefaultScheduleId(root, schedules, notes);

        Document document = new Document(version, asString(root.get("exportedAt")),
                asString(root.get("appVersionName")), activeSemesterId, semesters,
                coursesBySemester, schedules, defaultScheduleId, notes);
        return new Result(document, new ArrayList<String>());
    }

    /**
     * The file's chosen default, or null when it does not say or cannot be honoured.
     *
     * <p>A file that names a schedule it does not carry is not refused: the pointer
     * is a preference, not a piece of data, and the courses behind it are still
     * there. Clearing it here means the restore step falls back to the same
     * timetable the exporting build used, and the user keeps their work with one
     * setting to re-pick rather than a file that will not open.</p>
     */
    private static String readDefaultScheduleId(Map<String, Object> root,
            List<BellSchedule> schedules, List<String> notes) {
        Object raw = root.get("defaultScheduleId");
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            notes.add("\u5907\u4EFD\u4E2D\u7684\u9ED8\u8BA4\u4F5C\u606F\u8868\u6807\u8BC6"
                    + "\u4E0D\u5408\u6CD5\uFF0C\u5C06\u6539\u7528\u6309\u5730\u70B9\u5339"
                    + "\u914D\u7684\u9ED8\u8BA4\u8868");
            return null;
        }
        String wanted = ((String) raw).trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (BellSchedule schedule : schedules) {
            if (wanted.equals(schedule.id())) {
                return wanted;
            }
        }
        notes.add("\u5907\u4EFD\u4E2D\u7684\u9ED8\u8BA4\u4F5C\u606F\u8868\u4E0D\u5728"
                + "\u4F5C\u606F\u8868\u5217\u8868\u4E2D\uFF0C\u6062\u590D\u540E\u5C06"
                + "\u91CD\u65B0\u9009\u62E9");
        return null;
    }

    /**
     * Reads a backup from a stream and checks it, refusing anything past
     * {@code limit} bytes.
     *
     * <p>The limit is applied while reading, so a file picked by mistake is never
     * held in memory in full. The failure surfaces as an {@link IOException} whose
     * message is already fit to show the user.</p>
     */
    public static Result read(InputStream input, int limit) throws IOException {
        if (input == null) {
            throw new IOException("\u65E0\u6CD5\u6253\u5F00\u6240\u9009\u6587\u4EF6");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(chunk)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("\u5907\u4EFD\u6587\u4EF6\u8FC7\u5927\uFF0C"
                        + "\u5DF2\u62D2\u7EDD\u8BFB\u53D6");
            }
            buffer.write(chunk, 0, read);
        }
        return parse(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    }

    /** Reads and checks the semester list and every course inside it. */
    private static String readSemesters(Map<String, Object> root,
            List<Semester> out, Map<Long, List<Course>> coursesBySemester,
            List<String> notes) {
        Object raw = root.get("semesters");
        if (!(raw instanceof List)) {
            return "\u5907\u4EFD\u6587\u4EF6\u7F3A\u5C11 semesters \u5B66\u671F\u5217\u8868";
        }
        List<?> entries = (List<?>) raw;
        if (entries.isEmpty()) {
            return "\u5907\u4EFD\u6587\u4EF6\u91CC\u6CA1\u6709\u4EFB\u4F55\u5B66\u671F";
        }
        if (entries.size() > MAX_SEMESTERS) {
            return "\u5907\u4EFD\u6587\u4EF6\u91CC\u7684\u5B66\u671F\u6570\u91CF\u4E0D"
                    + "\u5408\u7406";
        }
        Set<Long> seenIds = new LinkedHashSet<>();
        int unnamed = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map)) {
                return "\u7B2C " + (i + 1) + " \u4E2A\u5B66\u671F\u4E0D\u662F\u4E00\u4E2A"
                        + " JSON \u5BF9\u8C61";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) entries.get(i);
            Long id = asLong(item.get("id"));
            if (id == null || id.longValue() <= 0L) {
                return "\u7B2C " + (i + 1) + " \u4E2A\u5B66\u671F\u7F3A\u5C11\u6709\u6548"
                        + "\u7684 id";
            }
            if (!seenIds.add(id)) {
                return "\u5907\u4EFD\u6587\u4EF6\u91CC\u6709\u91CD\u590D\u7684\u5B66\u671F"
                        + "\u6807\u8BC6\uFF08id=" + id + "\uFF09";
            }
            Object rawStart = item.get("start");
            if (!(rawStart instanceof String)) {
                return "\u7B2C " + (i + 1) + " \u4E2A\u5B66\u671F\u7F3A\u5C11\u5F00\u5B66"
                        + "\u65E5\u671F";
            }
            LocalDate start = ScheduleUtils.parseDate((String) rawStart);
            if (start == null) {
                return "\u7B2C " + (i + 1) + " \u4E2A\u5B66\u671F\u7684\u5F00\u5B66\u65E5"
                        + "\u671F\u65E0\u6CD5\u8BC6\u522B\uFF1A" + rawStart;
            }
            String name = asString(item.get("name"));
            out.add(new Semester(id.longValue(), start, name));
            if (name.isEmpty()) {
                unnamed++;
            }

            List<Course> courses = new ArrayList<>();
            String courseProblem = readCourses(item.get("courses"), courses,
                    i + 1, notes);
            if (courseProblem != null) {
                return courseProblem;
            }
            coursesBySemester.put(id, courses);
        }
        if (unnamed > 0) {
            notes.add(unnamed + " \u4E2A\u5B66\u671F\u6CA1\u6709\u81EA\u5B9A\u4E49\u540D"
                    + "\u79F0\uFF0C\u5C06\u6309\u65E5\u671F\u81EA\u52A8\u547D\u540D");
        }
        return null;
    }

    /** Reads and checks one semester's course array. */
    private static String readCourses(Object raw, List<Course> out,
            int semesterNumber, List<String> notes) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List)) {
            return "\u7B2C " + semesterNumber + " \u4E2A\u5B66\u671F\u7684 courses "
                    + "\u4E0D\u662F\u4E00\u4E2A\u6570\u7EC4";
        }
        List<?> entries = (List<?>) raw;
        if (entries.size() > MAX_COURSES_PER_SEMESTER) {
            return "\u7B2C " + semesterNumber + " \u4E2A\u5B66\u671F\u7684\u8BFE\u7A0B"
                    + "\u6570\u91CF\u4E0D\u5408\u7406";
        }
        int reassigned = 0;
        long fallbackId = 1L;
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map)) {
                return "\u7B2C " + semesterNumber + " \u4E2A\u5B66\u671F\u7684\u7B2C "
                        + (i + 1) + " \u95E8\u8BFE\u7A0B\u4E0D\u662F\u4E00\u4E2A JSON "
                        + "\u5BF9\u8C61";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) entries.get(i);
            int where = i + 1;

            Integer day = rangeOf(item, "day", 1, 7);
            if (day == null) {
                return fieldProblem(semesterNumber, where, "\u661F\u671F",
                        "day", "1 \u5230 7");
            }
            // Bounded by the app's ceiling rather than by the default fifteen: a
            // backup of a course on period 18 is a valid file once a schedule can
            // have more than fifteen periods, and refusing it would make the backup
            // unreadable by the very build that wrote it.
            Integer startPeriod = rangeOf(item, "startPeriod", 1,
                    ScheduleUtils.MAX_PERIODS);
            if (startPeriod == null) {
                return fieldProblem(semesterNumber, where, "\u5F00\u59CB\u8282\u6B21",
                        "startPeriod", "1 \u5230 " + ScheduleUtils.MAX_PERIODS);
            }
            Integer endPeriod = rangeOf(item, "endPeriod", 1,
                    ScheduleUtils.MAX_PERIODS);
            if (endPeriod == null || endPeriod.intValue() < startPeriod.intValue()) {
                return fieldProblem(semesterNumber, where, "\u7ED3\u675F\u8282\u6B21",
                        "endPeriod", startPeriod + " \u5230 "
                                + ScheduleUtils.MAX_PERIODS);
            }
            Integer startWeek = rangeOf(item, "startWeek", 1,
                    ScheduleUtils.SEMESTER_WEEKS);
            if (startWeek == null) {
                return fieldProblem(semesterNumber, where, "\u5F00\u59CB\u5468",
                        "startWeek", "1 \u5230 " + ScheduleUtils.SEMESTER_WEEKS);
            }
            Integer endWeek = rangeOf(item, "endWeek", 1, ScheduleUtils.SEMESTER_WEEKS);
            if (endWeek == null || endWeek.intValue() < startWeek.intValue()) {
                return fieldProblem(semesterNumber, where, "\u7ED3\u675F\u5468",
                        "endWeek", startWeek + " \u5230 " + ScheduleUtils.SEMESTER_WEEKS);
            }
            Integer parity = rangeOf(item, "parity", Course.PARITY_ALL,
                    Course.PARITY_EVEN);
            if (parity == null) {
                return fieldProblem(semesterNumber, where, "\u5355\u53CC\u5468",
                        "parity", "0\uFF08\u6BCF\u5468\uFF09\u30011\uFF08\u5355\u5468"
                                + "\uFF09\u6216 2\uFF08\u53CC\u5468\uFF09");
            }

            Course course = new Course();
            course.name = asString(item.get("name"));
            course.teacher = asString(item.get("teacher"));
            course.location = asString(item.get("location"));
            course.scheduleName = asString(item.get("scheduleName")).trim();
            course.dayOfWeek = day.intValue();
            course.startPeriod = startPeriod.intValue();
            course.endPeriod = endPeriod.intValue();
            course.startWeek = startWeek.intValue();
            course.endWeek = endWeek.intValue();
            course.parity = parity.intValue();
            Long color = asLong(item.get("color"));
            course.color = color == null
                    ? Course.DEFAULT_COLOR : (int) color.longValue();

            Long id = asLong(item.get("id"));
            course.id = id == null ? 0L : id.longValue();
            if (course.id == 0L || containsId(out, course.id)) {
                // A missing or duplicated id is repaired rather than refused: the
                // courses stay distinct, only the label they are addressed by
                // changes. Failing the whole file over a repairable label would
                // leave the user with no way back.
                course.id = CourseList.nextId(out, fallbackId);
                fallbackId = course.id + 1L;
                reassigned++;
            }
            out.add(course);
        }
        if (reassigned > 0) {
            notes.add(reassigned + " \u95E8\u8BFE\u7A0B\u7F3A\u5C11\u6216\u91CD\u590D\u4E86"
                    + "\u6807\u8BC6\uFF0C\u5C06\u5728\u6062\u590D\u65F6\u91CD\u65B0\u5206\u914D");
        }
        return null;
    }

    /** Reads and checks the bell schedule array. */
    private static String readSchedules(Map<String, Object> root,
            List<BellSchedule> out) {
        Object raw = root.get("schedules");
        if (!(raw instanceof List)) {
            return "\u5907\u4EFD\u6587\u4EF6\u7F3A\u5C11 schedules \u4F5C\u606F\u8868\u5217\u8868";
        }
        List<?> entries = (List<?>) raw;
        if (entries.isEmpty()) {
            return "\u5907\u4EFD\u6587\u4EF6\u91CC\u6CA1\u6709\u4EFB\u4F55\u4F5C\u606F\u8868";
        }
        if (entries.size() > MAX_SCHEDULES) {
            return "\u5907\u4EFD\u6587\u4EF6\u91CC\u7684\u4F5C\u606F\u8868\u6570\u91CF"
                    + "\u4E0D\u5408\u7406";
        }
        Set<String> seenNames = new LinkedHashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map)) {
                return "\u7B2C " + (i + 1) + " \u5F20\u4F5C\u606F\u8868\u4E0D\u662F\u4E00"
                        + "\u4E2A JSON \u5BF9\u8C61";
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) entries.get(i);
            String name = asString(item.get("name")).trim();
            if (name.isEmpty()) {
                return "\u7B2C " + (i + 1) + " \u5F20\u4F5C\u606F\u8868\u6CA1\u6709\u540D\u5B57";
            }
            if (!seenNames.add(name)) {
                return "\u5907\u4EFD\u6587\u4EF6\u91CC\u6709\u540C\u540D\u7684\u4F5C\u606F"
                        + "\u8868\uFF1A" + name;
            }

            List<String> keywords = new ArrayList<>();
            Object rawKeywords = item.get("keywords");
            if (rawKeywords != null) {
                if (!(rawKeywords instanceof List)) {
                    return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u7684 keywords "
                            + "\u4E0D\u662F\u4E00\u4E2A\u6570\u7EC4";
                }
                for (Object keyword : (List<?>) rawKeywords) {
                    if (keyword == null) {
                        continue;
                    }
                    if (!(keyword instanceof String)) {
                        return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u7684\u5730\u70B9"
                                + "\u5173\u952E\u8BCD\u4E0D\u662F\u6587\u672C";
                    }
                    String trimmed = ((String) keyword).trim();
                    if (!trimmed.isEmpty()) {
                        keywords.add(trimmed);
                    }
                }
            }

            int[] starts = readTimes(item.get("starts"));
            if (starts == null) {
                return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u7684\u5F00\u59CB\u65F6"
                        + "\u95F4\u4E0D\u5408\u6CD5";
            }
            int[] ends = readTimes(item.get("ends"));
            if (ends == null) {
                return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u7684\u7ED3\u675F\u65F6"
                        + "\u95F4\u4E0D\u5408\u6CD5";
            }
            if (starts.length != ends.length) {
                return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u7684\u5F00\u59CB\u4E0E"
                        + "\u7ED3\u675F\u65F6\u95F4\u6570\u91CF\u4E0D\u4E00\u81F4";
            }
            // The count is whatever the backup holds, bounded by the app's ceiling
            // rather than by the default fifteen. A backup of a twenty-period day
            // used to be rejected outright as malformed, which turned a valid file
            // into an unreadable one -- the opposite of what a backup is for. A
            // backup from a future build with more periods than this app supports is
            // still refused, and says so.
            if (starts.length < BellSchedule.MIN_PERIODS) {
                return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u81F3\u5C11\u9700\u8981 "
                        + BellSchedule.MIN_PERIODS + " \u8282";
            }
            if (starts.length > ScheduleUtils.MAX_PERIODS) {
                return "\u4F5C\u606F\u8868\u300C" + name + "\u300D\u5305\u542B "
                        + starts.length + " \u8282\uFF0C\u8D85\u51FA\u672C\u7248\u652F\u6301"
                        + "\u7684 " + ScheduleUtils.MAX_PERIODS + " \u8282";
            }
            // Absent in every backup made before the length was settable, and forty
            // is what those tables were saved with.
            int durationMinutes = MiniJson.optInt(item, "durationMinutes",
                    BellSchedule.DEFAULT_PERIOD_MINUTES);
            // Absent in every backup made before ids existed; null here means the
            // next read invents one, and the default pointer is resolved against
            // whatever ids the document ends up with.
            String id = MiniJson.optString(item, "id", "").trim();
            out.add(new BellSchedule(id.isEmpty() ? null : id, name, starts, ends,
                    keywords, durationMinutes));
        }
        return null;
    }

    /** One {@code starts}/{@code ends} array, or null when it is not usable. */
    private static int[] readTimes(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<?> entries = (List<?>) raw;
        int[] values = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Long minutes = asLong(entries.get(i));
            if (minutes == null || minutes.longValue() < 0L
                    || minutes.longValue() > BellSchedule.MAX_MINUTE) {
                return null;
            }
            values[i] = minutes.intValue();
        }
        return values;
    }

    /** Clears schedule references the document does not carry, and counts them. */
    private static int clearDanglingScheduleNames(
            Map<Long, List<Course>> coursesBySemester, List<BellSchedule> schedules) {
        Set<String> names = new LinkedHashSet<>();
        for (BellSchedule schedule : schedules) {
            names.add(schedule.name());
        }
        int cleared = 0;
        for (List<Course> courses : coursesBySemester.values()) {
            for (Course course : courses) {
                if (!course.scheduleName.isEmpty()
                        && !names.contains(course.scheduleName)) {
                    course.scheduleName = "";
                    cleared++;
                }
            }
        }
        return cleared;
    }

    // ----------------------------------------------------------------- helpers

    /** A whole-number field inside a range, or null when it is unusable. */
    private static Integer rangeOf(Map<String, Object> item, String key,
            int minimum, int maximum) {
        Long value = asLong(item.get(key));
        if (value == null) {
            return null;
        }
        long number = value.longValue();
        return number < minimum || number > maximum
                ? null : Integer.valueOf((int) number);
    }

    private static String fieldProblem(int semesterNumber, int courseNumber,
            String label, String field, String allowed) {
        return "\u7B2C " + semesterNumber + " \u4E2A\u5B66\u671F\u7684\u7B2C "
                + courseNumber + " \u95E8\u8BFE\u7A0B\u7684" + label + "\uFF08" + field
                + "\uFF09\u65E0\u6548\uFF0C\u5E94\u5F53\u662F " + allowed;
    }

    private static boolean containsId(List<Course> courses, long id) {
        for (Course course : courses) {
            if (course.id == id) {
                return true;
            }
        }
        return false;
    }

    private static Semester findSemester(List<Semester> semesters, long id) {
        for (Semester semester : semesters) {
            if (semester.id() == id) {
                return semester;
            }
        }
        return null;
    }

    /** A whole number out of JSON, whether it arrived as a number or as text. */
    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (value instanceof String) {
            try {
                return Long.valueOf(Long.parseLong(((String) value).trim()));
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Result fail(String problem) {
        List<String> problems = new ArrayList<>();
        problems.add(problem);
        return new Result(null, problems);
    }
}
