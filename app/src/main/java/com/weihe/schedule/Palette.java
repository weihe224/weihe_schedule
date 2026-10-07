package com.weihe.schedule;

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
    public static final int PRIMARY = 0xFF0F766E;
    /** Text and icons drawn on {@link #PRIMARY}. */
    public static final int ON_PRIMARY = 0xFFFFFFFF;
    /** Soft brand tint behind a selected chip or an active icon. */
    public static final int PRIMARY_CONTAINER = 0xFFD8EFEA;
    /** Text and icons on {@link #PRIMARY_CONTAINER}. */
    public static final int ON_PRIMARY_CONTAINER = 0xFF063C38;

    /** Secondary accent, for what is not the main action. */
    public static final int SECONDARY = 0xFF4A635E;
    public static final int ON_SECONDARY = 0xFFFFFFFF;
    public static final int SECONDARY_CONTAINER = 0xFFDFE9E6;
    public static final int ON_SECONDARY_CONTAINER = 0xFF17211F;

    /** Warm accent, reserved for "today" and other time-sensitive state. */
    public static final int ACCENT = 0xFFB25A21;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int ACCENT_CONTAINER = 0xFFFFE2CF;
    public static final int ON_ACCENT_CONTAINER = 0xFF54240A;

    // ---------------------------------------------------------------- surfaces

    /** The page behind everything. */
    public static final int SURFACE = 0xFFF7FAF9;
    /** One step up from the page: grouped rows, table headers. */
    public static final int SURFACE_CONTAINER_LOW = 0xFFF1F5F4;
    /** Card background. */
    public static final int SURFACE_CONTAINER = 0xFFFFFFFF;
    /** A field inside a card, or a card on top of a card. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFFE9EFED;
    /** Highest emphasis surface: pressed states, tracks, empty grid slots. */
    public static final int SURFACE_CONTAINER_HIGHEST = 0xFFE2E9E7;

    /** Primary text. */
    public static final int ON_SURFACE = 0xFF1A1C1B;
    /** Secondary text: labels, metadata, captions. */
    public static final int ON_SURFACE_VARIANT = 0xFF56605D;
    /** Placeholder text and disabled labels. */
    public static final int OUTLINE = 0xFF7C8783;
    /** Hairline borders and dividers. */
    public static final int OUTLINE_VARIANT = 0xFFDCE4E1;

    /** Header band: the one dark surface in the app. */
    public static final int INVERSE_SURFACE = 0xFF123F3A;
    public static final int ON_INVERSE = 0xFFFFFFFF;
    /** Muted text on the header. */
    public static final int ON_INVERSE_VARIANT = 0xFFCFE3DE;
    /**
     * Colour of a translucent panel inside the header.
     *
     * <p>Kept as colour plus alpha because a translucent colour has no single
     * value to reason about: what is drawn is this colour composited over
     * {@link #INVERSE_SURFACE}, and that is what the contrast test checks.</p>
     */
    public static final int INVERSE_PANEL_COLOR = 0xFFFFFFFF;
    /** Alpha of the panel above, 0-255. */
    public static final int INVERSE_PANEL_ALPHA = 0x2E;
    /** The panel as a drawable colour. */
    public static final int INVERSE_PANEL =
            (INVERSE_PANEL_ALPHA << 24) | (INVERSE_PANEL_COLOR & 0xFFFFFF);
    /** Border of a header icon button. */
    public static final int INVERSE_BORDER = 0x4DFFFFFF;

    // ------------------------------------------------------------------ status

    /** Destructive action and conflict warning. */
    public static final int ERROR = 0xFFB3261E;
    public static final int ON_ERROR = 0xFFFFFFFF;
    public static final int ERROR_CONTAINER = 0xFFFCDAD7;
    public static final int ON_ERROR_CONTAINER = 0xFF410E0B;

    /** Positive confirmation, used sparingly. */
    public static final int SUCCESS = 0xFF1E6B45;
    public static final int ON_SUCCESS = 0xFFFFFFFF;
    public static final int SUCCESS_CONTAINER = 0xFFCDEBD9;
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
    public static final int HEADER = INVERSE_SURFACE;
    @Deprecated
    public static final int ON_HEADER_SUBTLE = ON_INVERSE_VARIANT;
    @Deprecated
    public static final int HEADER_PANEL = INVERSE_PANEL;
    @Deprecated
    public static final int HEADER_BORDER = INVERSE_BORDER;
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
    public static final int PARITY_PILL = INVERSE_PANEL;
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
     */
    public static final int[] COURSE_COLORS = {
            Course.DEFAULT_COLOR, 0xFF2563EB, 0xFF7C3AED, 0xFFB25A21,
            0xFF0E8578, 0xFFB3261E, 0xFF8A6100, 0xFF4A5568
    };

    /** How much of a course colour is mixed into white for a block background. */
    public static final float COURSE_TINT = 0.90f;
    /** How much is mixed in for the block's hairline border. */
    public static final float COURSE_BORDER_TINT = 0.55f;
    /** How much of the ink colour is mixed into a course colour for its text. */
    public static final float COURSE_TEXT_MIX = 0.20f;
}
