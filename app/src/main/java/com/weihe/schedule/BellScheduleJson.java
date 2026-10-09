package com.weihe.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.weihe.schedule.model.BellSchedule;
import com.weihe.schedule.model.BellSchedules;

/**
 * Maps bell schedules to and from the JSON stored in {@code SharedPreferences}.
 *
 * <p>The storage schema, in full:</p>
 *
 * <pre>{@code
 * [
 *   { "name": "标准作息",
 *     "keywords": ["实验楼"],
 *     "starts": [480, 535, ...],   // minutes from midnight, period 1 first
 *     "ends":   [525, 580, ...] }
 * ]
 * }</pre>
 *
 * <p>Reading is deliberately forgiving, for the same reason
 * {@link CourseRepository} is: a damaged stored payload must not make the app
 * unusable. Anything unreadable is skipped, and a schedule whose time arrays do
 * not line up becomes the built-in standard timetable rather than a crash -- a
 * table with a shorter {@code starts} array than {@code ends} would otherwise
 * break every {@link BellSchedule#startMinute(int)} call that reaches the
 * missing index.</p>
 *
 * <p>Serialisation goes through {@link MiniJson} rather than {@code org.json},
 * which is what makes this class testable on a plain JVM.</p>
 */
public final class BellScheduleJson {

    private BellScheduleJson() {
    }

    /** The schedules in {@code raw}, or an empty list when nothing is readable. */
    public static List<BellSchedule> parse(String raw) {
        List<BellSchedule> schedules = new ArrayList<>();
        for (Object entry : MiniJson.parseArray(raw)) {
            if (entry instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) entry;
                schedules.add(fromJson(object));
            }
        }
        return schedules;
    }

    /** The stored form of {@code schedules}. */
    public static String write(List<BellSchedule> schedules) {
        List<Object> array = MiniJson.array();
        if (schedules != null) {
            for (BellSchedule schedule : schedules) {
                if (schedule != null) {
                    array.add(toJson(schedule));
                }
            }
        }
        return MiniJson.write(array);
    }

    private static Map<String, Object> toJson(BellSchedule schedule) {
        Map<String, Object> object = MiniJson.object();
        object.put("name", schedule.name());
        List<Object> keywords = MiniJson.array();
        keywords.addAll(schedule.locationKeywords());
        object.put("keywords", keywords);

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

    private static BellSchedule fromJson(Map<String, Object> object) {
        String name = MiniJson.optString(object, "name", "").trim();

        List<String> keywords = new ArrayList<>();
        for (Object entry : MiniJson.optArray(object, "keywords")) {
            if (entry instanceof String) {
                String keyword = ((String) entry).trim();
                if (!keyword.isEmpty()) {
                    keywords.add(keyword);
                }
            }
        }

        int[] starts = readTimes(MiniJson.optArray(object, "starts"));
        int[] ends = readTimes(MiniJson.optArray(object, "ends"));
        if (starts.length == 0 || starts.length != ends.length) {
            BellSchedule fallback = BellSchedules.standard();
            BellSchedule repaired = fallback.withLocationKeywords(keywords);
            return name.isEmpty() ? repaired : repaired.renamed(name);
        }
        return new BellSchedule(name.isEmpty()
                ? "\u672A\u547D\u540D\u4F5C\u606F" : name, starts, ends, keywords);
    }

    private static int[] readTimes(List<Object> array) {
        if (array.isEmpty()) {
            return new int[0];
        }
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            Object entry = array.get(i);
            if (!(entry instanceof Number)) {
                return new int[0];
            }
            int minutes = (int) ((Number) entry).longValue();
            // The bound has to be the one label() can produce, midnight-crossing
            // evening periods included, or a saved schedule would be repaired
            // away on the next read.
            if (minutes < 0 || minutes > BellSchedule.MAX_MINUTE) {
                return new int[0];
            }
            values[i] = minutes;
        }
        return values;
    }
}
