package com.weihe.schedule.data;

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
 *   { "id": "schedule-bc",           // optional; absent means "not assigned yet"
 *     "name": "标准作息",
 *     "keywords": ["实验楼"],
 *     "durationMinutes": 40,       // optional; absent means 40
 *     "starts": [480, 535, ...],   // minutes from midnight, period 1 first
 *     "ends":   [525, 580, ...] }
 * ]
 * }</pre>
 *
 * <p>The number of periods is the length of the {@code starts} array and nothing
 * else. A schedule with twenty entries is a twenty-period day and is read back as
 * one; there is no fifteen anywhere in this file, and a table saved with fewer
 * periods than the default is not padded up to it.</p>
 *
 * <p>{@code durationMinutes} was added after the first release. It is written
 * unconditionally now, and a payload without it -- every schedule stored before
 * the setting existed, and every backup made then -- reads back as
 * {@link BellSchedule#DEFAULT_PERIOD_MINUTES}. That is what keeps an old install's
 * tables on exactly the clock they were saved with.</p>
 *
 * <p>{@code id} is written only when the schedule has one, and a payload without it
 * reads back with a {@code null} id rather than an invented one. The two are
 * deliberately different: this class cannot know whether an id it made up would
 * collide with a sibling's, and the answer decides what the default pointer is
 * allowed to name. Filling ids in belongs where the whole list is in hand --
 * {@link BellSchedules#assignMissingIds} -- which is also where a store that needs
 * them decides to write them back. Writing an absent id as an empty string would
 * be worse than leaving it out: it would look like an assigned id that matches
 * nothing.</p>
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
        // Left out entirely rather than written as "", see the class comment: an
        // empty string reads back as an assigned id, which then matches nothing.
        if (schedule.id() != null) {
            object.put("id", schedule.id());
        }
        object.put("name", schedule.name());
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

    private static BellSchedule fromJson(Map<String, Object> object) {
        String id = MiniJson.optString(object, "id", "").trim();
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

        // Absent for every schedule stored before the setting existed, and that is
        // the point of the fallback: forty is what those tables were saved with, so
        // an upgrade reads them back unchanged instead of marking them as having
        // chosen something they never chose.
        int durationMinutes = MiniJson.optInt(object, "durationMinutes",
                BellSchedule.DEFAULT_PERIOD_MINUTES);

        int[] starts = readTimes(MiniJson.optArray(object, "starts"));
        int[] ends = readTimes(MiniJson.optArray(object, "ends"));
        if (starts.length == 0 || starts.length != ends.length) {
            // Too damaged to repair from: the built-in standard table takes over,
            // keeping whatever name and keywords the payload did carry. The id is
            // kept for the same reason the name is -- moving it would silently
            // repoint the default at a table the user never picked.
            BellSchedule fallback = BellSchedules.standard().withLocationKeywords(keywords);
            BellSchedule repaired = name.isEmpty() ? fallback : fallback.renamed(name);
            return id.isEmpty() ? repaired : repaired.withId(id);
        }
        return new BellSchedule(id.isEmpty() ? null : id,
                name.isEmpty() ? "\u672A\u547D\u540D\u4F5C\u606F" : name,
                starts, ends, keywords, durationMinutes);
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
