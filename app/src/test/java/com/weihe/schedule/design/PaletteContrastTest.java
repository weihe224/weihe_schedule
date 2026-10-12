package com.weihe.schedule.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.ui.UiKit;
import com.weihe.schedule.ui.UiTokens;

/**
 * Contrast checks for every text/background pair the app actually draws.
 *
 * <p>A hand-built theme has one failure mode that no compiler and no layout test
 * can catch: text that is technically "there" but too close in tone to its
 * background to read. That is arithmetic, so it is asserted here rather than
 * discovered on a phone in sunlight.</p>
 *
 * <p>The thresholds are WCAG AA: 4.5:1 for body text, 3:1 for large or bold
 * text. Where a pair fails, the fix belongs in {@link Palette} -- the point of
 * this test is to make that the only place to change.</p>
 */
public class PaletteContrastTest {

    private static void assertReadable(String what, int foreground, int background) {
        double ratio = Colors.contrastRatio(foreground, background);
        assertTrue(what + " has contrast " + round(ratio) + ":1, below AA "
                        + Colors.AA_NORMAL,
                ratio >= Colors.AA_NORMAL);
    }

    private static void assertLegible(String what, int foreground, int background) {
        double ratio = Colors.contrastRatio(foreground, background);
        assertTrue(what + " has contrast " + round(ratio) + ":1, below AA-large "
                        + Colors.AA_LARGE,
                ratio >= Colors.AA_LARGE);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Test
    public void bodyTextIsReadableOnEverySurface() {
        assertReadable("ink on the page", Palette.ON_SURFACE, Palette.SURFACE);
        assertReadable("ink on a low surface",
                Palette.ON_SURFACE, Palette.SURFACE_CONTAINER_LOW);
        assertReadable("ink on a card", Palette.ON_SURFACE, Palette.SURFACE_CONTAINER);
        assertReadable("ink on a high surface",
                Palette.ON_SURFACE, Palette.SURFACE_CONTAINER_HIGH);
        assertReadable("ink on the highest surface",
                Palette.ON_SURFACE, Palette.SURFACE_CONTAINER_HIGHEST);
    }

    @Test
    public void secondaryTextIsReadableOnEverySurface() {
        assertReadable("variant text on the page",
                Palette.ON_SURFACE_VARIANT, Palette.SURFACE);
        assertReadable("variant text on a card",
                Palette.ON_SURFACE_VARIANT, Palette.SURFACE_CONTAINER);
        assertReadable("variant text on a low surface",
                Palette.ON_SURFACE_VARIANT, Palette.SURFACE_CONTAINER_LOW);
        assertReadable("variant text on a high surface",
                Palette.ON_SURFACE_VARIANT, Palette.SURFACE_CONTAINER_HIGH);
    }

    @Test
    public void placeholderTextIsAtLeastLargeTextLegible() {
        // Hints and empty-state text are the quietest thing on screen, so they
        // are held to the large-text bar rather than the body-text one.
        assertLegible("hint on a card", Palette.OUTLINE, Palette.SURFACE_CONTAINER);
        assertLegible("hint on a field", Palette.OUTLINE, Palette.SURFACE_CONTAINER_LOW);
    }

    @Test
    public void textOnColouredContainersIsReadable() {
        assertReadable("on primary", Palette.ON_PRIMARY, Palette.PRIMARY);
        assertReadable("on primary container",
                Palette.ON_PRIMARY_CONTAINER, Palette.PRIMARY_CONTAINER);
        assertReadable("on secondary", Palette.ON_SECONDARY, Palette.SECONDARY);
        assertReadable("on secondary container",
                Palette.ON_SECONDARY_CONTAINER, Palette.SECONDARY_CONTAINER);
        assertReadable("on accent", Palette.ON_ACCENT, Palette.ACCENT);
        assertReadable("on accent container",
                Palette.ON_ACCENT_CONTAINER, Palette.ACCENT_CONTAINER);
        assertReadable("on error", Palette.ON_ERROR, Palette.ERROR);
        assertReadable("on error container",
                Palette.ON_ERROR_CONTAINER, Palette.ERROR_CONTAINER);
        assertReadable("on success", Palette.ON_SUCCESS, Palette.SUCCESS);
        assertReadable("on success container",
                Palette.ON_SUCCESS_CONTAINER, Palette.SUCCESS_CONTAINER);
    }

    @Test
    public void theHeaderIsReadable() {
        assertReadable("title in the app bar", Palette.ON_HEADER,
                Palette.HEADER_SURFACE);
        assertReadable("subtitle in the app bar", Palette.ON_HEADER_VARIANT,
                Palette.HEADER_SURFACE);
        // The bar is the page surface, so the status bar is painted the same
        // colour: there is no seam to check, but there is a claim worth pinning,
        // because the theme's @color/surface and this constant are kept in step by
        // hand and a drift would show up as a band above the header.
        assertEquals("the app bar must be the page surface",
                Palette.SURFACE, Palette.HEADER_SURFACE);
        // An icon button in the bar has to be visible against it.
        double action = Colors.contrastRatio(Palette.HEADER_ACTION_BG,
                Palette.HEADER_SURFACE);
        assertTrue("a header button is invisible on the bar: " + round(action),
                action >= 1.02);
        assertReadable("icon on a header button", Palette.ON_HEADER,
                Palette.HEADER_ACTION_BG);
        // The semester panel is a brand tint, so its own text pair has to hold up.
        assertReadable("label on the header panel", Palette.ON_HEADER_PANEL,
                Palette.HEADER_PANEL);
        double panel = Colors.contrastRatio(Palette.HEADER_PANEL,
                Palette.HEADER_SURFACE);
        assertTrue("the header panel is invisible on the bar: " + round(panel),
                panel >= 1.05);
    }

    @Test
    public void brandTextIsReadableOnThePage() {
        // A primary-coloured label or link sits directly on a card.
        assertReadable("primary on a card", Palette.PRIMARY, Palette.SURFACE_CONTAINER);
        assertReadable("error on a card", Palette.ERROR, Palette.SURFACE_CONTAINER);
        assertReadable("accent on a card", Palette.ACCENT, Palette.SURFACE_CONTAINER);
        assertReadable("success on a card", Palette.SUCCESS, Palette.SURFACE_CONTAINER);
        assertReadable("primary on primary container",
                Palette.PRIMARY, Palette.PRIMARY_CONTAINER);
    }

    @Test
    public void everyCourseColourWorksAsBlockText() {
        // The grid and the cards darken a course colour for its label and lighten
        // it for the block, so both ends of that blend have to hold up.
        for (int color : Palette.COURSE_COLORS) {
            int background = Colors.blend(color, 0xFFFFFFFF, Palette.COURSE_TINT);
            int text = Colors.blend(color, Colors.blend(Palette.ON_SURFACE,
                    0xFFFFFFFF, Palette.COURSE_TEXT_MIX), Palette.COURSE_TEXT_MIX);
            assertReadable(String.format("course colour %08X text on its tint",
                    Integer.valueOf(color)), text, background);
            // The border is decoration, not information: M3 asks a non-text
            // boundary to be *perceptible* (about 1.4:1), not readable.
            int border = Colors.blend(color, 0xFFFFFFFF, Palette.COURSE_BORDER_TINT);
            double ratio = Colors.contrastRatio(border, background);
            assertTrue(String.format("course colour %08X border is invisible: %s",
                    Integer.valueOf(color), round(ratio)), ratio >= 1.4);
            assertTrue(String.format("course colour %08X border is too heavy: %s",
                    Integer.valueOf(color), round(ratio)), ratio <= 2.5);
        }
    }

    @Test
    public void everyCourseRailStaysVisibleOnItsCard() {
        // The rail is the course's colour softened towards the card, and the
        // softening is the risk: push it far enough and the rail that identifies
        // the course disappears into the card it is drawn on. The rail sits on a
        // washed card now, not a white one, so that is the surface it is checked
        // against.
        for (int color : Palette.COURSE_COLORS) {
            int card = UiKit.courseCard(color);
            int rail = UiKit.courseRail(color);
            double ratio = Colors.contrastRatio(rail, card);
            assertTrue(String.format(
                            "course colour %08X has an invisible rail: %s",
                            Integer.valueOf(color), round(ratio)),
                    ratio >= 1.8);
        }
    }

    @Test
    public void aCourseCardStaysReadableAndIsNotJustWhite() {
        // The day list draws the course name in ink and the venue and teacher in
        // the variant role, both straight onto the washed card. A wash deep
        // enough to be visible is also deep enough to cost that pair its
        // contrast, so both are asserted, along with the wash actually being a
        // wash rather than white.
        for (int color : Palette.COURSE_COLORS) {
            int card = UiKit.courseCard(color);
            assertReadable(String.format("course card %08X name", Integer.valueOf(color)),
                    Palette.ON_SURFACE, card);
            assertReadable(String.format("course card %08X meta", Integer.valueOf(color)),
                    Palette.ON_SURFACE_VARIANT, card);
            // Perceptible as a colour rather than as the plain card surface, but
            // nowhere near opaque: the light theme depends on the card reading as
            // light. Distance is the measure here, not contrast, because a wash
            // this light has almost no luminance contrast by definition.
            double fromWhite = distance(card, 0xFFFFFFFF);
            assertTrue(String.format("course card %08X is invisible on the page: %s",
                            Integer.valueOf(color), round(fromWhite)),
                    fromWhite >= 12.0);
            assertTrue(String.format("course card %08X is too saturated to be a wash: %s",
                            Integer.valueOf(color), round(fromWhite)),
                    fromWhite <= 90.0);
        }
    }

    @Test
    public void theTimeTileInsideACardIsReadable() {
        // The period label and the clock times sit on a block tinted from the
        // course colour, which is drawn on the card wash. Two blends stack here,
        // so the pair is checked on the stacked result rather than on either one.
        for (int color : Palette.COURSE_COLORS) {
            assertReadable(String.format("course colour %08X time tile",
                            Integer.valueOf(color)),
                    UiKit.courseInk(color), UiKit.courseTint(color));
        }
    }

    @Test
    public void theCourseColoursAreSpreadAroundTheHueWheel() {
        // The complaint this palette exists to answer: the old set put blue,
        // cyan and slate within 2.5 degrees of each other, so two courses read as
        // one. Hue separation is the property, so it is what is asserted -- a
        // list of nine colours that are merely "different RGB values" can still
        // be nine shades of the same blue.
        //
        // Hue is computed here rather than with android.graphics.Color, because
        // unitTests.returnDefaultValues is off and the framework-free layer has to
        // stay testable on a bare JVM. Same reason Colors does its own arithmetic.
        java.util.List<Double> hues = new java.util.ArrayList<>();
        for (int color : Palette.COURSE_COLORS) {
            hues.add(Double.valueOf(hueOf(color)));
        }
        java.util.Collections.sort(hues);
        for (int i = 0; i < hues.size(); i++) {
            double current = hues.get(i).doubleValue();
            double next = i == hues.size() - 1
                    ? hues.get(0).doubleValue() + 360.0
                    : hues.get(i + 1).doubleValue();
            double gap = next - current;
            assertTrue(String.format(
                            "two course colours are only %.1f degrees apart; they will "
                                    + "read as the same colour",
                            Double.valueOf(gap)),
                    gap >= 6.0);
        }
    }

    /** Hue in degrees, 0-360. Pure arithmetic, so it runs without the framework. */
    private static double hueOf(int color) {
        double red = Colors.red(color) / 255.0;
        double green = Colors.green(color) / 255.0;
        double blue = Colors.blue(color) / 255.0;
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double span = max - min;
        if (span == 0.0) {
            return 0.0;
        }
        double hue;
        if (max == red) {
            hue = ((green - blue) / span) % 6.0;
        } else if (max == green) {
            hue = ((blue - red) / span) + 2.0;
        } else {
            hue = ((red - green) / span) + 4.0;
        }
        hue *= 60.0;
        return hue < 0 ? hue + 360.0 : hue;
    }

    /** Euclidean distance in RGB, for "is this wash distinguishable from white". */
    private static double distance(int first, int second) {
        double dr = Colors.red(first) - Colors.red(second);
        double dg = Colors.green(first) - Colors.green(second);
        double db = Colors.blue(first) - Colors.blue(second);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    @Test
    public void dividersAreVisibleWithoutBeingHeavy() {
        // A divider is not text: it needs to be distinguishable, not readable, and
        // it should stay subtle. Both ends are asserted so it cannot disappear or
        // turn into a black line.
        double onCard = Colors.contrastRatio(Palette.OUTLINE_VARIANT,
                Palette.SURFACE_CONTAINER);
        assertTrue("divider on a card is invisible: " + round(onCard),
                onCard >= 1.08);
        assertTrue("divider on a card is too heavy: " + round(onCard),
                onCard <= 2.0);
    }

    @Test
    public void surfacesStepInToneSoDepthIsVisible() {
        // Each surface must differ from the page it sits on, otherwise a card
        // reads as a hole rather than as a raised sheet.
        double card = Colors.contrastRatio(Palette.SURFACE_CONTAINER, Palette.SURFACE);
        assertTrue("a card does not stand out from the page: " + round(card),
                card >= 1.02);
        double field = Colors.contrastRatio(Palette.SURFACE_CONTAINER_HIGH,
                Palette.SURFACE_CONTAINER);
        assertTrue("a field does not stand out from its card: " + round(field),
                field >= 1.05);
    }

    @Test
    public void theDeprecatedAliasesStillPointAtTheirReplacement() {
        assertEquals(Palette.SURFACE_CONTAINER, Palette.CARD);
        assertEquals(Palette.HEADER_SURFACE, Palette.HEADER);
        assertEquals(Palette.ON_HEADER_VARIANT, Palette.ON_HEADER_SUBTLE);
        assertEquals(Palette.HEADER_ACTION_BORDER, Palette.HEADER_BORDER);
        assertEquals(Palette.ON_SURFACE, Palette.INK);
        assertEquals(Palette.ON_SURFACE_VARIANT, Palette.MUTED);
        assertEquals(Palette.OUTLINE_VARIANT, Palette.LINE);
        assertEquals(Palette.ERROR, Palette.DANGER);
        assertEquals(Palette.ACCENT_CONTAINER, Palette.ACCENT_SOFT);
        assertEquals(Palette.ON_ACCENT_CONTAINER, Palette.ACCENT_DARK);
        assertEquals(Palette.PRIMARY_CONTAINER, Palette.SELECTED_DAY_BG);
        assertEquals(Palette.HEADER_PANEL, Palette.PARITY_PILL);
    }

    @Test
    public void theCourseColoursAreAllDistinctAndOpaque() {
        for (int i = 0; i < Palette.COURSE_COLORS.length; i++) {
            int color = Palette.COURSE_COLORS[i];
            assertEquals("course colour " + i + " is not opaque",
                    0xFF, (color >>> 24) & 0xFF);
            for (int j = i + 1; j < Palette.COURSE_COLORS.length; j++) {
                assertFalse("course colours " + i + " and " + j + " are the same",
                        color == Palette.COURSE_COLORS[j]);
            }
        }
    }

    @Test
    public void theTypeScaleIsOrderedAndSpaced() {
        // A scale with two entries a hair apart is how a UI ends up with five
        // "sizes" that all look the same.
        float[] scale = {
                UiTokens.Type.LABEL_SMALL, UiTokens.Type.LABEL_MEDIUM,
                UiTokens.Type.LABEL_LARGE, UiTokens.Type.BODY_MEDIUM,
                UiTokens.Type.BODY_LARGE, UiTokens.Type.TITLE_SMALL,
                UiTokens.Type.TITLE_MEDIUM, UiTokens.Type.TITLE_LARGE,
                UiTokens.Type.HEADLINE_SMALL
        };
        for (int i = 1; i < scale.length; i++) {
            assertTrue("type sizes " + scale[i - 1] + " and " + scale[i]
                            + " are too close to be different steps",
                    scale[i] - scale[i - 1] >= 1f);
        }
    }

    @Test
    public void theSpacingScaleIsMultiplesOfTheBaseUnit() {
        int[] scale = {
                UiTokens.SPACE_XS, UiTokens.SPACE_S, UiTokens.SPACE_M,
                UiTokens.SPACE_L, UiTokens.SPACE_XL, UiTokens.SPACE_XXL
        };
        for (int value : scale) {
            assertEquals(value + " is not a multiple of the base unit",
                    0, value % UiTokens.SPACE_UNIT);
        }
    }
}
