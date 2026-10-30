package com.weihe.schedule.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.weihe.schedule.design.Colors;
import com.weihe.schedule.design.Palette;
import com.weihe.schedule.R;

/**
 * The component layer: every piece of chrome the app draws, defined once.
 *
 * <p>Before this class each screen assembled its own cards, chips and buttons, so
 * "a card" meant four slightly different things and a change to the corner radius
 * had to be made in five places. Here a screen asks for
 * {@link #card(Context)} or {@link #chip(Context, CharSequence, boolean)} and
 * gets the same thing the other screens get.</p>
 *
 * <p>It replaces what {@code com.google.android.material} would provide (see
 * {@link Palette} for why the library is not used). What the library gives for
 * free and this does not: state animations, ripple, and real elevation shadows on
 * pre-Lollipop. What it does give here is a rounded outline plus elevation, which
 * is what a Material card actually is.</p>
 *
 * <p>Sizes and colours come from {@link UiTokens} and {@link Palette}; nothing in
 * this file invents a number.</p>
 */
public final class UiKit {

    private UiKit() {
    }

    // ------------------------------------------------------------------- colour

    /** Mixes a course colour towards white for a block background. */
    public static int courseTint(int courseColor) {
        return Colors.blend(courseColor, Color.WHITE, Palette.COURSE_TINT);
    }

    /** Mixes a course colour towards white for a block border. */
    public static int courseBorder(int courseColor) {
        return Colors.blend(courseColor, Color.WHITE, Palette.COURSE_BORDER_TINT);
    }

    /** The readable text colour derived from a course colour. */
    public static int courseInk(int courseColor) {
        return Colors.blend(courseColor, Palette.ON_SURFACE, Palette.COURSE_TEXT_MIX);
    }

    /**
     * The colour of a course card's left rail.
     *
     * <p>Close to the course's own colour rather than a muted version of it. The
     * rail is what identifies the course at a glance in the day list, and at
     * 15% towards white it was doing that job badly; the card behind it now
     * carries a wash of the same colour, so the rail reads as the edge of the
     * course rather than as a separate stripe and does not need muting.</p>
     */
    public static int courseRail(int courseColor) {
        return Colors.blend(courseColor, Palette.SURFACE_CONTAINER,
                Palette.COURSE_RAIL_SOFTEN);
    }

    /**
     * The card background for a course in the day list.
     *
     * <p>A wash of the course colour rather than the plain card surface. This is
     * the difference between "a white card that happens to have a blue line on
     * it" and "the blue course", which is what makes a day scannable.</p>
     *
     * <p>Kept light on purpose. The course name is dark ink drawn directly on
     * this, so the wash is bounded by legibility, and the callers still draw the
     * name in {@link #courseInk} rather than in white: a saturated fill would
     * mean white text, which is the opposite of the quiet light theme the rest
     * of the app is built on.</p>
     */
    public static int courseCard(int courseColor) {
        return Colors.blend(courseColor, Palette.SURFACE_CONTAINER,
                Palette.COURSE_CARD_TINT);
    }

    // -------------------------------------------------------------------- shape

