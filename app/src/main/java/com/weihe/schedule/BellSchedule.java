package com.weihe.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A bell schedule: when every period starts and ends at one venue.
 *
 * <p>Different teaching buildings ring at different times, so "period 3" is not
 * one single moment across the timetable. A schedule holds the times for one
 * venue (or one campus) together with the location keywords that select it
 * automatically.</p>
 *
 * <p>Times are stored as minutes since midnight rather than as text: they sort,
 * compare and validate without any parsing, and are formatted only for the
 * screen. Like the rest of the framework-free layer, this class knows nothing
 * about Android and is covered by plain JVM tests.</p>
 */
public final class BellSchedule {

    /** Every schedule describes exactly this many periods. */
    public static final int PERIODS = ScheduleUtils.PERIODS_PER_DAY;

    /**
     * The last minute a period may be labelled with: 29:59.
     *
     * <p>{@link #label(int)} writes an evening period that runs past midnight as
     * {@code 24:05} rather than {@code 00:05}, so that "period 15" still belongs
     * to the day whose timetable it is. Every reader and writer of a time has to
     * share that bound -- a stored {@code 24:10} that {@link #parseLabel} refuses
     * would silently turn a saved schedule back into the standard one.</p>
     */
    public static final int MAX_MINUTE = 30 * 60 - 1;

    private final String name;
    private final int[] starts;
    private final int[] ends;
    private final List<String> locationKeywords;

    /**
     * @param starts start minute of each period, index 0 being period 1
     * @param ends   end minute of each period, index 0 being period 1
     * @param locationKeywords location text that selects this schedule; may be empty
     * @throws IllegalArgumentException when the two arrays do not match
     */
    public BellSchedule(String name, int[] starts, int[] ends,
            List<String> locationKeywords) {
        if (starts == null || ends == null || starts.length != ends.length) {
            throw new IllegalArgumentException(
                    "starts and ends must be non-null and equally long");
        }
        if (starts.length == 0) {
            throw new IllegalArgumentException("a schedule needs at least one period");
        }
        this.name = name == null ? "" : name;
        this.starts = starts.clone();
        this.ends = ends.clone();
        this.locationKeywords = locationKeywords == null
                ? Collections.<String>emptyList()
                : new ArrayList<>(locationKeywords);
    }

    public String name() {
        return name;
    }

    public int periodCount() {
        return starts.length;
    }

    public List<String> locationKeywords() {
        return Collections.unmodifiableList(locationKeywords);
    }

    /** Start of {@code period} (1-based), clamped into range. */
    public int startMinute(int period) {
        return starts[clampPeriod(period) - 1];
    }

    /** End of {@code period} (1-based), clamped into range. */
    public int endMinute(int period) {
        return ends[clampPeriod(period) - 1];
    }

