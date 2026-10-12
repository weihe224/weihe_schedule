package com.weihe.schedule.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import com.weihe.schedule.model.Course;
import com.weihe.schedule.ui.UiKit;

/**
 * The shape of the course palette, checked without touching storage.
 *
 * <p>These are the claims a later change to the swatches has to keep: nine
 * colours, the default among them, and the three roles a course colour plays
 * ordered so the block is palest and the rail deepest. The compatibility half of
 * the story -- that stored courses are not repainted -- is in
 * {@code data/CourseColorCompatibilityTest}, which needs the store.</p>
 */
public class CoursePaletteShapeTest {

    @Test
    public void theDefaultColourIsOneOfTheSelectableColours() {
        // The picker marks the current colour by comparing values, so a default
        // that is not in the array would open the page with nothing selected.
        boolean found = false;
        for (int candidate : Palette.COURSE_COLORS) {
            if (candidate == Course.DEFAULT_COLOR) {
                found = true;
            }
        }
        assertTrue("the default colour must be a selectable colour", found);
        assertEquals("the default colour is offered first",
                Course.DEFAULT_COLOR, Palette.COURSE_COLORS[0]);
    }

    @Test
    public void thePaletteStillOffersNineColours() {
        // Kept at nine on purpose: the import assigns a colour by hashing a
        // course code into this array, so changing its length would move which
        // colour a given imported course gets.
        assertEquals(9, Palette.COURSE_COLORS.length);
    }

    @Test
    public void theCardWashIsLighterThanTheRailAndDarkerThanTheBlock() {
        // The three roles a course colour plays, in the order they should read:
        // the block tint is the palest, the card wash sits between, and the rail
        // is close to the colour itself. Getting this backwards would put a heavy
        // fill behind the course name.
        for (int color : Palette.COURSE_COLORS) {
            double blockLight = Colors.luminance(UiKit.courseTint(color));
            double cardLight = Colors.luminance(UiKit.courseCard(color));
            double railLight = Colors.luminance(UiKit.courseRail(color));
            assertTrue(String.format("block %08X must be the palest",
                            Integer.valueOf(color)),
                    blockLight > cardLight);
            assertTrue(String.format("rail %08X must be the deepest",
                            Integer.valueOf(color)),
                    railLight < cardLight);
        }
    }

    @Test
    public void theCardWashIsATintRatherThanAFill() {
        // The light theme depends on a card reading as light. A wash deep enough
        // to become a fill would force white text, which is the one thing the
        // change was told not to do.
        for (int color : Palette.COURSE_COLORS) {
            int card = UiKit.courseCard(color);
            double fromWhite = Math.sqrt(
                    Math.pow(Colors.red(card) - 255, 2)
                            + Math.pow(Colors.green(card) - 255, 2)
                            + Math.pow(Colors.blue(card) - 255, 2));
            assertTrue(String.format("course card %08X is too saturated to be a tint",
                            Integer.valueOf(color)),
                    fromWhite <= 90.0);
        }
    }

    @Test
    public void aColourThePaletteNoLongerOffersStillGetsOneSwatch() {
        // Every course saved before the palette was retuned holds a colour the
        // array no longer lists. The picker marks the current colour by value, so
        // dropping it would open the editor with nothing selected -- the page
        // would say the course has no colour, when in fact it has one that
        // predates this palette.
        int legacy = 0xFF94521F;
        int[] swatches = Palette.swatchesFor(legacy);
        assertEquals("a legacy colour is appended rather than replaced",
                Palette.COURSE_COLORS.length + 1, swatches.length);
        assertEquals("the legacy colour is the last swatch",
                legacy, swatches[swatches.length - 1]);
        for (int i = 0; i < Palette.COURSE_COLORS.length; i++) {
            assertEquals("the palette itself is untouched",
                    Palette.COURSE_COLORS[i], swatches[i]);
        }
    }

    @Test
    public void aColourInsideThePaletteAddsNoExtraSwatch() {
        // The tenth swatch is a compatibility affordance, not a standing part of
        // the picker: a course on a current colour is offered the nine and
        // nothing else, so the grid reads the same for every ordinary edit.
        for (int color : Palette.COURSE_COLORS) {
            int[] swatches = Palette.swatchesFor(color);
            assertEquals("a palette colour adds nothing",
                    Palette.COURSE_COLORS.length, swatches.length);
            for (int i = 0; i < Palette.COURSE_COLORS.length; i++) {
                assertEquals(Palette.COURSE_COLORS[i], swatches[i]);
            }
        }
    }
}