    /**
     * Gives a view a rounded outline and a shadow.
     *
     * <p>A shadow on Android follows the view's outline, and the outline of a
     * plain background is a rectangle. Setting the outline provider to a rounded
     * rectangle first is what turns "a rectangle with rounded corners drawn on
     * it" into an actual rounded card with a soft shadow behind it.</p>
     *
     * @param radiusDp corner radius, from {@link UiTokens}
     */
    public static void elevate(View view, float radiusDp, float elevationDp) {
        final float radiusPx = dp(view.getContext(), radiusDp);
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View target, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(),
                        radiusPx);
            }
        });
        view.setClipToOutline(true);
        view.setElevation(dp(view.getContext(), elevationDp));
    }

    /** A rounded, filled background. */
    public static GradientDrawable fill(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** A rounded background with a hairline border. */
    public static GradientDrawable outline(Context context, int color, float radiusDp,
            int strokeColor, float strokeDp) {
        GradientDrawable drawable = fill(context, color, radiusDp);
        if (strokeDp > 0f) {
            drawable.setStroke(dp(context, strokeDp), strokeColor);
        }
        return drawable;
    }

    // ------------------------------------------------------------------ surfaces

    /** A card: the standard container for a group of related rows. */
    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L));
        styleAsCard(context, card, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);
        return card;
    }

    /** A card with no padding, for content that draws to its own edges. */
    public static LinearLayout bareCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        styleAsCard(context, card, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);
        return card;
    }

    /**
     * A card with no border, held up by its shadow alone.
     *
     * <p>For a white sheet on the tinted page. The hairline {@link #card} draws is
     * what makes a card legible when it sits on a surface of almost the same tone;
     * where the two already differ by more than that, the line is one edge too
     * many.</p>
     */
    public static LinearLayout sheet(Context context) {
        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L));
        sheet.setBackground(fill(context, Palette.SURFACE_CONTAINER, UiTokens.RADIUS_L));
        elevate(sheet, UiTokens.RADIUS_L, UiTokens.ELEVATION_LOW);
        return sheet;
    }

    /** Applies the card surface to any already-built view. */
    public static void styleAsCard(Context context, View view, float radiusDp,
            float elevationDp) {
        view.setBackground(outline(context, Palette.SURFACE_CONTAINER, radiusDp,
                Palette.OUTLINE_VARIANT, UiTokens.STROKE_HAIRLINE));
        elevate(view, radiusDp, elevationDp);
    }

    /**
     * The same card, on a tinted surface.
     *
     * <p>The border is the tint's own colour rather than the neutral hairline, so
     * a washed card is bounded by a slightly deeper version of itself instead of
     * by a grey line that fights the wash. The stroke stays a hairline: the point
     * is to close the card, not to outline it.</p>
     */
    public static void styleAsCourseCard(Context context, View view, float radiusDp,
            float elevationDp, int courseColor) {
        view.setBackground(outline(context, courseCard(courseColor), radiusDp,
                courseBorder(courseColor), UiTokens.STROKE_HAIRLINE));
        elevate(view, radiusDp, elevationDp);
    }

    /** A section heading: quiet, upper-case-free, above its group. */
    public static TextView sectionLabel(Context context, CharSequence text) {
        TextView label = text(context, text, UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, UiTokens.SPACE_S);
        label.setLayoutParams(params);
        return label;
    }

    /** A first-baseline-aligned fact whose value wraps without squeezing its label. */
    public static LinearLayout infoRow(Context context, CharSequence label,
            CharSequence value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        TextView name = text(context, label, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        row.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView content = text(context, value, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        content.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.leftMargin = dp(context, UiTokens.SPACE_L);
        row.addView(content, valueParams);
        return row;
    }

    // ----------------------------------------------------------------------- text

    /** A text view at one place in the type scale. */
    public static TextView text(Context context, CharSequence value, float sizeSp,
            int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0f, UiTokens.Type.BODY_LINE_SPACING);
        return view;
    }

    /**
     * The same, taking a bold flag rather than a {@link Typeface} constant.
     *
     * <p>The flag form is what most call sites want, and having it here means a
     * screen does not have to reach for {@code Typeface} just to say "bold".</p>
     */
    public static TextView text(Context context, CharSequence value, float sizeSp,
            int color, boolean bold) {
        return text(context, value, sizeSp, color,
                bold ? Typeface.BOLD : Typeface.NORMAL);
    }

    /** A title, at card or dialog level. */
    public static TextView title(Context context, CharSequence value) {        return text(context, value, UiTokens.Type.TITLE_MEDIUM, Palette.ON_SURFACE,
                Typeface.BOLD);
    }

    /** Body copy. */
    public static TextView body(Context context, CharSequence value) {
        return text(context, value, UiTokens.Type.BODY_LARGE, Palette.ON_SURFACE,
                Typeface.NORMAL);
    }

    /** Secondary copy: metadata, hints, captions. */
    public static TextView caption(Context context, CharSequence value) {
        return text(context, value, UiTokens.Type.LABEL_MEDIUM,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
    }

    /** A placeholder inside an empty field. */
    public static TextView placeholder(Context context, CharSequence value) {
        return text(context, value, UiTokens.Type.LABEL_LARGE, Palette.OUTLINE,
                Typeface.NORMAL);
    }

    // -------------------------------------------------------------------- buttons

    /** A filled button: one per screen, for the main action. */
    public static TextView filledButton(Context context, CharSequence label,
            View.OnClickListener listener) {
        return button(context, label, Palette.PRIMARY, Palette.ON_PRIMARY,
                UiTokens.RADIUS_M, UiTokens.BUTTON_HEIGHT, true, listener);
    }

    /** A filled button in a brand-neutral tone, for a row of equals. */
    public static TextView tonalButton(Context context, CharSequence label,
            View.OnClickListener listener) {
        return button(context, label, Palette.SURFACE_CONTAINER_HIGH,
                Palette.ON_SURFACE, UiTokens.RADIUS_M, UiTokens.BUTTON_HEIGHT_COMPACT,
                true, listener);
    }

    /** An outlined button: an alternative that is not the main action. */
    public static TextView outlinedButton(Context context, CharSequence label,
            View.OnClickListener listener) {
        TextView view = button(context, label, Color.TRANSPARENT, Palette.PRIMARY,
                UiTokens.RADIUS_M, UiTokens.BUTTON_HEIGHT_COMPACT, true, listener);
        view.setBackground(outline(context, Color.TRANSPARENT, UiTokens.RADIUS_M,
                Palette.OUTLINE, UiTokens.STROKE_HAIRLINE));
        return view;
    }

    /** A text button: the quietest action, for dialog buttons and links. */
    public static TextView textButton(Context context, CharSequence label, int color,
            View.OnClickListener listener) {
        TextView view = text(context, label, UiTokens.Type.BODY_LARGE, color,
                Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, UiTokens.SPACE_S), dp(context, UiTokens.SPACE_S),
                dp(context, UiTokens.SPACE_S), dp(context, UiTokens.SPACE_S));
        view.setOnClickListener(listener);
        view.setMinHeight(dp(context, UiTokens.TOUCH_TARGET));
        return view;
    }

    /**
     * The bar pinned to the bottom of an editor, holding its save action.
     *
     * <p>One surface with a raised shadow, so the form scrolls underneath it and
     * the way out is always on screen.</p>
     */
    public static LinearLayout actionBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_M),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_M));
        bar.setBackgroundColor(Palette.SURFACE_CONTAINER);
        bar.setElevation(dp(context, UiTokens.ELEVATION_HIGH));
        return bar;
    }

    /** A full-width filled button, for the single action an editor exists to do. */
    public static TextView primaryAction(Context context, CharSequence label,
            View.OnClickListener listener) {
        TextView button = text(context, label, UiTokens.Type.TITLE_SMALL,
                Palette.ON_PRIMARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        setRoundedBackground(button, Palette.PRIMARY, UiTokens.RADIUS_M,
                Color.TRANSPARENT, 0f);
        button.setOnClickListener(listener);
        ripple(button, UiTokens.RADIUS_M, false);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, UiTokens.BUTTON_HEIGHT)));
        return button;
    }

    private static TextView button(Context context, CharSequence label, int background,
            int foreground, float radiusDp, int heightDp, boolean bold,
            View.OnClickListener listener) {
        TextView view = text(context, label, UiTokens.Type.TITLE_SMALL, foreground,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, UiTokens.SPACE_M), 0, dp(context, UiTokens.SPACE_M),
                0);
        view.setMinHeight(dp(context, heightDp));
        setRoundedBackground(view, background, radiusDp, Color.TRANSPARENT, 0f);
        view.setOnClickListener(listener);
        return view;
    }

    /**
     * Replaces a view's background with a rounded one, keeping its shadow in step.
     *
     * <p>Kept in one place because the two have to agree: a rounded background on a
     * view whose outline is still a rectangle draws a rounded shape inside a
     * square shadow.</p>
     */
    public static void setRoundedBackground(View view, int color, float radiusDp,
            int strokeColor, float strokeDp) {
        view.setBackground(outline(view.getContext(), color, radiusDp, strokeColor,
                strokeDp));
    }

    // ---------------------------------------------------------------------- chips

    /**
     * A chip: a compact, selectable label.
     *
     * <p>Selected state is a filled brand container with dark brand text, the M3
     * pattern, rather than a colour swap that leaves the chip looking inactive.</p>
     */
    public static TextView chip(Context context, CharSequence label, boolean selected) {
        TextView chip = text(context, label, UiTokens.Type.LABEL_LARGE,
                selected ? Palette.ON_PRIMARY_CONTAINER : Palette.ON_SURFACE_VARIANT,
                selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, UiTokens.SPACE_M), 0, dp(context, UiTokens.SPACE_M),
                0);
        chip.setMinHeight(dp(context, UiTokens.CHIP_HEIGHT));
        styleChip(chip, selected);
        return chip;
    }

    /** Restyles a chip in place, for a selection that changes. */
    public static void styleChip(TextView chip, boolean selected) {
        Context context = chip.getContext();
        setRoundedBackground(chip, selected ? Palette.PRIMARY_CONTAINER
                        : Palette.SURFACE_CONTAINER, UiTokens.RADIUS_FULL,
                selected ? Color.TRANSPARENT : Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE);
        chip.setTextColor(selected ? Palette.ON_PRIMARY_CONTAINER
                : Palette.ON_SURFACE_VARIANT);
        chip.setTypeface(Typeface.create("sans-serif",
                selected ? Typeface.BOLD : Typeface.NORMAL));
        chip.setElevation(dp(context, selected ? UiTokens.ELEVATION_LOW
                : UiTokens.ELEVATION_NONE));
    }

    /** A small status badge, for example the red "conflict" pill. */
    public static TextView badge(Context context, CharSequence label, int container,
            int onContainer) {
        return pill(context, label, UiTokens.Type.LABEL_SMALL, container, onContainer,
                true, UiTokens.RADIUS_S);
    }

    /**
     * A rounded, filled label.
     *
     * <p>The one place a pill is drawn, so a week-parity chip, a course count and a
     * conflict badge are the same object at three sizes rather than three
     * hand-rolled backgrounds.</p>
     */
    public static TextView pill(Context context, CharSequence label, float sizeSp,
            int container, int onContainer, boolean bold, float radiusDp) {
        TextView pill = text(context, label, sizeSp, onContainer,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, UiTokens.SPACE_S + 1), dp(context, UiTokens.SPACE_XS + 1),
                dp(context, UiTokens.SPACE_S + 1), dp(context, UiTokens.SPACE_XS + 1));
        setRoundedBackground(pill, container, radiusDp, Color.TRANSPARENT, 0f);
        return pill;
    }

    // ------------------------------------------------------------------- controls

    /**
     * A text field.
     *
     * <p>Fields are filled in M3, and the filled tone is what separates an input
     * from the card it sits on without needing a box border around every one.</p>
     */
    public static EditText field(Context context, CharSequence hint, boolean centred) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setHintTextColor(Palette.OUTLINE);
        field.setTextColor(Palette.ON_SURFACE);
        field.setTextSize(UiTokens.Type.BODY_LARGE);
        field.setSingleLine(true);
        field.setPadding(dp(context, UiTokens.SPACE_M), dp(context, UiTokens.SPACE_S),
                dp(context, UiTokens.SPACE_M), dp(context, UiTokens.SPACE_S));
        field.setMinHeight(dp(context, UiTokens.FIELD_HEIGHT));
        field.setBackground(outline(context, Palette.SURFACE_CONTAINER_LOW,
                UiTokens.RADIUS_M, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        // The caret colour comes from the theme's accent, which themes.xml sets to
        // the brand colour, so no tinting is needed here.
        if (centred) {
            field.setGravity(Gravity.CENTER);
        }
        return field;
    }

    // ------------------------------------------------------------------ structure

    /** A vertical stack with the standard screen margin. */
    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L));
        return column;
    }

    /** Layout params for a full-width child with a top margin from the scale. */
    public static LinearLayout.LayoutParams stack(Context context, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topMarginDp);
        return params;
    }

    /** Layout params for a child that shares a row equally. */
    public static LinearLayout.LayoutParams weighted(Context context, float weight,
            int heightDp, int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, heightDp > 0 ? dp(context, heightDp) : ViewGroup.LayoutParams.WRAP_CONTENT,
                weight);
        params.leftMargin = dp(context, leftMarginDp);
        return params;
    }

    /** An empty state: a title, a hint, and nothing else competing for attention. */
    public static View emptyState(Context context, CharSequence title,
            CharSequence hint) {
        return emptyState(context, 0, title, hint);
    }

    /**
     * An empty state with a quiet glyph above it.
     *
     * <p>An icon rather than an illustration: the job is to stop a blank area
     * reading as a loading failure and to say which screen you are on, and one
     * muted 40dp glyph does both. Anything more competes with the hint.</p>
     *
     * @param iconRes a drawable to draw above the title, or 0 for none
     */
    public static View emptyState(Context context, int iconRes, CharSequence title,
            CharSequence hint) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(context, UiTokens.SPACE_XL), dp(context, UiTokens.SPACE_XXL),
                dp(context, UiTokens.SPACE_XL), dp(context, UiTokens.SPACE_XXL));
        box.setBackground(outline(context, Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_L, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));

        if (iconRes != 0) {
            android.widget.ImageView glyph = new android.widget.ImageView(context);
            glyph.setImageResource(iconRes);
            glyph.setColorFilter(Palette.OUTLINE_VARIANT);
            int size = dp(context, 40);
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(size, size);
            glyphParams.bottomMargin = dp(context, UiTokens.SPACE_M);
            box.addView(glyph, glyphParams);
        }

        TextView heading = text(context, title, UiTokens.Type.TITLE_SMALL,
                Palette.ON_SURFACE, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading);

        TextView detail = text(context, hint, UiTokens.Type.LABEL_LARGE,
                Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, UiTokens.SPACE_S);
        box.addView(detail, params);
        return box;
    }

    /** A horizontal hairline that lines up with the card margins. */
    public static View divider(Context context, int topMarginDp, int bottomMarginDp) {
        View divider = new View(context);
        divider.setBackgroundColor(Palette.OUTLINE_VARIANT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1f)));
        params.topMargin = dp(context, topMarginDp);
        params.bottomMargin = dp(context, bottomMarginDp);
        divider.setLayoutParams(params);
        return divider;
    }

    // ------------------------------------------------------------------ app bar

    /**
     * The standard app bar.
     *
     * <p>Every screen used to assemble its own: a dark band, a title, sometimes a
     * subtitle, sometimes an action. They agreed only by convention, which is how
     * the settings screen ended up with a different title size from the semester
     * screen. Here the bar is one object, on the page surface, with the padding
     * and the title row already decided.</p>
     *
     * @param title    the screen's name
     * @param subtitle a quiet second line, or {@code null}
     */
    public static LinearLayout appBar(Context context, CharSequence title,
            CharSequence subtitle) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Palette.HEADER_SURFACE);
        bar.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_M),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_S));
        bar.addView(appBarTitle(context, title));
        if (subtitle != null) {
            TextView line = appBarSubtitle(context, subtitle);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(context, 2);
            bar.addView(line, params);
        }
        return bar;
    }

    /** A screen's name, at the size the app bar uses for it. */
    public static TextView appBarTitle(Context context, CharSequence value) {
        return text(context, value, UiTokens.Type.TITLE_LARGE, Palette.ON_HEADER,
                Typeface.BOLD);
    }

    /** The quiet line under an app bar title. */
    public static TextView appBarSubtitle(Context context, CharSequence value) {
        return text(context, value, UiTokens.Type.LABEL_LARGE, Palette.ON_HEADER_VARIANT,
                Typeface.NORMAL);
    }

    /**
     * Wraps a bar's title row so a back arrow, the title and an action line up.
     *
     * <p>The arrow is inset by its own padding, so the row is pulled back by the
     * same amount: otherwise the title starts 8dp further right on a screen with a
     * back arrow than on one without, and the two screens' titles do not align
     * when you move between them.</p>
     */
    public static LinearLayout appBarRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    /**
     * The app bar for a screen pushed on top of another one: back, title, subtitle.
     *
     * <p>Six screens drew this themselves and no two agreed: four title sizes
     * between them, three amounts of side padding, and back arrows tinted three
     * different ways. One bar means a fix here reaches all of them.</p>
     *
     * <p>The title is {@link UiTokens.Type#TITLE_MEDIUM} here rather than the
     * {@code TITLE_LARGE} the home screen uses. That is the hierarchy working, not
     * an inconsistency: the home screen is a destination and gets the larger
     * title, while a detail bar is a label on a way back and should stay short.</p>
     *
     * @param onBack what the arrow does, usually {@code view -> finish()}
     */
    public static LinearLayout detailAppBar(Context context, CharSequence title,
            CharSequence subtitle, View.OnClickListener onBack) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(context, UiTokens.SPACE_XS), dp(context, UiTokens.SPACE_S),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_S));
        bar.setBackgroundColor(Palette.HEADER_SURFACE);

        android.widget.ImageButton back = backButton(context, onBack);
        bar.addView(back, new LinearLayout.LayoutParams(
                dp(context, UiTokens.ICON_BUTTON_SIZE),
                dp(context, UiTokens.ICON_BUTTON_SIZE)));

        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(text(context, title, UiTokens.Type.TITLE_MEDIUM,
                Palette.ON_HEADER, Typeface.BOLD));
        // Always present, even when empty: a screen that has to update the line in
        // place can then ask for it instead of reaching into the bar's children.
        TextView line = text(context, subtitle == null ? "" : subtitle,
                UiTokens.Type.LABEL_MEDIUM, Palette.ON_HEADER_VARIANT,
                Typeface.NORMAL);
        line.setMaxLines(1);
        line.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.topMargin = dp(context, 2);
        block.addView(line, lineParams);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blockParams.leftMargin = dp(context, UiTokens.SPACE_XS);
        bar.addView(block, blockParams);
        return bar;
    }

    /**
     * The subtitle line of a bar built by {@link #detailAppBar}.
     *
     * <p>For the one screen whose subtitle changes while it is open. The contract
     * is the builder's: the title block is the bar's last child and the subtitle
     * is the last child of that, which is why the two live next to each other.</p>
     */
    public static TextView appBarSubtitleOf(LinearLayout bar) {
        LinearLayout block = (LinearLayout) bar.getChildAt(bar.getChildCount() - 1);
        return (TextView) block.getChildAt(block.getChildCount() - 1);
    }

    /** The floating action button: a round, raised, brand-filled icon button. */
    public static android.widget.ImageButton fab(Context context, int iconRes,
            CharSequence description, View.OnClickListener listener) {
        android.widget.ImageButton fab = new android.widget.ImageButton(context);
        fab.setImageResource(iconRes);
        fab.setColorFilter(Palette.ON_PRIMARY);
        fab.setBackground(fill(context, Palette.PRIMARY, UiTokens.FAB_SIZE / 2f));
        fab.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L));
        fab.setContentDescription(description);
        fab.setOnClickListener(listener);
        ripple(fab, UiTokens.FAB_SIZE / 2f, true);
        elevate(fab, UiTokens.FAB_SIZE / 2f, UiTokens.ELEVATION_HIGH);
        return fab;
    }

    /**
     * A floating action button that is not the main one.
     *
     * <p>Two filled buttons stacked in a corner both claim to be the primary
     * action, and together they put more brand colour on the screen than anything
     * they act on. The secondary one gets a white surface and a brand icon, which
     * keeps it obviously a button while leaving the filled one to be the answer to
     * "what do I do here".</p>
     */
    public static android.widget.ImageButton fabTonal(Context context, int iconRes,
            CharSequence description, View.OnClickListener listener) {
        android.widget.ImageButton fab = new android.widget.ImageButton(context);
        fab.setImageResource(iconRes);
        fab.setColorFilter(Palette.PRIMARY);
        fab.setBackground(outline(context, Palette.SURFACE_CONTAINER,
                UiTokens.FAB_SIZE / 2f, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        fab.setPadding(dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L),
                dp(context, UiTokens.SPACE_L), dp(context, UiTokens.SPACE_L));
        fab.setContentDescription(description);
        fab.setOnClickListener(listener);
        ripple(fab, UiTokens.FAB_SIZE / 2f, false);
        elevate(fab, UiTokens.FAB_SIZE / 2f, UiTokens.ELEVATION_MEDIUM);
        return fab;
    }

    /**
     * An icon button sized for the app bar.
     *
     * <p>A quiet white tile on the light bar, with a hairline so it still reads as
     * a button. It used to be drawn on a dark header, where a translucent white
     * panel was enough to lift it off the background; on a light bar that has to
     * become a raised surface instead.</p>
     */
    public static android.widget.ImageButton headerIcon(Context context, int iconRes,
            CharSequence description, View.OnClickListener listener) {
        return iconButton(context, iconRes, description, UiTokens.ICON_BUTTON_SIZE,
                Palette.HEADER_ACTION_BG, Palette.ON_HEADER,
                Palette.HEADER_ACTION_BORDER, UiTokens.RADIUS_FULL, listener);
    }

    /**
     * A round icon button: an icon on a tinted surface with a hairline border.
     *
     * <p>The one place a button's size, radius, tint, border and feedback are
     * decided, so a week stepper, a header action and a dialog's icon action
     * cannot drift apart.</p>
     *
     * <p>{@code sizeDp} is how big the button <em>looks</em>; the view it lives in
     * is never smaller than {@link UiTokens#TOUCH_TARGET}. A 36dp circle is the
     * right weight for a week stepper and a 48dp one is not, but a control you can
     * see and cannot reliably hit is worse than either. The drawn surface is inset
     * inside the touchable one, which is the whole reason the background goes
     * through an {@link android.graphics.drawable.InsetDrawable} here rather than
     * being set directly.</p>
     */
    public static android.widget.ImageButton iconButton(Context context, int iconRes,
            CharSequence description, int sizeDp, int background, int tint, int border,
            float radiusDp, View.OnClickListener listener) {
        int visual = Math.max(1, sizeDp);
        int touch = Math.max(visual, UiTokens.TOUCH_TARGET);
        int inset = dp(context, (touch - visual) / 2f);

        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setImageResource(iconRes);
        button.setColorFilter(tint);
        GradientDrawable surface = outline(context, background, radiusDp, border,
                border == Color.TRANSPARENT ? 0f : UiTokens.STROKE_HAIRLINE);
        button.setBackground(inset > 0
                ? new android.graphics.drawable.InsetDrawable(surface, inset)
                : surface);
        int padding = dp(context, (touch - UiTokens.ICON_SIZE) / 2f);
        button.setPadding(padding, padding, padding, padding);
        button.setContentDescription(description);
        button.setOnClickListener(listener);
        // Both feedback channels, because an icon button has no text to change on
        // press: a ripple plus the same small scale the cards use.
        ripple(button, radiusDp, false);
        pressScale(button);
        if (sizeDp > 0) {
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(context, touch), dp(context, touch)));
        }
        return button;
    }

    /** A back arrow in the app bar. */
    public static android.widget.ImageButton backButton(Context context,
            View.OnClickListener listener) {
        android.widget.ImageButton back = new android.widget.ImageButton(context);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(Palette.ON_HEADER);
        back.setBackgroundColor(Color.TRANSPARENT);
        int padding = dp(context, UiTokens.SPACE_S);
        back.setPadding(padding, padding, padding, padding);
        // A transparent background still needs a ripple, or the arrow is the one
        // control in the app that does not answer a finger.
        ripple(back, UiTokens.RADIUS_FULL, false);
        back.setContentDescription("\u8FD4\u56DE");
        back.setOnClickListener(listener);
        return back;
    }

    // --------------------------------------------------------------------- util

    /**
     * Converts a dp value to pixels.
     *
     * <p>This is the single dp-to-pixel conversion in the app: every screen's
     * private {@code dp()} calls into it, and so does {@link UiTokens#px}. It has
     * to hold the real arithmetic rather than delegate to something else -- an
     * earlier revision had it calling itself after a careless rename, which
     * crashed the app on the first layout with a {@code StackOverflowError}.</p>
     */
    public static int dp(Context context, float value) {
        return Math.round(android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    /** A spacing value from the scale, in pixels. */
    public static int space(Context context, int token) {
        return dp(context, token);
    }

    // ------------------------------------------------------------------ feedback

    /**
     * Adds a ripple over whatever the view already draws.
     *
     * <p>Uses the foreground slot on purpose: a ripple set as the background
     * would replace the view's own rounded surface. The foreground is clipped to
     * the view's outline, so on a card the ripple dies at the rounded corner
     * instead of spilling into a rectangle.</p>
     *
     * @param onDark true when the view sits on a dark surface, which needs a
     *               light ripple rather than a dark one
     */
    public static void ripple(View view, float radiusDp, boolean onDark) {
        // minSdk is 26, so the foreground slot and its clipping flag are always
        // available; no version check is needed here.
        int colour = onDark ? 0x33FFFFFF : 0x14000000;
        android.graphics.drawable.RippleDrawable ripple =
                new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(colour), null,
                        fill(view.getContext(), colour, radiusDp));
        view.setForeground(ripple);
        view.setClipToOutline(true);
    }

    /**
     * Makes a view tappable with a press animation.
     *
     * <p>The scale is small and quick on purpose: 0.97 over 90 ms reads as the
     * surface responding to the finger, while anything slower or deeper reads as
     * a toy.</p>
     */
    public static void pressable(View view, View.OnClickListener listener) {
        view.setOnClickListener(listener);
        view.setClickable(true);
        pressScale(view);
    }

    /**
     * Adds only the press animation, leaving the click machinery alone.
     *
     * <p>For views that already wired themselves up, and for anything built with
     * {@link #pressable}.</p>
     */
    public static void pressScale(View view) {
        if (view instanceof PressFeedback) {
            ((PressFeedback) view).animatesPress();
            return;
        }
        // A view that cannot be swapped for the subclass -- an ImageButton built
        // elsewhere, say -- still gets a working button. Nothing is attached here
        // on purpose: see the note on PressFeedback for what went wrong when a
        // touch listener tried to drive the animation on every view.
    }

    /**
     * A layout that shrinks slightly while a finger is down.
     *
     * <p>Deliberately implemented by overriding {@code dispatchTouchEvent} rather
     * than with {@code setOnTouchListener}. An earlier revision used a touch
     * listener that animated on ACTION_DOWN and called {@code performClick()} on
     * ACTION_UP, which fought the framework's own click dispatch: sometimes the
     * click fired twice, and sometimes the listener's involvement left the view
     * waiting for a second tap, so buttons appeared to need two presses.</p>
     *
     * <p>Here the event is only observed and always handed on, so click
     * detection stays exactly the platform's. The cost is that a control built as
     * an {@code ImageButton} cannot use this -- a view's superclass is fixed --
     * which is why {@link #pressScale} degrades to no animation instead of
     * reaching for a touch listener again.</p>
     */
    public static class PressFeedback extends LinearLayout {
        private static final float PRESSED = 0.96f;

        public PressFeedback(Context context) {
            super(context);
        }

        /** Marks the view as wanting the press animation. */
        public final void animatesPress() {
            // The animation is driven from dispatchTouchEvent; this exists so the
            // intent is visible at the call site.
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    animatePress(this, PRESSED, 90);
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                case android.view.MotionEvent.ACTION_UP:
                    animatePress(this, 1f, 150);
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private static void animatePress(View view, float scale, long duration) {
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    // ------------------------------------------------------------------- motion

    /**
     * Fades and lifts a view into place.
     *
     * @param delayMs stagger between siblings; small values keep a list feeling
     *                like it arrived at once rather than one item at a time
     */
    public static void enter(View view, long delayMs) {
        view.setAlpha(0f);
        view.setTranslationY(dp(view.getContext(), UiTokens.SPACE_S));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    /** Fades a whole block in, for a day or week change. */
    public static void fadeIn(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(160).start();
    }

    /**
     * Brings swapped content in from the side it logically came from.
     *
     * <p>A plain fade says "something changed"; a short slide says <em>which way</em>.
     * Stepping to the next week and stepping back are the two things this screen
     * does most, and they were indistinguishable. The travel is deliberately tiny
     * -- a sixth of the content's own height, capped -- because the point is the
     * direction, not the distance.</p>
     *
     * @param direction -1 for back, +1 for forward, 0 for "no direction"
     */
    public static void swapIn(View view, int direction) {
        float travel = Math.min(dp(view.getContext(), 12),
                Math.max(1, view.getHeight()) / 6f) * Math.signum(direction);
        view.setAlpha(0f);
        view.setTranslationX(travel);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    /** A small leading icon for a metadata line. */
    public static android.widget.ImageView inlineIcon(Context context, int iconRes,
            int tint) {
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(tint);
        int size = dp(context, UiTokens.ICON_SIZE - 2);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return icon;
    }

    // -------------------------------------------------------------------- sheets

    /**
     * Shows a Material 3 style bottom sheet holding {@code content}.
     *
     * <p>Hand-built because {@code BottomSheetDialog} lives in the Material
     * library, which this project cannot depend on. What is reproduced is the
     * part that matters: a panel pinned to the bottom of the screen with only its
     * top corners rounded, a drag handle, a dim backdrop that dismisses it, a
     * slide-up entrance, and the whole panel wrapped in a scroll view so a tall
     * grid cannot run off the top of a short screen.</p>
     *
     * @param title the sheet's heading, above the content
     * @return the dialog, so the caller can dismiss it
     */
    public static android.app.Dialog bottomSheet(Context context, CharSequence title,
            View content) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(outline(context, Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_XL, Palette.OUTLINE_VARIANT,
                UiTokens.STROKE_HAIRLINE));
        // Square off the bottom: the panel meets the screen edge, so only the top
        // corners should read as rounded.
        ((GradientDrawable) panel.getBackground()).setCornerRadii(new float[] {
                dp(context, UiTokens.RADIUS_XL), dp(context, UiTokens.RADIUS_XL),
                dp(context, UiTokens.RADIUS_XL), dp(context, UiTokens.RADIUS_XL),
                0f, 0f, 0f, 0f});
        panel.setPadding(0, dp(context, UiTokens.SPACE_S), 0,
                dp(context, UiTokens.SPACE_L));

        View handle = new View(context);
        handle.setBackground(fill(context, Palette.OUTLINE_VARIANT, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                dp(context, UiTokens.SPACE_XXL), dp(context, 4f));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, handleParams);

        TextView heading = text(context, title, UiTokens.Type.TITLE_MEDIUM,
                Palette.ON_SURFACE, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.leftMargin = dp(context, UiTokens.SPACE_L);
        headingParams.rightMargin = dp(context, UiTokens.SPACE_L);
        headingParams.topMargin = dp(context, UiTokens.SPACE_M);
        headingParams.bottomMargin = dp(context, UiTokens.SPACE_XS);
        panel.addView(heading, headingParams);

        android.widget.ScrollView scroller = new android.widget.ScrollView(context);
        scroller.setClipToPadding(false);
        scroller.addView(content, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(SHEET_ANIMATION);
        }
        // Tapping the dim area dismisses, which is what makes it read as a sheet
        // rather than a dialog that has to be answered.
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        return dialog;
    }

    /**
     * The window animation for a bottom sheet.
     *
     * <p>Referenced as {@code R.style.SheetAnimation} rather than looked up by
     * name: a name lookup that misses animates nothing and says nothing, while a
     * wrong resource reference does not compile.</p>
     */
    private static final int SHEET_ANIMATION = R.style.SheetAnimation;

    // ------------------------------------------------------------------ dialogs

    /**
     * A button in an {@link #dialog}.
     *
     * <p>The colour is part of the button rather than a flag, because the useful
     * distinction is not "primary or not" but "does this button destroy
     * something". A destructive action drawn in the brand colour reads as the
     * recommended one; on a delete confirmation that is the wrong signal.</p>
     */
    public static final class Action {
        private final CharSequence label;
        private final int color;
        private final boolean filled;
        private final Object onClick;
        private Action(CharSequence label, int color, boolean filled, Object onClick) {
            this.label = label;
            this.color = color;
            this.filled = filled;
            this.onClick = onClick;
        }

        /** An ordinary action, in the brand colour. */
        public static Action of(CharSequence label, Runnable work) {
            return new Action(label, Palette.PRIMARY, false, work);
        }

        /**
         * An action whose work can refuse, keeping the dialog open.
         *
         * <p>A separate factory rather than an overload: a lambda has no type of its
         * own, so {@code of(label, () -> ...)} cannot tell {@link Runnable} and
         * {@link Success} apart on arity alone, and Java would pick the wrong one.</p>
         */
        public static Action validated(CharSequence label, Success work) {
            return new Action(label, Palette.PRIMARY, true, work);
        }

        /** An action that destroys data: red, and never the filled one. */
        public static Action destructive(CharSequence label, Runnable work) {
            return new Action(label, Palette.ERROR, false, work);
        }
    }

    /**
     * A task that can refuse.
     *
     * <p>Used with {@link Action#keepingOpenOnFailure()} so a validated form closes
     * only when it actually succeeded -- the error stays on screen next to the
     * field that caused it.</p>
     */
    public interface Success {
        boolean run();
    }

    /**
     * A Material 3 dialog: title, body, and a row of actions.
     *
     * <p>Replaces {@code AlertDialog}, which draws the platform's own idea of a
     * dialog -- square corners, a title far larger than the app's own headings,
     * action labels in the accent colour regardless of what they do, and edges
     * that run the full width of the screen. Every one of those fights the rest of
     * this app, so the chrome is drawn here instead and only the window and the
     * dismissal behaviour are left to the platform.</p>
     *
     * @param body      the message, or {@code null} for a title-only dialog
     * @param content   a custom view, or {@code null}; shown under the body
     * @param onDismiss run when the dialog closes by any route, so a caller can
     *                  tell "dismissed" from "answered"
     * @return the dialog, so the caller can close it
     */
    public static android.app.Dialog dialog(Context context, CharSequence title,
            CharSequence body, View content, Runnable onDismiss, Action... actions) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(fill(context, Palette.SURFACE_CONTAINER,
                UiTokens.RADIUS_XL));
        int pad = dp(context, UiTokens.SPACE_XL);
        panel.setPadding(pad, pad, pad, dp(context, UiTokens.SPACE_M));

        TextView heading = text(context, title, UiTokens.Type.TITLE_MEDIUM,
                Palette.ON_SURFACE, Typeface.BOLD);
        panel.addView(heading);

        if (body != null && body.length() > 0) {
            TextView message = text(context, body, UiTokens.Type.BODY_LARGE,
                    Palette.ON_SURFACE_VARIANT, Typeface.NORMAL);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(context, UiTokens.SPACE_M);
            panel.addView(message, messageParams);
        }

        if (content != null) {
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            contentParams.topMargin = dp(context, UiTokens.SPACE_L);
            panel.addView(content, contentParams);
        }

        if (actions.length > 0) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(context, UiTokens.SPACE_L);
            panel.addView(row, rowParams);

            final android.app.Dialog[] holder = new android.app.Dialog[1];
            for (int i = 0; i < actions.length; i++) {
                final Action action = actions[i];
                TextView button = action.filled
                        ? filledButton(context, action.label, null)
                        : textButton(context, action.label, action.color, null);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        boolean ok = true;
                        if (action.onClick instanceof Success) {
                            ok = ((Success) action.onClick).run();
                        } else if (action.onClick instanceof Runnable) {
                            ((Runnable) action.onClick).run();
                        }
                        if (ok && holder[0] != null) {
                            holder[0].dismiss();
                        }
                    }
                });
                ripple(button, action.filled ? UiTokens.RADIUS_M : UiTokens.RADIUS_S,
                        false);
                LinearLayout.LayoutParams buttonParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                action.filled ? dp(context, UiTokens.BUTTON_HEIGHT)
                                        : ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    buttonParams.leftMargin = dp(context, UiTokens.SPACE_S);
                }
                row.addView(button, buttonParams);
            }

            android.app.Dialog dialog = showDialog(context, panel, onDismiss);
            holder[0] = dialog;
            return dialog;
        }
        return showDialog(context, panel, onDismiss);
    }

    /** Titled message with one way out, for something that only needs acknowledging. */
    public static android.app.Dialog message(Context context, CharSequence title,
            CharSequence body) {
        return dialog(context, title, body, null, null, Action.of("\u77E5\u9053", null));
    }

    private static android.app.Dialog showDialog(Context context, View panel,
            final Runnable onDismiss) {
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT));
            // Lighter than the platform default: a dialog should sit on the page,
            // not black it out.
            window.setDimAmount(0.38f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Inset from the screen edges, which also decides how wide the panel
            // can grow on a large display.
            window.setLayout(Math.min(
                            context.getResources().getDisplayMetrics().widthPixels
                                    - dp(context, UiTokens.SPACE_XXL * 2),
                            dp(context, 400)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setWindowAnimations(DIALOG_ANIMATION);
        }
        dialog.setCanceledOnTouchOutside(true);
        if (onDismiss != null) {
            dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface ignored) {
                    onDismiss.run();
                }
            });
        }
        dialog.show();
        return dialog;
    }

    /** Fade-and-scale entrance, the platform's dialog animation for Material. */
    private static final int DIALOG_ANIMATION = R.style.DialogAnimation;

    // ----------------------------------------------------------------- controls

    /**
     * A checkbox drawn to the app's own scale.
     *
     * <p>The platform widget is not used because its box is sized and tinted by
     * the theme rather than by this app, and it lands next to text that is set
     * here -- so it looks borrowed. State lives in an {@code AtomicBoolean}-like
     * holder rather than in the view, because a hand-drawn box has no
     * {@code isChecked()} to ask.</p>
     */
    public static final class Check {
        private final View row;
        private final TextView box;
        private final TextView caption;
        private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();
        private boolean checked;

        private Check(View row, TextView box, TextView caption, boolean checked) {
            this.row = row;
            this.box = box;
            this.caption = caption;
            this.checked = checked;
            paint();
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean value) {
            if (checked == value) {
                return;
            }
            checked = value;
            paint();
            for (Runnable listener : listeners) {
                listener.run();
            }
        }

        /** Runs whenever the state changes, including from a tap on the row. */
        public void onChanged(Runnable listener) {
            listeners.add(listener);
        }

        public View view() {
            return row;
        }

        private void paint() {
            Context context = row.getContext();
            // A box drawn as an outlined square, filled in the brand colour with a
            // tick when on. A tick rather than a filled square: at 20dp a filled
            // square is indistinguishable from a selected radio button.
            box.setBackground(outline(context,
                    checked ? Palette.PRIMARY : Color.TRANSPARENT,
                    UiTokens.RADIUS_XS,
                    checked ? Color.TRANSPARENT : Palette.OUTLINE,
                    UiTokens.STROKE_EMPHASIS));
            box.setGravity(Gravity.CENTER);
            box.setText(checked ? "\u2713" : "");
            box.setTextColor(Palette.ON_PRIMARY);
            box.setTextSize(UiTokens.Type.LABEL_LARGE);
            box.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            caption.setTextColor(checked ? Palette.ON_SURFACE
                    : Palette.ON_SURFACE_VARIANT);
        }
    }

    /**
     * A labelled checkbox: a box drawn here, and a label that taps with it.
     *
     * <p>The whole row is the target, not just the box. A 20dp box is far below
     * the minimum touch size, and a settings-style row is what people actually
     * aim at.</p>
     */
    public static Check checkbox(Context context, CharSequence label,
            boolean initiallyChecked) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, UiTokens.SPACE_S), dp(context, UiTokens.SPACE_S),
                dp(context, UiTokens.SPACE_S), dp(context, UiTokens.SPACE_S));
        row.setMinimumHeight(dp(context, UiTokens.TOUCH_TARGET));

        final TextView box = new TextView(context);
        final int boxSize = dp(context, UiTokens.ICON_SIZE - 2);
        row.addView(box, new LinearLayout.LayoutParams(boxSize, boxSize));

        final TextView caption = text(context, label, UiTokens.Type.BODY_MEDIUM,
                Palette.ON_SURFACE, Typeface.NORMAL);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        captionParams.leftMargin = dp(context, UiTokens.SPACE_M);
        row.addView(caption, captionParams);

        final Check check = new Check(row, box, caption, initiallyChecked);
        ripple(row, UiTokens.RADIUS_S, false);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                check.setChecked(!check.isChecked());
            }
        });
        return check;
    }

    /**
     * A row of radio options, drawn here.
     *
     * <p>Same reasoning as {@link #checkbox}: the platform button is themed
     * elsewhere, and three of them in a row next to this app's typography read as
     * a different application.</p>
     *
     * @return a view whose selected index can be read with {@link Choice#selected}
     */
    public static Choice radioRow(Context context, CharSequence[] labels,
            int selectedIndex) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final Choice choice = new Choice(row, selectedIndex);
        final java.util.List<TextView> dots = new java.util.ArrayList<>();
        final java.util.List<TextView> captions = new java.util.ArrayList<>();

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout option = new LinearLayout(context);
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinimumHeight(dp(context, UiTokens.TOUCH_TARGET));

            TextView dot = new TextView(context);
            int dotSize = dp(context, UiTokens.ICON_SIZE);
            option.addView(dot, new LinearLayout.LayoutParams(dotSize, dotSize));
            dots.add(dot);

            TextView caption = text(context, labels[i], UiTokens.Type.BODY_MEDIUM,
                    Palette.ON_SURFACE, Typeface.NORMAL);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            captionParams.leftMargin = dp(context, UiTokens.SPACE_S);
            option.addView(caption, captionParams);
            captions.add(caption);

            ripple(option, UiTokens.RADIUS_S, false);
            option.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    choice.selected = index;
                    paintChoice(context, dots, captions, choice.selected);
                }
            });

            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(option, optionParams);
        }
        paintChoice(context, dots, captions, selectedIndex);
        return choice;
    }

    private static void paintChoice(Context context, java.util.List<TextView> dots,
            java.util.List<TextView> captions, int selected) {
        for (int i = 0; i < dots.size(); i++) {
            boolean on = i == selected;
            TextView dot = dots.get(i);
            dot.setGravity(Gravity.CENTER);
            // A small dot inside a ring, not a filled disc: "●" at the label size
            // fills the whole circle and the two states stop reading as a pair.
            dot.setText(on ? "\u2022" : "");
            dot.setTextColor(Palette.ON_PRIMARY);
            dot.setTextSize(UiTokens.Type.LABEL_SMALL);
            dot.setBackground(outline(context, on ? Palette.PRIMARY
                            : Color.TRANSPARENT, UiTokens.RADIUS_FULL,
                    on ? Color.TRANSPARENT : Palette.OUTLINE,
                    UiTokens.STROKE_EMPHASIS));
            captions.get(i).setTextColor(on ? Palette.ON_SURFACE
                    : Palette.ON_SURFACE_VARIANT);
            captions.get(i).setTypeface(Typeface.create("sans-serif",
                    on ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    /**
     * The selected index of a {@link #radioRow}, and the view showing it.
     *
     * <p>The view travels with the state so a caller cannot wire up one row and
     * read another: the two are handed out together and there is nothing to keep in
     * step by hand.</p>
     */
    public static final class Choice {
        private final View row;
        private int selected;

        private Choice(View row, int selected) {
            this.row = row;
            this.selected = selected;
        }

        /** The row, to be added to the form. */
        public View view() {
            return row;
        }

        public int selected() {
            return selected;
        }
    }
}
