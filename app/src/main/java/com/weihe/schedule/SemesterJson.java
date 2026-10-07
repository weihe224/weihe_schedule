package com.weihe.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps semesters to and from the JSON stored in {@code SharedPreferences}.
 *
 * <p>The stored schema:</p>
 *
 * <pre>{@code
 * [ { "id": 1757000000000, "start": "2026-08-31", "name": "" } ]
 * }</pre>
 *
 * <p>Reading is forgiving in the same way the rest of the storage layer is: an
 * entry without a usable id or start date is skipped rather than throwing, and
 * the caller supplies the fallback start date so a damaged payload still yields
 * a working semester.</p>
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
            semesters.add(new Semester(id, start,
                    MiniJson.optString(object, "name", "")));
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
                array.add(object);
            }
        }
        return MiniJson.write(array);
    }
}
