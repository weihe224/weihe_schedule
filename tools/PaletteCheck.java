/**
 * Offline checker for candidate palettes.
 *
 * <p>Mirrors the arithmetic in {@code Colors} and the assertions in
 * {@code PaletteContrastTest} so a palette can be tuned in one pass instead of one
 * Gradle build per attempt. It is a development tool: nothing here ships, and the
 * real assertions still live in the unit test, which is what actually guards the
 * palette.</p>
 *
 * <p>Usage: {@code java tools/PaletteCheck.java}</p>
 */
public final class PaletteCheck {

    // ------------------------------------------------------------ candidates

    static final int PRIMARY = 0xFF11584E;
    static final int ON_PRIMARY = 0xFFFFFFFF;
    static final int PRIMARY_CONTAINER = 0xFFCFE5DE;
    static final int ON_PRIMARY_CONTAINER = 0xFF06322C;

    static final int SECONDARY = 0xFF44605A;
    static final int ON_SECONDARY = 0xFFFFFFFF;
    static final int SECONDARY_CONTAINER = 0xFFDBE8E3;
    static final int ON_SECONDARY_CONTAINER = 0xFF14201D;

    static final int ACCENT = 0xFF94521F;
    static final int ON_ACCENT = 0xFFFFFFFF;
    static final int ACCENT_CONTAINER = 0xFFFBE2CE;
    static final int ON_ACCENT_CONTAINER = 0xFF4A2008;

    static final int SURFACE = 0xFFF3F7F3;
    static final int SURFACE_CONTAINER_LOW = 0xFFEDF3EE;
    static final int SURFACE_CONTAINER = 0xFFFFFFFF;
    static final int SURFACE_CONTAINER_HIGH = 0xFFE8EFEA;
    static final int SURFACE_CONTAINER_HIGHEST = 0xFFE0E9E3;

    static final int ON_SURFACE = 0xFF16201D;
    static final int ON_SURFACE_VARIANT = 0xFF4E5C57;
    static final int OUTLINE = 0xFF78867F;
    static final int OUTLINE_VARIANT = 0xFFDFE7E1;

    static final int ERROR = 0xFFAF2E27;
    static final int ON_ERROR = 0xFFFFFFFF;
    static final int ERROR_CONTAINER = 0xFFFADBD8;
    static final int ON_ERROR_CONTAINER = 0xFF3F0D0A;

    static final int SUCCESS = 0xFF2A6B4F;
    static final int ON_SUCCESS = 0xFFFFFFFF;
    static final int SUCCESS_CONTAINER = 0xFFCFEBDD;
    static final int ON_SUCCESS_CONTAINER = 0xFF082E1C;

    static final float COURSE_TINT = 0.90f;
    static final float COURSE_BORDER_TINT = 0.55f;
    static final float COURSE_TEXT_MIX = 0.20f;

    /** Low-saturation course palette: blue, green, orange, then quieter hues. */
    static final int[] COURSE_COLORS = {
            0xFF3C6A8E, // slate blue -- the default
            0xFF3A7563, // sage green
            0xFF94521F, // soft orange
            0xFF6A5F94, // dusty violet
            0xFF35707E, // deep aqua
            0xFF9A5454, // brick
            0xFF7E6C33, // olive gold
            0xFF525F6B, // graphite
            0xFF8C5A70, // dusty plum
    };

