package com.weihe.schedule;

import com.weihe.schedule.model.Course;

/**
 * The colour system: one place that defines every colour the app draws with.
 *
 * <p>This is a Material 3 colour scheme written out by hand rather than pulled
 * from {@code com.google.android.material}, because that library is not
 * available in this build environment and adding it would break the build. The
 * <em>roles</em> are the point, not the library: a screen never picks "a grey",
 * it picks {@code SURFACE_CONTAINER_HIGH} or {@code OUTLINE_VARIANT}, which is
 * what keeps a palette coherent as it grows.</p>
 *
 * <p>The hues are taken from the launcher icon: its deep teal band is
 * {@link #PRIMARY}, its cream page and pale sage blocks are the surface ramp, and
 * its single orange tile is {@link #ACCENT}, reserved for "today" and other
 * time-sensitive state. Everything is deliberately low-saturation, so the one
 * orange on a screen is the thing that catches the eye.</p>
 *
 * <p>Rules this class follows:</p>
 *
 * <ul>
 *   <li>every container has an {@code ON_} partner that is legible on it;
 *       {@code PaletteContrastTest} checks each pair against WCAG AA;</li>
 *   <li>surfaces step in tone rather than in opacity, which is how depth is
 *       built without stacking translucent layers;</li>
 *   <li>plain {@code int} literals and no Android API, so that test can run on a
 *       bare JVM.</li>
 * </ul>
 */
public final class Palette {

    private Palette() {
    }

    // ------------------------------------------------------------ brand roles

    /** Primary action, selected state, focus. */
    public static final int PRIMARY = 0xFF11584E;
    /** Text and icons drawn on {@link #PRIMARY}. */
    public static final int ON_PRIMARY = 0xFFFFFFFF;
    /** Soft brand tint behind a selected chip or an active icon. */
    public static final int PRIMARY_CONTAINER = 0xFFCFE5DE;
    /** Text and icons on {@link #PRIMARY_CONTAINER}. */
    public static final int ON_PRIMARY_CONTAINER = 0xFF06322C;

    /** Secondary accent, for what is not the main action. */
    public static final int SECONDARY = 0xFF44605A;
    public static final int ON_SECONDARY = 0xFFFFFFFF;
    public static final int SECONDARY_CONTAINER = 0xFFDBE8E3;
    public static final int ON_SECONDARY_CONTAINER = 0xFF14201D;

    /** Warm accent, reserved for "today" and other time-sensitive state. */
    public static final int ACCENT = 0xFF94521F;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int ACCENT_CONTAINER = 0xFFFBE2CE;
    public static final int ON_ACCENT_CONTAINER = 0xFF4A2008;

    // ---------------------------------------------------------------- surfaces

    /** The page behind everything. */
    public static final int SURFACE = 0xFFF3F7F3;
    /** One step up from the page: grouped rows, table headers. */
    public static final int SURFACE_CONTAINER_LOW = 0xFFEDF3EE;
    /** Card background. */
    public static final int SURFACE_CONTAINER = 0xFFFFFFFF;
    /** A field inside a card, or a card on top of a card. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFFE8EFEA;
    /** Highest emphasis surface: pressed states, tracks, empty grid slots. */
    public static final int SURFACE_CONTAINER_HIGHEST = 0xFFE0E9E3;

    /** Primary text. */
    public static final int ON_SURFACE = 0xFF16201D;
    /** Secondary text: labels, metadata, captions. */
    public static final int ON_SURFACE_VARIANT = 0xFF4E5C57;
    /** Placeholder text and disabled labels. */
    public static final int OUTLINE = 0xFF78867F;
    /** Hairline borders and dividers. */
    public static final int OUTLINE_VARIANT = 0xFFDFE7E1;

    // ------------------------------------------------------------------ header

