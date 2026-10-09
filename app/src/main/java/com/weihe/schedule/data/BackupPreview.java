package com.weihe.schedule.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.weihe.schedule.model.Course;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * What a checked backup would put on screen, worked out without touching
 * anything.
 *
 * <p>Everything here is derived from a {@link BackupCodec.Document} -- a value
 * that came out of a file, not out of storage. The class therefore holds no
 * repository, writes nothing, and can be exercised on a plain JVM, which is
 * where the numbers and the wording are actually verified.</p>
 *
 * <p>The distinction the screen has to make is between "this is what the file
 * contains" and "this is what your timetable is". Nothing in this class reads
 * the current timetable, so it cannot blur the two.</p>
 */
public final class BackupPreview {

    /** One line of the semester breakdown. */
    public static final class SemesterSummary {
        private final String label;
        private final String dateRange;
        private final int courseCount;

        private SemesterSummary(String label, String dateRange, int courseCount) {
            this.label = label;
            this.dateRange = dateRange;
            this.courseCount = courseCount;
        }

        /** The semester's own name, or the label derived from its date. */
        public String label() {
            return label;
        }

        /** Week 1 to the last day, formatted for reading. */
        public String dateRange() {
            return dateRange;
        }

        public int courseCount() {
            return courseCount;
        }
    }

    private final String fileName;
    private final int formatVersion;
    private final String exportedAt;
    private final String appVersionName;
    private final List<SemesterSummary> semesters;
    private final int courseCount;
    private final int scheduleCount;
    private final int parityCourseCount;
    private final int namedSemesterCount;
    private final int keywordCount;
    private final List<String> notes;

    private BackupPreview(String fileName, int formatVersion, String exportedAt,
            String appVersionName, List<SemesterSummary> semesters, int courseCount,
            int scheduleCount, int parityCourseCount, int namedSemesterCount,
            int keywordCount, List<String> notes) {
        this.fileName = fileName;
        this.formatVersion = formatVersion;
        this.exportedAt = exportedAt;
        this.appVersionName = appVersionName;
        this.semesters = semesters;
        this.courseCount = courseCount;
        this.scheduleCount = scheduleCount;
        this.parityCourseCount = parityCourseCount;
        this.namedSemesterCount = namedSemesterCount;
        this.keywordCount = keywordCount;
        this.notes = notes;
    }

    /** Builds the summary of an already-checked document. */
    public static BackupPreview of(String fileName, BackupCodec.Document document) {
        List<SemesterSummary> rows = new ArrayList<>();
        int parity = 0;
        int named = 0;
        for (Semester semester : document.semesters()) {
            List<Course> courses = document.coursesOf(semester.id());
            for (Course course : courses) {
                if (course.parity != Course.PARITY_ALL) {
                    parity++;
                }
            }
            if (semester.hasCustomName()) {
                named++;
            }
            rows.add(new SemesterSummary(semester.label(),
                    ScheduleUtils.formatDate(semester.start()) + " - "
                            + ScheduleUtils.formatDate(semester.lastDay()),
                    courses.size()));
        }
        int keywords = 0;
        for (int i = 0; i < document.scheduleCount(); i++) {
            keywords += document.schedules().get(i).locationKeywords().size();
        }
        return new BackupPreview(fileName == null ? "" : fileName,
                document.formatVersion(), document.exportedAt(),
                document.appVersionName(), rows, document.courseCount(),
                document.scheduleCount(), parity, named, keywords,
                new ArrayList<>(document.notes()));
    }

    /** The name the picker showed, or {@code ""} when it could not be read. */
    public String fileName() {
        return fileName;
    }

    /** The {@code formatVersion} the file stated. */
    public int formatVersion() {
        return formatVersion;
    }

    /** When the file was written, or {@code ""} when it did not say. */
    public String exportedAt() {
        return exportedAt;
    }

    /**
     * Whether the file stated when it was written.
     *
     * <p>False means the screen has to say the time is unknown. Substituting the
     * current time, or the file's modification time, would be inventing a fact
     * about somebody else's file.</p>
     */
    public boolean hasExportedAt() {
        return !exportedAt.trim().isEmpty();
    }

    /** The writing app's version name, or {@code ""} when it did not say. */
    public String appVersionName() {
        return appVersionName;
    }

    public boolean hasAppVersionName() {
        return !appVersionName.trim().isEmpty();
    }

    /** One row per semester, in the order the file lists them. */
    public List<SemesterSummary> semesters() {
        return Collections.unmodifiableList(semesters);
    }

    public int semesterCount() {
        return semesters.size();
    }

    public int courseCount() {
        return courseCount;
    }

    public int scheduleCount() {
        return scheduleCount;
    }

    /** How many courses are limited to odd or even weeks. */
    public int parityCourseCount() {
        return parityCourseCount;
    }

    /** How many semesters carry a name the user typed rather than a derived one. */
    public int namedSemesterCount() {
        return namedSemesterCount;
    }

    /** How many venue keywords the schedules carry between them. */
    public int keywordCount() {
        return keywordCount;
    }

    /**
     * What the reader had to repair. Non-empty means the file can be restored but
     * not exactly as written, so the screen has to show these before anything is
     * applied.
     */
    public List<String> notes() {
        return Collections.unmodifiableList(notes);
    }

    public boolean needsRepair() {
        return !notes.isEmpty();
    }

    /** {@code 2 个学期 · 23 门课程 · 3 张作息表}, for a toast or a heading. */
    public String summary() {
        return semesterCount() + " \u4E2A\u5B66\u671F \u00B7 " + courseCount
                + " \u95E8\u8BFE\u7A0B \u00B7 " + scheduleCount + " \u5F20\u4F5C\u606F\u8868";
    }
}
