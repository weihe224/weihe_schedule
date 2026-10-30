package com.weihe.schedule.ui;

import com.weihe.schedule.design.Palette;

/**
 * Design tokens: the numbers that make the screens look like one product.
 *
 * <p>Split from {@link Palette} because these are sizes, not colours, and split
 * from the component builders because a token is worth testing on its own: the
 * spacing scale, the corner radii and the type scale are what "consistent" means
 * here, and a stray 13dp gap or a fifth font size is exactly how a UI stops
 * looking designed.</p>
 *
 * <p>Values follow Material 3's scales, rounded to what a {@code dp} on a phone
 * can actually show. Nothing here touches Android, so the whole scale is
 * available to unit tests.</p>
 */
public final class UiTokens {

    private UiTokens() {
    }

    // ------------------------------------------------------------------ spacing

    /** Base unit. Every gap in the app is a multiple of this. */
    public static final int SPACE_UNIT = 4;
    /** 4dp: between an icon and its label. */
    public static final int SPACE_XS = 4;
    /** 8dp: inside a chip, between tightly related items. */
    public static final int SPACE_S = 8;
    /** 12dp: between the rows of a card. */
    public static final int SPACE_M = 12;
    /** 16dp: the screen's horizontal margin, and card padding. */
    public static final int SPACE_L = 16;
    /** 24dp: between cards and between sections. */
    public static final int SPACE_XL = 24;
    /** 32dp: above a page's first group. */
    public static final int SPACE_XXL = 32;

    // -------------------------------------------------------------------- shape

    /** 4dp: a hairline tint, an indicator bar. */
    public static final int RADIUS_XS = 4;
    /** 8dp: small controls, chips, badges. */
    public static final int RADIUS_S = 8;
    /** 12dp: fields and buttons. */
    public static final int RADIUS_M = 12;
    /** 16dp: cards. */
    public static final int RADIUS_L = 16;
    /** 24dp: sheets and dialogs. */
    public static final int RADIUS_XL = 24;
    /** 28dp: pills. */
    public static final int RADIUS_FULL = 28;

    // ---------------------------------------------------------------- elevation

    /** Flat: outlines only, for a card on a tinted page. */
    public static final float ELEVATION_NONE = 0f;
    /** 1dp: the resting state of a card. */
    public static final float ELEVATION_LOW = 1f;
    /** 3dp: a card being pressed, or a sticky bar. */
    public static final float ELEVATION_MEDIUM = 3f;
    /** 6dp: the floating action button. */
    public static final float ELEVATION_HIGH = 6f;

    // ------------------------------------------------------------------- strokes

    /** Hairline for card outlines and dividers. */
    public static final float STROKE_HAIRLINE = 1f;
    /** Emphasis border, for a selected card. */
    public static final float STROKE_EMPHASIS = 2f;

    // ---------------------------------------------------------------- typography

    /**
     * Type scale, in sp, following Material 3's naming.
     *
     * <p>Five sizes were used before this existed, chosen per screen; the scale
     * is what makes a caption on the grid and a caption on a card agree.</p>
     */
    public static final class Type {
        private Type() {
        }

        /** 28sp: a screen's own title when it is the only thing on the row. */
        public static final float HEADLINE_SMALL = 28f;
        /** 22sp: the app title in the header. */
        public static final float TITLE_LARGE = 22f;
        /** 17sp: a card's title, a dialog's title. */
        public static final float TITLE_MEDIUM = 17f;
        /** 15sp: a section heading, a button label. */
        public static final float TITLE_SMALL = 15f;
        /** 14sp: body text, list rows, form fields. */
        public static final float BODY_LARGE = 14f;
        /** 13sp: secondary body text. */
        public static final float BODY_MEDIUM = 13f;
        /** 12sp: labels and metadata. */
        public static final float LABEL_LARGE = 12f;
        /** 11sp: captions. */
        public static final float LABEL_MEDIUM = 11f;
        /** 10sp: the smallest legal caption, used inside grid blocks. */
        public static final float LABEL_SMALL = 10f;

        /** Line spacing multiplier for multi-line body text. */
        public static final float BODY_LINE_SPACING = 1.25f;
        /** Line spacing multiplier for the small text inside grid blocks. */
        public static final float DENSE_LINE_SPACING = 1.1f;
    }

    // ------------------------------------------------------------------ controls

    /** Minimum touch target, from the accessibility guidelines. */
    public static final int TOUCH_TARGET = 48;
    /** Height of a standard button. */
    public static final int BUTTON_HEIGHT = 48;
    /** Height of a compact button, used in rows of three. */
    public static final int BUTTON_HEIGHT_COMPACT = 40;
    /** Height of a form field. */
    public static final int FIELD_HEIGHT = 52;
    /** Height of a chip. */
    public static final int CHIP_HEIGHT = 36;
    /** Size of a round icon button in an app bar or a week stepper. */
    public static final int ICON_BUTTON_SIZE = 40;
    /**
     * Height of one day in the date selector: a weekday over a date, with enough
     * room that neither line is cramped on a small phone.
     */
    public static final int DAY_CELL_HEIGHT = 56;
    /**
     * Width of the colour rail down the left edge of a course card.
     *
     * <p>5dp rather than 4. At 4dp on a phone the rail is a hairline that the eye
     * can miss when scrolling, and it is the primary thing telling one course
     * from another in the day list. It is still narrow enough that it reads as
     * the card's edge rather than as a separate graphic.</p>
     */
    public static final int COURSE_RAIL_WIDTH = 5;
    /** Minimum width of the day list time column; grows with the type scale. */
    public static final int COURSE_TIME_MIN_WIDTH = 56;
    /** Height of the top app bar. */
    public static final int TOP_BAR_HEIGHT = 64;
    /** Size of a floating action button. */
    public static final int FAB_SIZE = 56;
    /** Size of an icon inside a button. */
    public static final int ICON_SIZE = 22;

    /** Converts a token in dp to pixels, rounding consistently. */
    public static int px(android.content.Context context, float dp) {
        return UiKit.dp(context, dp);
    }
}
