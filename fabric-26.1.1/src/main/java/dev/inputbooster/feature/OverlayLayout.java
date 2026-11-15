package dev.inputbooster.feature;

/**
 * Screen-space placement for the debug overlay.
 *
 * <p>Pure integer math with no Minecraft types, so the corner and clamping rules
 * can be tested without a game. A panel larger than the screen, or a screen
 * smaller than the panel after a resize, is pinned to 0 instead of being drawn
 * off-screen with a negative coordinate.
 */
public final class OverlayLayout {

    public record Pos(int x, int y) {}

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_LEFT = 2;
    public static final int BOTTOM_RIGHT = 3;
    public static final int TOP_CENTER = 4;

    private OverlayLayout() {}

    public static Pos compute(int screenWidth, int screenHeight, int panelWidth, int panelHeight, int position) {
        int x = switch (position) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - panelWidth;
            case TOP_CENTER -> (screenWidth - panelWidth) / 2;
            default -> 0;
        };
        int y = switch (position) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - panelHeight;
            default -> 0;
        };
        int maxX = Math.max(0, screenWidth - panelWidth);
        int maxY = Math.max(0, screenHeight - panelHeight);
        return new Pos(clamp(x, maxX), clamp(y, maxY));
    }

    private static int clamp(int value, int max) {
        if (value < 0) return 0;
        return Math.min(value, max);
    }

    public static String positionName(int position) {
        return switch (position) {
            case TOP_RIGHT -> "top-right";
            case BOTTOM_LEFT -> "bottom-left";
            case BOTTOM_RIGHT -> "bottom-right";
            case TOP_CENTER -> "top-center";
            default -> "top-left";
        };
    }
}