    /**
     * Whether this schedule should be used for a venue.
     *
     * <p>Matching is deliberately loose about spacing and case. Venue text is
     * typed by hand, so "教学楼 C302", "教学楼C302" and "教学楼 C 302" all have to
     * find the same building; requiring an exact substring made a keyword like
     * "C楼" miss every one of them.</p>
     *
     * <p>A building keyword is accepted in two shapes, because the app's own
     * sample courses and its own placeholder text use the second one and a plain
     * substring search sent all of them to the fallback group:</p>
     *
     * <ul>
     *   <li>{@code D楼305} -- the letter and 楼 are adjacent;</li>
     *   <li>{@code 教学楼 D305} / {@code D305} / {@code 四教D305} -- the letter
     *       stands on its own and the room number follows it.</li>
     * </ul>
     *
     * <p>Only "<em>one ASCII letter</em> + 楼" keywords get the second reading, so
     * room keywords keep working exactly as before: {@code j301} is still a
     * substring test and never means "building J".</p>
     *
     * <p>A schedule with no keywords never matches, which is what lets the
     * generic standard schedule act purely as the fallback.</p>
     */
    public boolean matchesLocation(String location) {
        if (location == null) {
            return false;
        }
        String haystack = squeeze(location);
        if (haystack.isEmpty()) {
            return false;
        }
        for (String keyword : locationKeywords) {
            String needle = squeeze(keyword);
            if (needle.isEmpty()) {
                continue;
            }
            if (haystack.contains(needle)) {
                return true;
            }
            String letter = buildingLetter(needle);
            if (letter != null && namesBuilding(haystack, letter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The single letter of a {@code <letter>楼} keyword, or {@code null} when the
     * keyword is not of that shape.
     *
     * <p>Deliberately narrow. A two-character keyword ending in 楼 is a building;
     * anything else -- {@code j301}, {@code 实验楼}, {@code 东校区} -- is matched
     * as a substring only, so this rule cannot widen a room keyword into a whole
     * building.</p>
     */
    private static String buildingLetter(String squeezedKeyword) {
        if (squeezedKeyword.length() != 2
                || squeezedKeyword.charAt(1) != '\u697C') {
            return null;
        }
        char letter = squeezedKeyword.charAt(0);
        return letter >= 'a' && letter <= 'z' ? String.valueOf(letter) : null;
    }

    /**
     * Whether {@code haystack} names that building by putting the letter straight
     * in front of a room number.
     *
     * <p>"教学楼 D305" squeezes to {@code 教学楼d305}: the D is followed by a
     * digit. A venue that merely contains the letter -- {@code 东操场},
     * {@code 现代工业训练中心训2307} -- has no ASCII letter in front of a digit and
     * keeps falling through to the fallback group, which is where the school's
     * table puts "other teaching places".</p>
     */
    private static boolean namesBuilding(String haystack, String letter) {
        int from = 0;
        while (true) {
            int at = haystack.indexOf(letter, from);
            if (at < 0) {
                return false;
            }
            int next = at + letter.length();
            if (next < haystack.length()
                    && Character.isDigit(haystack.charAt(next))) {
                return true;
            }
            from = at + 1;
        }
    }

    /** Lower case with every space and separator removed. */
    private static String squeeze(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!Character.isWhitespace(character) && character != '-'
                    && character != '\u00B7' && character != '_') {
                out.append(Character.toLowerCase(character));
            }
        }
        return out.toString();
    }

    /**
     * The displayed span of a run of periods, for example {@code 08:00 - 09:40}.
     *
     * <p>Both ends are clamped into range and the end is never allowed to fall
     * before the start, so a nonsensical request still yields something
     * readable instead of an exception.</p>
     */
    public String timeRange(int fromPeriod, int toPeriod) {
        int from = clampPeriod(fromPeriod);
        int to = Math.max(from, clampPeriod(toPeriod));
        return label(startMinute(from)) + " - " + label(endMinute(to));
    }

    public BellSchedule renamed(String newName) {
        return new BellSchedule(newName, starts, ends, locationKeywords);
    }

    /** The same table with new times. */
    public BellSchedule withTimes(int[] newStarts, int[] newEnds) {
        return new BellSchedule(name, newStarts, newEnds, locationKeywords);
    }

    /** The same table with different venue keywords. */
    public BellSchedule withLocationKeywords(List<String> keywords) {
        return new BellSchedule(name, starts, ends, keywords);
    }

    private int clampPeriod(int period) {
        return Math.max(1, Math.min(starts.length, period));
    }

    /** Formats elapsed minutes from midnight, including late-night periods. */
    public static String label(int minutes) {
        int safe = Math.max(0, Math.min(MAX_MINUTE, minutes));
        return two(safe / 60) + ":" + two(safe % 60);
    }

    /**
     * Reads {@code H:mm} or {@code HH:mm}.
     *
     * <p>Hours up to 29 are accepted because {@link #label(int)} writes the
     * evening periods past midnight that way ({@code 24:05} rather than
     * {@code 00:05}); the two methods have to agree or a saved schedule would
     * not survive a read-back.</p>
     *
     * @return minutes since midnight, or {@code null} when the text is unusable
     */
    public static Integer parseLabel(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            return null;
        }
        try {
            int hours = Integer.parseInt(trimmed.substring(0, separator).trim());
            int minutes = Integer.parseInt(trimmed.substring(separator + 1).trim());
            if (hours < 0 || hours > 29 || minutes < 0 || minutes > 59) {
                return null;
            }
            return hours * 60 + minutes;
        } catch (NumberFormatException notATime) {
            return null;
        }
    }

    /**
     * Reads the way people actually type a time into a field.
     *
     * <p>Accepts what {@link #parseLabel} accepts plus the separators a phone
     * numeric keypad offers and the compact four-digit form:</p>
     *
     * <pre>
     *   08:00   8:05   8.05   8：00   800   0800
     * </pre>
     *
     * @return minutes since midnight, or {@code null} when the text is unusable
     */
    public static Integer parseLoose(String text) {
        if (text == null) {
            return null;
        }
        String compact = text.trim()
                .replace('\uFF1A', ':')
                .replace('.', ':')
                .replace('\u3002', ':')
                .replace("\uFF0E", ":")
                .replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return null;
        }
        if (compact.indexOf(':') < 0) {
            if (!compact.matches("\\d{3,4}")) {
                return null;
            }
            compact = compact.length() == 3
                    ? compact.substring(0, 1) + ":" + compact.substring(1)
                    : compact.substring(0, 2) + ":" + compact.substring(2);
        }
        return parseLabel(compact);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    @Override
    public String toString() {
        return name + "(" + timeRange(1, 1) + ", " + periodCount() + " periods)";
    }
}
