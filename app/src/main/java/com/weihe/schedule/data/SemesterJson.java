package com.weihe.schedule.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.weihe.schedule.model.ScheduleUtils;
import com.weihe.schedule.model.Semester;

/**
 * Maps semesters to and from the JSON stored in {@code SharedPreferences}.
 *
 * <p>The stored schema:</p>
 *
 * <pre>{@code
 * [ { "id": 1757000000000, "start": "2026-08-31", "name": "", "weeks": 18 } ]
 * }</pre>
 *
 * <p>Reading is forgiving in the same way the rest of the storage layer is: an
 * entry without a usable id or start date is skipped rather than throwing, and
 * the caller supplies the fallback start date so a damaged payload still yields
 * a working semester.</p>
 *
 * <p>{@code weeks} is optional and written only when it differs from the default
 * count. A term that was never given a length of its own therefore stores exactly
 * what it stored before this key existed, and an older build reading the payload
 * sees the shape it always saw. A missing, non-numeric or out-of-range value
 * reads back as the default rather than as a broken term.</p>
 */
public final class SemesterJson {

    private SemesterJson() {
    }

    /** The semesters in {@code raw}, or an empty list when nothing is readable. */
    public static List<Semester> parse(String raw) {
        List<Semester> semesters = new ArrayList<>();
        for (Object entry : MiniJson.parseArray(raw)) {
            if (!(entry instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) entry;
            long id = MiniJson.optLong(object, "id", 0L);
            LocalDate start = ScheduleUtils.parseDate(
                    MiniJson.optString(object, "start", ""));
            if (id == 0L || start == null) {
                continue;
            }
            // A count outside the allowed range means a damaged or hand-edited
            // payload, not a term the user asked for, so it is repaired to the
            // default rather than clamped: an 18 that used to read 900 is less
            // surprising than a silent 18 that used to read 17.
            int weeks = ScheduleUtils.SEMESTER_WEEKS;
            if (object.containsKey("weeks")) {
                int stored = MiniJson.optInt(object, "weeks", 0);
                if (stored >= Semester.MIN_WEEKS && stored <= Semester.MAX_WEEKS) {
                    weeks = stored;
                }
            }
            semesters.add(new Semester(id, start,
                    MiniJson.optString(object, "name", ""), weeks));
        }
        return semesters;
    }

    /** The stored form of {@code semesters}. */
    public static String write(List<Semester> semesters) {
        List<Object> array = MiniJson.array();
        if (semesters != null) {
            for (Semester semester : semesters) {
                if (semester == null) {
                    continue;
                }
                Map<String, Object> object = MiniJson.object();
                object.put("id", Long.valueOf(semester.id()));
                object.put("start", semester.start().toString());
                object.put("name", semester.name());
                if (semester.weeks() != ScheduleUtils.SEMESTER_WEEKS) {
                    // Only when it says something new. A stored default would be
                    // a second copy of a constant that already has a home.
                    object.put("weeks", Integer.valueOf(semester.weeks()));
                }
                array.add(object);
            }
        }
        return MiniJson.write(array);
    }
}
