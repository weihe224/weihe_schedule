package com.weihe.schedule.data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The raw payload a course list was kept as, when it could not be read.
 *
 * <p>{@link CourseRepository} files a payload under {@code courses_backup} before
 * the next save can overwrite it. Until now nothing could read it back, so the
 * only way to reach it was to pull the preferences file off the device by hand --
 * a safety net with no way to reach it is not much of one.</p>
 *
 * <h2>Existence is not the same question as readability</h2>
 *
 * <p>A copy is reported as {@link State#PRESENT} when the slot holds text, not
 * when that text can be understood. The payload is by definition something the
 * reader could not make sense of, so deciding existence by parsing would answer
 * "there is nothing here" for exactly the case this class exists to serve.</p>
 *
 * <h2>Nothing here repairs, rewrites or restores</h2>
 *
 * <p>The text is carried and written out unchanged. It is never fed back to the
 * course reader, never merged into a timetable and never re-serialised: a copy
 * that does not parse stays a copy that does not parse, and the file the user
 * gets holds the same characters that were kept.</p>
 */
public final class DamagedCourseCopy {

    /** What the damaged-payload slot currently holds. */
    public enum State {
        /** Nothing is filed: no course list has ever failed to read. */
        ABSENT,
        /** The slot holds an empty string, so there is nothing to export. */
        EMPTY,
        /** The slot holds the raw text that failed to read. */
        PRESENT,
        /** The slot holds something that is not text at all. */
        UNREADABLE
    }

    private static final DamagedCourseCopy NONE =
            new DamagedCourseCopy(State.ABSENT, null);
    private static final DamagedCourseCopy EMPTY_COPY =
            new DamagedCourseCopy(State.EMPTY, "");
    private static final DamagedCourseCopy UNREADABLE_COPY =
            new DamagedCourseCopy(State.UNREADABLE, null);

    private final State state;
    private final String raw;

    private DamagedCourseCopy(State state, String raw) {
        this.state = state;
        this.raw = raw;
    }

    /** No copy is filed. */
    public static DamagedCourseCopy absent() {
        return NONE;
    }

    /** The slot holds an empty string. */
    public static DamagedCourseCopy empty() {
        return EMPTY_COPY;
    }

    /** The slot holds something that is not text. */
    public static DamagedCourseCopy unreadable() {
        return UNREADABLE_COPY;
    }

    /**
     * The copy for whatever the slot held.
     *
     * @param raw the stored value, or {@code null} when the slot does not exist
     */
    public static DamagedCourseCopy of(String raw) {
        if (raw == null) {
            return NONE;
        }
        return raw.isEmpty() ? EMPTY_COPY : new DamagedCourseCopy(State.PRESENT, raw);
    }

    public State state() {
        return state;
    }

    /** Whether anything is filed at all, readable or not. */
    public boolean exists() {
        return state != State.ABSENT;
    }

    /** Whether there is text worth exporting. */
    public boolean isExportable() {
        return state == State.PRESENT;
    }

    /**
     * The text exactly as it was kept, or {@code null} when there is none.
     *
     * <p>Returned as stored rather than as parsed: the user is exporting the
     * evidence, so anything this class did to tidy it up would be a lie about what
     * the store actually held.</p>
     */
    public String raw() {
        return raw;
    }

    /** How many characters the kept text has; 0 when there is none. */
    public int length() {
        return raw == null ? 0 : raw.length();
    }

    /**
     * Writes the copy out unchanged, as UTF-8 with no byte-order mark.
     *
     * <p>UTF-8 because that is what the preferences file itself is read as, so the
     * characters survive the round trip; no BOM because the payload is not a format
     * this app defines and a marker it did not put there would be an invention.
     * Nothing is appended, wrapped or escaped.</p>
     *
     * <p>The stream is flushed but not closed: the caller owns it, so a failure to
     * close is reported as a failure to write rather than being hidden here.</p>
     *
     * @throws IOException when there is nothing to write, or the stream refuses
     */
    public void writeTo(OutputStream out) throws IOException {
        if (!isExportable()) {
            throw new IOException("\u6CA1\u6709\u53EF\u5BFC\u51FA\u7684\u5185\u5BB9");
        }
        if (out == null) {
            throw new IOException("\u65E0\u6CD5\u5199\u5165\u6240\u9009\u4F4D\u7F6E");
        }
        out.write(raw.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public String toString() {
        return state + (raw == null ? "" : "(" + raw.length() + " chars)");
    }
}