    /**
     * The app bar's own background.
     *
     * <p>The same tone as the page on purpose. The header used to be a full-width
     * dark band, which made the lightest screen in the app open with its heaviest
     * element; a title, a semester row and one icon button do not need a colour
     * block behind them. Matching {@link #SURFACE} also means the status bar --
     * which the theme paints with the same value -- has nothing to seam against.</p>
     */
    public static final int HEADER_SURFACE = SURFACE;
    /** The screen's title in the header. */
    public static final int ON_HEADER = ON_SURFACE;
    /** The quiet second line in the header. */
    public static final int ON_HEADER_VARIANT = ON_SURFACE_VARIANT;
    /** A round icon button sitting in the header. */
    public static final int HEADER_ACTION_BG = SURFACE_CONTAINER;
    /** Its hairline border, so a white button is visible on a near-white bar. */
    public static final int HEADER_ACTION_BORDER = OUTLINE_VARIANT;
    /**
     * A tinted panel inside the header, used for the semester row.
     *
     * <p>A tint rather than a translucent white: the header is light now, so the
     * "lighter than its background" trick the dark bar used would be invisible.</p>
     */
    public static final int HEADER_PANEL = PRIMARY_CONTAINER;
    public static final int ON_HEADER_PANEL = ON_PRIMARY_CONTAINER;

    // ------------------------------------------------------------------ status

    /** Destructive action and conflict warning. */
    public static final int ERROR = 0xFFAF2E27;
    public static final int ON_ERROR = 0xFFFFFFFF;
    public static final int ERROR_CONTAINER = 0xFFFADBD8;
    public static final int ON_ERROR_CONTAINER = 0xFF3F0D0A;

    /** Positive confirmation, used sparingly. */
    public static final int SUCCESS = 0xFF2A6B4F;
    public static final int ON_SUCCESS = 0xFFFFFFFF;
    public static final int SUCCESS_CONTAINER = 0xFFCFEBDD;
    public static final int ON_SUCCESS_CONTAINER = 0xFF082E1C;

    // -------------------------------------------------------------- deprecated

    /**
     * Card background.
     *
     * @deprecated use {@link #SURFACE_CONTAINER}; the alias exists so the rename
     *         does not have to happen in every file at once.
     */
    @Deprecated
    public static final int CARD = SURFACE_CONTAINER;
    @Deprecated
    public static final int HEADER = HEADER_SURFACE;
    @Deprecated
    public static final int ON_HEADER_SUBTLE = ON_HEADER_VARIANT;
    @Deprecated
    public static final int HEADER_BORDER = HEADER_ACTION_BORDER;
    @Deprecated
    public static final int INK = ON_SURFACE;
    @Deprecated
    public static final int MUTED = ON_SURFACE_VARIANT;
    @Deprecated
    public static final int HINT = OUTLINE;
    @Deprecated
    public static final int LINE = OUTLINE_VARIANT;
    @Deprecated
    public static final int DANGER = ERROR;
    @Deprecated
    public static final int TRACK = SURFACE_CONTAINER_LOW;
    @Deprecated
    public static final int CHIP_BG = SURFACE_CONTAINER_HIGH;
    @Deprecated
    public static final int INPUT_BG = SURFACE_CONTAINER_LOW;
    @Deprecated
    public static final int SELECTED_DAY_BG = PRIMARY_CONTAINER;
    @Deprecated
    public static final int PARITY_PILL = HEADER_PANEL;
    @Deprecated
    public static final int ACCENT_SOFT = ACCENT_CONTAINER;
    @Deprecated
    public static final int ACCENT_DARK = ON_ACCENT_CONTAINER;

    // ---------------------------------------------------------------- courses

    /**
     * Colours offered when creating a course.
     *
     * <p>Chosen to survive being blended towards white for a block background and
     * towards the ink colour for text: every one of them stays readable as an
     * 11-17sp label, which is what the grid and the cards do with them. The first
     * entry is {@link Course#DEFAULT_COLOR}.</p>
     *
     * <p>All nine sit around a third to a half saturation. A timetable is a
     * colour-coded document, so the point is to make courses tell each other apart
     * at a glance without any single block shouting; saturated primaries turn the
     * grid into a warning sign.</p>
     */
    public static final int[] COURSE_COLORS = {
            Course.DEFAULT_COLOR, 0xFF3A7563, 0xFF94521F, 0xFF6A5F94,
            0xFF35707E, 0xFF9A5454, 0xFF7E6C33, 0xFF525F6B, 0xFF8C5A70
    };

    /** How much of a course colour is mixed into white for a block background. */
    public static final float COURSE_TINT = 0.90f;
    /** How much is mixed in for the block's hairline border. */
    public static final float COURSE_BORDER_TINT = 0.55f;
    /** How much of the ink colour is mixed into a course colour for its text. */
    public static final float COURSE_TEXT_MIX = 0.20f;
    /** How far a card's colour rail is softened towards the card surface. */
    public static final float COURSE_RAIL_SOFTEN = 0.15f;
}
