package com.weihe.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
        // the course disappears into the white it is drawn on.
        for (int color : Palette.COURSE_COLORS) {
            int rail = Colors.blend(color, Palette.SURFACE_CONTAINER,
                    Palette.COURSE_RAIL_SOFTEN);
            double ratio = Colors.contrastRatio(rail, Palette.SURFACE_CONTAINER);
            assertTrue(String.format(
                            "course colour %08X has an invisible rail: %s",
                            Integer.valueOf(color), round(ratio)),
                    ratio >= 1.8);
        }
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
