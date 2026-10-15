package com.weihe.schedule.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The bell schedules the app knows about, and the rules that pick one.
 *
 * <p>Resolution order, which is what "automatic by venue, overridable by hand"
 * means in practice:</p>
 *
 * <ol>
 *   <li>the schedule the course names explicitly, if that name still exists;</li>
 *   <li>otherwise the first schedule whose location keywords appear in the
 *       course's location;</li>
 *   <li>otherwise the schedule the user has picked as the default;</li>
 *   <li>otherwise, when nothing has been picked (or the pick no longer exists),
 *       the built-in standard schedule.</li>
 * </ol>
 *
 * <p>Step 3 used to be the hard-coded name of the B/C group, because the school's
 * own table files "other teaching places" -- which is what an unnamed venue and a
 * blank venue both are -- under that group. That is still where a store with
 * nothing chosen lands, but it is no longer a rule of the code: the group it
 * lands on is now whichever one the user names, and {@link #defaultOf} is what
 * reads the choice. See {@link #legacyDefaultId} for how an install that predates
 * the setting keeps the behaviour it had.</p>
 *
 * <p>The school rings the same bells for periods 1, 2 and 6 onwards; only periods
 * 3 to 5 differ between buildings. Those three periods split the campus into
 * three groups, and the groups differ from each other at every one of them, so
 * there is no shorter way to describe this than three schedules.</p>
 *
 * <p>Every time here comes from the school's own table. Nothing in this class is
 * a guess.</p>
 *
 * <p>This class is framework-free and therefore fully testable on the JVM.</p>
 */
public final class BellSchedules {

    /** Name of the schedule for 教学楼 A, F and J301 室. */
    public static final String STANDARD_NAME = "A/F \u697C\u4F5C\u606F";

    /** Name of the second group: 教学楼 B, C, J302 室 and other teaching places. */
    public static final String BC_NAME = "B/C \u697C\u4F5C\u606F";

    /** Name of the third group: 教学楼 D, E and J303 室. */
    public static final String DE_NAME = "D/E \u697C\u4F5C\u606F";

    /**
     * Ids the three shipped schedules are stored under.
     *
     * <p>Fixed strings rather than generated ones. An install that has never been
     * upgraded has no ids at all, and the only way it can be given a default that
     * <em>means</em> something is for the assignments to be reproducible: the same
     * three schedules have to come out with the same three ids on every device, so
     * that {@link #legacyDefaultId} can name one of them before any of it is
     * written down.</p>
     */
    public static final String STANDARD_ID = "schedule-standard";
    public static final String BC_ID = "schedule-bc";
    public static final String DE_ID = "schedule-de";

    /** Length of one teaching period, in minutes. Constant all day. */
    public static final int PERIOD_MINUTES = 40;

    /** When the first period of the day starts. */
    public static final int FIRST_PERIOD_START = 8 * 60 + 15;

    private BellSchedules() {
    }

    /**
     * The default timetable: 教学楼 A, F and J301 室.
     *
     * <p>Read line by line off the school's table: periods 1-2 are the
     * 08:15-09:35 block, period 3 is 09:55-10:35, periods 4-5 are the
     * 10:40-12:00 block, and from period 6 on the whole campus agrees.</p>
     */
    public static BellSchedule standard() {
        return new BellSchedule(STANDARD_ID, STANDARD_NAME,
                new int[] {495, 535, 595, 640, 680, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 635, 680, 720, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("a\u697C", "f\u697C", "j301"),
                PERIOD_MINUTES);
    }

    /**
     * 教学楼 B, C and J302 室.
     *
     * <p>Periods 3-4 are one 09:55-11:15 block and period 5 runs 11:20-12:00.</p>
     */
    public static BellSchedule bcBuilding() {
        return new BellSchedule(BC_ID, BC_NAME,
                new int[] {495, 535, 595, 635, 680, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 635, 675, 720, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("b\u697C", "c\u697C", "j302"),
                PERIOD_MINUTES);
    }

    /**
     * The timetable for 教学楼 D, E and J303 室.
     *
     * <p>Their whole morning runs later: periods 3-4 are the 10:15-11:35 block and
     * period 5 does not ring until 11:40-12:20.</p>
     */
    public static BellSchedule deBuilding() {
        return new BellSchedule(DE_ID, DE_NAME,
                new int[] {495, 535, 615, 655, 700, 800, 840, 900, 940, 995,
                        1035, 1090, 1130, 1175, 1220},
                new int[] {535, 575, 655, 695, 740, 840, 880, 940, 980, 1035,
                        1075, 1130, 1170, 1215, 1260},
                Arrays.asList("d\u697C", "e\u697C", "j303"),
                PERIOD_MINUTES);
    }

    /**
     * Schedules a fresh install starts with, in the order they are offered.
     *
     * <p>The standard one comes first because that is the school's own leading
     * group. Every one carries its own venue keywords, and each building belongs to
     * exactly one of them, so no keyword can outrank another. A venue the table
     * does not name -- and a course with no venue at all -- is handled by
     * {@link #resolve}, not by a catch-all keyword.</p>
     *
     * <p>Order is presentation only. What a course falls back to is
     * {@link #defaultOf}, which reads the user's choice and not the position.</p>
     */
    public static List<BellSchedule> presets() {
        List<BellSchedule> schedules = new ArrayList<>();
        schedules.add(standard());
        schedules.add(deBuilding());
        schedules.add(bcBuilding());
        return schedules;
    }

    /**
     * The default an install that predates the setting should be given.
     *
     * <p>Before a default could be chosen, an unmatched venue ran to the B/C group
     * -- the school's table files "other teaching places" there. Re-deriving that
     * group on the first read is what keeps an upgrade from moving anybody's
     * remaining lessons: defaulting to "whatever is first" would put them on the
     * A/F clock instead, twenty minutes out in periods 3 to 5.</p>
     *
     * <p>Falls back to the first schedule when there is no B/C group, which is the
     * case for a store whose tables were all renamed. That is a weaker answer, but
     * it is only reached where the old code would have gone on to the standard
     * schedule anyway, and the user can change it.</p>
     *
     * @return the id to adopt, or {@code null} when there is nothing to name
     */
    public static String legacyDefaultId(List<BellSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        BellSchedule bc = byName(schedules, BC_NAME);
        if (bc != null && bc.id() != null) {
            return bc.id();
        }
        BellSchedule standard = byName(schedules, STANDARD_NAME);
        if (standard != null && standard.id() != null) {
            return standard.id();
        }
        return schedules.get(0).id();
    }

    /**
     * The schedule the user has picked, or {@code null} when there is no usable
     * pick.
     *
     * <p>Looked up by id, never by name or position: the point of the setting is
     * that it survives a rename and a reorder. A pick that names a schedule which
     * is no longer stored is reported as "no pick" rather than repaired here -- the
     * repository is what knows whether the list it just read is the list the pick
     * was made against.</p>
     *
     * @param defaultId the stored pick, or {@code null} when nothing was chosen
     */
    public static BellSchedule defaultOf(List<BellSchedule> schedules,
            String defaultId) {
        if (schedules == null || defaultId == null || defaultId.trim().isEmpty()) {
            return null;
        }
        String wanted = defaultId.trim();
        for (BellSchedule schedule : schedules) {
            if (wanted.equals(schedule.id())) {
                return schedule;
            }
        }
        return null;
    }

    /**
     * An independent copy of {@code schedules}.
     *
     * <p>{@link BellSchedule} is immutable, so the copy is shallow on purpose:
     * what matters is that the returned list can be edited (schedules added,
     * removed or replaced) without touching the caller's list.</p>
     */
    public static List<BellSchedule> copyOf(List<BellSchedule> schedules) {
        List<BellSchedule> copy = new ArrayList<>();
        if (schedules != null) {
            for (BellSchedule schedule : schedules) {
                if (schedule != null) {
                    copy.add(schedule);
                }
            }
        }
        return copy;
    }

    /**
     * {@code schedules} with an id on every entry, inventing the missing ones.
     *
     * <p>An install that predates the setting stores schedules with no ids, and a
     * schedule with no id can never be pointed at. Rather than write an id at a
     * dozen call sites, every reader passes the list through here, so "a stored
     * schedule has an id" holds everywhere downstream.</p>
     *
     * <p>Ids already present are collected first, so an invented one can never
     * collide with a stored one. The three shipped names get their fixed ids, which
     * is what lets {@link #legacyDefaultId} name one before anything is written.
     * Anything else gets an id derived from its name and suffixed until unique --
     * derived rather than random so that reading the same store twice yields the
     * same ids, which is what stops a second read from silently repointing the
     * default.</p>
     *
     * <p>Dropped early when every entry already has one, because the caller uses
     * the answer to decide whether to write: a store that needs nothing must not
     * look like a store that changed.</p>
     *
     * @return true when an id was invented and the caller should store the result
     */
    public static boolean assignMissingIds(List<BellSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return false;
        }
        boolean missing = false;
        for (BellSchedule schedule : schedules) {
            if (schedule.id() == null) {
                missing = true;
                break;
            }
        }
        if (!missing) {
            return false;
        }
        List<String> taken = new ArrayList<>();
        for (BellSchedule schedule : schedules) {
            String existing = schedule.id();
            if (existing != null && !taken.contains(existing)) {
                taken.add(existing);
            }
        }
        for (int i = 0; i < schedules.size(); i++) {
            BellSchedule schedule = schedules.get(i);
            if (schedule.id() != null) {
                continue;
            }
            String base = fixedIdFor(schedule.name());
            if (base == null) {
                base = derivedId(schedule.name());
            }
            String unique = base;
            int suffix = 2;
            while (taken.contains(unique)) {
                unique = base + "-" + suffix;
                suffix++;
            }
            taken.add(unique);
            schedules.set(i, schedule.withId(unique));
        }
        return true;
    }

    /**
     * The fixed id for one of the three shipped schedules, or {@code null}.
     *
     * <p>Detected by name. An install that predates ids holds exactly these three
     * names, so matching on them is what reproduces the ids
     * {@link #presets()} would have written.</p>
     */
    private static String fixedIdFor(String name) {
        if (STANDARD_NAME.equals(name)) {
            return STANDARD_ID;
        }
        if (BC_NAME.equals(name)) {
            return BC_ID;
        }
        if (DE_NAME.equals(name)) {
            return DE_ID;
        }
        return null;
    }

    /**
     * An id derived from a user-chosen name.
     *
     * <p>Deterministic on purpose -- see {@link #assignMissingIds}. The name is
     * reduced to characters that survive a round trip through the settings file
     * unchanged, and an empty result falls back to a constant that the uniquing
     * loop in the caller will suffix.</p>
     */
    private static String derivedId(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                out.append(Character.toLowerCase(character));
            }
        }
        return out.length() == 0 ? "schedule" : "schedule-" + out;
    }

    /** The schedule with this name, or {@code null} when there is none. */
    public static BellSchedule byName(List<BellSchedule> schedules, String name) {
        if (schedules == null || name == null) {
            return null;
        }
        String wanted = name.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (BellSchedule schedule : schedules) {
            if (schedule.name().equals(wanted)) {
                return schedule;
            }
        }
        return null;
    }

    /** The first schedule whose keywords appear in {@code location}, or null. */
    public static BellSchedule forLocation(List<BellSchedule> schedules, String location) {
        if (schedules == null) {
            return null;
        }
        for (BellSchedule schedule : schedules) {
            if (schedule.matchesLocation(location)) {
                return schedule;
            }
        }
        return null;
    }

    /**
     * The schedule that actually applies to a course, under the default the user
     * has not chosen.
     *
     * <p>Kept for callers that genuinely have no default in hand: the tests that
     * pin the shipped three, and any reader running before the repository has been
     * asked. Falls back to the legacy group, so it answers the same way a store
     * with nothing chosen does.</p>
     *
     * @param scheduleName the course's explicit choice, empty for "decide for me"
     * @param location     the course's venue text
     */
    public static BellSchedule resolve(List<BellSchedule> schedules,
            String scheduleName, String location) {
        return resolve(schedules, scheduleName, location,
                legacyDefaultId(schedules));
    }

    /**
     * The schedule that actually applies to a course.
     *
     * <p>Steps 1 and 2 are the ones this app has always had, and neither changes
     * here: a course that names a schedule still gets it, and a venue that matches
     * keywords still wins. What changed is step 3. It used to be the hard-coded
     * B/C name; it is now the schedule the user picked, which for an upgraded
     * install is still the B/C group because {@link #legacyDefaultId} adopted it.
     * A course therefore keeps its clock across the upgrade, and a user who wants
     * a different fallback can now say so.</p>
     *
     * <p>The choice is read by id and so is unaffected by a rename or a reorder in
     * the list. A choice naming a schedule that is no longer stored simply does not
     * match, and the standard schedule takes over -- the user cannot be left with a
     * timetable that resolves to nothing.</p>
     *
     * @param scheduleName the course's explicit choice, empty for "decide for me"
     * @param location     the course's venue text
     * @param defaultId    the user's default pick, or {@code null} for "the legacy
     *                     group"
     */
    public static BellSchedule resolve(List<BellSchedule> schedules,
            String scheduleName, String location, String defaultId) {
        BellSchedule chosen = byName(schedules, scheduleName);
        if (chosen != null) {
            return chosen;
        }
        chosen = forLocation(schedules, location);
        if (chosen != null) {
            return chosen;
        }
        chosen = defaultOf(schedules, defaultId);
        if (chosen != null) {
            return chosen;
        }
        chosen = byName(schedules, STANDARD_NAME);
        return chosen != null ? chosen : standard();
    }
}
