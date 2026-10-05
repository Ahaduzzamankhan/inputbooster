package dev.inputbooster.feature;

/**
 * Corner layout and colour maths for the HUD overlay badge.
 *
 * <p>Kept free of Minecraft types on purpose: the overlay is only ever drawn
 * from the render-state hook, which cannot run inside the automated tests, so
 * the parts that decide whether the badge is visible at all live here where a
 * plain JUnit test can assert them.
 *
 * <p>Colours are ARGB. The alpha byte is what decides visibility — Minecraft's
 * own {@code GuiGraphicsExtractor.text} drops any text whose alpha is zero, so
 * an "RGB-looking" constant such as {@code 0x55FFFF} (alpha {@code 0x00}) makes
 * the whole badge disappear instead of showing cyan text.
 */
public final class OverlayLayout {

    /** Top-left, the default. */
    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_LEFT = 2;
    public static final int BOTTOM_RIGHT = 3;

    /** Fully opaque aqua. The alpha byte must stay {@code 0xFF}. */
    public static final int COLOR_AQUA = 0xFF55FFFF;
    /** Fully opaque orange. The alpha byte must stay {@code 0xFF}. */
    public static final int COLOR_ORANGE = 0xFFFFAA00;

    /** Background panel opacity at {@code opacity = 1}. */
    private static final int MAX_BACKGROUND_ALPHA = 0xB0;

    private OverlayLayout() {
    }

    /** Clamps a user-supplied opacity into {@code 0..1}. */
    public static float clampOpacity(float opacity) {
        if (Float.isNaN(opacity)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, opacity));
    }

    /** Clamps a user-supplied scale into the range the settings screen offers. */
    public static float clampScale(float scale) {
        if (Float.isNaN(scale)) return 1.0f;
        return Math.max(0.5f, Math.min(3.0f, scale));
    }

    /**
     * Applies the overlay opacity to a colour's alpha channel.
     *
     * @param base an ARGB colour; only its alpha byte is replaced
     * @return the colour with alpha scaled by {@code opacity}; at
     *         {@code opacity = 1} the result is fully opaque
     */
    public static int withOpacity(int base, float opacity) {
        int alpha = Math.round(clampOpacity(opacity) * 255.0f);
        return (base & 0x00FFFFFF) | (alpha << 24);
    }

    /** The panel background for the given opacity; fully transparent at 0. */
    public static int backgroundColor(float opacity) {
        int alpha = Math.round(MAX_BACKGROUND_ALPHA * clampOpacity(opacity));
        return alpha << 24;
    }

    /** Left edge of the badge for {@code pos}, given its scaled width. */
    public static int originX(int pos, int screenW, int panelW) {
        return isRight(pos) ? screenW - panelW : 0;
    }

    /** Top edge of the badge for {@code pos}, given its scaled height. */
    public static int originY(int pos, int screenH, int panelH) {
        return isBottom(pos) ? screenH - panelH : 0;
    }

    /** True when {@code pos} selects a right-hand corner. */
    public static boolean isRight(int pos) {
        return pos == TOP_RIGHT || pos == BOTTOM_RIGHT;
    }

    /** True when {@code pos} selects a bottom corner. */
    public static boolean isBottom(int pos) {
        return pos == BOTTOM_LEFT || pos == BOTTOM_RIGHT;
    }

    /** Human-readable corner name, used by the settings screen and stats. */
    public static String positionName(int pos) {
        return switch (pos) {
            case TOP_RIGHT -> "top-right";
            case BOTTOM_LEFT -> "bottom-left";
            case BOTTOM_RIGHT -> "bottom-right";
            default -> "top-left";
        };
    }
}