    public static void main(String[] args) {
        int failures = 0;

        failures += pair("ink / page", ON_SURFACE, SURFACE, 4.5);
        failures += pair("ink / low surface", ON_SURFACE, SURFACE_CONTAINER_LOW, 4.5);
        failures += pair("ink / card", ON_SURFACE, SURFACE_CONTAINER, 4.5);
        failures += pair("ink / high surface", ON_SURFACE, SURFACE_CONTAINER_HIGH, 4.5);
        failures += pair("ink / highest surface", ON_SURFACE, SURFACE_CONTAINER_HIGHEST, 4.5);

        failures += pair("variant / page", ON_SURFACE_VARIANT, SURFACE, 4.5);
        failures += pair("variant / card", ON_SURFACE_VARIANT, SURFACE_CONTAINER, 4.5);
        failures += pair("variant / low", ON_SURFACE_VARIANT, SURFACE_CONTAINER_LOW, 4.5);
        failures += pair("variant / high", ON_SURFACE_VARIANT, SURFACE_CONTAINER_HIGH, 4.5);
        failures += pair("variant / highest", ON_SURFACE_VARIANT,
                SURFACE_CONTAINER_HIGHEST, 4.5);
        failures += pair("variant / primary container", ON_SURFACE_VARIANT,
                PRIMARY_CONTAINER, 4.5);

        failures += pair("hint / card", OUTLINE, SURFACE_CONTAINER, 3.0);
        failures += pair("hint / field", OUTLINE, SURFACE_CONTAINER_LOW, 3.0);

        failures += pair("on primary", ON_PRIMARY, PRIMARY, 4.5);
        failures += pair("on primary container", ON_PRIMARY_CONTAINER,
                PRIMARY_CONTAINER, 4.5);
        failures += pair("on secondary", ON_SECONDARY, SECONDARY, 4.5);
        failures += pair("on secondary container", ON_SECONDARY_CONTAINER,
                SECONDARY_CONTAINER, 4.5);
        failures += pair("on accent", ON_ACCENT, ACCENT, 4.5);
        failures += pair("on accent container", ON_ACCENT_CONTAINER,
                ACCENT_CONTAINER, 4.5);
        failures += pair("on error", ON_ERROR, ERROR, 4.5);
        failures += pair("on error container", ON_ERROR_CONTAINER, ERROR_CONTAINER, 4.5);
        failures += pair("on success", ON_SUCCESS, SUCCESS, 4.5);
        failures += pair("on success container", ON_SUCCESS_CONTAINER,
                SUCCESS_CONTAINER, 4.5);

        failures += pair("primary / card", PRIMARY, SURFACE_CONTAINER, 4.5);
        failures += pair("primary / page", PRIMARY, SURFACE, 4.5);
        failures += pair("error / card", ERROR, SURFACE_CONTAINER, 4.5);
        failures += pair("accent / card", ACCENT, SURFACE_CONTAINER, 4.5);
        failures += pair("success / card", SUCCESS, SURFACE_CONTAINER, 4.5);
        failures += pair("primary / primary container", PRIMARY, PRIMARY_CONTAINER, 4.5);
        failures += pair("accent / accent container", ACCENT, ACCENT_CONTAINER, 4.5);
        failures += pair("error / error container", ERROR, ERROR_CONTAINER, 4.5);
        failures += pair("success / success container", SUCCESS, SUCCESS_CONTAINER, 4.5);

        // Header roles: the app bar is the page surface with dark text on it.
        failures += pair("title on header", ON_SURFACE, SURFACE, 4.5);
        failures += pair("subtitle on header", ON_SURFACE_VARIANT, SURFACE, 4.5);
        failures += pair("icon on header action", ON_SURFACE, SURFACE_CONTAINER, 4.5);

        System.out.println();
        failures += range("card stands out from page",
                contrast(SURFACE_CONTAINER, SURFACE), 1.02, 99);
        failures += range("field stands out from card",
                contrast(SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER), 1.05, 99);
        failures += range("divider on card visible",
                contrast(OUTLINE_VARIANT, SURFACE_CONTAINER), 1.08, 2.0);
        failures += range("header action stands out on header",
                contrast(SURFACE_CONTAINER, SURFACE), 1.02, 2.0);
        failures += range("primary container reads on the page",
                contrast(PRIMARY_CONTAINER, SURFACE), 1.05, 3.0);

        System.out.println();
        for (int color : COURSE_COLORS) {
            int background = blend(color, 0xFFFFFFFF, COURSE_TINT);
            int mixBase = blend(ON_SURFACE, 0xFFFFFFFF, COURSE_TEXT_MIX);
            int text = blend(color, mixBase, COURSE_TEXT_MIX);
            int border = blend(color, 0xFFFFFFFF, COURSE_BORDER_TINT);
            System.out.printf("#%06X  name/text %s  border %s  bg #%06X%n",
                    color & 0xFFFFFF,
                    verdict(contrast(text, background), 4.5),
                    verdict(contrast(border, background), 1.4, 2.5),
                    background & 0xFFFFFF);
            if (contrast(text, background) < 4.5) {
                failures++;
            }
            if (contrast(border, background) < 1.4 || contrast(border, background) > 2.5) {
                failures++;
            }
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASS" : failures + " CHECK(S) FAILED");
    }

    private static int pair(String what, int foreground, int background, double min) {
        double ratio = contrast(foreground, background);
        boolean ok = ratio >= min;
        System.out.printf("%-34s %s  (%.2f, need %.2f)%n", what, ok ? "ok  " : "FAIL", ratio, min);
        return ok ? 0 : 1;
    }

    private static int range(String what, double value, double min, double max) {
        boolean ok = value >= min && value <= max;
        System.out.printf("%-34s %s  (%.3f, need %.2f..%.2f)%n",
                what, ok ? "ok  " : "FAIL", value, min, max);
        return ok ? 0 : 1;
    }

    private static String verdict(double value, double min) {
        return String.format("%s %.2f", value >= min ? "ok  " : "FAIL", value);
    }

    private static String verdict(double value, double min, double max) {
        return String.format("%s %.2f", value >= min && value <= max ? "ok  " : "FAIL", value);
    }

    // ------------------------------------------------------- copied arithmetic

    static int blend(int color, int background, float amount) {
        float safe = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(red(color) * (1f - safe) + red(background) * safe);
        int green = Math.round(green(color) * (1f - safe) + green(background) * safe);
        int blue = Math.round(blue(color) * (1f - safe) + blue(background) * safe);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    static double contrast(int first, int second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    static double luminance(int color) {
        return 0.2126 * channel(red(color)) + 0.7152 * channel(green(color))
                + 0.0722 * channel(blue(color));
    }

    static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    static int blue(int color) {
        return color & 0xFF;
    }

    private static double channel(int value) {
        double ratio = value / 255.0;
        return ratio <= 0.03928 ? ratio / 12.92 : Math.pow((ratio + 0.055) / 1.055, 2.4);
    }
}
