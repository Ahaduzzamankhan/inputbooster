package dev.inputbooster;

import dev.inputbooster.feature.OverlayLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the corner HUD badge.
 *
 * The badge was invisible in 3.1.5 because its colours were written as RGB
 * literals with an alpha byte of {@code 0x00} ({@code 0x55FFFF} /
 * {@code 0xFFAA00}). Minecraft treats a zero alpha as "do not draw" — its own
 * {@code GuiGraphicsExtractor.text} short-circuits on
 * {@code ARGB.alpha(color) == 0} — so the badge was enqueued every frame and
 * blended away. These tests fail if that ever comes back.
 */
class OverlayLayoutTest {

    /** The alpha byte Minecraft actually reads to decide visibility. */
    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    @Test
    void badgeColorsAreNotFullyTransparent() {
        // The exact bug: alpha 0 means the text is never drawn.
        assertEquals(0xFF, alpha(OverlayLayout.COLOR_AQUA),
            "the aqua badge colour must be fully opaque or Minecraft drops the text");
        assertEquals(0xFF, alpha(OverlayLayout.COLOR_ORANGE),
            "the orange burst badge colour must be fully opaque or Minecraft drops the text");
        assertEquals(0xFF, alpha(OverlayLayout.withOpacity(OverlayLayout.COLOR_AQUA, 1.0f)),
            "at opacity 1.0 the badge must be fully opaque");
        assertTrue(alpha(OverlayLayout.withOpacity(OverlayLayout.COLOR_AQUA, 0.5f)) > 0,
            "a partially transparent badge must still be visible");
    }

    @Test
    void zeroOpacityMakesTheBadgeInvisible() {
        // Opacity 0 is the documented "off" switch and must never leave a
        // visible-but-black badge behind.
        assertEquals(0, alpha(OverlayLayout.withOpacity(OverlayLayout.COLOR_AQUA, 0.0f)));
        assertEquals(0, alpha(OverlayLayout.backgroundColor(0.0f)));
    }

    @Test
    void opacityKeepsTheColourChannels() {
        int aqua = OverlayLayout.withOpacity(OverlayLayout.COLOR_AQUA, 0.8f);
        assertEquals(OverlayLayout.COLOR_AQUA & 0x00FFFFFF, aqua & 0x00FFFFFF,
            "only the alpha byte may change; the aqua channels must survive");
    }

    @Test
    void opacityAndScaleAreClamped() {
        assertEquals(0.0f, OverlayLayout.clampOpacity(-5f));
        assertEquals(1.0f, OverlayLayout.clampOpacity(5f));
        assertEquals(0.0f, OverlayLayout.clampOpacity(Float.NaN));
        assertEquals(0.5f, OverlayLayout.clampScale(0.1f));
        assertEquals(3.0f, OverlayLayout.clampScale(99f));
        assertEquals(1.0f, OverlayLayout.clampScale(Float.NaN));
    }

    @Test
    void badgeIsAnchoredToTheChosenCorner() {
        int screenW = 1920, screenH = 1080, panelW = 60, panelH = 20;
        assertEquals(0, OverlayLayout.originX(OverlayLayout.TOP_LEFT, screenW, panelW));
        assertEquals(0, OverlayLayout.originY(OverlayLayout.TOP_LEFT, screenH, panelH));
        assertEquals(screenW - panelW, OverlayLayout.originX(OverlayLayout.TOP_RIGHT, screenW, panelW));
        assertEquals(0, OverlayLayout.originY(OverlayLayout.TOP_RIGHT, screenH, panelH));
        assertEquals(0, OverlayLayout.originX(OverlayLayout.BOTTOM_LEFT, screenW, panelW));
        assertEquals(screenH - panelH, OverlayLayout.originY(OverlayLayout.BOTTOM_LEFT, screenH, panelH));
        assertEquals(screenW - panelW, OverlayLayout.originX(OverlayLayout.BOTTOM_RIGHT, screenW, panelW));
        assertEquals(screenH - panelH, OverlayLayout.originY(OverlayLayout.BOTTOM_RIGHT, screenH, panelH));
    }

    @Test
    void everyCornerNameIsDistinctAndMatchesItsFlags() {
        String[] names = {
            OverlayLayout.positionName(OverlayLayout.TOP_LEFT),
            OverlayLayout.positionName(OverlayLayout.TOP_RIGHT),
            OverlayLayout.positionName(OverlayLayout.BOTTOM_LEFT),
            OverlayLayout.positionName(OverlayLayout.BOTTOM_RIGHT)
        };
        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                assertFalse(names[i].equals(names[j]),
                    "corner " + i + " and " + j + " report the same name");
            }
            // Names are hyphenated ("bottom-right"), so match on the prefix.
            assertEquals(OverlayLayout.isRight(i), names[i].contains("right"),
                names[i] + " disagrees with isRight(" + i + ")");
            assertEquals(OverlayLayout.isBottom(i), names[i].startsWith("bottom"),
                names[i] + " disagrees with isBottom(" + i + ")");
        }
    }

    @Test
    void unknownPositionFallsBackToTopLeft() {
        // A hand-edited config can hold any int; it must not flip the badge to a
        // corner the player never chose.
        assertEquals(OverlayLayout.originX(OverlayLayout.TOP_LEFT, 800, 50),
            OverlayLayout.originX(99, 800, 50));
        assertEquals(OverlayLayout.originY(OverlayLayout.TOP_LEFT, 600, 20),
            OverlayLayout.originY(-4, 600, 20));
    }

    @Test
    void cpsSparklineColoursTrackTheLimiterCap() {
        int cap = 20;
        assertEquals(OverlayLayout.COLOR_CPS_LOW, OverlayLayout.cpsBarColor(0, cap));
        assertEquals(OverlayLayout.COLOR_CPS_LOW, OverlayLayout.cpsBarColor(11, cap), "55% is still green");
        assertEquals(OverlayLayout.COLOR_CPS_MID, OverlayLayout.cpsBarColor(12, cap), "60% turns yellow");
        assertEquals(OverlayLayout.COLOR_CPS_MID, OverlayLayout.cpsBarColor(16, cap), "80% is still yellow");
        assertEquals(OverlayLayout.COLOR_CPS_HIGH, OverlayLayout.cpsBarColor(17, cap), "85% turns red");
        assertEquals(OverlayLayout.COLOR_CPS_HIGH, OverlayLayout.cpsBarColor(40, cap), "over the cap stays red");
    }

    @Test
    void cpsSparklineColoursAreOpaqueAndSurviveABrokenCap() {
        // A zero or negative cap used to divide by zero; a hand-edited config
        // can still contain one.
        for (int cap : new int[]{0, -5, Integer.MIN_VALUE}) {
            assertEquals(OverlayLayout.COLOR_CPS_HIGH, OverlayLayout.cpsBarColor(20, cap),
                "a cap of " + cap + " must not divide by zero");
        }
        assertEquals(0xFF, alpha(OverlayLayout.COLOR_CPS_LOW));
        assertEquals(0xFF, alpha(OverlayLayout.COLOR_CPS_MID));
        assertEquals(0xFF, alpha(OverlayLayout.COLOR_CPS_HIGH));
        // A negative sample (impossible, but cheap to guard) must not be green.
        assertEquals(OverlayLayout.COLOR_CPS_LOW, OverlayLayout.cpsBarColor(-5, 20));
    }
}