package com.weihe.schedule;

/**
 * WCAG contrast arithmetic, so the palette can be checked without a device.
 *
 * <p>This exists because "the text is hard to read" is the failure mode of a
 * hand-built theme that no test can see. Contrast is arithmetic, so it can be
 * asserted: {@code PaletteContrastTest} walks every text/background pair the app
 * actually draws and fails if one drops below the AA threshold.</p>
 *
 * <p>Only plain arithmetic on {@code int} colours, so it runs on a bare JVM.</p>
 */
public final class Colors {

    /** WCAG AA for body text. */
    public static final double AA_NORMAL = 4.5;
    /** WCAG AA for text at 18sp and above, or 14sp bold. */
    public static final double AA_LARGE = 3.0;
    /** WCAG AAA for body text. */
    public static final double AAA_NORMAL = 7.0;

    private Colors() {
    }

    /** Blends {@code color} towards {@code background}, as the UI does for tints. */
    public static int blend(int color, int background, float amount) {
        float safe = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(red(color) * (1f - safe) + red(background) * safe);
        int green = Math.round(green(color) * (1f - safe) + green(background) * safe);
        int blue = Math.round(blue(color) * (1f - safe) + blue(background) * safe);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Contrast ratio between two opaque colours, from 1.0 to 21.0.
     *
     * <p>Alpha is ignored: every colour in {@link Palette} that is meant to be
     * read against another is opaque. Translucent header panels are excluded from
     * the contrast test rather than approximated.</p>
     */
    public static double contrastRatio(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** True when the pair clears {@code threshold}. */
    public static boolean meets(int foreground, int background, double threshold) {
        return contrastRatio(foreground, background) >= threshold;
    }

    /** Relative luminance, per the WCAG definition. */
    public static double luminance(int color) {
        return 0.2126 * channel(red(color))
                + 0.7152 * channel(green(color))
                + 0.0722 * channel(blue(color));
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    private static double channel(int value) {
        double ratio = value / 255.0;
        return ratio <= 0.03928
                ? ratio / 12.92
                : Math.pow((ratio + 0.055) / 1.055, 2.4);
    }
}
